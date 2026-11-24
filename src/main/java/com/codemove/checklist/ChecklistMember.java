package com.codemove.checklist;

import java.time.LocalDateTime;

import lombok.Data;

/** 체크리스트를 쓰는 고객 한 명 (관리자 진행 현황) */
@Data
public class ChecklistMember {
	private Long memberId;
	private String memberName;
	private String phone;
	private LocalDateTime lastAt;
}
