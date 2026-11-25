package com.codemove.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import com.codemove.member.Member;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 고객 로그인 유지 (30일). 같은 기기에서는 다시 문자 인증을 하지 않아 요금이 들지 않습니다.
 * 쿠키에는 무작위 토큰, DB 에는 그 SHA-256 만 둡니다. 로그아웃 · 다른 계정 로그인 · 번호 변경 · 탈퇴하면 지웁니다.
 */
@Service
public class LoginDevices {

	static final String COOKIE = "codemove_keep";
	static final int DAYS = 30;

	private final LoginDeviceMapper mapper;
	private final SecureRandom random = new SecureRandom();

	public LoginDevices(LoginDeviceMapper mapper) {
		this.mapper = mapper;
	}

	public boolean hasCookie(HttpServletRequest request) {
		return token(request) != null;
	}

	public Member find(HttpServletRequest request) {
		String token = token(request);
		return token == null ? null : mapper.findMember(hash(token));
	}

	/** 이 기기를 기억 (전에 있던 것은 지움) */
	public void remember(long memberId, HttpServletRequest request, HttpServletResponse response) {
		deleteCurrent(request);
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		mapper.insert(memberId, hash(token), DAYS);
		cookie(request, response, token, Duration.ofDays(DAYS));
	}

	/** 이 기기를 잊음 */
	public void forget(HttpServletRequest request, HttpServletResponse response) {
		if (hasCookie(request)) {
			deleteCurrent(request);
			cookie(request, response, "", Duration.ZERO);
		}
	}

	/** 이 회원의 모든 기기를 잊음 */
	public void forgetAll(long memberId) {
		mapper.deleteMember(memberId);
	}

	private void deleteCurrent(HttpServletRequest request) {
		String token = token(request);
		if (token != null) {
			mapper.delete(hash(token));
		}
	}

	private static String token(HttpServletRequest request) {
		Cookie c = WebUtils.getCookie(request, COOKIE);
		return c == null || c.getValue() == null || c.getValue().isBlank() ? null : c.getValue();
	}

	private static void cookie(HttpServletRequest request, HttpServletResponse response, String value, Duration age) {
		String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).path(path).maxAge(age)
				.httpOnly(true).secure(request.isSecure()).sameSite("Lax").build().toString());
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
