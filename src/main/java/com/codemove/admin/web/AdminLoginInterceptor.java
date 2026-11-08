package com.codemove.admin.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.codemove.customer.web.CustomerSession;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 관리자 화면(/admin/**)은 관리자 계정으로 로그인해야 접근. Spring Security 도입 시 대체합니다.
 * 비로그인 → 첫 화면(온보딩, 로그인하면 관리자 홈), 고객 계정 → 고객 홈
 */
public class AdminLoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (AdminSession.isLoggedIn(request.getSession())) {
			return true;
		}
		if (CustomerSession.of(request.getSession()).isLoggedIn()) {
			response.sendRedirect(request.getContextPath() + "/home");
			return false;
		}
		response.sendRedirect(request.getContextPath() + "/");
		return false;
	}
}
