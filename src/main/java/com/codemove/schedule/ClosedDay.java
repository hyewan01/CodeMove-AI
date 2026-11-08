package com.codemove.schedule;

import java.time.LocalDate;

import lombok.Data;

/** TBL_CLOSED_DAY (CLOSED_TYPE 01 업체 휴무일 / 02 이사 예약 불가능) */
@Data
public class ClosedDay {
	private LocalDate closedDate;
	private String closedType;
}
