package com.codemove.admin.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.common.Fmt;
import com.codemove.member.MemberService;
import com.codemove.move.MoveLine;
import com.codemove.move.MoveMapper;
import com.codemove.move.QuoteService;
import com.codemove.notification.NotificationService;

import jakarta.servlet.http.HttpSession;

/**
 * 관리자 화면 (SCR-A-011 예약 · 012 리뷰 · 016 홈 · 023 회원). 프로토타입 App의 page 상태를 URL로 나눴습니다.
 * 목록의 필터·검색·페이지는 쿼리 파라미터, 상세/수정 모달은 modal·id 파라미터로 엽니다.
 */
@Controller
@RequestMapping("/admin")
public class AdminPageController {

	/** 목록 한 페이지 행 수 (프로토타입 4건) */
	private static final int PAGE_SIZE = 4;

	private final AdminViews views;
	private final MoveMapper moves;
	private final MemberService members;
	private final QuoteService quotes;
	private final NotificationService notifications;

	public AdminPageController(AdminViews views, MoveMapper moves, MemberService members, QuoteService quotes,
			NotificationService notifications) {
		this.views = views;
		this.moves = moves;
		this.members = members;
		this.quotes = quotes;
		this.notifications = notifications;
	}

	/** 헤더 알림 패널을 열면 빨간 점 제거 */
	@PostMapping("/notifications/panel-read")
	public ResponseEntity<Void> notificationPanelRead() {
		notifications.markAdminRead();
		return ResponseEntity.noContent().build();
	}

	/** 홈 · 대시보드 (SCR-A-016) */
	@GetMapping({ "", "/" })
	public String home(Model model) {
		List<AdminViews.Reservation> rs = views.reservations();
		long reviewing = quotes.listLines().stream().filter(q -> QuoteService.REVIEWING.equals(q.getStatus())).count();
		model.addAttribute("stats", List.of(
				new AdminViews.Stat("전체 예약 신청", rs.size(), "calendar", "/admin/reservations"),
				new AdminViews.Stat("리뷰 등록 현황", views.reviews().size(), "star", "/admin/reviews"),
				new AdminViews.Stat("견적 검토", reviewing, "document", "/admin/estimates"),
				new AdminViews.Stat("예약 완료", rs.stream().filter(r -> "예약 완료".equals(r.displayStatus())).count(), "truck",
						"/admin/reservations")));
		model.addAttribute("recentReservations", rs.stream().limit(3).toList());
		return "admin/home";
	}

	/* ---------- 예약 관리 (SCR-A-011) ---------- */

	public record ReservationRow(AdminViews.Reservation record, String displayStatus, String paymentStatus) {
	}

