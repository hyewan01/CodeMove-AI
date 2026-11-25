package com.codemove.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.Cookie;

/** 로그인 유지 토큰 저장 · 찾기 · 지우기 (공용 DB 라 끝나면 모두 되돌림) */
@SpringBootTest
@Transactional
class LoginDeviceDbTest {

	@Autowired
	LoginDevices devices;
	@Autowired
	JdbcTemplate jdbc;

	private static MockHttpServletRequest with(String token) {
		MockHttpServletRequest r = new MockHttpServletRequest();
		r.setCookies(new Cookie(LoginDevices.COOKIE, token));
		return r;
	}

	private static String token(MockHttpServletResponse response) {
		return response.getHeader("Set-Cookie").replaceAll(LoginDevices.COOKIE + "=([^;]*);.*", "$1");
	}

	@Test
	void rememberFindForget() {
		long memberId = jdbc.queryForObject("SELECT MAX(MEMBER_ID) FROM TBL_MEMBER WHERE IS_ACTIVE = 'Y'", Long.class);
		MockHttpServletResponse response = new MockHttpServletResponse();
		devices.remember(memberId, new MockHttpServletRequest(), response);
		String token = token(response);
		assertEquals(memberId, devices.find(with(token)).getMemberId());
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TBL_LOGIN_DEVICE WHERE TOKEN_HASH = ?", Integer.class, token),
				"원문은 DB 에 없음");
		assertNull(devices.find(with("forged")));

		MockHttpServletResponse again = new MockHttpServletResponse();
		devices.remember(memberId, with(token), again);
		assertNotEquals(token, token(again));
		assertNull(devices.find(with(token)), "새로 기억하면 전 토큰은 못 씀");

		devices.forgetAll(memberId);
		assertNull(devices.find(with(token(again))));
	}

	@Test
	void expiredOrWithdrawnIsNotFound() {
		long memberId = jdbc.queryForObject("SELECT MAX(MEMBER_ID) FROM TBL_MEMBER WHERE IS_ACTIVE = 'Y'", Long.class);
		MockHttpServletResponse response = new MockHttpServletResponse();
		devices.remember(memberId, new MockHttpServletRequest(), response);
		String token = token(response);
		jdbc.update("UPDATE TBL_LOGIN_DEVICE SET EXPIRES_AT = SYSDATE - 1 WHERE TOKEN_HASH = ?", LoginDevices.hash(token));
		assertNull(devices.find(with(token)));
		jdbc.update("UPDATE TBL_LOGIN_DEVICE SET EXPIRES_AT = SYSDATE + 1 WHERE TOKEN_HASH = ?", LoginDevices.hash(token));
		jdbc.update("UPDATE TBL_MEMBER SET IS_ACTIVE = 'N' WHERE MEMBER_ID = ?", memberId);
		assertNull(devices.find(with(token)));
	}
}
