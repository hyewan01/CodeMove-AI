package com.codemove.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.codemove.member.Member;

/** 로그인 유지 기기 (mapper/LoginDeviceMapper.xml, TBL_LOGIN_DEVICE) */
@Mapper
public interface LoginDeviceMapper {

	void insert(@Param("memberId") long memberId, @Param("hash") String hash, @Param("days") int days);

	/** 만료되지 않은 토큰의 회원 (탈퇴했으면 null) */
	Member findMember(String hash);

	int delete(String hash);

	int deleteMember(long memberId);
}
