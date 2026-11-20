package com.codemove.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.DangiCalendar;
import com.ibm.icu.util.TimeZone;

/**
 * 음력(한국 단기력) 변환과 손 없는 날.
 * 손 없는 날 = 음력 날짜 끝자리가 9 또는 0 (음력 9·10·19·20·29·30일). 윤달도 같은 규칙.
 * 관리자 일정 달력 · 고객 견적 달력 · (요금 확정 후) 날짜 할증이 모두 이 함수를 씁니다.
 */
public final class LunarDays {

	private LunarDays() {
	}

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 음력 날짜. month 1~12, leap 윤달 여부 */
	public record Lunar(int year, int month, int day, boolean leap) {

		/** 음 3.9 / 음 윤3.9 */
		public String label() {
			return "음 " + (leap ? "윤" : "") + month + "." + day;
		}
	}

	/**
	 * ICU 의 DangiCalendar 는 '내부용(deprecated)' 표시가 있지만, 중국 음력(UTC+8)과 달리 한국 시간(UTC+9) 기준으로
	 * 계산하는 유일한 구현이라 씁니다. build.gradle 에서 icu4j 버전을 고정했고 LunarDaysTest 로 확인합니다.
	 */
	@SuppressWarnings("deprecation")
	public static Lunar lunar(LocalDate date) {
		DangiCalendar cal = new DangiCalendar();
		cal.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
		// 정오로 잡아 시간대 경계 문제를 피함
		cal.setTimeInMillis(date.atTime(12, 0).atZone(SEOUL).toInstant().toEpochMilli());
		return new Lunar(cal.get(Calendar.EXTENDED_YEAR), cal.get(Calendar.MONTH) + 1,
				cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.IS_LEAP_MONTH) == 1);
	}

	public static boolean isNoSonDay(LocalDate date) {
		int d = lunar(date).day() % 10;
		return d == 9 || d == 0;
	}

	/** [from, to) 의 손 없는 날 → 음력 표시 (yyyy-MM-dd → 음 3.9) */
	public static Map<String, String> noSonDays(LocalDate from, LocalDate to) {
		Map<String, String> days = new LinkedHashMap<>();
		for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
			if (isNoSonDay(d)) {
				days.put(d.toString(), lunar(d).label());
			}
		}
		return days;
	}
}
