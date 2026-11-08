package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** TBL_RESERVATION (예약) */
@Data
public class Reservation {
	private Long reservationId;
	private String reservationNo;
	private Long requestId;
	private String status;
	private String payStatus;
	private LocalDate moveDate;
	private String moveTime;
	private String moveMemo;
	private LocalDateTime appliedAt;
	private LocalDateTime depositPaidAt;
	private LocalDateTime cancelledAt;
	private String cancelledBy;
}
