package com.codemove.customer.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.codemove.admin.web.AdminMockData;
import com.codemove.admin.web.AdminSession;
import com.codemove.customer.social.SocialProvider;

import jakarta.servlet.http.HttpSession;
import lombok.Data;

/**
 * 고객 화면 (SCR-U-001~034) - 화면 개발 단계.
 * 데이터는 CustomerMockData, 상태는 CustomerSession 을 사용하며 백엔드 개발 시 Service 호출로 교체합니다.
 */
@Controller
public class CustomerPageController {

	private static final String SIGNUP_KEY = "signupForm";

	@Data
	public static class SignupForm {
		private String name;
		private String phone;
		private boolean termsAccepted;
	}

	@ModelAttribute("cs")
	public CustomerSession customerSession(HttpSession session) {
		return CustomerSession.of(session);
	}

	/* ---------- 온보딩 · 홈 ---------- */

	@GetMapping("/")
	public String onboarding(HttpSession session, Model model) {
		CustomerSession cs = CustomerSession.of(session);
		if (AdminSession.isLoggedIn(session)) {
			return "redirect:/admin";
		}
		if (cs.isLoggedIn()) {
			return "redirect:/home";
		}
		boolean first = session.getAttribute("splashShown") == null;
		session.setAttribute("splashShown", true);
		model.addAttribute("showSplash", first);
		return "customer/onboarding";
	}

	@GetMapping("/home")
	public String home(HttpSession session, Model model) {
		addHomeModel(model, CustomerSession.of(session));
		return "customer/home";
	}

	/** 예약 일정 조회 (프로토타입 Reservation 화면) */
	@GetMapping("/reservation")
	public String reservation() {
		return "customer/reservation";
	}

	/** 일반 / 쉬운 모드 전환 (SCR-U-028~029) */
	@GetMapping("/mode/{mode}")
	public String mode(@PathVariable String mode, HttpSession session,
			@RequestHeader(value = "Referer", required = false) String referer) {
		CustomerSession.of(session).setMode("easy".equals(mode) ? "easy" : "standard");
		return "redirect:" + (referer != null ? referer : "/home");
	}

	/* ---------- 로그인 · 회원가입 (SCR-U-022~027) ---------- */

	@GetMapping("/login")
	public String login(@RequestParam(defaultValue = "onboarding") String backdrop,
			@RequestParam(required = false) String social, @RequestParam(required = false) String error,
			HttpSession session, Model model) {
		CustomerSession cs = CustomerSession.of(session);
		if (AdminSession.isLoggedIn(session)) {
			return "redirect:/admin";
		}
		if (cs.isLoggedIn()) {
			return "redirect:/home";
		}
		model.addAttribute("backdrop", backdrop);
		model.addAttribute("showSplash", false);
		// 간편 로그인(SocialLoginController)에서 돌아온 경우 안내 문구
		SocialProvider.of(social).ifPresent(p -> model.addAttribute("socialError", switch (String.valueOf(error)) {
			case "cancel" -> p.label() + " 로그인을 취소했어요.";
			case "unconfigured" -> p.label() + " 로그인은 아직 준비 중이에요. 휴대전화로 로그인해 주세요.";
			default -> p.label() + " 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.";
		}));
		addHomeModel(model, cs);
		return "customer/auth/login";
	}

	@GetMapping("/login/phone")
	public String phoneLoginForm() {
		return "customer/auth/phone-login";
	}

	/** 휴대전화 로그인 (화면 시연: 010-5678-1234 → 김코드, 010-9445-8224 → 관리자 김선국) */
	@PostMapping("/login/phone")
	public String phoneLogin(@RequestParam String phone, HttpSession session) {
		CustomerSession cs = CustomerSession.of(session);
		String digits = phone.replaceAll("\\D", "");
		AdminMockData.AdminAccount admin = AdminMockData.findAdminByPhone(digits);
		if (admin != null) {
			cs.setLoggedIn(false);
			AdminSession.login(session, admin);
			return CustomerSession.redirectAfterAdminLogin(session);
		}
		AdminSession.logout(session);
		cs.loginAs("01056781234".equals(digits) ? "kimCode" : "kimMove", null);
		cs.setPhone(phone);
		return CustomerSession.redirectAfterLogin(session);
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		CustomerSession.of(session).setLoggedIn(false);
		return "redirect:/";
	}

