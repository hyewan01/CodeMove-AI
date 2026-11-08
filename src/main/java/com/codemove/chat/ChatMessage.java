package com.codemove.chat;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_CHAT_MESSAGE (SENDER_TYPE 01 고객 / 02 AI / 03 관리자, MESSAGE_TYPE 01 텍스트 / 02 파일) */
@Data
public class ChatMessage {
	private Long messageId;
	private Long roomId;
	private Long adminId;
	private Long topicId;
	private String senderType;
	private String messageType;
	private String content;
	private String attachmentPath;
	private String attachmentName;
	private String isRead;
	private LocalDateTime sentAt;
}
