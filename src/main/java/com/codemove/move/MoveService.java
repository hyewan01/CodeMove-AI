package com.codemove.move;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.FileStore;
import com.codemove.member.Member;
import com.codemove.member.MemberMapper;
import com.codemove.notification.NotificationService;

/**
 * 고객 이사 흐름: 견적 요청 → (관리자 승인) → 견적 확정 = 예약 → 이사 완료 → 후기.
 * 고객 화면의 진행 상태(estimateStatus)는 가장 최근 견적 요청의 견적서·예약 상태로 정합니다.
 */
@Service
public class MoveService {

	/** RSV_STATUS 공통코드 */
	public static final String BOOKED = "01", DONE = "02", CANCELLED = "03";

	/**
	 * 지금 진행 중인 이사 (가장 최근 요청).
	 * status: none / waiting-approval / approved / confirmed (프로토타입 estimateStatus)
	 */
	public record CurrentMove(MoveRequest request, Quote quote, Reservation reservation, String status) {

		public static final CurrentMove NONE = new CurrentMove(null, null, null, "none");
	}

	/** 견적 요청 화면 입력값 (SCR-U-003~009) */
	public record RequestForm(String houseType, String packType, LocalDate moveDate, String moveTime,
			String fromAddress, String fromDetail, String fromZipcode, String toAddress, String toDetail,
			String toZipcode, List<RequestItem> items) {
	}

	/** 예상 견적 계산식 (quote.js estimate() 와 같음): 기본요금 + 부피 × 단가, 반포장 할증, 만원 단위 */
	public static final long BASE = 100_000, PER_CBM = 24_000, MINUS = 10_000, PLUS = 70_000;
	public static final double PARTIAL_RATE = 1.15;

	public record Estimate(long amount, long min, long max) {
	}

	public static Estimate estimate(double cbm, String packType) {
		double raw = BASE + cbm * PER_CBM;
		long amount = Math.round(("02".equals(packType) ? raw * PARTIAL_RATE : raw) / 10_000) * 10_000;
		return new Estimate(amount, amount - MINUS, amount + PLUS);
	}

	private final MoveMapper moves;
	private final QuoteService quotes;
	private final MemberMapper members;
	private final NotificationService notifications;
	private final FileStore files;

	public MoveService(MoveMapper moves, QuoteService quotes, MemberMapper members, NotificationService notifications,
			FileStore files) {
		this.moves = moves;
		this.quotes = quotes;
		this.members = members;
		this.notifications = notifications;
		this.files = files;
	}

	public CurrentMove current(long memberId) {
		List<MoveRequest> requests = moves.listRequests(memberId);
		if (requests.isEmpty()) {
			return CurrentMove.NONE;
		}
		MoveRequest r = requests.get(0);
		Quote q = quotes.findByRequest(r.getRequestId());
		Reservation v = moves.findReservationByRequest(r.getRequestId());
		String status;
		if (v != null && DONE.equals(v.getStatus())) {
			status = "none";
		} else if (q == null || QuoteService.REVIEWING.equals(q.getStatus())) {
			status = "waiting-approval";
		} else if (QuoteService.APPROVED.equals(q.getStatus())) {
			status = "approved";
		} else if (QuoteService.CONFIRMED.equals(q.getStatus()) && v != null && BOOKED.equals(v.getStatus())) {
			status = "confirmed";
		} else {
			status = "none";
		}
		return new CurrentMove(r, q, v, status);
	}

	/* ---------- 견적 요청 ---------- */

	@Transactional
	public MoveRequest request(long memberId, RequestForm f, MultipartFile video) {
		MoveRequest r = new MoveRequest();
		r.setMemberId(memberId);
		r.setHouseType(f.houseType());
		r.setPackType(f.packType());
		r.setMoveDate(f.moveDate());
		r.setMoveTime(f.moveDate() == null ? null : f.moveTime());
		r.setPreferTime(f.moveDate() == null || f.moveTime() == null ? "03"
				: Integer.parseInt(f.moveTime().substring(0, 2)) < 12 ? "01" : "02");
		r.setFromAddress(f.fromAddress());
		r.setFromDetail(f.fromDetail());
		r.setFromZipcode(f.fromZipcode());
		r.setToAddress(f.toAddress());
		r.setToDetail(f.toDetail());
		r.setToZipcode(f.toZipcode());
		double cbm = f.items().stream().mapToDouble(i -> i.getVolume() == null ? 0 : i.getVolume()).sum();
		r.setAiCbm(Math.round(cbm * 100) / 100.0);
		Estimate est = estimate(cbm, f.packType());
		r.setAiEstAmount(est.amount());
		r.setAiEstMin(est.min());
		r.setAiEstMax(est.max());
		r.setAiAnalysisSec(38);
		r.setAiConfidence(94);
		moves.insertRequest(r);
		for (RequestItem item : f.items()) {
			item.setRequestId(r.getRequestId());
			moves.insertRequestItem(item);
		}
		if (video != null && !video.isEmpty()) {
			ReVideo v = new ReVideo();
			v.setRequestId(r.getRequestId());
			String name = video.getOriginalFilename() == null ? "video" : video.getOriginalFilename();
			v.setFileName(name.length() > 200 ? name.substring(name.length() - 200) : name);
			v.setFilePath(files.save("videos", video));
			moves.insertVideo(v);
		}
		Member m = members.findMember(memberId);
		quotes.createForRequest(r, m.getMemberName(), m.getPhone(), f.items());
		notifications.toAdmin("01", "견적서 등록", m.getMemberName() + " 고객님의 신규 견적 요청이 접수되었습니다.", "/admin/estimates",
				false);
		notifications.toMember(memberId, "01", "견적 요청이 접수됐어요", "담당자 확인이 끝나면 견적 정보를 알려드려요.", "/more/estimates");
		return r;
	}

