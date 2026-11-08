package com.codemove.member;

import java.time.LocalDateTime;

import lombok.Data;

/** TBL_MEMBER */
@Data
public class Member {
	private Long memberId;
	private String memberName;
	private String phone;
	private String isVerified;
	private String isActive;
	private LocalDateTime joinedAt;
	private LocalDateTime termsAgreedAt;
	private LocalDateTime withdrawnAt;
}
