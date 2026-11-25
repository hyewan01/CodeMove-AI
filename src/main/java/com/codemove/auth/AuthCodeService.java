package com.codemove.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codemove.common.Fmt;
import com.codemove.member.MemberMapper;

/**
 * 휴대전화 인증번호 (로그인 · 회원가입 · 번호 변경 · 탈퇴).
 * <ul>
 * <li>번호는 서버가 만들고 서버가 확인. 3분 유효, 한 번만 쓸 수 있고, 5번 틀리면 무효</li>
 * <li>같은 휴대전화로 10번 틀리면 15분 동안 막음 (새 번호를 계속 받아 맞혀 보는 것 방지)</li>
 * <li>문자 요금: 60초에 한 번, 번호마다 하루 5번, 접속 주소마다 하루 20번, 전체 하루 200번까지</li>
 * <li>관리자 번호로는 절대 보내지 않음 (관리자는 비밀번호로 로그인)</li>
 * <li>SmsSender 가 없으면 시연 모드: 설정에 적은 테스트 번호만 정해 둔 인증번호로 통과</li>
 * </ul>
 * 상태는 이 서버 메모리에만 둡니다 (서버가 여러 대가 되면 DB 로 옮겨야 함).
 */
@Service
public class AuthCodeService {

	private static final Logger log = LoggerFactory.getLogger(AuthCodeService.class);

	public enum Purpose {
		LOGIN, SIGNUP, PHONE, WITHDRAW
	}

	/** @param demoCode 시연 번호면 화면에 보여 줄 인증번호 (아니면 null) */
	public record Sent(boolean ok, String message, String demoCode) {
	}

	static final Duration VALID = Duration.ofMinutes(3), COOLDOWN = Duration.ofSeconds(60), LOCK = Duration.ofMinutes(15);
	static final int MAX_TRIES = 5, MAX_FAILS = 10, PHONE_PER_DAY = 5, IP_PER_DAY = 20, TOTAL_PER_DAY = 200;

	private static final String NOT_READY = "지금은 문자 인증을 준비 중이에요. 카카오 · 네이버 로그인을 이용해 주세요.";

	private static final class Code {
		final String value;
		final Instant expires;
		int tries;

		Code(String value, Instant expires) {
			this.value = value;
			this.expires = expires;
		}
	}

	private static final class Fails {
		int count;
		Instant last;
	}

	private final MemberMapper members;
	private final SmsSender sms;
	private final Set<String> demoPhones;
	private final String demoCode;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	private final Map<String, Code> codes = new HashMap<>();
	private final Map<String, Fails> fails = new HashMap<>();
	private final Map<String, Instant> lastSent = new HashMap<>();
	private final Map<String, Integer> sentToday = new HashMap<>();
	private LocalDate countDay;

	@Autowired
	public AuthCodeService(MemberMapper members, ObjectProvider<SmsSender> sms,
			@Value("${codemove.auth.demo-phones:}") List<String> demoPhones,
			@Value("${codemove.auth.demo-code:}") String demoCode) {
		this(members, sms.getIfAvailable(), demoPhones, demoCode, Clock.systemDefaultZone());
	}

	AuthCodeService(MemberMapper members, SmsSender sms, List<String> demoPhones, String demoCode, Clock clock) {
		this.members = members;
		this.sms = sms;
		this.demoPhones = demoPhones.stream().map(Fmt::digits).filter(p -> !p.isEmpty()).collect(Collectors.toSet());
		this.demoCode = demoCode == null ? "" : demoCode.trim();
		this.clock = clock;
	}

	private boolean isDemo(String digits) {
		return sms == null && demoCode.matches("\\d{6}") && demoPhones.contains(digits);
	}

	private boolean isAdmin(String digits) {
		return members.findActiveAdminByPhone(digits) != null;
	}

	/** 이 번호로 인증번호를 받을 수 있는지 (관리자 번호는 항상 false) */
	public boolean canSend(String phone) {
		String digits = Fmt.digits(phone);
		return digits.matches("01\\d{8,9}") && !isAdmin(digits) && (sms != null || isDemo(digits));
	}

