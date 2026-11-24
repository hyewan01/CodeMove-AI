package com.codemove.pay;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.member.Member;
import com.codemove.member.MemberMapper;
import com.codemove.move.MoveMapper;
import com.codemove.move.MoveService;
import com.codemove.move.MoveService.CurrentMove;
import com.codemove.move.QuoteService;
import com.codemove.notification.NotificationService;

/**
 * 계약금 온라인 결제. 견적을 확정한(예약 확정) 예약만, 견적서의 계약금 10% 금액으로.
 * <ol>
 * <li>start: 주문번호를 만들어 TBL_PAYMENT 에 '결제 대기'로 남김 → 화면이 결제위젯을 띄움</li>
 * <li>토스 결제창에서 결제 → successUrl 로 paymentKey · orderId · amount 가 돌아옴</li>
 * <li>confirm: 우리 주문과 고객 · 금액이 맞는지 확인하고 토스 승인 API 호출 → 성공이면 결제 완료 +
 * 예약 PAY_STATUS 02 (관리자 예약 관리에 바로 '결제 완료') + 알림</li>
 * </ol>
 * 고객이 결제 금액을 바꿔 보내도 서버에 남긴 금액과 다르면 승인하지 않음.
 */
@Service
public class PaymentService {

	/** 결제 화면에 넘길 값 */
	public record Checkout(String orderId, String orderName, long amount, String customerName, String customerPhone) {
	}

	/** 결제 결과 (화면 안내용) */
	public record Outcome(boolean ok, long reservationId, String message) {
	}

	private static final String ORDER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PaymentMapper payments;
	private final MoveService moves;
	private final MoveMapper moveMapper;
	private final MemberMapper members;
	private final NotificationService notifications;
	private final TossPayments toss;

	public PaymentService(PaymentMapper payments, MoveService moves, MoveMapper moveMapper, MemberMapper members,
			NotificationService notifications, TossPayments toss) {
		this.payments = payments;
		this.moves = moves;
		this.moveMapper = moveMapper;
		this.members = members;
		this.notifications = notifications;
		this.toss = toss;
	}

	public boolean configured() {
		return toss.configured();
	}

	/** 계약금을 낼 수 있는 예약인지: 내 예약 · 진행 중 · 견적 확정 · 아직 결제 전 */
	public boolean payable(CurrentMove c) {
		return c != null && c.reservation() != null && MoveService.BOOKED.equals(c.reservation().getStatus())
				&& !"02".equals(c.reservation().getPayStatus()) && c.quote() != null
				&& QuoteService.CONFIRMED.equals(c.quote().getStatus()) && c.quote().getDepositAmount() != null
				&& c.quote().getDepositAmount() > 0;
	}

	/** 결제 시작: 주문번호를 만들고 '결제 대기'로 남김. 낼 수 없는 예약이면 null */
	@Transactional
	public Checkout start(long memberId, long reservationId) {
		CurrentMove c = moves.forReservation(memberId, reservationId);
		if (!payable(c)) {
			return null;
		}
		Member m = members.findMember(memberId);
		Payment p = new Payment();
		p.setReservationId(reservationId);
		p.setMemberId(memberId);
		p.setOrderId(orderId(reservationId));
		p.setOrderName("명품익스프레스 이사 계약금 (" + c.reservation().getReservationNo() + ")");
		p.setAmount(c.quote().getDepositAmount());
		payments.insert(p);
		return new Checkout(p.getOrderId(), p.getOrderName(), p.getAmount(), m.getMemberName(),
				m.getPhone() == null ? "" : m.getPhone().replaceAll("\\D", ""));
	}

	/** 결제창에서 돌아온 뒤 승인 */
	@Transactional
	public Outcome confirm(long memberId, String orderId, String paymentKey, long amount) {
		Payment p = orderId == null ? null : payments.findByOrder(orderId);
		if (p == null || p.getMemberId() != memberId) {
			return new Outcome(false, 0, "결제 정보를 찾지 못했어요.");
		}
		if (Payment.DONE.equals(p.getStatus())) {
			return new Outcome(true, p.getReservationId(), "이미 결제가 끝났어요."); // 새로 고침
		}
		if (!Payment.WAITING.equals(p.getStatus()) || p.getAmount() != amount || paymentKey == null
				|| paymentKey.isBlank()) {
			payments.markFailed(orderId, "AMOUNT_MISMATCH", "주문 금액과 결제 금액이 달라요");
			return new Outcome(false, p.getReservationId(), "결제 금액이 주문과 달라 승인하지 않았어요.");
		}
		TossPayments.Result r = toss.confirm(paymentKey, orderId, amount);
		if (!r.ok()) {
			payments.markFailed(orderId, r.code(), r.message());
			return new Outcome(false, p.getReservationId(), r.message() == null ? "결제 승인에 실패했어요." : r.message());
		}
		payments.markDone(orderId, paymentKey, r.method(), r.receiptUrl());
		moveMapper.markPaid(p.getReservationId());
		Member m = members.findMember(memberId);
		notifications.toAdmin("10", "계약금 결제", m.getMemberName() + " 고객님이 계약금 " + String.format("%,d", amount)
				+ "원을 결제했습니다. (" + r.method() + ")", "/admin/reservations?id=" + p.getReservationId(), false);
		notifications.toMember(memberId, "10", "계약금 결제가 완료되었어요",
				String.format("%,d", amount) + "원이 결제됐어요. 잔금은 이사 당일에 결제해요.", "/move/booking/" + p.getReservationId());
		return new Outcome(true, p.getReservationId(), "계약금 결제가 완료되었어요.");
	}

	/** 결제창에서 취소 · 실패하고 돌아옴 */
	@Transactional
	public Outcome fail(long memberId, String orderId, String code, String message) {
		Payment p = orderId == null ? null : payments.findByOrder(orderId);
		if (p == null || p.getMemberId() != memberId) {
			return new Outcome(false, 0, message == null ? "결제를 하지 못했어요." : message);
		}
		payments.markFailed(orderId, code, message);
		return new Outcome(false, p.getReservationId(),
				"PAY_PROCESS_CANCELED".equals(code) ? "결제를 취소했어요." : message == null ? "결제를 하지 못했어요." : message);
	}

	/** 예약의 완료된 결제 (영수증 · 수단 표시용), 없으면 null */
	public Payment paid(long reservationId) {
		List<Payment> list = payments.listByReservation(reservationId);
		return list.stream().filter(p -> Payment.DONE.equals(p.getStatus())).findFirst().orElse(null);
	}

	/** CM287-AB12CD34EF56: 예약 번호 + 추측할 수 없는 글자 (토스 규칙: 영문 · 숫자 · - _ =, 6~64자) */
	static String orderId(long reservationId) {
		StringBuilder sb = new StringBuilder("CM").append(reservationId).append('-');
		for (int i = 0; i < 12; i++) {
			sb.append(ORDER_CHARS.charAt(RANDOM.nextInt(ORDER_CHARS.length())));
		}
		return sb.toString();
	}
}
