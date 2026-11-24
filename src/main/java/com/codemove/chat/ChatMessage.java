package com.codemove.chat;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * TBL_CHAT_MESSAGE (SENDER_TYPE 01 고객 / 02 AI / 03 관리자, MESSAGE_TYPE 01 텍스트 / 02 파일).
 * needsReply: 챗봇이 답하지 못했거나 상담사 연결을 요청한 고객 메시지 = Y
 */
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
	private String needsReply;
	private LocalDateTime sentAt;
	/** 고친 · 지운 시각 (V7). 지운 메시지는 줄은 남기고 화면에만 "삭제된 메시지" */
	private LocalDateTime editedAt;
	private LocalDateTime deletedAt;
}
