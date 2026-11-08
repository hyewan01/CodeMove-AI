package com.codemove.notification;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_NOTIFICATION (+ 알림 내역용 회원 이름) */
@Data
public class Notification {
	private Long notificationId;
	/** RECEIVER_TYPE 01 고객 / 02 관리자 */
	private String receiverType;
	private Long memberId;
	private String ntfType;
	private String ntfDetail;
	private String ntfTitle;
	private String ntfContent;
	private String linkUrl;
	private String isImportant;
	private String isRead;
	private String ntfChannel;
	private LocalDateTime createdAt;
	private LocalDateTime sentAt;
	private String memberName;
}
