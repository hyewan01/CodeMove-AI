package com.codemove.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class LunarDaysTest {

	@Test
	void convertsKnownHolidays() {
		// 2026 설날 2/17 = 음 1.1, 추석 9/25 = 음 8.15, 2025 설날 1/29 = 음 1.1
		assertEquals(new LunarDays.Lunar(2026, 1, 1, false), LunarDays.lunar(LocalDate.of(2026, 2, 17)));
		assertEquals(new LunarDays.Lunar(2026, 8, 15, false), LunarDays.lunar(LocalDate.of(2026, 9, 25)));
		assertEquals(new LunarDays.Lunar(2025, 1, 1, false), LunarDays.lunar(LocalDate.of(2025, 1, 29)));
	}

	@Test
	void leapMonth() {
		// 2025 윤6월 1일 = 양력 7/25
		assertEquals(new LunarDays.Lunar(2025, 6, 1, true), LunarDays.lunar(LocalDate.of(2025, 7, 25)));
	}

	@Test
	void noSonDaysEndWithNineOrZero() {
		assertTrue(LunarDays.isNoSonDay(LocalDate.of(2026, 2, 25))); // 음 1.9
		assertTrue(LunarDays.isNoSonDay(LocalDate.of(2026, 2, 26))); // 음 1.10
		assertFalse(LunarDays.isNoSonDay(LocalDate.of(2026, 2, 17))); // 음 1.1
		assertEquals(6, LunarDays.noSonDays(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 3, 19)).size());
	}
}
