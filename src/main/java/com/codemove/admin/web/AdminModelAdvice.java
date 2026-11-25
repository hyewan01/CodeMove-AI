package com.codemove.admin.web;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.codemove.notification.NotificationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 모든 관리자 화면 공통 모델: 헤더 알림 패널, 통합 메뉴 검색, 전체 메뉴, 루틴 토스트, 현재 주소 */
@ControllerAdvice(basePackageClasses = AdminModelAdvice.class)
public class AdminModelAdvice {

	private final AdminViews views;
	private final NotificationService notifications;
	private final com.codemove.chat.ChatService chats;

	public AdminModelAdvice(AdminViews views, NotificationService notifications, com.codemove.chat.ChatService chats) {
		this.views = views;
		this.notifications = notifications;
		this.chats = chats;
	}

	/** 안 읽은 고객 메시지 수 (모든 방 합) */
	int chatUnread() {
		return chats.rooms().stream().mapToInt(r -> r.getUnread() == null ? 0 : r.getUnread()).sum();
	}

	@ModelAttribute
	public void common(HttpServletRequest request, HttpSession session, Model model) {
		if (!request.getRequestURI().startsWith(request.getContextPath() + "/admin")
				|| !com.codemove.config.PageRequest.is(request)) {
			return;
		}
		model.addAttribute("notificationGroups", AdminViews.grouped(views.notifications()));
		model.addAttribute("hasUnreadNotifications", notifications.hasUnreadAdmin());
		model.addAttribute("chatUnread", chatUnread());
		model.addAttribute("searchMenus", AdminViews.SEARCH_MENUS);
		model.addAttribute("drawerMenus", AdminViews.DRAWER_MENUS);
		String query = request.getQueryString();
		model.addAttribute("currentUrl", request.getRequestURI().substring(request.getContextPath().length())
				+ (query != null ? "?" + query : ""));
		model.addAttribute("toast", AdminState.of(session).takeToast());
		model.addAttribute("adminName", AdminSession.admin(session) == null ? "" : AdminSession.admin(session).getAdminName());
	}
}
