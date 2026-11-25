package com.codemove.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codemove.auth.AuthCodeService.Purpose;
import com.codemove.member.Admin;
import com.codemove.member.MemberMapper;

/** 인증번호 규칙: 유효 시간 · 시도 횟수 · 발송 한도 · 시연 번호 · 관리자 번호 (문자는 가짜 발송기) */
class AuthCodeServiceTest {

	private static final String PHONE = "010-1111-2222", ADMIN = "010-9999-0000", DEMO = "010-1234-5678";

	static class FakeClock extends Clock {
		Instant now = Instant.parse("2026-11-25T01:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneId.of("Asia/Seoul");
		}

		@Override
		public Clock withZone(ZoneId z) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

		void pass(Duration d) {
			now = now.plus(d);
		}
	}

	private final FakeClock clock = new FakeClock();
	private final MemberMapper members = mock(MemberMapper.class);
	private final List<String> sent = new ArrayList<>();
	private AuthCodeService real, demo;

	@BeforeEach
	void setUp() {
		when(members.findActiveAdminByPhone("01099990000")).thenReturn(new Admin());
		real = new AuthCodeService(members, (to, text) -> sent.add(to + " " + text), List.of(DEMO), "123456", clock);
		demo = new AuthCodeService(members, null, List.of(DEMO, ADMIN), "123456", clock);
	}

	private String lastCode() {
		return sent.get(sent.size() - 1).replaceAll(".*인증번호 (\\d{6}).*", "$1");
	}

	private static String wrong(String code) {
		return code.equals("000000") ? "000001" : "000000";
	}

	@Test
	void codeWorksOnceAndOnlyForItsSessionPurposeAndPhone() {
		assertTrue(real.send("s1", Purpose.LOGIN, PHONE, "ip").ok());
		String code = lastCode();
		assertNotNull(real.verify("s2", Purpose.LOGIN, PHONE, code));
		assertNotNull(real.verify("s1", Purpose.WITHDRAW, PHONE, code));
		assertNotNull(real.verify("s1", Purpose.LOGIN, "010-1111-3333", code));
		assertNull(real.verify("s1", Purpose.LOGIN, PHONE, code));
		assertNotNull(real.verify("s1", Purpose.LOGIN, PHONE, code), "한 번 쓰면 끝");
	}

	@Test
	void emptyOrMissingCodeNeverPasses() {
		assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, "123456"));
		real.send("s", Purpose.LOGIN, PHONE, "ip");
		assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, ""));
		assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, null));
	}

	@Test
	void codeExpiresAfterThreeMinutes() {
		real.send("s", Purpose.LOGIN, PHONE, "ip");
		clock.pass(Duration.ofMinutes(3));
		assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, lastCode()));
	}

	@Test
	void fiveWrongTriesKillTheCode() {
		real.send("s", Purpose.LOGIN, PHONE, "ip");
		String code = lastCode();
		for (int i = 0; i < 5; i++) assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, wrong(code)));
		assertNotNull(real.verify("s", Purpose.LOGIN, PHONE, code));
	}

	@Test
	void tenWrongTriesLockThePhoneForFifteenMinutes() {
		for (int r = 0; r < 2; r++) {
			demo.send("s" + r, Purpose.LOGIN, DEMO, "ip");
			for (int i = 0; i < 5; i++) demo.verify("s" + r, Purpose.LOGIN, DEMO, "000000");
		}
		assertFalse(demo.send("s9", Purpose.LOGIN, DEMO, "ip").ok());
		clock.pass(Duration.ofMinutes(15));
		assertTrue(demo.send("s9", Purpose.LOGIN, DEMO, "ip").ok());
		assertNull(demo.verify("s9", Purpose.LOGIN, DEMO, "123456"));
	}

	@Test
	void sendingIsLimited() {
		assertTrue(real.send("s", Purpose.LOGIN, PHONE, "ip").ok());
		assertFalse(real.send("s", Purpose.LOGIN, PHONE, "ip").ok(), "60초 안에는 다시 못 받음");
		for (int i = 0; i < 4; i++) {
			clock.pass(Duration.ofSeconds(60));
			assertTrue(real.send("s", Purpose.LOGIN, PHONE, "ip").ok());
		}
		clock.pass(Duration.ofSeconds(60));
		assertFalse(real.send("s", Purpose.LOGIN, PHONE, "ip").ok(), "하루 5번까지");
		assertEquals(5, sent.size());
		clock.pass(Duration.ofDays(1));
		assertTrue(real.send("s", Purpose.LOGIN, PHONE, "ip").ok());
	}

	@Test
	void oneAddressCannotSendToManyPhones() {
		for (int i = 0; i < 20; i++) assertTrue(real.send("s", Purpose.LOGIN, "0105555" + (1000 + i), "ip").ok());
		assertFalse(real.send("s", Purpose.LOGIN, "01055552000", "ip").ok());
		assertTrue(real.send("s", Purpose.LOGIN, "01055552001", "other").ok());
	}

	@Test
	void adminPhoneNeverGetsAMessage() {
		for (Purpose p : Purpose.values()) {
			assertFalse(real.send("s", p, ADMIN, "ip").ok());
			assertFalse(demo.send("s", p, ADMIN, "ip").ok(), "시연 번호 목록에 적혀 있어도 안 됨");
		}
		assertFalse(real.canSend(ADMIN));
		assertTrue(sent.isEmpty());
	}

	@Test
	void withoutSenderOnlyDemoPhonesPass() {
		AuthCodeService.Sent s = demo.send("s", Purpose.LOGIN, DEMO, "ip");
		assertEquals("123456", s.demoCode());
		assertNull(demo.verify("s", Purpose.LOGIN, DEMO, "123456"));
		assertFalse(demo.send("s", Purpose.LOGIN, PHONE, "ip").ok());
		assertNotNull(demo.verify("s", Purpose.LOGIN, PHONE, "123456"));
	}

	@Test
	void demoCodeIsOffOnceMessagesAreReal() {
		assertNull(real.send("s", Purpose.LOGIN, DEMO, "ip").demoCode());
		assertTrue(sent.get(0).startsWith("01012345678 "));
	}

	@Test
	void failedSendLeavesNoCode() {
		AuthCodeService broken = new AuthCodeService(members, (to, text) -> {
			throw new IllegalStateException("down");
		}, List.of(), "", clock);
		assertFalse(broken.send("s", Purpose.LOGIN, PHONE, "ip").ok());
		assertNotNull(broken.verify("s", Purpose.LOGIN, PHONE, ""));
	}
}
