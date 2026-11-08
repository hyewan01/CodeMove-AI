package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** TBL_QUOTE (견적서) */
@Data
public class Quote {
	private Long quoteId;
	private String quoteNo;
	private Long requestId;
	private Long memberId;
	private Long adminId;
	private String sourceType;
	private String status;
	private String companyName;
	private String contactName;
	private String contactPhone;
	private LocalDate estimateDate;
	private String fromAddress;
	private String toAddress;
	private LocalDate workDate;
	private String workTime;
	private LocalDate transportDate;
	private String workType;
	private String fromLadderUse;
	private String toLadderUse;
	private String fromElevatorUse;
	private String toElevatorUse;
	private String fromStairsUse;
	private String toStairsUse;
	private Double volumeTon;
	private String note;
	private Long movingCost;
	private Integer fromLadderFloor;
	private Long fromLadderCost;
	private Integer toLadderFloor;
	private Long toLadderCost;
	private Long totalAmount;
	private Long depositAmount;
	private Long balanceAmount;
	private String documentPath;
	private LocalDateTime approvedAt;
	private LocalDateTime expiresAt;
	private LocalDateTime updatedAt;
	private String itemGroup1;
	private String itemGroup2;
	private String itemGroup3;
	private String itemGroup4;
}
