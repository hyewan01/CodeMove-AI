package com.codemove.checklist;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_MOVE_CHECKLIST 한 줄: 기본 항목 체크 상태(ITEM_CODE) 또는 고객이 넣은 할 일(TITLE) */
@Data
public class ChecklistRow {
	private Long checkId;
	private Long memberId;
	private Long requestId;
	private String itemCode;
	private String phase;
	private String title;
	private String doneYn;
	private LocalDateTime createdAt;
	private LocalDateTime doneAt;
}