	@GetMapping("/reservations")
	public String reservations(@RequestParam(defaultValue = "전체") String status,
			@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "1") int page,
			@RequestParam(required = false) Long id, Model model) {
		List<ReservationRow> all = views.reservations().stream()
				.map(r -> new ReservationRow(r, r.displayStatus(), r.paymentStatus()))
				.toList();
		String query = q.trim().toLowerCase();
		String digits = Fmt.digits(query);
		List<ReservationRow> filtered = all.stream()
				.filter(row -> "전체".equals(status) || row.displayStatus().equals(status))
				.filter(row -> query.isEmpty() || row.record().name().toLowerCase().contains(query)
						|| row.record().appliedDate().contains(query)
						|| (!digits.isEmpty() && Fmt.digits(row.record().phone()).contains(digits)))
				.toList();
		addPage(model, filtered, page);
		model.addAttribute("status", status);
		model.addAttribute("q", q);
		long done = all.stream().filter(row -> "예약 완료".equals(row.displayStatus())).count();
		long size = Math.max(1, all.size());
		model.addAttribute("statusCounts", List.of(
				new long[] { done, Math.round(done * 100.0 / size) },
				new long[] { all.size() - done, Math.round((all.size() - done) * 100.0 / size) }));
		if (id != null) {
			all.stream().filter(row -> row.record().id() == id).findFirst()
					.ifPresent(row -> model.addAttribute("selected", row));
		}
		return "admin/reservations";
	}

	/** 계약금 결제 완료 처리 → 고객 알림 */
	@PostMapping("/reservations/{id}/paid")
	public String reservationPaid(@PathVariable long id, @RequestParam(defaultValue = "") String back) {
		MoveLine line = moves.findMoveLine(id);
		if (line != null && moves.markPaid(id) > 0 && !"02".equals(line.getPayStatus())) {
			notifications.toMember(line.getMemberId(), "10", "예약금 결제가 완료되었어요", "결제 내역과 남은 결제 일정을 확인할 수 있어요.",
					"/move/reservation");
		}
		return "redirect:" + safeBack(back, "/admin/reservations?id=" + id);
	}

	/* ---------- 회원 관리 (SCR-A-023) ---------- */

	@GetMapping("/members")
	public String members(@RequestParam(defaultValue = "전체") String type, @RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String modal,
			@RequestParam(required = false) Long id, @RequestParam(required = false) String error, Model model) {
		List<AdminViews.Member> all = views.members();
		String query = q.trim().toLowerCase();
		String digits = Fmt.digits(query);
		List<AdminViews.Member> filtered = all.stream()
				.filter(m -> "전체".equals(type) || m.type().equals(type))
				.filter(m -> query.isEmpty() || m.name().toLowerCase().contains(query)
						|| (!digits.isEmpty() && Fmt.digits(m.phone()).contains(digits)))
				.toList();
		addPage(model, filtered, page);
		model.addAttribute("type", type);
		model.addAttribute("q", q);
		model.addAttribute("modal", modal);
		model.addAttribute("registerError", "exists".equals(error) ? "이미 등록된 연락처예요." : null);
		if (id != null) {
			all.stream().filter(m -> m.id() == id).findFirst().ifPresent(m -> model.addAttribute("selected", m));
		}
		return "admin/members";
	}

	@PostMapping("/members")
	public String registerMember(@RequestParam String name, @RequestParam String phone, @RequestParam String joined,
			@RequestParam(defaultValue = "고객") String type, HttpSession session) {
		if (name.isBlank() || Fmt.digits(phone).length() < 10) {
			return "redirect:/admin/members?modal=register";
		}
		if (!members.register(name, phone, Fmt.parseDate(joined), "관리자".equals(type))) {
			return "redirect:/admin/members?modal=register&error=exists";
		}
		AdminState.of(session).toast(notifications.fire("회원 관리 · 신규 회원이 가입되었을 시", null, name.trim()));
		return "redirect:/admin/members";
	}

	@PostMapping("/members/{id}/type")
	public String changeMemberType(@PathVariable long id, @RequestParam String type,
			@RequestParam(defaultValue = "") String back) {
		members.changeType(id, "관리자".equals(type));
		return "redirect:" + safeBack(back, "/admin/members");
	}

	@PostMapping("/members/{id}/delete")
	public String deleteMember(@PathVariable long id, @RequestParam(defaultValue = "") String back) {
		members.delete(id);
		return "redirect:" + safeBack(back, "/admin/members");
	}

	/* ---------- 리뷰 관리 (SCR-A-012) ---------- */

	@GetMapping("/reviews")
	public String reviews(@RequestParam(defaultValue = "전체") String rating,
			@RequestParam(required = false) Long reply, @RequestParam(required = false) Long edit, Model model) {
		List<AdminViews.Review> all = views.reviews();
		List<AdminViews.Review> visible = "전체".equals(rating) ? all
				: all.stream().filter(r -> String.valueOf(r.rating()).equals(rating)).toList();
		double average = all.stream().mapToInt(r -> r.rating()).average().orElse(0);
		long size = Math.max(1, all.size());
		model.addAttribute("reviews", visible);
		model.addAttribute("reviewCount", all.size());
		model.addAttribute("average", String.format("%.1f", average));
		model.addAttribute("averageStars", (int) Math.round(Double.parseDouble(String.format("%.1f", average))));
		model.addAttribute("distribution", java.util.stream.IntStream.of(5, 4, 3, 2, 1).mapToObj(star -> {
			long count = all.stream().filter(r -> r.rating() == star).count();
			return new long[] { star, count, Math.round(count * 100.0 / size) };
		}).toList());
		model.addAttribute("rating", rating);
		java.util.Map<Long, String> replies = new java.util.LinkedHashMap<>();
		all.stream().filter(r -> r.answered()).forEach(r -> replies.put(r.id(), r.reply()));
		model.addAttribute("replies", replies);
		// 프로토타입은 처음에 답변 안 한 첫 리뷰의 입력창이 열린 상태
		long firstOpen = visible.stream().filter(r -> !r.answered()).mapToLong(r -> r.id()).findFirst().orElse(-1);
		model.addAttribute("replyingTo", reply != null ? reply : (edit == null ? firstOpen : -1));
		model.addAttribute("editing", edit);
		return "admin/reviews";
	}

	@PostMapping("/reviews/{id}/reply")
	public String replyReview(@PathVariable long id, @RequestParam String content,
			@RequestParam(defaultValue = "전체") String rating, HttpSession session) {
		if (!content.isBlank()) {
			boolean isNew = views.reviews().stream().noneMatch(r -> r.id() == id && r.answered());
			moves.updateReply(id, content.trim().length() > 500 ? content.trim().substring(0, 500) : content.trim(),
					AdminSession.adminId(session));
			if (isNew) {
				AdminState.of(session).toast(notifications.fire("리뷰 관리 · 관리자가 답변 할 시", null, null));
			}
		}
		return "redirect:/admin/reviews?reply=-1&rating=" + java.net.URLEncoder.encode(rating, java.nio.charset.StandardCharsets.UTF_8);
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
		return back.startsWith("/admin") && !back.startsWith("//") ? back : fallback;
	}
}
