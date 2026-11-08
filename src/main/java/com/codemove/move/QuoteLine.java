package com.codemove.move;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** 견적 관리 목록 한 줄 (견적서 + 요청 영상) */
@Data
public class QuoteLine {
	private Long quoteId;
	private String quoteNo;
	private String status;
	private String contactName;
	private String contactPhone;
	private String companyName;
	private LocalDate estimateDate;
	private LocalDateTime approvedAt;
	private Long requestId;
	private Long memberId;
	private String videoPath;
	private String videoName;
	private LocalDateTime videoUploadedAt;
}
