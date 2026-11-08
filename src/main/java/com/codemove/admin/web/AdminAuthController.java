package com.codemove.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import com.codemove.customer.web.CustomerSession;

import jakarta.servlet.http.HttpSession;

/**
 * 관리자 로그아웃. 로그인은 고객과 같은 화면(/login)을 쓰고,
 * 관리자 계정이면 CustomerPageController 로그인 처리에서 관리자 화면으로 보냅니다.
 */
@Controller
public class AdminAuthController {

	@PostMapping("/admin/logout")
	public String logout(HttpSession session) {
		AdminSession.logout(session);
		CustomerSession.of(session).setLoggedIn(false);
		return "redirect:/";
	}
}
