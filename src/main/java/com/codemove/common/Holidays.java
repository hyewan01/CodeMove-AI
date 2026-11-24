package com.codemove.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관공서 공휴일 (해마다 계산): 양력 고정일 + 음력 설날·부처님오신날·추석(LunarDays) + 대체공휴일.
 * <ul>
 * <li>설날 · 추석 연휴가 일요일이나 다른 공휴일과 겹치면 연휴 다음 첫 평일(일요일 · 공휴일이 아닌 날)</li>
 * <li>3·1절 · 어린이날 · 부처님오신날 · 광복절 · 개천절 · 한글날 · 성탄절이 토 · 일요일이나 다른 공휴일과 겹치면 다음 첫 평일</li>
 * <li>신정 · 현충일은 대체공휴일 없음. 선거일 · 임시공휴일은 미리 알 수 없어 넣지 않음</li>
 * </ul>
 */
public final class Holidays {

	private Holidays() {
	}

	private static final Map<Integer, Map<LocalDate, String>> CACHE = new ConcurrentHashMap<>();

	/** 공휴일 이름, 아니면 null */
	public static String name(LocalDate d) {
		return year(d.getYear()).get(d);
	}

	public static boolean is(LocalDate d) {
		return name(d) != null;
	}

	/** [from, to) 의 공휴일 (yyyy-MM-dd → 이름) */
	public static Map<String, String> between(LocalDate from, LocalDate to) {
		Map<String, String> out = new LinkedHashMap<>();
		for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
			String n = name(d);
			if (n != null) {
				out.put(d.toString(), n);
			}
		}
		return out;
	}

	static Map<LocalDate, String> year(int y) {
		return CACHE.computeIfAbsent(y, Holidays::compute);
	}

	private static Map<LocalDate, String> compute(int y) {
		Map<LocalDate, String> h = new TreeMap<>();
		LocalDate seollal = lunarDay(y, 1, 1), chuseok = lunarDay(y, 8, 15), buddha = lunarDay(y, 4, 8);
		// 설날 연휴는 전날이 지난해 음력 12월 말일이라 양력 1월 1일과 겹치지 않음
		List<LocalDate> seollalDays = List.of(seollal.minusDays(1), seollal, seollal.plusDays(1));
		List<LocalDate> chuseokDays = List.of(chuseok.minusDays(1), chuseok, chuseok.plusDays(1));
		h.put(LocalDate.of(y, 1, 1), "신정");
		seollalDays.forEach(d -> h.put(d, "설날"));
		h.put(LocalDate.of(y, 3, 1), "삼일절");
		h.put(LocalDate.of(y, 5, 5), "어린이날");
		h.merge(buddha, "부처님오신날", (a, b) -> a + "·" + b);
		h.put(LocalDate.of(y, 6, 6), "현충일");
		h.put(LocalDate.of(y, 8, 15), "광복절");
		chuseokDays.forEach(d -> h.merge(d, "추석", (a, b) -> a + "·" + b));
		h.merge(LocalDate.of(y, 10, 3), "개천절", (a, b) -> a + "·" + b);
		h.merge(LocalDate.of(y, 10, 9), "한글날", (a, b) -> a + "·" + b);
		h.put(LocalDate.of(y, 12, 25), "성탄절");

		Map<LocalDate, String> base = new TreeMap<>(h);
		// 설날 · 추석: 일요일 또는 다른 공휴일과 겹친 날 수만큼
		for (List<LocalDate> days : List.of(seollalDays, chuseokDays)) {
			long overlaps = days.stream().filter(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY || base.get(d).contains("·"))
					.count();
			LocalDate d = days.get(2);
			for (int i = 0; i < overlaps; i++) {
				d = nextFree(d, h, false);
				h.put(d, "대체공휴일");
			}
		}
		// 나머지 대체 대상: 토 · 일요일 또는 다른 공휴일과 겹치면
		for (LocalDate d : List.of(LocalDate.of(y, 3, 1), LocalDate.of(y, 5, 5), buddha, LocalDate.of(y, 8, 15),
				LocalDate.of(y, 10, 3), LocalDate.of(y, 10, 9), LocalDate.of(y, 12, 25))) {
			boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
			if (weekend || base.get(d).contains("·")) {
				LocalDate s = nextFree(d, h, true);
				h.put(s, "대체공휴일");
			}
		}
		return h;
	}

	/** d 다음의 첫 날: 공휴일이 아니고 일요일이 아닌 날 (weekend 이면 토요일도 건너뜀) */
	private static LocalDate nextFree(LocalDate d, Map<LocalDate, String> h, boolean skipSaturday) {
		LocalDate n = d.plusDays(1);
		while (h.containsKey(n) || n.getDayOfWeek() == DayOfWeek.SUNDAY
				|| (skipSaturday && n.getDayOfWeek() == DayOfWeek.SATURDAY)) {
			n = n.plusDays(1);
		}
		return n;
	}

	/** 양력 y 년 안에서 음력 month.day (윤달 아님) 인 날 */
	static LocalDate lunarDay(int y, int month, int day) {
		for (LocalDate d = LocalDate.of(y, 1, 1); d.getYear() == y; d = d.plusDays(1)) {
			LunarDays.Lunar l = LunarDays.lunar(d);
			if (!l.leap() && l.month() == month && l.day() == day) {
				return d;
			}
		}
		throw new IllegalStateException(y + "년에 음력 " + month + "." + day + " 이 없습니다");
	}
}
