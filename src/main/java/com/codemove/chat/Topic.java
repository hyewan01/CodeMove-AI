package com.codemove.chat;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** TBL_CHATBOT_TOPIC + 로어북 */
@Data
public class Topic {
	private Long topicId;
	private String title;
	private String intro;
	private String topicType;
	private String isActive;
	private List<Lorebook> lorebooks = new ArrayList<>();
}
