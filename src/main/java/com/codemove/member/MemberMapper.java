package com.codemove.member;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 회원 · 관리자 · 간편로그인 (mapper/MemberMapper.xml) */
@Mapper
public interface MemberMapper {

	/** 휴대폰 번호(숫자만)로 사용 중인 관리자 */
	Admin findActiveAdminByPhone(String digits);

	/** 휴대폰 번호(숫자만)로 사용 중인 회원 */
	Member findActiveMemberByPhone(String digits);

	Member findMember(long memberId);

	/** memberId 는 SEQ_MEMBER 로 채워짐 */
	void insertMember(Member member);

	Long findMemberIdBySocial(@Param("loginType") String loginType, @Param("socialNo") String socialNo);

	void insertSocial(@Param("memberId") long memberId, @Param("loginType") String loginType,
			@Param("socialNo") String socialNo);

	List<String> findSocialTypes(long memberId);

	int updatePhone(@Param("memberId") long memberId, @Param("phone") String phone);

	/* ---------- 회원 관리 (SCR-A-023) ---------- */

	List<MemberListRow> listForAdmin();

	void insertMemberByAdmin(@Param("name") String name, @Param("phone") String phone,
			@Param("joined") LocalDate joined);

	/** 같은 번호의 관리자 계정을 다시 사용 (없으면 0) */
	int activateAdminByPhone(String digits);

	void insertAdmin(@Param("name") String name, @Param("phone") String phone);

	int deactivateAdminByPhone(String digits);

	int deactivateAdmin(long adminId);

	int withdrawMember(long memberId);

	Admin findAdmin(long adminId);
}
