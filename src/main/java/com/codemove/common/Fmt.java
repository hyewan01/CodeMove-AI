package com.codemove.common;

import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 화면 표시 · 입력 해석 공통 형식 (날짜, 금액, 휴대폰 번호) */
public final class Fmt {

	private Fmt() {
	}

	private static final DateTimeFormatter YY = DateTimeFormatter.ofPattern("yy.MM.dd");
	private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy. MM. dd");
	private static final DateTimeFormatter DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String WEEK = "월화수목금토일";

	/** 26.03.25 */
	public static String yy(LocalDate d) {
		return d == null ? "" : d.format(YY);
	}

	public static String yy(LocalDateTime t) {
		return t == null ? "" : yy(t.toLocalDate());
	}

	/** 2026. 03. 25 */
	public static String dot(LocalDate d) {
		return d == null ? "" : d.format(DOT);
	}

	/** 2026-03-25 */
	public static String dash(LocalDate d) {
		return d == null ? "" : d.format(DASH);
	}

	public static String compact(LocalDate d) {
		return d.format(COMPACT);
	}

	/** 수 */
	public static String weekday(LocalDate d) {
		return String.valueOf(WEEK.charAt(d.getDayOfWeek().getValue() - 1));
	}

	/** 3월 18일 (수) */
	public static String monthDay(LocalDate d) {
		return d == null ? "" : d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + weekday(d) + ")";
	}

	/** 10:00 → 오전 10:00 */
	public static String ampm(String hhmm) {
		if (hhmm == null || !hhmm.matches("\\d{2}:\\d{2}")) {
			return "";
		}
		int h = Integer.parseInt(hhmm.substring(0, 2));
		return (h < 12 ? "오전 " : "오후 ") + String.format("%d", h % 12 == 0 ? 12 : h % 12) + hhmm.substring(2);
	}

	/** D-30, D-DAY, D+3 */
	public static String dday(LocalDate d) {
		long days = ChronoUnit.DAYS.between(LocalDate.now(), d);
		return days == 0 ? "D-DAY" : days > 0 ? "D-" + days : "D+" + (-days);
	}

	/** 1050000 → 1,050,000 */
	public static String money(Long v) {
		return v == null ? "" : new DecimalFormat("#,###").format(v);
	}

	public static String won(Long v) {
		return v == null ? "-" : money(v) + "원";
	}

	/** "1,050,000", "₩ 1,050,000", "0" → 숫자 (없으면 null) */
	public static Long parseMoney(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.replaceAll("[^0-9]", "");
		return digits.isEmpty() ? null : Long.parseLong(digits);
	}

	/** "3.5톤" → 3.5 */
	public static Double parseDecimal(String s) {
		if (s == null) {
			return null;
		}
		String n = s.replaceAll("[^0-9.]", "");
		try {
			return n.isEmpty() ? null : Double.parseDouble(n);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 26.03.25 · 2026.03.25 · 2026. 03. 25 · 2026-03-25 → 날짜 (모르면 null) */
	public static LocalDate parseDate(String s) {
		if (s == null) {
			return null;
		}
		String digits = s.replaceAll("[^0-9]", "");
		try {
			if (digits.length() == 6) {
				return LocalDate.parse("20" + digits, COMPACT);
			}
			if (digits.length() == 8) {
				return LocalDate.parse(digits, COMPACT);
			}
		} catch (DateTimeParseException e) {
			return null;
		}
		return null;
	}

	/** 숫자만 */
	public static String digits(String phone) {
		return phone == null ? "" : phone.replaceAll("\\D", "");
	}

	/** 01012345678 → 010-1234-5678 */
	public static String phone(String raw) {
		String d = digits(raw);
		if (d.length() == 11) {
			return d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7);
		}
		if (d.length() == 10) {
			return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
		}
		return raw == null ? "" : raw.trim();
	}

	/** "서울 강남구 테헤란로 123" → "강남구" (시·도 다음 시·군·구) */
	public static String district(String address) {
		if (address == null || address.isBlank()) {
			return "";
		}
		String[] parts = address.trim().split("\\s+");
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].endsWith("구") || parts[i].endsWith("군")) {
				return parts[i];
			}
		}
		return parts.length > 1 ? parts[1] : parts[0];
	}

	/** "서울 강남구 테헤란로 123" → "서울 강남구" */
	public static String region(String address) {
		if (address == null || address.isBlank()) {
			return "";
		}
		String[] parts = address.trim().split("\\s+");
		return parts.length > 1 ? parts[0] + " " + parts[1] : parts[0];
	}

	/** 공휴일 (양력 고정 + 2026년 음력 명절·대체공휴일) */
	private static final List<String> HOLIDAYS = List.of("01-01", "03-01", "05-05", "06-06", "08-15", "10-03", "10-09",
			"12-25", "2026-02-16", "2026-02-17", "2026-02-18", "2026-03-02", "2026-05-24", "2026-05-25", "2026-08-17",
			"2026-09-24", "2026-09-25", "2026-09-26", "2026-10-05");

	public static boolean isHoliday(LocalDate d) {
		String md = String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth());
		return HOLIDAYS.contains(md) || HOLIDAYS.contains(d.toString());
	}

	public static boolean isWeekend(LocalDate d) {
		return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
	}
}
