package com.codemove.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class HolidaysTest {

	@Test
	void year2026MatchesTheOfficialList() {
		// 2026 관공서 공휴일 (지방선거일 같은 임시공휴일 제외). 예전 Fmt 고정 목록과 같음
		List<String> expected = List.of("2026-01-01", "2026-02-16", "2026-02-17", "2026-02-18", "2026-03-01",
				"2026-03-02", "2026-05-05", "2026-05-24", "2026-05-25", "2026-06-06", "2026-08-15", "2026-08-17",
				"2026-09-24", "2026-09-25", "2026-09-26", "2026-10-03", "2026-10-05", "2026-10-09", "2026-12-25");
		assertEquals(expected, List.copyOf(Holidays.between(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)).keySet()));
		assertEquals("설날", Holidays.name(LocalDate.of(2026, 2, 17)));
		assertEquals("대체공휴일", Holidays.name(LocalDate.of(2026, 10, 5)));
	}

	@Test
	void year2027() {
		var h = Holidays.between(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1));
		System.out.println("2027 공휴일 " + h);
		// 설날 2027-02-07(일) → 연휴 2.6~2.8, 일요일과 겹쳐 2.9 대체
		assertEquals("설날", h.get("2027-02-07"));
		assertEquals("대체공휴일", h.get("2027-02-09"));
		// 광복절 8.15(일) → 8.16, 개천절 10.3(일) → 10.4, 한글날 10.9(토) → 10.11, 성탄절 12.25(토) → 12.27
		assertEquals("대체공휴일", h.get("2027-08-16"));
		assertEquals("대체공휴일", h.get("2027-10-04"));
		assertEquals("대체공휴일", h.get("2027-10-11"));
		assertEquals("대체공휴일", h.get("2027-12-27"));
		assertTrue(Fmt.isHoliday(LocalDate.of(2027, 9, 15)), "추석");
		assertFalse(Fmt.isHoliday(LocalDate.of(2027, 6, 7)), "현충일은 대체 없음");
	}
}
