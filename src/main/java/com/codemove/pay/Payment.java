package com.codemove.pay;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_PAYMENT 한 줄: 계약금 결제 한 건 (STATUS 01 대기 · 02 완료 · 03 실패 · 04 취소) */
@Data
public class Payment {
	public static final String WAITING = "01", DONE = "02", FAILED = "03", CANCELLED = "04";

	private Long paymentId;
	private Long reservationId;
	private Long memberId;
	private String orderId;
	private String orderName;
	private Long amount;
	private String payType;
	private String status;
	private String paymentKey;
	private String method;
	private String receiptUrl;
	private String failCode;
	private String failMessage;
	private LocalDateTime requestedAt;
	private LocalDateTime approvedAt;
	private LocalDateTime cancelledAt;
}