	@GetMapping("/signup")
	public String signupForm(HttpSession session, Model model) {
		model.addAttribute("signup", signupForm(session));
		return "customer/auth/signup";
	}

	/** 약관 화면으로 가기 전 입력값 임시 저장 */
	@PostMapping("/signup/draft")
	public String signupDraft(@ModelAttribute SignupForm form, HttpSession session) {
		SignupForm saved = signupForm(session);
		saved.setName(form.getName());
		saved.setPhone(form.getPhone());
		return "redirect:/signup/terms";
	}

	@PostMapping("/signup")
	public String signup(@ModelAttribute SignupForm form, HttpSession session) {
		SignupForm saved = signupForm(session);
		saved.setName(form.getName());
		saved.setPhone(form.getPhone());
		if (!saved.isTermsAccepted()) {
			return "redirect:/signup";
		}
		return "redirect:/signup/verify";
	}

	@GetMapping("/signup/terms")
	public String terms() {
		return "customer/auth/terms";
	}

	@PostMapping("/signup/terms")
	public String acceptTerms(HttpSession session) {
		signupForm(session).setTermsAccepted(true);
		return "redirect:/signup";
	}

	@GetMapping("/signup/verify")
	public String verifyForm(HttpSession session, Model model) {
		model.addAttribute("name", signupForm(session).getName());
		return "customer/auth/verify";
	}

	@PostMapping("/signup/verify")
	public String verify() {
		return "redirect:/signup/welcome";
	}

	@GetMapping("/signup/welcome")
	public String welcome(HttpSession session, Model model) {
		String name = signupForm(session).getName();
		model.addAttribute("name", name != null && !name.isBlank() ? name : CustomerSession.of(session).getUserName());
		model.addAttribute("particles", List.of("6514c.svg", "6a3f5.svg", "a82d3.svg", "04e05.svg", "b2424.svg",
				"dca9e.svg", "e0c5f.svg", "48dcd.svg", "1429b.svg", "97ffe.svg", "63c36.svg"));
		return "customer/auth/welcome";
	}

	@PostMapping("/signup/complete")
	public String signupComplete(HttpSession session) {
		SignupForm form = signupForm(session);
		CustomerSession.of(session).signupComplete(form.getName(), form.getPhone());
		session.removeAttribute(SIGNUP_KEY);
		return CustomerSession.redirectAfterLogin(session);
	}

	/* ---------- 영상 견적 (SCR-U-003~010) ---------- */

	@GetMapping("/quote")
	public String quote(Model model) {
		model.addAttribute("houseTypes", CustomerMockData.houseTypes());
		model.addAttribute("packTypes", CustomerMockData.packTypes());
		model.addAttribute("itemOptions", CustomerMockData.itemOptions());
		model.addAttribute("recognizedItems", CustomerMockData.recognizedItems());
		model.addAttribute("addresses", CustomerMockData.addresses());
		model.addAttribute("estimate", CustomerMockData.estimate());
		return "customer/quote/quote";
	}

	/** 이사 요청 (백엔드 개발 시 영상 저장 · TBL_MOVE_REQUEST / TBL_REQUEST_ITEM / TBL_RE_VIDEO 등록) */
	@PostMapping("/quote")
	public String requestQuote(HttpSession session) {
		CustomerSession cs = CustomerSession.of(session);
		cs.setEstimateStatus("waiting-approval");
		cs.setMoveTab("estimate");
		return "redirect:/quote/success";
	}

