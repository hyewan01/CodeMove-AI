package com.codemove.schedule;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * TBL_RESERVATION_SETTING (예약 설정, 한 행).
 * dailyCapacity: 하루에 받을 최대 예약 수 (1~10), closedWeekdays: 정기 휴무 요일 "0,6" (0=일 … 6=토),
 * holidayClosed: 공휴일 휴무 Y/N
 */
@Data
public class ReservationSetting {
	private Long settingId;
	private Integer dailyCapacity;
	private String closedWeekdays;
	private String holidayClosed;

	/** "0,6" → [0, 6] */
	public List<Integer> weekdayList() {
		List<Integer> days = new ArrayList<>();
		if (closedWeekdays != null) {
			for (String d : closedWeekdays.split(",")) {
				if (d.trim().matches("[0-6]")) days.add(Integer.parseInt(d.trim()));
			}
		}
		return days;
	}

	public int capacity() {
		return dailyCapacity == null ? 3 : dailyCapacity;
	}

	public boolean closesOnHolidays() {
		return "Y".equals(holidayClosed);
	}
}
