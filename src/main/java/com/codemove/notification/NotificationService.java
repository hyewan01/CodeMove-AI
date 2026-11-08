package com.codemove.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 (TBL_NOTIFICATION) · 메시징 운영 설정 · 자동 메시징 루틴.
 * 루틴은 편집 화면이 없고 "일정 기준 자동 알림 규칙 설정"이 제외 범위라 코드에 고정합니다 (프로토타입 fireRoutines).
 * 문자·알림톡 발송 업체는 연동하지 않았고 앱 알림(NTF_CHANNEL 01)만 저장합니다.
 */
@Service
public class NotificationService {

	/** 루틴이 만든 알림을 다음 관리자 화면에 띄울 토스트 */
	public record Toast(String title, String message, String href) implements java.io.Serializable {
	}

	/** recipient: 고객 | 관리자 본인, message 의 @회원 은 회원 이름으로 바뀜 */
	public record Routine(String title, boolean enabled, String condition, String recipient, String message,
			String detail) {
	}

	public static final List<Routine> ROUTINES = List.of(
			new Routine("예약 승인 시 자동 안내", true, "예약 관리 · 예약 확정", "고객",
					"안녕하세요 @회원 님, 요청하신 이사 예약이 확정되었습니다.", "04"),
			new Routine("견적서 등록 완료 알림", true, "견적서 등록 · 견적서를 등록했을 시", "고객",
					"@회원 님의 신규 견적서가 등록되었습니다. 내용을 확인해 주세요.", "02"),
			new Routine("신규 회원 가입 관리자 알림", false, "회원 관리 · 신규 회원이 가입되었을 시", "관리자 본인",
					"새로운 회원이 가입했습니다. 회원 정보를 확인해 주세요.", "14"));

	/** NTF_DETAIL → 관리자 알림 패널 아이콘 */
	public static String icon(String detail) {
		return switch (detail == null ? "" : detail.trim()) {
			case "13", "15" -> "chat";
			case "07", "08", "09" -> "truck";
			case "04", "05", "10" -> "calendar";
			case "01", "02", "03" -> "document";
			case "06" -> "edit";
			case "14" -> "user";
			case "16" -> "refresh";
			case "17" -> "trash";
			default -> "bell";
		};
	}

	/** 조건 → 관련 화면 (프로토타입 conditionToTarget) */
	private static String conditionHref(String key) {
		if (key.startsWith("견적 관리") || key.startsWith("견적서 등록")) return "/admin/estimates";
		if (key.startsWith("회원 관리")) return "/admin/members";
		if (key.startsWith("일정 관리")) return "/admin/schedule";
		if (key.startsWith("리뷰 관리")) return "/admin/reviews";
		return "/admin/reservations";
	}

	private final NotificationMapper mapper;

	public NotificationService(NotificationMapper mapper) {
		this.mapper = mapper;
	}

	/* ---------- 보내기 ---------- */

	/** 고객 알림 (NTF_TYPE 01 내 이사) */
	public void toMember(long memberId, String detail, String title, String content, String link) {
		Notification n = base("01", detail, title, content, link, false);
		n.setMemberId(memberId);
		n.setNtfType("01");
		mapper.insert(n);
	}

	/** 관리자 알림 (NTF_TYPE 03 관리자 업무) */
	public void toAdmin(String detail, String title, String content, String link, boolean important) {
		mapper.insert(base("02", detail, title, content, link, important));
	}

	private static Notification base(String receiver, String detail, String title, String content, String link,
			boolean important) {
		Notification n = new Notification();
		n.setReceiverType(receiver);
		n.setNtfType("03");
		n.setNtfDetail(detail);
		n.setNtfTitle(title.length() > 100 ? title.substring(0, 100) : title);
		n.setNtfContent(content.length() > 500 ? content.substring(0, 500) : content);
		n.setLinkUrl(link);
		n.setIsImportant(important ? "Y" : "N");
		n.setNtfChannel("01");
		return n;
	}

	/**
	 * 조건이 생기면 켜진 루틴마다 알림을 보내고, 첫 루틴은 토스트로 돌려줌.
	 * 관리자 알림 패널에는 항상 남기고(중요 표시), 수신자가 고객이면 그 회원에게도 보냄.
	 * @return 띄울 토스트 (전체 자동 메시징이 꺼졌거나 맞는 루틴이 없으면 null)
	 */
	@Transactional
	public Toast fire(String condition, Long memberId, String memberName) {
		if (!setting().isMessagingEnabled()) {
			return null;
		}
		Toast toast = null;
		String href = conditionHref(condition);
		for (Routine r : ROUTINES) {
			if (!r.enabled() || !r.condition().equals(condition)) {
				continue;
			}
			String message = r.message().replace("@회원", memberName != null ? memberName : "고객");
			toAdmin(r.detail(), r.title(), message, href, true);
			if ("고객".equals(r.recipient()) && memberId != null) {
				toMember(memberId, r.detail(), r.title(), message, "/move");
			}
			if (toast == null) {
				toast = new Toast(r.title(), message, href);
			}
		}
		return toast;
	}

	/* ---------- 관리자 ---------- */

	public List<Notification> adminRecent() {
		return mapper.listAdmin(30);
	}

	public boolean hasUnreadAdmin() {
		return mapper.countUnreadAdmin() > 0;
	}

	@Transactional
	public void markAdminRead() {
		mapper.markAllAdminRead();
	}

	public List<Notification> history(int limit) {
		return mapper.listHistory(limit);
	}

	/* ---------- 고객 ---------- */

	public List<Notification> memberAlerts(long memberId) {
		return mapper.listMember(memberId);
	}

	public int unreadCount(long memberId) {
		return mapper.countUnreadMember(memberId);
	}

	@Transactional
	public void markRead(long memberId, long id) {
		mapper.markMemberRead(memberId, id);
	}

	@Transactional
	public void markAllRead(long memberId) {
		mapper.markAllMemberRead(memberId);
	}

	/* ---------- 메시징 운영 설정 ---------- */

	@Transactional
	public NotificationSetting setting() {
		NotificationSetting s = mapper.findSetting();
		if (s == null) {
			mapper.insertSetting();
			s = mapper.findSetting();
		}
		return s;
	}

	/** 바꾼 항목만 (null 은 그대로) */
	@Transactional
	public void updateSetting(Boolean enabled, Boolean quietEnabled, String quietStart, String quietEnd,
			Boolean smsFallback) {
		NotificationSetting s = setting();
		if (enabled != null) s.setIsActive(enabled ? "Y" : "N");
		if (quietEnabled != null) s.setQuietEnabled(quietEnabled ? "Y" : "N");
		if (quietStart != null && quietStart.matches("\\d{2}:\\d{2}")) s.setQuietStart(quietStart);
		if (quietEnd != null && quietEnd.matches("\\d{2}:\\d{2}")) s.setQuietEnd(quietEnd);
		if (smsFallback != null) s.setSmsFallback(smsFallback ? "Y" : "N");
		mapper.updateSetting(s);
	}
}
