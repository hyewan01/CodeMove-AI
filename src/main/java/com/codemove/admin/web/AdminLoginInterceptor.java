package com.codemove.admin.web;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 관리자 화면(/admin/**)은 관리자 로그인 후에만 접근. Spring Security 도입 시 대체합니다. */
public class AdminLoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (AdminSession.isLoggedIn(request.getSession())) {
			return true;
		}
		response.sendRedirect(request.getContextPath() + "/login/admin");
		return false;
	}
}
