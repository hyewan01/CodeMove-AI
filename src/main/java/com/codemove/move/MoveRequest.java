package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** TBL_MOVE_REQUEST (견적 요청) */
@Data
public class MoveRequest {
	private Long requestId;
	private Long memberId;
	private String houseType;
	private String packType;
	private LocalDate moveDate;
	private String preferTime;
	private String moveTime;
	private String fromAddress;
	private String fromDetail;
	private String fromZipcode;
	private String toAddress;
	private String toDetail;
	private String toZipcode;
	private Double aiCbm;
	private Long aiEstAmount;
	private Long aiEstMin;
	private Long aiEstMax;
	private Integer aiAnalysisSec;
	private Integer aiConfidence;
	private LocalDateTime requestedAt;
}
