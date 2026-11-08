package com.codemove.customer.web;

import java.io.Serializable;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;

/**
 * 로그인한 고객 (세션 보관). 회원 정보·이사 진행 상태는 매 요청 DB 에서 읽습니다 (CustomerView).
 * Spring Security 도입 시 대체합니다.
 */
@Getter
@Setter
public class CustomerSession implements Serializable {

	private static final String KEY = "customerSession";

	/** 간편 로그인했지만 아직 회원이 아닌 소셜 계정 → 회원가입을 마치면 연결 */
	public record PendingSocial(String provider, String socialNo, String name) implements Serializable {
	}

	private Long memberId;
	private String userName;
	/** 내 이사 탭: booking / estimate / done (null 이면 진행 상태로 정함) */
	private String moveTab;
	/** 화면 모드: standard / easy */
	private String mode = "standard";
	private PendingSocial pendingSocial;

	/** 고객 로그인 후에는 항상 고객 홈 */
	public static String redirectAfterLogin(HttpSession session) {
		return "redirect:/home";
	}

	/** 관리자 로그인 후에는 항상 관리자 홈 */
	public static String redirectAfterAdminLogin(HttpSession session) {
		return "redirect:/admin";
	}

	public static CustomerSession of(HttpSession session) {
		CustomerSession state = (CustomerSession) session.getAttribute(KEY);
		if (state == null) {
			state = new CustomerSession();
			session.setAttribute(KEY, state);
		}
		return state;
	}

	public boolean isLoggedIn() {
		return memberId != null;
	}

	public boolean isEasy() {
		return "easy".equals(mode);
	}

	public void login(long memberId, String name) {
		this.memberId = memberId;
		this.userName = name;
		this.moveTab = null;
		this.pendingSocial = null;
	}

	public void logout() {
		memberId = null;
		userName = null;
		moveTab = null;
		pendingSocial = null;
	}
}
