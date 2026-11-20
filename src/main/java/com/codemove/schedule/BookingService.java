package com.codemove.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.Fmt;

/**
 * 고객이 이사 날짜로 고를 수 있는 날인지 정합니다.
 * <ul>
 * <li>closed: 업체 휴무일 (일정 관리에서 지정한 날 · 정기 휴무 요일 · 공휴일 휴무)</li>
 * <li>unavailable: 이사 예약 불가능 (일정 관리에서 지정한 날)</li>
 * <li>full: 예약 마감 (그날 예약 완료 수 ≥ 하루 최대 예약 수)</li>
 * </ul>
 * 지난 날짜는 화면에서 따로 막습니다.
 */
@Service
public class BookingService {

	public static final String CLOSED = "closed", UNAVAILABLE = "unavailable", FULL = "full";

	private final ScheduleMapper schedules;

	public BookingService(ScheduleMapper schedules) {
		this.schedules = schedules;
	}

	@Transactional
	public ReservationSetting setting() {
		ReservationSetting s = schedules.findReservationSetting();
		if (s == null) {
			schedules.insertReservationSetting();
			s = schedules.findReservationSetting();
		}
		return s;
	}

	/** capacity 1~10, weekdays 0(일)~6(토) */
	@Transactional
	public void updateSetting(int capacity, List<Integer> weekdays, boolean holidayClosed, Long adminId) {
		ReservationSetting s = setting();
		s.setDailyCapacity(Math.max(1, Math.min(10, capacity)));
		String days = weekdays == null ? "" : weekdays.stream().filter(d -> d != null && d >= 0 && d <= 6).distinct().sorted()
				.map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
		s.setClosedWeekdays(days.isEmpty() ? null : days);
		s.setHolidayClosed(holidayClosed ? "Y" : "N");
		schedules.updateReservationSetting(s, adminId);
	}

	/** 날짜별 예약 완료 수 [from, to) */
	public Map<LocalDate, Integer> bookingCounts(LocalDate from, LocalDate to) {
		Map<LocalDate, Integer> counts = new LinkedHashMap<>();
		for (Map<String, Object> row : schedules.countBookingsByDate(from, to)) {
			Object day = row.get("MOVE_DAY");
			LocalDate d = day instanceof java.sql.Timestamp t ? t.toLocalDateTime().toLocalDate()
					: day instanceof LocalDateTime t ? t.toLocalDate()
					: day instanceof java.sql.Date t ? t.toLocalDate() : (LocalDate) day;
			counts.merge(d, ((Number) row.get("CNT")).intValue(), (a, b) -> a + b);
		}
		return counts;
	}

	/** [from, to) 에서 고를 수 없는 날 → closed | unavailable | full */
	public Map<LocalDate, String> blockedDates(LocalDate from, LocalDate to) {
		ReservationSetting s = setting();
		List<Integer> weekdays = s.weekdayList();
		Map<LocalDate, String> manual = new LinkedHashMap<>();
		for (ClosedDay c : schedules.listClosedDays()) {
			manual.put(c.getClosedDate(), "02".equals(c.getClosedType()) ? UNAVAILABLE : CLOSED);
		}
		Map<LocalDate, Integer> counts = bookingCounts(from, to);
		Map<LocalDate, String> blocked = new LinkedHashMap<>();
		for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
			String reason = manual.get(d);
			if (reason == null && (weekdays.contains(d.getDayOfWeek().getValue() % 7) || (s.closesOnHolidays() && Fmt.isHoliday(d)))) {
				reason = CLOSED;
			}
			if (reason == null && counts.getOrDefault(d, 0) >= s.capacity()) {
				reason = FULL;
			}
			if (reason != null) {
				blocked.put(d, reason);
			}
		}
		return blocked;
	}

	/** 그날 이사 예약을 받을 수 있는지 (지난 날짜 포함 검사) */
	public boolean isBookable(LocalDate date) {
		return date != null && !date.isBefore(LocalDate.now())
				&& !blockedDates(date, date.plusDays(1)).containsKey(date);
	}

	/** 화면 글자 */
	public static String label(String reason) {
		return switch (reason) {
			case FULL -> "마감";
			case UNAVAILABLE -> "불가";
			default -> "휴무";
		};
	}
}
