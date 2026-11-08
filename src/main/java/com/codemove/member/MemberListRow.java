package com.codemove.member;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 회원 관리 목록 한 줄. id 가 음수면 회원 정보 없이 관리자만 있는 계정(-ADMIN_ID).
 * isAdmin: 같은 휴대폰 번호의 사용 중인 TBL_ADMIN 이 있으면 Y
 */
@Data
public class MemberListRow {
	private Long id;
	private String name;
	private String phone;
	private LocalDateTime joinedAt;
	private String isAdmin;
	private String lastAddress;
}
