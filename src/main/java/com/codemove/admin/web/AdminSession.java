package com.codemove.admin.web;

import jakarta.servlet.http.HttpSession;

/** 로그인한 관리자 정보 (세션 보관). Spring Security 도입 시 대체합니다. */
public final class AdminSession {

	private static final String KEY = "adminSession";

	private AdminSession() {
	}

	public static void login(HttpSession session, AdminMockData.AdminAccount admin) {
		session.setAttribute(KEY, admin);
	}

	public static void logout(HttpSession session) {
		session.removeAttribute(KEY);
	}

	public static boolean isLoggedIn(HttpSession session) {
		return session.getAttribute(KEY) != null;
	}
}
