package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** 예약 + 견적 요청 + 견적서 + 회원 조인 (관리자 예약 관리 · 일정, 고객 예약 내역) */
@Data
public class MoveLine {
	private Long reservationId;
	private String reservationNo;
	private String status;
	private String payStatus;
	private LocalDate moveDate;
	private String moveTime;
	private String moveMemo;
	private LocalDateTime appliedAt;
	private LocalDateTime depositPaidAt;
	private Long requestId;
	private Long memberId;
	private String memberName;
	private String phone;
	private String houseType;
	private String packType;
	private String fromAddress;
	private String fromDetail;
	private String toAddress;
	private String toDetail;
	private Long quoteId;
	private String quoteNo;
	private Long totalAmount;
	private Long depositAmount;
	private Double volumeTon;
	private String ladderUse;
	private String elevatorUse;
}
