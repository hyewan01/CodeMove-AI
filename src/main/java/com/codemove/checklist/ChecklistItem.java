package com.codemove.checklist;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_CHECKLIST_ITEM 한 줄: 관리자가 고치는 기본 할 일 */
@Data
public class ChecklistItem {
	private String itemCode;
	private String phase;
	private String title;
	private String detail;
	private String whereText;
	private String linkUrl;
	private Integer sortNo;
	private String useYn;
	private LocalDateTime updatedAt;

	public boolean isUsed() {
		return !"N".equals(useYn);
	}
}