	/* ---------- 견적 확정 = 예약 ---------- */

	/** 견적 도착(승인) 상태의 견적을 확정하고 예약 완료로 */
	@Transactional
	public boolean confirm(long memberId) {
		CurrentMove c = current(memberId);
		if (!"approved".equals(c.status())) {
			return false;
		}
		Quote q = c.quote();
		quotes.updateStatus(q.getQuoteId(), QuoteService.CONFIRMED);
		LocalDate date = q.getWorkDate() != null ? q.getWorkDate()
				: c.request().getMoveDate() != null ? c.request().getMoveDate() : LocalDate.now().plusDays(14);
		String time = q.getWorkTime() != null ? q.getWorkTime() : c.request().getMoveTime();
		Reservation existing = c.reservation();
		if (existing != null) {
			moves.reopenReservation(existing.getReservationId(), date, time);
		} else {
			Reservation v = new Reservation();
			v.setRequestId(c.request().getRequestId());
			v.setMoveDate(date);
			v.setMoveTime(time);
			moves.insertReservation(v);
		}
		Member m = members.findMember(memberId);
		notifications.toAdmin("04", "이사 예약", m.getMemberName() + " 고객님이 견적을 확정해 이사 예약을 신청했습니다.",
				"/admin/reservations", false);
		notifications.fire("예약 관리 · 예약 확정", memberId, m.getMemberName());
		return true;
	}

	/** 예약 취소 → 견적은 다시 '견적 승인'(견적 도착)으로 */
	@Transactional
	public boolean cancel(long memberId) {
		CurrentMove c = current(memberId);
		if (!"confirmed".equals(c.status())) {
			return false;
		}
		moves.cancelReservation(c.reservation().getReservationId(), "01");
		quotes.updateStatus(c.quote().getQuoteId(), QuoteService.APPROVED);
		Member m = members.findMember(memberId);
		notifications.toAdmin("05", "예약 취소", m.getMemberName() + " 고객님이 이사 예약을 취소했습니다.", "/admin/reservations",
				true);
		notifications.toMember(memberId, "05", "이사 예약이 취소되었어요", "다시 예약하려면 도착한 견적을 확정해주세요.", "/move");
		return true;
	}

	@Transactional
	public void updateMemo(long memberId, String memo) {
		CurrentMove c = current(memberId);
		if (c.reservation() != null) {
			String m = memo == null ? "" : memo.strip();
			moves.updateMemo(c.reservation().getReservationId(), m.length() > 500 ? m.substring(0, 500) : m);
		}
	}

	/* ---------- 내역 ---------- */

	public List<MoveRequest> requests(long memberId) {
		return moves.listRequests(memberId);
	}

	public List<RequestItem> requestItems(long requestId) {
		return moves.listRequestItems(requestId);
	}

	public List<MoveLine> reservations(long memberId) {
		return moves.listMoveLines(memberId);
	}

	/* ---------- 후기 ---------- */

	/** 후기를 쓸 수 있는 가장 최근 이사 완료 예약 (없으면 null) */
	public MoveLine reviewTarget(long memberId) {
		List<Long> reviewed = moves.listReviews(memberId, null).stream().map(r -> r.getReservationId()).toList();
		return moves.listMoveLines(memberId).stream()
				.filter(l -> DONE.equals(l.getStatus()) && !reviewed.contains(l.getReservationId()))
				.max((a, b) -> a.getMoveDate().compareTo(b.getMoveDate()))
				.orElse(null);
	}

	@Transactional
	public boolean writeReview(long memberId, int rating, String content, List<MultipartFile> photos) {
		MoveLine target = reviewTarget(memberId);
		if (target == null) {
			return false;
		}
		Review r = new Review();
		r.setReservationId(target.getReservationId());
		r.setRating(Math.max(1, Math.min(5, rating)));
		String text = content == null || content.isBlank() ? "친절하고 안전하게 이사를 마쳤어요." : content.strip();
		r.setContent(text.length() > 500 ? text.substring(0, 500) : text);
		moves.insertReview(r);
		int order = 0;
		if (photos != null) {
			for (MultipartFile p : photos) {
				if (p != null && !p.isEmpty() && order < 5) {
					moves.insertReviewPhoto(r.getReviewId(), files.save("reviews", p), ++order);
				}
			}
		}
		notifications.toAdmin("09", "리뷰 등록", target.getMemberName() + " 고객님이 " + r.getRating() + "점 후기를 남겼습니다.",
				"/admin/reviews", false);
		return true;
	}

	public List<Review> myReviews(long memberId) {
		return moves.listReviews(memberId, null);
	}

	/** 홈 칭찬후기: 4점 이상 최근 순 */
	public List<Review> praiseReviews(int limit) {
		List<Review> all = moves.listReviews(null, 4);
		return all.subList(0, Math.min(limit, all.size()));
	}
}
