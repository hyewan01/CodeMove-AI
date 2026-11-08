package com.codemove.admin.web;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** 모든 관리자 화면 공통 모델: 헤더 알림 패널, 통합 메뉴 검색, 전체 메뉴, 루틴 토스트, 현재 주소 */
@ControllerAdvice(basePackageClasses = AdminModelAdvice.class)
public class AdminModelAdvice {

	@ModelAttribute
	public void common(HttpServletRequest request, HttpSession session, Model model) {
		if (!request.getRequestURI().startsWith(request.getContextPath() + "/admin")) {
			return;
		}
		AdminState state = AdminState.of(session);
		model.addAttribute("notificationGroups", AdminMockData.groupedNotifications(state.getNotifications()));
		model.addAttribute("hasUnreadNotifications", !state.isNotificationsRead());
		model.addAttribute("searchMenus", AdminMockData.searchMenus());
		model.addAttribute("drawerMenus", AdminMockData.drawerMenus());
		String query = request.getQueryString();
		model.addAttribute("currentUrl", request.getRequestURI().substring(request.getContextPath().length())
				+ (query != null ? "?" + query : ""));
		if ("GET".equals(request.getMethod())) {
			model.addAttribute("toast", state.takeToast());
		}
	}
}
