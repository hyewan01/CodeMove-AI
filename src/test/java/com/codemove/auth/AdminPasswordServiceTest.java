package com.codemove.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.codemove.member.MemberMapper;

class AdminPasswordServiceTest {

	private static final String RIGHT = "test-only-pw";

	private final AuthCodeServiceTest.FakeClock clock = new AuthCodeServiceTest.FakeClock();
	private final MemberMapper members = mock(MemberMapper.class);
	private final AdminPasswordService service = new AdminPasswordService(members, clock);

	@BeforeEach
	void setUp() {
		when(members.findAdminPassword(1L)).thenReturn(new BCryptPasswordEncoder(4).encode(RIGHT));
	}

	@Test
	void onlyTheRightPasswordPasses() {
		assertNull(service.check(1, RIGHT));
		assertNotNull(service.check(1, "nope-nope"));
		assertNotNull(service.check(1, ""));
		assertNotNull(service.check(1, null));
		assertNotNull(service.check(1, "x".repeat(100)));
	}

	@Test
	void adminWithoutPasswordCannotLogIn() {
		assertNotNull(service.check(2, ""));
		assertNotNull(service.check(2, RIGHT));
	}

	@Test
	void fiveWrongTriesLockForTenMinutesEvenWithTheRightPassword() {
		for (int i = 0; i < 5; i++) assertNotNull(service.check(1, "nope-nope"));
		assertNotNull(service.check(1, RIGHT));
		clock.pass(Duration.ofMinutes(10));
		assertNull(service.check(1, RIGHT));
	}

	@Test
	void changeNeedsCurrentPasswordAndMatchingNewOne() {
		assertNotNull(service.change(1, "nope-nope", "brand-new-1", "brand-new-1"));
		assertNotNull(service.change(1, RIGHT, "short", "short"));
		assertNotNull(service.change(1, RIGHT, "brand-new-1", "brand-new-2"));
		verify(members, never()).updateAdminPassword(anyLong(), anyString());
		assertNull(service.change(1, RIGHT, "brand-new-1", "brand-new-1"));
		verify(members).updateAdminPassword(eq(1L), anyString());
	}

	@Test
	void firstPasswordDoesNotReplaceAnExistingOne() {
		when(members.setFirstAdminPassword(eq(1L), anyString())).thenReturn(0);
		when(members.setFirstAdminPassword(eq(2L), anyString())).thenReturn(1);
		assertNotNull(service.setFirst(1, "brand-new-1", "brand-new-1"));
		assertNull(service.setFirst(2, "brand-new-1", "brand-new-1"));
	}
}