	@GetMapping("/quote/success")
	public String quoteSuccess() {
		return "customer/quote/success";
	}

	/* ---------- 내 이사 (SCR-U-011~016) ---------- */

	@GetMapping("/move")
	public String move(@RequestParam(required = false) String tab, HttpSession session, Model model) {
		CustomerSession cs = CustomerSession.of(session);
		if (tab != null && List.of("booking", "estimate", "done").contains(tab)) {
			cs.setMoveTab(tab);
		}
		model.addAttribute("tab", cs.getMoveTab());
		model.addAttribute("booking", CustomerMockData.booking());
		model.addAttribute("quote", CustomerMockData.moveQuote());
		model.addAttribute("vendorDoc", CustomerMockData.vendorDoc());
		model.addAttribute("doneMoves", CustomerMockData.doneMoves());
		return "customer/move/move";
	}

	/** 견적 확정 (백엔드 개발 시 TBL_ESTIMATE 확정 · TBL_RESERVATION 등록) */
	@PostMapping("/move/estimate/confirm")
	public String confirmEstimate(HttpSession session,
			@RequestHeader(value = "Referer", required = false) String referer) {
		CustomerSession cs = CustomerSession.of(session);
		if ("approved".equals(cs.getEstimateStatus())) {
			cs.setEstimateStatus("confirmed");
		}
		return "redirect:" + (referer != null ? referer : "/move");
	}

	/** 견적 상세 (견적 금액 · AI 분석 · 이사 정보) */
	@GetMapping("/move/estimate")
	public String estimateDetail(HttpSession session, Model model) {
		String status = CustomerSession.of(session).getEstimateStatus();
		if (!"approved".equals(status) && !"confirmed".equals(status)) {
			return "redirect:/move?tab=estimate";
		}
		model.addAttribute("vendorDoc", CustomerMockData.vendorDoc());
		model.addAttribute("estimateDocItems", CustomerMockData.estimateDocItems());
		model.addAttribute("receiptRows", CustomerMockData.receiptRows());
		model.addAttribute("moveInfoRows", CustomerMockData.moveInfoRows());
		model.addAttribute("detectedItems", CustomerMockData.detectedItems());
		return "customer/move/estimate-detail";
	}

	/** 예약 상세 */
	@GetMapping("/move/reservation")
	public String reservationDetail(Model model) {
		model.addAttribute("paymentRows", CustomerMockData.paymentRows());
		return "customer/move/detail";
	}

	@PostMapping("/move/reservation/request")
	public String saveRequestNote(@RequestParam String requestNote, HttpSession session, RedirectAttributes ra) {
		CustomerSession.of(session).setRequestNote(requestNote.strip());
		ra.addFlashAttribute("toast", "요청사항을 저장했어요.");
		return "redirect:/move/reservation";
	}

	/** 예약 취소 → 견적 도착 상태로 되돌림 */
	@PostMapping("/move/reservation/cancel")
	public String cancelReservation(HttpSession session) {
		CustomerSession cs = CustomerSession.of(session);
		cs.setEstimateStatus("approved");
		cs.setMoveTab("estimate");
		return "redirect:/home";
	}

	/* ---------- 상담 (SCR-U-017~019) ---------- */

	@GetMapping("/chat")
	public String chat(Model model) {
		model.addAttribute("topics", CustomerMockData.chatTopics());
		return "customer/chat/chat";
	}

	/* ---------- 더보기 (SCR-U-020~021, 031~034) ---------- */

	@GetMapping("/more")
	public String more() {
		return "customer/more/more";
	}

	@GetMapping("/more/profile")
	public String profile() {
		return "customer/more/profile";
	}

	/** 휴대폰 번호 변경 (백엔드 개발 시 본인인증 검증 후 TBL_MEMBER 수정) */
	@PostMapping("/more/profile/phone")
	public String changePhone(@RequestParam String phone, HttpSession session) {
		if (phone.replaceAll("\\D", "").length() == 11) {
			CustomerSession.of(session).setPhone(phone);
		}
		return "redirect:/more/profile";
	}

