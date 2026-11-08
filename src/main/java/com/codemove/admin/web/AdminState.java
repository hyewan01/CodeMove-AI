package com.codemove.admin.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 화면에서 바뀌는 상태 (프로토타입 App의 useState). 세션에 보관하며 백엔드 개발 시 DB로 대체합니다.
 */
@Getter
@Setter
public class AdminState {

	private static final String KEY = "adminState";

	/** 계약금 결제 완료 처리한 예약 ID (TBL_RESERVATION.PAY_STATUS) */
	private Set<Integer> paidReservations = new LinkedHashSet<>();
	private List<AdminMockData.Member> members = new ArrayList<>(AdminMockData.members());
	/** 리뷰 ID → 관리자 답변 (TBL_REVIEW.REPLY_CONTENT) */
	private Map<Integer, String> reviewReplies = new LinkedHashMap<>();
	private List<AdminMockData.Notification> notifications = new ArrayList<>(AdminMockData.notifications());
	private boolean notificationsRead;
	/** 자동 메시징 사용 (메시징 운영 설정) */
	private boolean messagingEnabled = true;
	private List<Routine> routines = new ArrayList<>(defaultRoutines());
	/** 다음 화면에서 한 번 보여줄 루틴 토스트 */
	private Toast pendingToast;
	/** 일정 관리: yy.MM.dd → closed(업체 휴무일) | unavailable(이사 예약 불가능) */
	private Map<String, String> managedDateStatuses = new LinkedHashMap<>(Map.of("26.09.20", "closed"));
	/** 일정 등록으로 추가한 일정 (DB 테이블 없음: TBL_SCHEDULE 삭제됨 → DB 대조 때 검토) */
	private List<ManualSchedule> manualSchedules = new ArrayList<>();

	/** date: yy.MM.dd, memberId: 회원 아이디, reservationId: 연결 예약 */
	public record ManualSchedule(long id, String date, String title, String category, String time, String place,
			String memberId, Integer reservationId) {
	}

	public AdminState() {
		AdminMockData.reviews().stream()
				.filter(r -> r.answered())
				.forEach(r -> reviewReplies.put(r.id(), AdminMockData.DEFAULT_REVIEW_REPLY));
	}

	public static AdminState of(HttpSession session) {
		AdminState state = (AdminState) session.getAttribute(KEY);
		if (state == null) {
			state = new AdminState();
			session.setAttribute(KEY, state);
		}
		return state;
	}

	/* ---------- 자동 메시징 루틴 (프로토타입 fireRoutines) ---------- */

	public record RoutineAction(String recipient, String message) {
	}

	@Getter
	@Setter
	public static class Routine {
		private long id;
		private String title;
		private boolean enabled;
		private List<String> conditions;
		private List<RoutineAction> actions;

		public Routine(long id, String title, boolean enabled, List<String> conditions, List<RoutineAction> actions) {
			this.id = id;
			this.title = title;
			this.enabled = enabled;
			this.conditions = new ArrayList<>(conditions);
			this.actions = new ArrayList<>(actions);
		}

		public String messageText() {
			return String.join(" / ", actions.stream().map(a -> a.message()).toList());
		}
	}

	public record Toast(String title, String message, String href) {
	}

	private static List<Routine> defaultRoutines() {
		return List.of(
				new Routine(1, "예약 승인 시 자동 안내", true, List.of("예약 관리 · 예약 확정"),
						List.of(new RoutineAction("고객", "안녕하세요 @회원 님, 요청하신 이사 예약이 확정되었습니다."))),
				new Routine(2, "견적서 등록 완료 알림", true, List.of("견적서 등록 · 견적서를 등록했을 시"),
						List.of(new RoutineAction("고객", "@회원 님의 신규 견적서가 등록되었습니다. 내용을 확인해 주세요."))),
				new Routine(3, "신규 회원 가입 관리자 알림", false, List.of("회원 관리 · 신규 회원이 가입되었을 시"),
						List.of(new RoutineAction("관리자 본인", "새로운 회원이 가입했습니다. 회원 정보를 확인해 주세요."))));
	}

	/** 조건이 발생하면 켜진 루틴마다 알림을 추가하고, 첫 루틴은 토스트로 띄움 */
	public void fire(String conditionKey) {
		if (!messagingEnabled) {
			return;
		}
		List<Routine> matched = routines.stream()
				.filter(r -> r.isEnabled() && r.getConditions().stream().anyMatch(c -> c.equals(conditionKey)
						|| (conditionKey.startsWith("일정 관리 · 특정") && c.startsWith(conditionKey))))
				.toList();
		if (matched.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		String href = conditionHref(conditionKey);
		for (Routine routine : matched) {
			notifications.add(0, new AdminMockData.Notification(now + routine.getId(), routine.getTitle(),
					routine.messageText(), now, conditionIcon(conditionKey), true, href));
		}
		notificationsRead = false;
		pendingToast = new Toast(matched.get(0).getTitle(), matched.get(0).messageText(), href);
	}

	/** 프로토타입 conditionToTarget */
	private static String conditionHref(String key) {
		if (key.startsWith("견적 관리") || key.startsWith("견적서 등록")) return "/admin/estimates";
		if (key.startsWith("회원 관리")) return "/admin/members";
		if (key.startsWith("일정 관리")) return "/admin/schedule";
		return "/admin/reservations";
	}

	/** 프로토타입 conditionToIcon */
	private static String conditionIcon(String key) {
		if (key.startsWith("예약 관리") || key.startsWith("일정 관리")) return "calendar";
		if (key.startsWith("견적 관리") || key.startsWith("견적서 등록")) return "document";
		if (key.startsWith("회원 관리")) return "user";
		if (key.startsWith("리뷰 관리")) return "star";
		return "bell";
	}

	public Toast takeToast() {
		Toast toast = pendingToast;
		pendingToast = null;
		return toast;
	}
}
