package com.codemove.admin.web;

import com.codemove.member.Admin;

import jakarta.servlet.http.HttpSession;

/** 로그인한 관리자 (세션 보관). Spring Security 도입 시 대체합니다. */
public final class AdminSession {

	private static final String KEY = "adminSession";

	private AdminSession() {
	}

	public static void login(HttpSession session, Admin admin) {
		session.setAttribute(KEY, admin);
	}

	public static void logout(HttpSession session) {
		session.removeAttribute(KEY);
	}

	public static boolean isLoggedIn(HttpSession session) {
		return session.getAttribute(KEY) != null;
	}

	public static Admin admin(HttpSession session) {
		return (Admin) session.getAttribute(KEY);
	}

	/** 등록·수정한 관리자 기록용 (로그인 안 했으면 null) */
	public static Long adminId(HttpSession session) {
		Admin a = admin(session);
		return a == null ? null : a.getAdminId();
	}
}
