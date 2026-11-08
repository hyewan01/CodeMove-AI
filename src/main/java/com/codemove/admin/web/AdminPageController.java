package com.codemove.admin.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 관리자 화면 (SCR-A-001~023). 프로토타입 App의 page 상태를 URL로 나눴습니다.
 * 데이터는 AdminMockData 목업 + AdminState(세션)이며, 백엔드 개발 시 서비스 계층으로 대체합니다.
 * 목록의 필터·검색·페이지는 쿼리 파라미터, 상세/수정 모달은 modal·id 파라미터로 엽니다.
 */
@Controller
@RequestMapping("/admin")
public class AdminPageController {

	/** 목록 한 페이지 행 수 (프로토타입 4건) */
	private static final int PAGE_SIZE = 4;

	/** 아직 옮기지 않은 화면: URL → (화면 이름, Screen ID) */
	private static final Map<String, String[]> PENDING = Map.ofEntries(
			Map.entry("/admin/estimates", new String[] { "견적 관리", "SCR-A-001" }),
			Map.entry("/admin/estimates/new", new String[] { "견적서 등록", "SCR-A-003" }),
			Map.entry("/admin/chat-management", new String[] { "채팅 관리", "SCR-A-005" }),
			Map.entry("/admin/chats", new String[] { "채팅 목록", "SCR-A-006" }),
			Map.entry("/admin/chat-management/operation", new String[] { "채팅 운영 설정", "SCR-A-008" }),
			Map.entry("/admin/chat-management/entry", new String[] { "첫 화면/퀵메뉴 설정", "SCR-A-009" }),
			Map.entry("/admin/chat-management/answers", new String[] { "답변 설정 관리", "SCR-A-010" }),
			Map.entry("/admin/notifications", new String[] { "알림 관리", "SCR-A-013" }),
			Map.entry("/admin/notifications/history", new String[] { "알림 내역", "SCR-A-014" }),
			Map.entry("/admin/notifications/messaging", new String[] { "메시징 운영 설정", "SCR-A-015" }));

	/** 헤더 알림 패널을 열면 빨간 점 제거 */
	@PostMapping("/notifications/panel-read")
	public ResponseEntity<Void> notificationPanelRead(HttpSession session) {
		AdminState.of(session).setNotificationsRead(true);
		return ResponseEntity.noContent().build();
	}

	/** 홈 · 대시보드 (SCR-A-016) */
	@GetMapping({ "", "/" })
	public String home(HttpSession session, Model model) {
		model.addAttribute("stats", AdminMockData.stats());
		model.addAttribute("recentReservations", AdminMockData.recentReservations(3));
		return "admin/home";
	}

	/* ---------- 예약 관리 (SCR-A-011) ---------- */

	public record ReservationRow(AdminMockData.Reservation record, String displayStatus, String paymentStatus) {
	}

	private static String paymentStatus(AdminMockData.Reservation r, AdminState state) {
		if ("반려".equals(r.status())) return "결제 취소";
		return state.getPaidReservations().contains(r.id()) ? "결제 완료" : "결제 대기";
	}

	@GetMapping("/reservations")
	public String reservations(@RequestParam(defaultValue = "전체") String status,
			@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(required = false) Integer id, HttpSession session, Model model) {
		AdminState state = AdminState.of(session);
		List<ReservationRow> all = AdminMockData.reservations().stream()
				.map(r -> new ReservationRow(r, r.displayStatus(), paymentStatus(r, state)))
				.toList();
		String query = q.trim().toLowerCase();
		List<ReservationRow> filtered = all.stream()
				.filter(row -> "전체".equals(status) || row.displayStatus().equals(status))
				.filter(row -> query.isEmpty()
						|| (row.record().name() + row.record().phone() + row.record().appliedDate()).toLowerCase().contains(query))
				.sorted((a, b) -> b.record().appliedDate().compareTo(a.record().appliedDate()))
				.toList();
		addPage(model, filtered, page);
		model.addAttribute("status", status);
		model.addAttribute("q", q);
		long done = all.stream().filter(row -> "예약 완료".equals(row.displayStatus())).count();
		model.addAttribute("statusCounts", List.of(
				new long[] { done, Math.round(done * 100.0 / all.size()) },
				new long[] { all.size() - done, Math.round((all.size() - done) * 100.0 / all.size()) }));
		if (id != null) {
			all.stream().filter(row -> row.record().id() == id).findFirst()
					.ifPresent(row -> model.addAttribute("selected", row));
		}
		model.addAttribute("deposit", "132,000원");
		return "admin/reservations";
	}

	@PostMapping("/reservations/{id}/paid")
	public String reservationPaid(@PathVariable int id, @RequestParam(defaultValue = "") String back,
			HttpSession session) {
		AdminState.of(session).getPaidReservations().add(id);
		return "redirect:" + safeBack(back, "/admin/reservations?id=" + id);
	}

	/* ---------- 회원 관리 (SCR-A-023) ---------- */

	@GetMapping("/members")
	public String members(@RequestParam(defaultValue = "전체") String type, @RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String modal,
			@RequestParam(required = false) Long id, HttpSession session, Model model) {
		AdminState state = AdminState.of(session);
		String query = q.trim().toLowerCase();
		List<AdminMockData.Member> filtered = state.getMembers().stream()
				.filter(m -> "전체".equals(type) || m.type().equals(type))
				.filter(m -> query.isEmpty() || m.name().toLowerCase().contains(query) || m.phone().contains(query))
				.toList();
		addPage(model, filtered, page);
		model.addAttribute("type", type);
		model.addAttribute("q", q);
		model.addAttribute("modal", modal);
		if (id != null) {
			state.getMembers().stream().filter(m -> m.id() == id).findFirst()
					.ifPresent(m -> model.addAttribute("selected", m));
		}
		return "admin/members";
	}

