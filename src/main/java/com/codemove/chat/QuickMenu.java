package com.codemove.chat;

import lombok.Data;

/** TBL_QUICK_MENU (+ 주제 소개) */
@Data
public class QuickMenu {
	private Long menuId;
	private Long topicId;
	private String menuName;
	private Integer sortOrder;
	private String intro;
}
