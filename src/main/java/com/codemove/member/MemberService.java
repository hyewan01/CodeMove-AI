package com.codemove.member;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.Fmt;

/**
 * 로그인 · 회원가입 · 간편로그인 연결 · 회원 관리.
 * 관리자는 전화번호 + 비밀번호로 로그인하고(auth/AdminPasswordService), 소셜 로그인은 고객 전용입니다.
 */
@Service
public class MemberService {

	/** 휴대전화 로그인 결과: 관리자 / 회원 / 가입 안 된 번호 (둘 다 null) */
	public record PhoneLogin(Admin admin, Member member) {
	}

	/** 소셜 제공자 코드 → LOGIN_TYPE 공통코드 */
	public static String loginType(String provider) {
		return switch (provider) {
			case "kakao" -> "01";
			case "naver" -> "02";
			case "apple" -> "03";
			default -> throw new IllegalArgumentException(provider);
		};
	}

	private final MemberMapper members;

	public MemberService(MemberMapper members) {
		this.members = members;
	}

	public PhoneLogin loginByPhone(String phone) {
		String digits = Fmt.digits(phone);
		Admin admin = members.findActiveAdminByPhone(digits);
		return admin != null ? new PhoneLogin(admin, null) : new PhoneLogin(null, members.findActiveMemberByPhone(digits));
	}

	public boolean isRegistered(String phone) {
		String digits = Fmt.digits(phone);
		return members.findActiveMemberByPhone(digits) != null || members.findActiveAdminByPhone(digits) != null;
	}

	public Member find(long memberId) {
		return members.findMember(memberId);
	}

	/** 소셜 계정에 연결된 회원 (없으면 null → 회원가입으로) */
	public Member findBySocial(String provider, String socialNo) {
		Long id = members.findMemberIdBySocial(loginType(provider), socialNo);
		return id == null ? null : members.findMember(id);
	}

	/**
	 * 회원가입. provider·socialNo 가 있으면 그 소셜 계정도 연결.
	 * @param verified 휴대전화 인증을 마쳤는지. 문자를 보낼 수 없을 때 간편 로그인으로 가입하면 false
	 */
	@Transactional
	public Member signup(String name, String phone, String provider, String socialNo, boolean verified) {
		Member m = new Member();
		m.setMemberName(name.trim());
		m.setPhone(Fmt.phone(phone));
		m.setIsVerified(verified ? "Y" : "N");
		members.insertMember(m);
		if (provider != null && socialNo != null) {
			members.insertSocial(m.getMemberId(), loginType(provider), socialNo);
		}
		return members.findMember(m.getMemberId());
	}

	/** 연결된 간편로그인 제공자 코드 (kakao, naver, apple) */
	private final com.codemove.config.ShortMemory<Long, List<String>> providers = new com.codemove.config.ShortMemory<>(
			java.time.Duration.ofMinutes(1));

	public List<String> connectedProviders(long memberId) {
		return providers.get(memberId, id -> members.findSocialTypes(id).stream()
				.map(t -> switch (t) { case "01" -> "kakao"; case "02" -> "naver"; default -> "apple"; })
				.toList());
	}

	/** @return false 면 다른 회원이 쓰는 번호 */
	@Transactional
	public boolean changePhone(long memberId, String phone) {
		Member other = members.findActiveMemberByPhone(Fmt.digits(phone));
		if (other != null && other.getMemberId() != memberId) {
			return false;
		}
		members.updatePhone(memberId, Fmt.phone(phone));
		return true;
	}

	/* ---------- 회원 관리 (SCR-A-023) ---------- */

	public List<MemberListRow> listForAdmin() {
		return members.listForAdmin();
	}

	/** @return false 면 이미 있는 번호 */
	@Transactional
	public boolean register(String name, String phone, LocalDate joined, boolean admin) {
		if (isRegistered(phone)) {
			return false;
		}
		if (admin) {
			members.insertAdmin(name.trim(), Fmt.phone(phone));
		} else {
			members.insertMemberByAdmin(name.trim(), Fmt.phone(phone), joined != null ? joined : LocalDate.now());
		}
		return true;
	}

	/**
	 * 분류 변경. 관리자로 → 같은 번호의 TBL_ADMIN 을 다시 쓰거나 새로 만들고, 고객으로 → 그 관리자 계정을 사용 안 함.
	 * 관리자만 있는 계정(id 음수)은 고객 정보가 없어 고객으로 바꿀 수 없음.
	 */
	@Transactional
	public void changeType(long id, boolean admin) {
		if (id < 0) {
			return;
		}
		Member m = members.findMember(id);
		if (m == null || !"Y".equals(m.getIsActive())) {
			return; // 탈퇴 회원은 권한을 바꿀 수 없음
		}
		String digits = Fmt.digits(m.getPhone());
		if (admin) {
			if (members.activateAdminByPhone(digits) == 0) {
				members.insertAdmin(m.getMemberName(), m.getPhone());
			}
		} else {
			members.deactivateAdminByPhone(digits);
		}
	}

	/** 삭제: 회원은 탈퇴 처리(이력 보존), 관리자만 있는 계정은 사용 안 함 */
	@Transactional
	public void delete(long id) {
		if (id < 0) {
			members.deactivateAdmin(-id);
			return;
		}
		withdraw(id);
	}

	/**
	 * 회원 탈퇴 (고객이 직접 · 관리자가 삭제). 견적·예약·결제·후기 기록은 지우지 않고,
	 * 회원을 사용 안 함으로 바꾸고 같은 번호의 관리자 권한과 간편로그인 연결만 끊음.
	 */
	@Transactional
	public void withdraw(long memberId) {
		Member m = members.findMember(memberId);
		if (m == null || !"Y".equals(m.getIsActive())) {
			return;
		}
		members.deactivateAdminByPhone(Fmt.digits(m.getPhone()));
		members.deleteSocials(memberId);
		providers.forget(memberId);
		members.withdrawMember(memberId);
	}
}