	@PostMapping("/members")
	public String registerMember(@RequestParam String name, @RequestParam String phone, @RequestParam String joined,
			@RequestParam(defaultValue = "고객") String type, HttpSession session) {
		AdminState state = AdminState.of(session);
		long id = System.currentTimeMillis();
		state.getMembers().add(new AdminMockData.Member(id, name.trim(), "member" + id, type, "고객".equals(type),
				phone.trim(), joined.trim(), ""));
		state.fire("회원 관리 · 신규 회원이 가입되었을 시");
		return "redirect:/admin/members";
	}

	@PostMapping("/members/{id}/type")
	public String changeMemberType(@PathVariable long id, @RequestParam String type,
			@RequestParam(defaultValue = "") String back, HttpSession session) {
		List<AdminMockData.Member> members = AdminState.of(session).getMembers();
		members.replaceAll(m -> m.id() == id ? m.withType(type) : m);
		return "redirect:" + safeBack(back, "/admin/members");
	}

	@PostMapping("/members/{id}/delete")
	public String deleteMember(@PathVariable long id, @RequestParam(defaultValue = "") String back,
			HttpSession session) {
		AdminState.of(session).getMembers().removeIf(m -> m.id() == id);
		return "redirect:" + safeBack(back, "/admin/members");
	}

	/* ---------- 리뷰 관리 (SCR-A-012) ---------- */

	@GetMapping("/reviews")
	public String reviews(@RequestParam(defaultValue = "전체") String rating,
			@RequestParam(required = false) Integer reply, @RequestParam(required = false) Integer edit,
			HttpSession session, Model model) {
		AdminState state = AdminState.of(session);
		List<AdminMockData.Review> all = AdminMockData.reviews();
		List<AdminMockData.Review> visible = "전체".equals(rating) ? all
				: all.stream().filter(r -> String.valueOf(r.rating()).equals(rating)).toList();
		double average = all.stream().mapToInt(r -> r.rating()).average().orElse(0);
		model.addAttribute("reviews", visible);
		model.addAttribute("reviewCount", all.size());
		model.addAttribute("average", String.format("%.1f", average));
		model.addAttribute("averageStars", (int) Math.round(Double.parseDouble(String.format("%.1f", average))));
		model.addAttribute("distribution", java.util.stream.IntStream.of(5, 4, 3, 2, 1).mapToObj(star -> {
			long count = all.stream().filter(r -> r.rating() == star).count();
			return new long[] { star, count, Math.round(count * 100.0 / all.size()) };
		}).toList());
		model.addAttribute("rating", rating);
		model.addAttribute("replies", state.getReviewReplies());
		// 프로토타입은 처음에 2번 리뷰의 답변 입력창이 열린 상태
		model.addAttribute("replyingTo", reply != null ? reply : (edit == null ? 2 : -1));
		model.addAttribute("editing", edit);
		return "admin/reviews";
	}

	@PostMapping("/reviews/{id}/reply")
	public String replyReview(@PathVariable int id, @RequestParam String content,
			@RequestParam(defaultValue = "전체") String rating, HttpSession session) {
		AdminState state = AdminState.of(session);
		boolean isNew = !state.getReviewReplies().containsKey(id);
		if (!content.isBlank()) {
			state.getReviewReplies().put(id, content.trim());
			if (isNew) {
				state.fire("리뷰 관리 · 관리자가 답변 할 시");
			}
		}
		return "redirect:/admin/reviews?reply=-1&rating=" + java.net.URLEncoder.encode(rating, java.nio.charset.StandardCharsets.UTF_8);
	}

	/* ---------- 아직 옮기지 않은 화면 ---------- */

	@GetMapping({ "/estimates", "/estimates/new", "/chat-management", "/chats", "/chat-management/operation",
			"/chat-management/entry", "/chat-management/answers", "/notifications", "/notifications/history",
			"/notifications/messaging" })
	public String pending(HttpServletRequest request, Model model) {
		String[] page = PENDING.get(request.getRequestURI().substring(request.getContextPath().length()));
		model.addAttribute("pageName", page[0]);
		model.addAttribute("screenId", page[1]);
		return "admin/pending";
	}

	@GetMapping("/chats/{chatId}")
	public String chatDetail(@PathVariable String chatId, Model model) {
		model.addAttribute("pageName", "채팅 상세");
		model.addAttribute("screenId", "SCR-A-007");
		return "admin/pending";
	}

	/* ---------- 공통 ---------- */

	/** 목록 페이지 나누기: rows, page, pageCount, rowOffset */
	static void addPage(Model model, List<?> filtered, int page) {
		int pageCount = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
		int current = Math.min(Math.max(1, page), pageCount);
		int from = (current - 1) * PAGE_SIZE;
		model.addAttribute("rows", filtered.subList(from, Math.min(from + PAGE_SIZE, filtered.size())));
		model.addAttribute("page", current);
		model.addAttribute("pageCount", pageCount);
		model.addAttribute("rowOffset", from);
	}

	/** 같은 관리자 화면 안의 주소로만 되돌아가기 */
	static String safeBack(String back, String fallback) {
		return back.startsWith("/admin") ? back : fallback;
	}
}
