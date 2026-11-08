package com.codemove.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * 관리자 로그인. 로그인 화면은 고객과 나누지 않고, 고객 로그인 시트의 [관리자 로그인]에서 들어옵니다.
 */
@Controller
public class AdminAuthController {

	@GetMapping("/login/admin")
	public String loginForm(HttpSession session) {
		if (AdminSession.isLoggedIn(session)) {
			return "redirect:/admin";
		}
		return "customer/auth/admin-login";
	}

	@PostMapping("/login/admin")
	public String login(@RequestParam String loginId, @RequestParam String password, HttpSession session,
			Model model) {
		AdminMockData.AdminAccount admin = AdminMockData.findAdmin(loginId.trim());
		if (admin == null || !admin.password().equals(password)) {
			model.addAttribute("loginId", loginId);
			model.addAttribute("error", "아이디 또는 비밀번호가 올바르지 않아요.");
			return "customer/auth/admin-login";
		}
		AdminSession.login(session, admin);
		return "redirect:/admin";
	}

	@PostMapping("/admin/logout")
	public String logout(HttpSession session) {
		AdminSession.logout(session);
		return "redirect:/login/admin";
	}
}
