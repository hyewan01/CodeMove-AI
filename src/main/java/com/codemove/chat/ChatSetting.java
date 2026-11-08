package com.codemove.chat;

import lombok.Data;

/** TBL_CHAT_SETTING (채팅 운영 설정, 한 행) */
@Data
public class ChatSetting {
	private Long settingId;
	private String botEnabled;
	private String consultEnabled;
	private String startTime;
	private String endTime;
	private String greeting;
	private String delayMessage;
	private String awayMessage;
}