	public synchronized Sent send(String sessionId, Purpose purpose, String phone, String ip) {
		String digits = Fmt.digits(phone);
		if (!digits.matches("01\\d{8,9}")) {
			return new Sent(false, "휴대폰 번호를 확인해 주세요.", null);
		}
		if (isAdmin(digits)) {
			return new Sent(false, "관리자 번호로는 인증번호를 보내지 않아요.", null);
		}
		Instant now = clock.instant();
		prune(now);
		if (locked(digits, now)) {
			return new Sent(false, lockedMessage(), null);
		}
		if (isDemo(digits)) {
			codes.put(key(sessionId, purpose, digits), new Code(demoCode, now.plus(VALID)));
			return new Sent(true, "시연용 번호예요. 문자는 가지 않아요.", demoCode);
		}
		if (sms == null) {
			return new Sent(false, NOT_READY, null);
		}
		Instant last = lastSent.get(digits);
		if (last != null && now.isBefore(last.plus(COOLDOWN))) {
			long wait = Math.max(1, Duration.between(now, last.plus(COOLDOWN)).toSeconds());
			return new Sent(false, wait + "초 뒤에 다시 받을 수 있어요.", null);
		}
		if (count("phone:" + digits) >= PHONE_PER_DAY || count("ip:" + ip) >= IP_PER_DAY || count("total") >= TOTAL_PER_DAY) {
			return new Sent(false, "오늘 받을 수 있는 인증번호를 모두 썼어요. 내일 다시 하거나 카카오 · 네이버로 로그인해 주세요.", null);
		}
		// 실패해도 요금이 나갈 수 있어 보내기 전에 셈
		lastSent.put(digits, now);
		for (String k : List.of("phone:" + digits, "ip:" + ip, "total")) {
			sentToday.put(k, count(k) + 1);
		}
		String value = String.format("%06d", random.nextInt(1_000_000));
		try {
			sms.send(digits, "[명품익스프레스] 인증번호 " + value + " (3분 안에 입력해 주세요)");
		} catch (RuntimeException e) {
			log.warn("인증번호 문자를 보내지 못했습니다", e);
			return new Sent(false, "문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요.", null);
		}
		codes.put(key(sessionId, purpose, digits), new Code(value, now.plus(VALID)));
		return new Sent(true, "인증번호를 보냈어요.", null);
	}

	/** @return 맞으면 null, 아니면 고객에게 보여 줄 이유 */
	public synchronized String verify(String sessionId, Purpose purpose, String phone, String input) {
		String digits = Fmt.digits(phone);
		Instant now = clock.instant();
		if (locked(digits, now)) {
			return lockedMessage();
		}
		String key = key(sessionId, purpose, digits);
		Code code = codes.get(key);
		if (code == null || !now.isBefore(code.expires)) {
			codes.remove(key);
			return "인증번호가 없거나 시간이 지났어요. 다시 받아 주세요.";
		}
		if (code.value.equals(input == null ? "" : input.trim())) {
			codes.remove(key);
			fails.remove(digits);
			return null;
		}
		Fails f = fails.computeIfAbsent(digits, k -> new Fails());
		f.count++;
		f.last = now;
		if (++code.tries >= MAX_TRIES) {
			codes.remove(key);
			return "5번 틀려서 이 인증번호는 쓸 수 없어요. 다시 받아 주세요.";
		}
		return "인증번호가 맞지 않아요. (" + (MAX_TRIES - code.tries) + "번 남음)";
	}

	private boolean locked(String digits, Instant now) {
		Fails f = fails.get(digits);
		if (f != null && !now.isBefore(f.last.plus(LOCK))) {
			fails.remove(digits);
			return false;
		}
		return f != null && f.count >= MAX_FAILS;
	}

	private static String lockedMessage() {
		return "여러 번 틀려서 잠시 막았어요. 15분 뒤에 다시 시도해 주세요.";
	}

	private int count(String key) {
		return sentToday.getOrDefault(key, 0);
	}

	private void prune(Instant now) {
		codes.values().removeIf(c -> !now.isBefore(c.expires));
		fails.values().removeIf(f -> !now.isBefore(f.last.plus(LOCK)));
		lastSent.values().removeIf(t -> !now.isBefore(t.plus(COOLDOWN)));
		LocalDate today = LocalDate.now(clock);
		if (!today.equals(countDay)) {
			countDay = today;
			sentToday.clear();
		}
	}

	private static String key(String sessionId, Purpose purpose, String digits) {
		return sessionId + "|" + purpose + "|" + digits;
	}
}
