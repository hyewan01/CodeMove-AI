package com.codemove.chat;

import lombok.Data;

/** TBL_LOREBOOK (주제별 AI 전달 내용, KEYWORDS 는 #견적#비용 형식) */
@Data
public class Lorebook {
	private Long lorebookId;
	private Long topicId;
	private String keywords;
	private String content;
	private Integer sortOrder;
}
