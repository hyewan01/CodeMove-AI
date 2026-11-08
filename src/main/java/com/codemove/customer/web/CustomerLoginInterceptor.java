package com.codemove.customer.web;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 필요 화면 접근 시 첫 화면(온보딩)으로 이동 (프로토타입 protectedScreens). 로그인하면 항상 홈.
 * Spring Security 도입 시 대체합니다.
 */
public class CustomerLoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (CustomerSession.of(request.getSession()).isLoggedIn()) {
			return true;
		}
		response.sendRedirect(request.getContextPath() + "/");
		return false;
	}
}
