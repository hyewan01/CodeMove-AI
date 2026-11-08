package com.codemove.customer.social;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codemove.admin.web.AdminSession;
import com.codemove.customer.web.CustomerSession;

import jakarta.servlet.http.HttpSession;

/**
 * 간편 로그인 (SCR-U-023): 버튼 → 카카오·네이버·애플 로그인 화면 → 콜백 → 고객 홈.
 * 소셜 로그인은 항상 고객 계정 (관리자는 휴대전화 인증만).
 */
@Controller
public class SocialLoginController {

	private static final Logger log = LoggerFactory.getLogger(SocialLoginController.class);
	/** 진행 중인 로그인: "제공자:state" (콜백이 같은 브라우저에서 왔는지 확인) */
	private static final String STATE_KEY = "socialLoginState";
	private static final long TTL_MILLIS = 10 * 60 * 1000;

	/**
	 * 애플은 결과를 다른 사이트에서 POST 로 보내 세션 쿠키가 따라오지 않으므로,
	 * state 와 결과를 서버에 잠시 보관했다가 GET(complete)으로 넘어온 뒤 세션에 로그인합니다.
	 */
	private final Map<String, Long> applePending = new ConcurrentHashMap<>();
	private final Map<String, AppleResult> appleResults = new ConcurrentHashMap<>();

	private record AppleResult(SocialLoginService.SocialProfile profile, String state, long expiresAt) {
	}

	private final SocialLoginService social;

	public SocialLoginController(SocialLoginService social) {
		this.social = social;
	}

	@GetMapping("/login/social/{code}")
	public String start(@PathVariable String code, HttpSession session) {
		SocialProvider provider = SocialProvider.of(code).orElse(null);
		if (provider == null) {
			return "redirect:/login";
		}
		if (!social.configured(provider)) {
			log.warn("{} 로그인 키가 없습니다. application-local.yml 의 codemove.social.{} 를 설정하세요.", provider.label(), code);
			return fail(provider, "unconfigured");
		}
		String state = UUID.randomUUID().toString();
		session.setAttribute(STATE_KEY, code + ":" + state);
		if (provider == SocialProvider.APPLE) {
			cleanUp();
			applePending.put(state, System.currentTimeMillis() + TTL_MILLIS);
		}
		return "redirect:" + social.authorizeUrl(provider, social.redirectUri(provider, currentBase()), state);
	}

	/** 카카오 · 네이버 콜백 */
	@GetMapping("/login/oauth/{code}/callback")
	public String callback(@PathVariable String code, @RequestParam(required = false) String state,
			@RequestParam(name = "code", required = false) String authCode,
			@RequestParam(required = false) String error, HttpSession session) {
		SocialProvider provider = SocialProvider.of(code).filter(p -> p != SocialProvider.APPLE).orElse(null);
		if (provider == null) {
			return "redirect:/login";
		}
		if (error != null || authCode == null) {
			return fail(provider, "cancel");
		}
		if (!(code + ":" + state).equals(session.getAttribute(STATE_KEY))) {
			return fail(provider, "failed");
		}
		try {
			return complete(social.fetchProfile(provider, authCode, social.redirectUri(provider, currentBase()), state, null),
					session);
		} catch (RuntimeException e) {
			log.warn("{} 로그인 실패", provider.label(), e);
			return fail(provider, "failed");
		}
	}

	/** 애플 콜백 (form_post) */
	@PostMapping("/login/oauth/apple/callback")
	public String appleCallback(@RequestParam(required = false) String state,
			@RequestParam(name = "code", required = false) String authCode, @RequestParam(required = false) String user,
			@RequestParam(required = false) String error) {
		Long expiresAt = state != null ? applePending.remove(state) : null;
		if (error != null || authCode == null) {
			return fail(SocialProvider.APPLE, "cancel");
		}
		if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
			return fail(SocialProvider.APPLE, "failed");
		}
		try {
			SocialLoginService.SocialProfile profile = social.fetchProfile(SocialProvider.APPLE, authCode,
					social.redirectUri(SocialProvider.APPLE, currentBase()), state, user);
			String ticket = UUID.randomUUID().toString();
			appleResults.put(ticket, new AppleResult(profile, state, System.currentTimeMillis() + TTL_MILLIS));
			return "redirect:/login/oauth/apple/complete?ticket=" + ticket;
		} catch (RuntimeException e) {
			log.warn("애플 로그인 실패", e);
			return fail(SocialProvider.APPLE, "failed");
		}
	}

	@GetMapping("/login/oauth/apple/complete")
	public String appleComplete(@RequestParam String ticket, HttpSession session) {
		AppleResult result = appleResults.remove(ticket);
		if (result == null || result.expiresAt() < System.currentTimeMillis()
				|| !("apple:" + result.state()).equals(session.getAttribute(STATE_KEY))) {
			return fail(SocialProvider.APPLE, "failed");
		}
		return complete(result.profile(), session);
	}

	private String complete(SocialLoginService.SocialProfile profile, HttpSession session) {
		session.removeAttribute(STATE_KEY);
		AdminSession.logout(session);
		// 회원 테이블 연동 전: 제공자 이름으로 고객 로그인 (백엔드에서 TBL_SOCIAL.SOCIAL_ID 로 회원 조회·가입)
		CustomerSession.of(session).loginWithSocial(profile.provider().code(), profile.name());
		return CustomerSession.redirectAfterLogin(session);
	}

	/** 로그인 방법 선택 화면으로 돌아가 안내 문구 표시 */
	private static String fail(SocialProvider provider, String reason) {
		return "redirect:/login?social=" + provider.code() + "&error=" + reason;
	}

	private static String currentBase() {
		return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
	}

	private void cleanUp() {
		long now = System.currentTimeMillis();
		applePending.values().removeIf(t -> t < now);
		appleResults.values().removeIf(r -> r.expiresAt() < now);
	}
}
