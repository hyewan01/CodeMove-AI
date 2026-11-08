package com.codemove.schedule;

import java.time.LocalDate;

import lombok.Data;

/** TBL_SCHEDULE (관리자 수동 일정) + 연결 회원 이름 */
@Data
public class Schedule {
	private Long scheduleId;
	private String title;
	private String category;
	private LocalDate scheduleDate;
	private String scheduleTime;
	private String place;
	private Long memberId;
	private Long reservationId;
	private Long adminId;
	private String memberName;
	private String memberPhone;
}
