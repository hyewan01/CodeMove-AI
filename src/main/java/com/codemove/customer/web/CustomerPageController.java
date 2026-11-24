package com.codemove.customer.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
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

import com.codemove.admin.web.AdminSession;
import com.codemove.chat.ChatService;
import com.codemove.schedule.BookingService;
import com.codemove.common.Fmt;
import com.codemove.common.LunarDays;
import com.codemove.move.ItemCatalog;
import com.codemove.customer.social.SocialProvider;
import com.codemove.customer.web.CustomerViews.MoveView;
import com.codemove.member.Member;
import com.codemove.member.MemberService;
import com.codemove.member.MemberService.PhoneLogin;
import com.codemove.move.MoveService;
import com.codemove.move.MoveService.CurrentMove;
import com.codemove.move.RequestItem;
import com.codemove.notification.NotificationService;

import jakarta.servlet.http.HttpSession;
import lombok.Data;

/**
 * 고객 화면 (SCR-U-001~034). 회원·이사·후기·알림은 DB(서비스), 로그인 상태는 CustomerSession.
 * 휴대전화 본인인증 문자는 아직 연동하지 않아 인증번호는 아무 6자리나 통과합니다.
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

	/** 견적 요청 품목 (quote.js writeItemInputs) */
	@Data
	public static class QuoteItemForm {
		private String name;
		private String size;
		private int count;
		private double cbm;
		/** AI 원본 인식명 · 매핑방식(01~05, 고객이 고치거나 추가하면 05) · 확인 필요 */
		private String detectedName;
		private String mapping;
		private boolean needsReview;
	}

	@Data
	public static class QuoteForm {
		private String houseType;
		private String packType;
		private String moveDate;
		private String moveTime;
		private boolean dateUndecided;
		private String fromAddress;
		private String fromAddressDetail;
		private String fromZipcode;
		private String toAddress;
		private String toAddressDetail;
		private String toZipcode;
		/** 층수 · 나르는 방법 E 엘리베이터 / L 사다리차 / S 계단 */
		private Integer fromFloor;
		private String fromCarry;
		private Integer toFloor;
		private String toCarry;
		/** 보관이사 보관 일수 */
		private Integer storageDays;
		private List<QuoteItemForm> items = new ArrayList<>();
		/** 추가 작업(견적서 옵션 칸) · 관리자에게 남길 말 · 귀중품 */
		private List<QuoteOptionForm> options = new ArrayList<>();
		private String memo;
		private String valuables;
	}

	/** 추가 작업 한 줄 (quote.js 3단계): 견적서 옵션 코드 01~08, 두 선택, 수량, 다른 업체에 맡김 */
	@Data
	public static class QuoteOptionForm {
		private String code;
		private boolean first;
		private boolean second;
		private int count;
		private boolean other;
	}

	/** 화면 값 → 계산기 옵션 (모르는 코드 · 06 수작업 · 아무것도 안 고른 줄은 뺌) */
	static List<com.codemove.estimate.EstimateCalculator.OptionChoice> options(List<QuoteOptionForm> rows) {
		List<com.codemove.estimate.EstimateCalculator.OptionChoice> out = new ArrayList<>();
		for (QuoteOptionForm f : rows == null ? List.<QuoteOptionForm>of() : rows) {
			if (f == null) {
				continue;
			}
			var c = new com.codemove.estimate.EstimateCalculator.OptionChoice(f.getCode(), f.isFirst(),
					// 피아노는 일반 · 그랜드 중 하나
					"01".equals(f.getCode()) && f.isFirst() ? false : f.isSecond(), Math.min(f.getCount(), 20),
					f.isOther());
			if (c.index() >= 0 && c.index() != 5 && c.count() > 0 && (c.other() || c.first() || c.second())) {
				out.add(c);
			}
		}
		return out;
	}

	/** 관리자에게 남길 말 + 귀중품 → 한 줄 (예약 메모 · 견적서 비고, 500자 칸) */
	static String memo(String memo, String valuables) {
		String m = memo == null ? "" : memo.strip(), v = valuables == null ? "" : valuables.strip();
		String out = (v.isEmpty() ? "" : "[귀중품] " + v) + (!v.isEmpty() && !m.isEmpty() ? " / " : "")
				+ (m.isEmpty() ? "" : "[요청] " + m);
		return out.isEmpty() ? null : cut(out, 300);
	}

	/** 층수(1~80)와 나르는 방법 → 견적 조건 */
	static com.codemove.estimate.EstimateCalculator.Site site(Integer floor, String carry) {
		Integer f = floor == null || floor < 1 || floor > 80 ? null : floor;
		return new com.codemove.estimate.EstimateCalculator.Site(f,
				com.codemove.estimate.EstimateCalculator.Site.parse(carry));
	}

	/** 층수를 따로 받지 않으면 상세주소("3층 301호")에서 읽음 */
	static com.codemove.estimate.EstimateCalculator.Site site(Integer floor, String carry, String detail) {
		return site(floor != null ? floor : com.codemove.estimate.Floors.of(detail), carry);
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}

	static Integer storageDays(String packType, Integer days) {
		return "03".equals(packType) && days != null && days > 0 && days <= 365 ? days : null;
	}

	private final MemberService members;
	private final MoveService moves;
	private final NotificationService notifications;
	private final CustomerViews views;
	private final ChatService chats;
	private final BookingService booking;

	private final com.codemove.pay.PaymentService payments;

	public CustomerPageController(MemberService members, MoveService moves, NotificationService notifications,
			CustomerViews views, ChatService chats, BookingService booking, com.codemove.pay.PaymentService payments) {
		this.payments = payments;
		this.booking = booking;
		this.members = members;
		this.moves = moves;
		this.notifications = notifications;
		this.views = views;
		this.chats = chats;
	}

	private CurrentMove current(HttpSession session) {
		CustomerSession cs = CustomerSession.of(session);
		return cs.isLoggedIn() ? moves.current(cs.getMemberId()) : CurrentMove.NONE;
	}

	/** 모든 고객 화면 공통: cs (로그인 · 이름 · 진행 상태 · 알림 수) */
	@ModelAttribute
	public void common(HttpSession session, Model model) {
		CustomerSession cs = CustomerSession.of(session);
		if (cs.isLoggedIn()) {
			Member me = members.find(cs.getMemberId());
			if (me == null || !"Y".equals(me.getIsActive())) {
				cs.logout();
			}
		}
		CurrentMove c = current(session);
		model.addAttribute("cs", views.view(cs, c));
		model.addAttribute("current", c);
	}

	private static long memberId(HttpSession session) {
		return CustomerSession.of(session).getMemberId();
	}

	/* ---------- 온보딩 · 홈 ---------- */

	@GetMapping("/")
	public String onboarding(HttpSession session, Model model) {
		if (AdminSession.isLoggedIn(session)) {
			return "redirect:/admin";
		}
		if (CustomerSession.of(session).isLoggedIn()) {
			return "redirect:/home";
		}
		boolean first = session.getAttribute("splashShown") == null;
		session.setAttribute("splashShown", true);
		model.addAttribute("showSplash", first);
		return "customer/onboarding";
	}

	@GetMapping("/home")
	public String home(Model model) {
		addHomeModel(model);
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
		if (AdminSession.isLoggedIn(session)) {
			return "redirect:/admin";
		}
		if (CustomerSession.of(session).isLoggedIn()) {
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
		addHomeModel(model);
		return "customer/auth/login";
	}

	@GetMapping("/login/phone")
	public String phoneLoginForm() {
		return "customer/auth/phone-login";
	}

	/** 로그인 도움말: 휴대폰 번호가 바뀐 경우 (휴대전화 로그인은 번호가 곧 계정이라 '계정 찾기'가 따로 없음) */
	@GetMapping("/login/help")
	public String loginHelp() {
		return "customer/auth/login-help";
	}

	/**
	 * 휴대전화 로그인: 관리자 번호면 관리자 화면, 회원이면 고객 홈, 처음 보는 번호면 회원가입으로.
	 * (샘플: 010-9445-8224 관리자 김선국 / 010-5678-1234 김코드 / 010-4321-8765 김무브)
	 */
	@PostMapping("/login/phone")
	public String phoneLogin(@RequestParam String phone, HttpSession session) {
		CustomerSession cs = CustomerSession.of(session);
		PhoneLogin result = members.loginByPhone(phone);
		if (result.admin() != null) {
			cs.logout();
			AdminSession.login(session, result.admin());
			return CustomerSession.redirectAfterAdminLogin(session);
		}
		AdminSession.logout(session);
		if (result.member() == null) {
			SignupForm form = signupForm(session);
			form.setPhone(Fmt.phone(phone));
			return "redirect:/signup?notice=new";
		}
		cs.login(result.member().getMemberId(), result.member().getMemberName());
		return CustomerSession.redirectAfterLogin(session);
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		CustomerSession.of(session).logout();
		return "redirect:/";
	}

	@GetMapping("/signup")
	public String signupForm(@RequestParam(required = false) String notice, HttpSession session, Model model) {
		SignupForm form = signupForm(session);
		CustomerSession.PendingSocial pending = CustomerSession.of(session).getPendingSocial();
		if (pending != null && (form.getName() == null || form.getName().isBlank())) {
			form.setName(pending.name());
		}
		model.addAttribute("signup", form);
		model.addAttribute("notice", switch (String.valueOf(notice)) {
			case "new" -> "가입된 회원이 아니에요. 회원가입 후 이용해 주세요.";
			case "exists" -> "이미 가입된 휴대폰 번호예요. 휴대전화로 로그인해 주세요.";
			case "social" -> pending != null ? SocialProvider.of(pending.provider()).map(p -> p.label()).orElse("간편")
					+ " 계정으로 처음 오셨어요. 회원가입을 마치면 연결돼요." : null;
			default -> null;
		});
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
		if (members.isRegistered(saved.getPhone())) {
			return "redirect:/signup?notice=exists";
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
		model.addAttribute("name", signupForm(session).getName());
		model.addAttribute("particles", List.of("6514c.svg", "6a3f5.svg", "a82d3.svg", "04e05.svg", "b2424.svg",
				"dca9e.svg", "e0c5f.svg", "48dcd.svg", "1429b.svg", "97ffe.svg", "63c36.svg"));
		return "customer/auth/welcome";
	}

	/** 회원가입 완료 → TBL_MEMBER (+ 간편 로그인으로 왔으면 TBL_SOCIAL) */
	@PostMapping("/signup/complete")
	public String signupComplete(HttpSession session) {
		SignupForm form = signupForm(session);
		if (form.getName() == null || form.getName().isBlank() || Fmt.digits(form.getPhone()).length() < 10
				|| !form.isTermsAccepted()) {
			return "redirect:/signup";
		}
		CustomerSession cs = CustomerSession.of(session);
		CustomerSession.PendingSocial pending = cs.getPendingSocial();
		Member m;
		try {
			m = members.signup(form.getName(), form.getPhone(), pending != null ? pending.provider() : null,
					pending != null ? pending.socialNo() : null);
		} catch (DuplicateKeyException e) {
			return "redirect:/signup?notice=exists";
		}
		session.removeAttribute(SIGNUP_KEY);
		AdminSession.logout(session);
		cs.login(m.getMemberId(), m.getMemberName());
		notifications.toAdmin("14", "신규 가입", m.getMemberName() + " 고객님이 신규 회원으로 가입했습니다.", "/admin/members", false);
		notifications.fire("회원 관리 · 신규 회원이 가입되었을 시", m.getMemberId(), m.getMemberName());
		return CustomerSession.redirectAfterLogin(session);
	}

	/* ---------- 영상 견적 (SCR-U-003~010) ---------- */

	@GetMapping("/quote")
	public String quote(Model model) {
		model.addAttribute("houseTypes", views.houseTypes());
		model.addAttribute("packTypes", views.packTypes());
		model.addAttribute("catalog", AiDemo.catalog());
		model.addAttribute("categories", AiDemo.categories());
		model.addAttribute("recognizedItems", AiDemo.recognizedItems());
		model.addAttribute("addresses", AiDemo.addresses());
		// 이사 날짜 달력에서 고를 수 없는 날 (yyyy-MM-dd → 휴무 · 불가 · 마감), 오늘부터 1년
		LocalDate today = LocalDate.now();
		Map<String, String> blocked = new java.util.LinkedHashMap<>();
		booking.blockedDates(today, today.plusYears(1))
				.forEach((d, reason) -> blocked.put(d.toString(), BookingService.label(reason)));
		model.addAttribute("blockedDates", blocked);
		// 손 없는 날 (yyyy-MM-dd → 음 3.9): 이사가 몰려 비용이 오를 수 있다고 달력에 표시
		model.addAttribute("noSonDays", LunarDays.noSonDays(today, today.plusYears(1)));
		// 공휴일 (yyyy-MM-dd → 이름): 손 없는 날과 함께 빨간 날짜로
		model.addAttribute("holidays", com.codemove.common.Holidays.between(today, today.plusYears(1)));
		// 추가 작업 (견적서 옵션 칸, 06 수작업은 나르는 방법으로 계산하므로 뺌)
		List<Map<String, String>> extras = new ArrayList<>();
		String[][] rows = com.codemove.move.EstimateDoc.OPTION_ROWS;
		for (int i = 0; i < rows.length; i++) {
			if (i != 5) {
				extras.add(Map.of("code", String.format("%02d", i + 1), "name", rows[i][0], "first", rows[i][1],
						"second", rows[i][2]));
			}
		}
		model.addAttribute("extras", extras);
		return "customer/quote/quote";
	}

	/** 이사 요청: 영상 저장 · TBL_MOVE_REQUEST / TBL_REQUEST_ITEM / TBL_RE_VIDEO + 검토용 견적서 */
	@PostMapping("/quote")
	public String requestQuote(@ModelAttribute QuoteForm form, @RequestParam(required = false) MultipartFile video,
			HttpSession session, RedirectAttributes ra) {
		if (form.getFromAddress() == null || form.getFromAddress().isBlank() || form.getToAddress() == null
				|| form.getToAddress().isBlank()) {
			return "redirect:/quote";
		}
		List<RequestItem> items = new ArrayList<>();
		for (QuoteItemForm f : form.getItems()) {
			if (f == null || f.getName() == null || f.getName().isBlank() || f.getCount() <= 0) {
				continue;
			}
			RequestItem i = new RequestItem();
			i.setItemName(f.getName().length() > 50 ? f.getName().substring(0, 50) : f.getName());
			i.setCategory(CustomerViews.categoryOf(f.getName()));
			i.setItemSize(CustomerViews.sizeCode(f.getSize()));
			i.setQuantity(Math.min(99999, f.getCount()));
			// 품목표에 있는 품목은 화면 값 대신 표의 크기별 기준 부피로 다시 계산
			ItemCatalog.Item known = ItemCatalog.find(f.getName());
			double cbm = known != null ? known.size(i.getItemSize()).cbm() * i.getQuantity() : f.getCbm();
			i.setVolume(Math.min(999.99, Math.max(0, Math.round(cbm * 100) / 100.0)));
			i.setConfidence(90);
			i.setNeedsReview(f.isNeedsReview() ? "Y" : "N");
			// ITEM_SOURCE 01 AI 인식 / 02 사용자 추가 (AI 원본 이름이 없으면 고객이 넣은 품목)
			boolean fromAi = f.getDetectedName() != null && !f.getDetectedName().isBlank();
			i.setSource(fromAi ? "01" : "02");
			i.setDetectedName(fromAi ? cut(f.getDetectedName().strip(), 100) : null);
			i.setMappingType(f.getMapping() != null && f.getMapping().matches("0[1-5]") ? f.getMapping() : "05");
			items.add(i);
		}
		LocalDate date = form.isDateUndecided() ? null : Fmt.parseDate(form.getMoveDate());
		if (date != null && !moves.isBookable(date)) {
			// 화면에서 막지만, 그사이 마감됐거나 휴무로 바뀐 경우
			ra.addFlashAttribute("toast", "선택한 날짜는 예약이 마감됐거나 휴무일이에요. 다른 날짜를 골라주세요.");
			return "redirect:/quote";
		}
		String time = MoveService.moveTime(form.getMoveTime());
		String house = form.getHouseType() != null && form.getHouseType().matches("0[1-4]") ? form.getHouseType() : "01";
		String pack = form.getPackType() != null && form.getPackType().matches("0[1-4]") ? form.getPackType() : "04";
		// 분석 단계에서 이미 올린 영상은 세션의 주소로 붙임 (화면은 영상을 두 번 보내지 않음)
		String savedUrl = (String) session.getAttribute(QuoteAnalysisController.VIDEO_URL);
		String savedName = (String) session.getAttribute(QuoteAnalysisController.VIDEO_NAME);
		Integer sec = (Integer) session.getAttribute(QuoteAnalysisController.ANALYSIS_SEC);
		String analysisId = (String) session.getAttribute(QuoteAnalysisController.ANALYSIS_ID);
		moves.request(memberId(session), new MoveService.RequestForm(house, pack, date, time, form.getFromAddress().trim(),
				blank(form.getFromAddressDetail()), zip(form.getFromZipcode()), form.getToAddress().trim(),
				blank(form.getToAddressDetail()), zip(form.getToZipcode()), items,
				site(form.getFromFloor(), form.getFromCarry(), form.getFromAddressDetail()),
				site(form.getToFloor(), form.getToCarry(), form.getToAddressDetail()),
				storageDays(pack, form.getStorageDays()), options(form.getOptions()),
				memo(form.getMemo(), form.getValuables())), video, savedUrl, savedName, sec, analysisId);
		session.removeAttribute(QuoteAnalysisController.VIDEO_URL);
		session.removeAttribute(QuoteAnalysisController.VIDEO_NAME);
		session.removeAttribute(QuoteAnalysisController.ANALYSIS_SEC);
		session.removeAttribute(QuoteAnalysisController.ANALYSIS_ID);
		CustomerSession.of(session).setMoveTab("estimate");
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
		CurrentMove c = current(session);
		if (tab != null && List.of("booking", "estimate", "done").contains(tab)) {
			cs.setMoveTab(tab);
		}
		String active = cs.getMoveTab() != null ? cs.getMoveTab() : "confirmed".equals(c.status()) ? "booking" : "estimate";
		model.addAttribute("tab", active);
		addMoveModel(model, c);
		// 견적 · 예약은 여러 건일 수 있어 목록으로 (누르면 상세)
		List<CurrentMove> all = moves.all(memberId(session));
		model.addAttribute("estimateCards", views.estimateCards(all));
		model.addAttribute("bookingCards", views.bookingCards(all));
		model.addAttribute("doneMoves", views.doneMoves(memberId(session)));
		return "customer/move/move";
	}

	/** 견적 상세 (견적 정보 목록에서 한 건): 영수증 · 견적서 두 가지로 보기 */
	@GetMapping("/move/request/{requestId}")
	public String requestDetail(@PathVariable long requestId, HttpSession session, Model model) {
		CurrentMove c = moves.forRequest(memberId(session), requestId);
		if (c == null) {
			return "redirect:/move?tab=estimate";
		}
		addMoveModel(model, c);
		model.addAttribute("req", c);
		// 견적 확정은 가장 최근 요청(지금 진행 중인 이사)일 때만
		model.addAttribute("canConfirm", "approved".equals(c.status())
				&& current(session).request() != null && current(session).request().getRequestId() == requestId);
		return "customer/move/request-detail";
	}

	/** 견적서: 관리자 화면과 똑같은 양식 (관리자 CSS 를 쓰므로 따로 그려 상세 화면에 끼움) */
	@GetMapping("/move/request/{requestId}/sheet")
	public String requestSheet(@PathVariable long requestId, HttpSession session, Model model) {
		CurrentMove c = moves.forRequest(memberId(session), requestId);
		if (c == null || c.quote() == null) {
			return "redirect:/move?tab=estimate";
		}
		model.addAttribute("doc", views.estimateDoc(c.quote()));
		// 옵션 칸 줄(피아노 · 돌침대 …)은 고르지 않았어도 양식대로 보임
		model.addAttribute("optionRows", com.codemove.move.EstimateDoc.OPTION_ROWS);
		return "customer/move/sheet";
	}

	/** 예약 상세 (예약 정보 목록에서 한 건): 요약 + 예약 취소 */
	@GetMapping("/move/booking/{reservationId}")
	public String bookingDetail(@PathVariable long reservationId, HttpSession session, Model model) {
		CurrentMove c = moves.forReservation(memberId(session), reservationId);
		if (c == null) {
			return "redirect:/move?tab=booking";
		}
		addMoveModel(model, c);
		model.addAttribute("summary", views.bookingSummary(c));
		// 계약금: 견적을 확정한 예약이면 결제 버튼, 낸 뒤면 수단 · 영수증
		model.addAttribute("payable", payments.payable(c));
		model.addAttribute("payReady", payments.configured());
		model.addAttribute("paid", payments.paid(reservationId));
		return "customer/move/booking-detail";
	}

	@PostMapping("/move/booking/{reservationId}/cancel")
	public String cancelBooking(@PathVariable long reservationId, HttpSession session, RedirectAttributes ra) {
		boolean ok = moves.cancel(memberId(session), reservationId);
		ra.addFlashAttribute("toast", ok ? "이사 예약을 취소했어요." : "취소할 수 없는 예약이에요.");
		return "redirect:/move/booking/" + reservationId;
	}

	/** 견적 확정 → 예약 */
	@PostMapping("/move/estimate/confirm")
	public String confirmEstimate(HttpSession session,
			@RequestHeader(value = "Referer", required = false) String referer) {
		if (moves.confirm(memberId(session))) {
			CustomerSession.of(session).setMoveTab("booking");
		}
		return "redirect:" + (referer != null ? referer : "/move");
	}

	/** 견적 상세 (견적 금액 · AI 분석 · 이사 정보) */
	@GetMapping("/move/estimate")
	public String estimateDetail(HttpSession session, Model model) {
		CurrentMove c = current(session);
		if (!"approved".equals(c.status()) && !"confirmed".equals(c.status())) {
			return "redirect:/move?tab=estimate";
		}
		addMoveModel(model, c);
		return "customer/move/estimate-detail";
	}

	/** 예약 상세 */
	@GetMapping("/move/reservation")
	public String reservationDetail(HttpSession session, Model model) {
		CurrentMove c = current(session);
		if (!"confirmed".equals(c.status())) {
			return "redirect:/move?tab=booking";
		}
		addMoveModel(model, c);
		return "customer/move/detail";
	}

	@PostMapping("/move/reservation/request")
	public String saveRequestNote(@RequestParam String requestNote, HttpSession session, RedirectAttributes ra) {
		moves.updateMemo(memberId(session), requestNote);
		ra.addFlashAttribute("toast", "요청사항을 저장했어요.");
		return "redirect:/move/reservation";
	}

	/** 예약 취소 (확정한 견적이면 견적 도착 상태로 되돌림) */
	@PostMapping("/move/reservation/cancel")
	public String cancelReservation(@RequestParam(defaultValue = "") String back, HttpSession session,
			RedirectAttributes ra) {
		boolean ok = moves.cancel(memberId(session));
		ra.addFlashAttribute("toast", ok ? "이사 예약을 취소했어요." : "취소할 예약이 없어요.");
		if ("booking".equals(back)) {
			CustomerSession.of(session).setMoveTab("booking");
			return "redirect:/move?tab=booking";
		}
		CustomerSession.of(session).setMoveTab("estimate");
		return "redirect:/home";
	}

	/* ---------- 상담 (SCR-U-017~019) ---------- */

	@GetMapping("/chat")
	public String chat(HttpSession session, Model model) {
		long memberId = memberId(session);
		notifications.markChatRead(memberId); // 상담 화면을 열면 하단 채팅 점 · 상담 답변 알림은 읽음
		model.addAttribute("topics", chats.quickMenuTitles());
		model.addAttribute("greeting", chats.settings().getGreeting());
		model.addAttribute("hours", chats.consultHours());
		model.addAttribute("history", chats.customerThread(memberId));
		return "customer/chat/chat";
	}

	/** 상담 메시지 → 챗봇 답변 (상담사 연결 요청이면 대기 상태로) */
	@PostMapping("/chat/messages")
	@ResponseBody
	public Map<String, Object> chatMessage(@RequestParam(defaultValue = "") String text,
			@RequestParam(required = false) MultipartFile file, HttpSession session) {
		return chats.customerAsk(memberId(session), text, file);
	}

	/** 상담사 답변 확인 (after 이후 메시지) */
	@GetMapping("/chat/messages")
	@ResponseBody
	public List<Map<String, Object>> chatUpdates(@RequestParam(defaultValue = "0") long after, HttpSession session) {
		return chats.customerUpdates(memberId(session), after);
	}

	/* ---------- 더보기 (SCR-U-020~021, 031~034) ---------- */

	@GetMapping("/more")
	public String more() {
		return "customer/more/more";
	}

	@GetMapping("/more/profile")
	public String profile(@RequestParam(required = false) String error, Model model) {
		model.addAttribute("phoneError", "taken".equals(error) ? "다른 회원이 쓰고 있는 번호예요." : null);
		return "customer/more/profile";
	}

	/** 휴대폰 번호 변경 (본인인증 문자 연동 전: 인증번호 확인은 화면에서만) */
	@PostMapping("/more/profile/phone")
	public String changePhone(@RequestParam String phone, HttpSession session) {
		if (Fmt.digits(phone).length() == 11 && !members.changePhone(memberId(session), phone)) {
			return "redirect:/more/profile?error=taken";
		}
		return "redirect:/more/profile";
	}

	/* ---------- 회원 탈퇴: 안내 → 예/아니요 → 휴대폰 본인인증 → 완료 ---------- */

	private static final String WITHDRAW_KEY = "withdrawConfirmed";

	/** 예약이 확정된 이사가 남아 있으면 탈퇴 전에 먼저 취소하거나 이사를 마쳐야 함 */
	private boolean hasBookedMove(HttpSession session) {
		return "confirmed".equals(current(session).status());
	}

	@GetMapping("/more/withdraw")
	public String withdrawInfo(HttpSession session, Model model) {
		session.removeAttribute(WITHDRAW_KEY);
		model.addAttribute("blocked", hasBookedMove(session));
		return "customer/more/withdraw";
	}

	/** [예, 탈퇴할게요] → 본인인증 */
	@PostMapping("/more/withdraw")
	public String withdrawConfirm(HttpSession session) {
		if (hasBookedMove(session)) {
			return "redirect:/more/withdraw";
		}
		session.setAttribute(WITHDRAW_KEY, memberId(session));
		return "redirect:/more/withdraw/verify";
	}

	@GetMapping("/more/withdraw/verify")
	public String withdrawVerifyForm(HttpSession session, Model model) {
		if (!Long.valueOf(memberId(session)).equals(session.getAttribute(WITHDRAW_KEY))) {
			return "redirect:/more/withdraw";
		}
		Member me = members.find(memberId(session));
		model.addAttribute("maskedPhone", Fmt.maskPhone(me.getPhone()));
		return "customer/more/withdraw-verify";
	}

	/** 본인인증 완료 → 탈퇴 (문자 인증 연동 전: 인증번호 6자리 입력만 확인) */
	@PostMapping("/more/withdraw/verify")
	public String withdraw(@RequestParam(defaultValue = "") String code, HttpSession session) {
		long id = memberId(session);
		if (!Long.valueOf(id).equals(session.getAttribute(WITHDRAW_KEY)) || !code.matches("\\d{6}")) {
			return "redirect:/more/withdraw";
		}
		if (hasBookedMove(session)) {
			return "redirect:/more/withdraw";
		}
		String name = CustomerSession.of(session).getUserName();
		members.withdraw(id);
		session.removeAttribute(WITHDRAW_KEY);
		CustomerSession.of(session).logout();
		session.setAttribute("withdrawnName", name);
		return "redirect:/withdrawn";
	}

	/** 탈퇴 완료 (로그인 없이 보는 화면) */
	@GetMapping("/withdrawn")
	public String withdrawn(HttpSession session, Model model) {
		Object name = session.getAttribute("withdrawnName");
		if (name == null) {
			return "redirect:/";
		}
		model.addAttribute("name", name);
		return "customer/more/withdrawn";
	}

	@GetMapping("/more/estimates")
	public String estimates(HttpSession session, Model model) {
		model.addAttribute("estimates", views.estimateHistory(memberId(session)));
		addMoveModel(model, current(session));
		return "customer/more/estimates";
	}

	@GetMapping("/more/reservations")
	public String reservations(HttpSession session, Model model) {
		Map<String, List<CustomerViews.HistoryRow>> byYear = views.pastReservations(memberId(session));
		model.addAttribute("pastByYear", byYear);
		model.addAttribute("pastCount", byYear.values().stream().mapToInt(rows -> rows.size()).sum());
		addMoveModel(model, current(session));
		return "customer/more/reservations";
	}

	@GetMapping("/more/reviews")
	public String reviews(@RequestParam(defaultValue = "0") int write, HttpSession session, Model model) {
		long memberId = memberId(session);
		CustomerViews.ReviewTarget target = views.reviewTarget(memberId);
		boolean submitted = model.containsAttribute("submitted");
		model.addAttribute("writing", write == 1 && target != null);
		model.addAttribute("target", target);
		model.addAttribute("myReviews", views.myReviews(memberId, submitted));
		return "customer/more/reviews";
	}

	/** 후기 등록 → TBL_REVIEW / TBL_REVIEW_PHOTO */
	@PostMapping("/more/reviews")
	public String submitReview(@RequestParam(defaultValue = "5") int rating,
			@RequestParam(defaultValue = "") String content,
			@RequestParam(required = false) List<MultipartFile> photos,
			HttpSession session, RedirectAttributes ra) {
		if (moves.writeReview(memberId(session), rating, content, photos)) {
			ra.addFlashAttribute("submitted", true);
		}
		return "redirect:/more/reviews";
	}

	/* ---------- 알림 (SCR-U-030) ---------- */

	@GetMapping("/notifications")
	public String notifications(HttpSession session, Model model) {
		List<CustomerViews.Alert> alerts = views.alerts(memberId(session));
		Map<String, List<CustomerViews.Alert>> groups = new java.util.LinkedHashMap<>();
		alerts.forEach(a -> groups.computeIfAbsent(a.day(), k -> new ArrayList<>()).add(a));
		model.addAttribute("alerts", alerts);
		model.addAttribute("alertGroups", groups);
		model.addAttribute("categories", CustomerViews.ALERT_CATEGORIES);
		return "customer/notifications";
	}

	@PostMapping("/notifications/read/{id}")
	@ResponseBody
	public ResponseEntity<Void> readNotification(@PathVariable long id, HttpSession session) {
		notifications.markRead(memberId(session), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/notifications/read-all")
	public String readAllNotifications(HttpSession session) {
		notifications.markAllRead(memberId(session));
		return "redirect:/notifications";
	}

	/* ---------- 공통 ---------- */

	private void addHomeModel(Model model) {
		CurrentMove c = (CurrentMove) model.getAttribute("current");
		MoveView mv = addMoveModel(model, c);
		model.addAttribute("homeReviews", views.homeReviews());
		model.addAttribute("easyStatus", views.easyStatus(mv));
	}

	/** 현재 이사 화면 값 (mv) + 템플릿이 쓰던 이름 (booking, quote, vendorDoc …) */
	private MoveView addMoveModel(Model model, CurrentMove c) {
		MoveView mv = views.move(c == null ? CurrentMove.NONE : c);
		model.addAttribute("mv", mv);
		model.addAttribute("booking", mv.booking());
		model.addAttribute("quote", mv.quote());
		model.addAttribute("vendorDoc", mv.vendorDoc());
		model.addAttribute("receiptRows", mv.receiptRows());
		model.addAttribute("moveInfoRows", mv.moveInfoRows());
		model.addAttribute("paymentRows", mv.paymentRows());
		model.addAttribute("detectedItems", mv.detectedItems());
		return mv;
	}

	private SignupForm signupForm(HttpSession session) {
		SignupForm form = (SignupForm) session.getAttribute(SIGNUP_KEY);
		if (form == null) {
			form = new SignupForm();
			session.setAttribute(SIGNUP_KEY, form);
		}
		return form;
	}

	private static String blank(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String zip(String s) {
		String d = Fmt.digits(s);
		return d.length() == 5 ? d : null;
	}
}