	@GetMapping("/more/estimates")
	public String estimates(Model model) {
		model.addAttribute("estimates", CustomerMockData.estimateHistory());
		return "customer/more/estimates";
	}

	@GetMapping("/more/reservations")
	public String reservations(Model model) {
		Map<String, List<CustomerMockData.HistoryRow>> byYear = CustomerMockData.pastReservationsByYear();
		model.addAttribute("pastByYear", byYear);
		model.addAttribute("pastCount", byYear.values().stream().mapToInt(rows -> rows.size()).sum());
		return "customer/more/reservations";
	}

	@GetMapping("/more/reviews")
	public String reviews(@RequestParam(defaultValue = "0") int write, HttpSession session, Model model) {
		List<CustomerMockData.MyReview> reviews = new ArrayList<>(CustomerSession.of(session).getSubmittedReviews());
		reviews.add(CustomerMockData.pastReview());
		model.addAttribute("writing", write == 1);
		model.addAttribute("myReviews", reviews);
		return "customer/more/reviews";
	}

	/** 후기 등록 (백엔드 개발 시 TBL_REVIEW / TBL_REVIEW_PHOTO 저장) */
	@PostMapping("/more/reviews")
	public String submitReview(@RequestParam(defaultValue = "5") int rating,
			@RequestParam(defaultValue = "") String content,
			@RequestParam(required = false) List<MultipartFile> photos,
			HttpSession session, RedirectAttributes ra) {
		int stars = Math.max(1, Math.min(5, rating));
		int photoCount = photos == null ? 0 : (int) photos.stream().filter(p -> !p.isEmpty()).count();
		List<CustomerMockData.MyReview> submitted = CustomerSession.of(session).getSubmittedReviews();
		submitted.replaceAll(r -> new CustomerMockData.MyReview(r.stars(), r.date(), r.route(), r.content(),
				r.moveType(), r.photoCount(), false));
		submitted.add(0, new CustomerMockData.MyReview("★".repeat(stars) + "☆".repeat(5 - stars), "방금 전",
				"강남구 → 서초구", content.isBlank() ? "친절하고 안전하게 이사를 마쳤어요." : content.strip(),
				"원룸 · 일반포장", Math.min(photoCount, 5), true));
		ra.addFlashAttribute("submitted", true);
		return "redirect:/more/reviews";
	}

	/* ---------- 알림 (SCR-U-030) ---------- */

	@GetMapping("/notifications")
	public String notifications(Model model) {
		model.addAttribute("alerts", CustomerMockData.alerts());
		model.addAttribute("categories", CustomerMockData.alertCategories());
		return "customer/notifications";
	}

	@PostMapping("/notifications/read/{id}")
	@ResponseBody
	public ResponseEntity<Void> readNotification(@PathVariable int id, HttpSession session) {
		CustomerSession.of(session).getUnreadNotifications().remove(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/notifications/read-all")
	public String readAllNotifications(HttpSession session) {
		CustomerSession.of(session).getUnreadNotifications().clear();
		return "redirect:/notifications";
	}

	/* ---------- 공통 ---------- */

	private void addHomeModel(Model model, CustomerSession cs) {
		model.addAttribute("booking", CustomerMockData.booking());
		model.addAttribute("homeReviews", CustomerMockData.homeReviews());
		model.addAttribute("easyStatus", CustomerMockData.easyStatus(cs.getEstimateStatus()));
	}

	private SignupForm signupForm(HttpSession session) {
		SignupForm form = (SignupForm) session.getAttribute(SIGNUP_KEY);
		if (form == null) {
			form = new SignupForm();
			session.setAttribute(SIGNUP_KEY, form);
		}
		return form;
	}
}
