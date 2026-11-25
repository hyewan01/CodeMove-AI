package com.codemove.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.member.Admin;
import com.codemove.member.MemberMapper;

/**
 * 관리자 비밀번호 (TBL_ADMIN.PASSWORD, BCrypt). 5번 틀리면 10분 동안 막습니다.
 * 막힌 기록은 이 서버 메모리에만 둡니다.
 */
@Service
public class AdminPasswordService {

	static final int MAX_FAILS = 5;
	static final Duration LOCK = Duration.ofMinutes(10);

	private static final class Fails {
		int count;
		Instant last;
	}

	private final MemberMapper members;
	private final Clock clock;
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private final Map<Long, Fails> fails = new HashMap<>();

	@Autowired
	public AdminPasswordService(MemberMapper members) {
		this(members, Clock.systemDefaultZone());
	}

	AdminPasswordService(MemberMapper members, Clock clock) {
		this.members = members;
		this.clock = clock;
	}

	/** @return 맞으면 null, 아니면 이유. 동시에 여러 번 시도해도 횟수를 넘기지 못하게 한 번에 하나씩 확인 */
	public synchronized String check(long adminId, String password) {
		Instant now = clock.instant();
		Fails f = fails.get(adminId);
		if (f != null && !now.isBefore(f.last.plus(LOCK))) {
			fails.remove(adminId);
			f = null;
		}
		if (f != null && f.count >= MAX_FAILS) {
			return "비밀번호를 여러 번 틀려서 잠시 막았어요. 10분 뒤에 다시 시도해 주세요.";
		}
		String hash = members.findAdminPassword(adminId);
		if (hash == null || hash.isBlank()) {
			return "아직 비밀번호가 없는 관리자예요. 다른 관리자에게 첫 비밀번호를 정해 달라고 해 주세요.";
		}
		if (password != null && tooLong(password) == null && encoder.matches(password, hash)) {
			fails.remove(adminId);
			return null;
		}
		if (f == null) {
			f = new Fails();
			fails.put(adminId, f);
		}
		f.count++;
		f.last = now;
		return f.count >= MAX_FAILS ? "비밀번호를 여러 번 틀려서 잠시 막았어요. 10분 뒤에 다시 시도해 주세요."
				: "비밀번호가 맞지 않아요. (" + (MAX_FAILS - f.count) + "번 남음)";
	}

	/** 내 비밀번호 바꾸기. @return 됐으면 null, 아니면 이유 */
	@Transactional
	public String change(long adminId, String current, String next, String confirm) {
		String problem = problem(next, confirm);
		if (problem != null) {
			return problem;
		}
		String wrong = check(adminId, current);
		if (wrong != null) {
			return wrong;
		}
		members.updateAdminPassword(adminId, encoder.encode(next));
		return null;
	}

	/** 비밀번호가 없는 다른 관리자의 첫 비밀번호 정하기 (이미 있으면 안 바뀜) */
	@Transactional
	public String setFirst(long adminId, String next, String confirm) {
		String problem = problem(next, confirm);
		if (problem != null) {
			return problem;
		}
		return members.setFirstAdminPassword(adminId, encoder.encode(next)) == 1 ? null : "이미 비밀번호가 있는 관리자예요.";
	}

	public List<Admin> withoutPassword() {
		return members.findAdminsWithoutPassword();
	}

	private static String problem(String next, String confirm) {
		if (next == null || next.length() < 6) {
			return "새 비밀번호는 6자 이상이어야 해요.";
		}
		if (tooLong(next) != null) {
			return tooLong(next);
		}
		return next.equals(confirm) ? null : "새 비밀번호가 서로 달라요.";
	}

	/** BCrypt 는 72바이트까지만 씀 */
	private static String tooLong(String p) {
		return p.getBytes(StandardCharsets.UTF_8).length > 72 ? "비밀번호가 너무 길어요." : null;
	}
}
