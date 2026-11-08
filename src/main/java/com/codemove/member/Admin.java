package com.codemove.member;

import java.io.Serializable;

import lombok.Data;

/** TBL_ADMIN (로그인 세션에도 보관) */
@Data
public class Admin implements Serializable {
	private Long adminId;
	private String adminName;
	private String phone;
	private String isActive;
}
