package com.codemove.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.codemove.auth.AdminPasswordService;

import jakarta.servlet.http.HttpSession;

/** 관리자 비밀번호: 내 것 바꾸기 · 비밀번호가 없는 관리자의 첫 비밀번호 정하기 */
@Controller
@RequestMapping("/admin/password")
public class AdminPasswordController {

	private final AdminPasswordService passwords;

	public AdminPasswordController(AdminPasswordService passwords) {
		this.passwords = passwords;
	}

	@GetMapping
	public String form(Model model) {
		model.addAttribute("pending", passwords.withoutPassword());
		return "admin/password";
	}

	@PostMapping
	public String change(@RequestParam String current, @RequestParam String next, @RequestParam String confirm,
			HttpSession session, RedirectAttributes flash) {
		String error = passwords.change(AdminSession.adminId(session), current, next, confirm);
		flash.addFlashAttribute(error == null ? "notice" : "error", error == null ? "비밀번호를 바꿨어요." : error);
		return "redirect:/admin/password";
	}

	@PostMapping("/first")
	public String first(@RequestParam long adminId, @RequestParam String next, @RequestParam String confirm,
			RedirectAttributes flash) {
		String error = passwords.setFirst(adminId, next, confirm);
		flash.addFlashAttribute(error == null ? "notice" : "error", error == null ? "첫 비밀번호를 정했어요." : error);
		return "redirect:/admin/password";
	}
}
