package com.codemove.chat;

import java.time.LocalDateTime;

import lombok.Data;

/** 채팅 목록 한 줄 (TBL_CHAT_ROOM + 회원 + 마지막 고객 메시지·시각 + 안 읽은 고객 메시지 수) */
@Data
public class ChatRoomLine {
	private Long roomId;
	private Long memberId;
	private String memberName;
	private String status;
	private LocalDateTime startedAt;
	private LocalDateTime waitingAt;
	private String lastMessage;
	private LocalDateTime lastAt;
	/** 고객이 마지막으로 메시지를 보낸 시각 (채팅 목록 정렬 기준) */
	private LocalDateTime lastCustomerAt;
	private Integer unread;
}
