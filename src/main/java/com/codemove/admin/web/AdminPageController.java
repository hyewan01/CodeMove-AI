package com.codemove.admin.web;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 관리자 화면 (SCR-A-001~023). 프로토타입 App의 page 상태를 URL로 나눴습니다.
 * 데이터는 AdminMockData 목업이며, 백엔드 개발 시 서비스 계층으로 대체합니다.
 */
@Controller
@RequestMapping("/admin")
public class AdminPageController {

	/** 아직 옮기지 않은 화면: URL → (화면 이름, Screen ID) */
	private static final Map<String, String[]> PENDING = Map.ofEntries(
			Map.entry("/admin/estimates", new String[] { "견적 관리", "SCR-A-001" }),
			Map.entry("/admin/estimates/new", new String[] { "견적서 등록", "SCR-A-003" }),
			Map.entry("/admin/chat-management", new String[] { "채팅 관리", "SCR-A-005" }),
			Map.entry("/admin/chats", new String[] { "채팅 목록", "SCR-A-006" }),
			Map.entry("/admin/chat-management/operation", new String[] { "채팅 운영 설정", "SCR-A-008" }),
			Map.entry("/admin/chat-management/entry", new String[] { "첫 화면/퀵메뉴 설정", "SCR-A-009" }),
			Map.entry("/admin/chat-management/answers", new String[] { "답변 설정 관리", "SCR-A-010" }),
			Map.entry("/admin/reservations", new String[] { "예약 관리", "SCR-A-011" }),
			Map.entry("/admin/reviews", new String[] { "리뷰 관리", "SCR-A-012" }),
			Map.entry("/admin/notifications", new String[] { "알림 관리", "SCR-A-013" }),
			Map.entry("/admin/notifications/history", new String[] { "알림 내역", "SCR-A-014" }),
			Map.entry("/admin/notifications/messaging", new String[] { "메시징 운영 설정", "SCR-A-015" }),
			Map.entry("/admin/schedule", new String[] { "일정 캘린더", "SCR-A-020" }),
			Map.entry("/admin/schedule/manage", new String[] { "일정 관리", "SCR-A-022" }),
			Map.entry("/admin/members", new String[] { "회원 관리", "SCR-A-023" }));

	/** 모든 관리자 화면 공통: 헤더 알림 패널, 통합 메뉴 검색, 전체 메뉴 */
	@ModelAttribute
	public void common(Model model) {
		model.addAttribute("notificationGroups", AdminMockData.groupedNotifications());
		model.addAttribute("searchMenus", AdminMockData.searchMenus());
		model.addAttribute("drawerMenus", AdminMockData.drawerMenus());
	}

	/** 홈 · 대시보드 (SCR-A-016) */
	@GetMapping({ "", "/" })
	public String home(Model model) {
		model.addAttribute("stats", AdminMockData.stats());
		model.addAttribute("recentReservations", AdminMockData.recentReservations(3));
		return "admin/home";
	}

	@GetMapping({ "/estimates", "/estimates/new", "/chat-management", "/chats", "/chat-management/operation",
			"/chat-management/entry", "/chat-management/answers", "/reservations", "/reviews", "/notifications",
			"/notifications/history", "/notifications/messaging", "/schedule", "/schedule/manage", "/members" })
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
}
