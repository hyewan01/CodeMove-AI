package com.codemove.customer.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codemove.pay.PaymentService;
import com.codemove.pay.TossPayments;

import jakarta.servlet.http.HttpSession;

/**
 * 계약금 결제 (토스페이먼츠 결제위젯): 예약 상세 → 결제 화면 → 토스 결제창 → successUrl / failUrl → 예약 상세.
 * 모두 /move/** 아래라 고객 로그인이 필요함 (WebConfig).
 */
@Controller
public class PaymentController {

	private final PaymentService payments;
	private final TossPayments toss;
	private final CustomerViews views;
	private final com.codemove.move.MoveService moves;

	public PaymentController(PaymentService payments, TossPayments toss, CustomerViews views,
			com.codemove.move.MoveService moves) {
		this.payments = payments;
		this.toss = toss;
		this.views = views;
		this.moves = moves;
	}

	/** 결제 화면: 주문을 만들고 결제위젯을 띄움 */
	@GetMapping("/move/booking/{reservationId}/pay")
	public String pay(@PathVariable long reservationId, HttpSession session, Model model, RedirectAttributes ra) {
		if (!payments.configured()) {
			ra.addFlashAttribute("toast", "온라인 결제를 준비하고 있어요. 담당자에게 문의해 주세요.");
			return "redirect:/move/booking/" + reservationId;
		}
		PaymentService.Checkout co = payments.start(memberId(session), reservationId);
		if (co == null) {
			ra.addFlashAttribute("toast", "견적을 확정한 예약만 계약금을 결제할 수 있어요.");
			return "redirect:/move/booking/" + reservationId;
		}
		String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
		// 고객 화면 공통 값 (쉬운 모드 · 이름 등, CustomerPageController.common 과 같게)
		model.addAttribute("cs", views.view(CustomerSession.of(session), moves.current(memberId(session))));
		model.addAttribute("checkout", co);
		model.addAttribute("reservationId", reservationId);
		model.addAttribute("clientKey", toss.clientKey());
		model.addAttribute("testMode", toss.testMode());
		model.addAttribute("windowMode", toss.windowMode());
		// 결제창 방식의 고객 키: 22~255자, 특수문자 하나 이상, 추측할 수 없게 (결제마다 새로)
		model.addAttribute("customerKey", "cm_" + java.util.UUID.randomUUID().toString().replace("-", "") + "-k");
		model.addAttribute("successUrl", base + "/move/pay/success");
		model.addAttribute("failUrl", base + "/move/pay/fail");
		return "customer/move/pay";
	}

	/** 결제창에서 결제를 마치면 토스가 paymentKey · orderId · amount 를 붙여 보냄 → 서버에서 승인 */
	@GetMapping("/move/pay/success")
	public String success(@RequestParam String paymentKey, @RequestParam String orderId, @RequestParam long amount,
			HttpSession session, RedirectAttributes ra) {
		PaymentService.Outcome o = payments.confirm(memberId(session), orderId, paymentKey, amount);
		ra.addFlashAttribute("toast", o.message());
		return o.reservationId() > 0 ? "redirect:/move/booking/" + o.reservationId() : "redirect:/move?tab=booking";
	}

	@GetMapping("/move/pay/fail")
	public String fail(@RequestParam(required = false) String code, @RequestParam(required = false) String message,
			@RequestParam(required = false) String orderId, HttpSession session, RedirectAttributes ra) {
		PaymentService.Outcome o = payments.fail(memberId(session), orderId, code, message);
		ra.addFlashAttribute("toast", o.message());
		return o.reservationId() > 0 ? "redirect:/move/booking/" + o.reservationId() : "redirect:/move?tab=booking";
	}

	private static long memberId(HttpSession session) {
		return CustomerSession.of(session).getMemberId();
	}
}
