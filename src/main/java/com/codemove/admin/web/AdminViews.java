package com.codemove.admin.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.codemove.common.CodeService;
import com.codemove.common.Fmt;
import com.codemove.member.MemberListRow;
import com.codemove.member.MemberService;
import com.codemove.move.MoveLine;
import com.codemove.move.MoveMapper;
import com.codemove.notification.NotificationService;

/** 관리자 화면 표시용 값 (DB → 화면 글자). 템플릿이 쓰는 이름은 프로토타입 목업과 같게 유지 */
@Component
public class AdminViews {

	/* ---------- 예약 ---------- */

	/** status: 예약 완료 / 이사 완료 / 예약 취소 (RSV_STATUS), userId: 회원 번호 */
	public record Reservation(long id, String status, String name, String userId, String appliedDate, String phone,
			String service, String origin, String destination, String movingDate, String movingTime, String reservationNo,
			String quoteNo, Long quoteId, String payStatus, String deposit, String ladder, String elevator, String volume,
			String memo, LocalDate moveDay) {

		/** 홈 · 예약 관리 배지 */
		public String displayStatus() {
			return "예약 취소".equals(status) ? "예약 취소" : "예약 완료";
		}

		/** 계약금 결제 상태 (PAY_STATUS) */
		public String paymentStatus() {
			return switch (payStatus == null ? "" : payStatus) {
				case "02" -> "결제 완료";
				case "03" -> "결제 취소";
				default -> "결제 대기";
			};
		}

		public String initial() {
			return name.substring(0, 1);
		}
	}

	/* ---------- 홈 ---------- */

	public record Stat(String label, long value, String icon, String href) {
	}

	/* ---------- 알림 패널 ---------- */

	public record Notification(long id, String category, String message, long createdAt, String icon,
			boolean important, String href) {

		public long minutesAgo() {
			return Math.max(0, (System.currentTimeMillis() - createdAt) / 60_000);
		}

		private long dayDifference() {
			ZoneId zone = ZoneId.systemDefault();
			LocalDate created = java.time.Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate();
			return Math.max(0, ChronoUnit.DAYS.between(created, LocalDate.now(zone)));
		}

		/** 프로토타입 getNotificationGroup (일 단위 차이로 7일 / 30일) */
		public String group() {
			long days = dayDifference();
			return days < 7 ? "최근 7일" : days < 30 ? "최근 30일" : null;
		}

		/** 프로토타입 getRelativeNotificationTime */
		public String relativeTime() {
			long days = dayDifference();
			long minutes = minutesAgo();
			if (days == 1) return "어제";
			if (days > 1) return days + "일 전";
			if (minutes < 1) return "방금";
			if (minutes < 60) return minutes + "분 전";
			return (minutes / 60) + "시간 전";
		}
	}

	/* ---------- 회원 · 리뷰 ---------- */

	/** id 가 음수면 회원 정보 없는 관리자 계정, type: 고객 | 관리자 */
	public record Member(long id, String name, String userId, String type, boolean isNew, String phone, String joined,
			String address) {

		public String initial() {
			return name.substring(0, 1);
		}
	}

	public record Review(long id, String name, String userId, String date, String type, int usageCount, int rating,
			String text, String photo, boolean answered, String reply) {

		public String initial() {
			return name.substring(0, 1);
		}
	}

	/* ---------- 메뉴 (SCR-A-017 전체 메뉴 · SCR-A-018 통합 메뉴 검색) ---------- */

	public record Menu(String label, String description, String icon, String href) {
	}

	public static final List<Menu> DRAWER_MENUS = List.of(
			new Menu("견적 관리", null, "document", "/admin/estimates"),
			new Menu("예약 관리", null, "calendar", "/admin/reservations"),
			new Menu("채팅 관리", null, "chat", "/admin/chat-management"),
			new Menu("알림 관리", null, "bell", "/admin/notifications"),
			new Menu("리뷰 관리", null, "star", "/admin/reviews"),
			new Menu("회원 관리", null, "people", "/admin/members"),
			new Menu("일정 관리", null, "calendar", "/admin/schedule/manage"),
			new Menu("견적서 등록", null, "document", "/admin/estimates/new"));

	public static final List<Menu> SEARCH_MENUS = List.of(
			new Menu("견적 관리", "견적 기록 및 상태 관리", "document", "/admin/estimates"),
			new Menu("견적서 등록", "사진 또는 PDF 견적서 등록", "document", "/admin/estimates/new"),
			new Menu("예약 관리", "예약 내역 및 상태 관리", "calendar", "/admin/reservations"),
			new Menu("예약 일정", "월간 이사 일정 확인", "calendar", "/admin/schedule"),
			new Menu("일정 관리", "휴무일 및 예약 불가일 관리", "calendar", "/admin/schedule/manage"),
			new Menu("리뷰 관리", "고객 리뷰와 답변 관리", "star", "/admin/reviews"),
			new Menu("회원 관리", "고객 및 관리자 관리", "people", "/admin/members"),
			new Menu("홈", "관리자 대시보드", "home", "/admin"));

	private final MoveMapper moves;
	private final MemberService members;
	private final NotificationService notifications;
	private final CodeService codes;

	public AdminViews(MoveMapper moves, MemberService members, NotificationService notifications, CodeService codes) {
		this.moves = moves;
		this.members = members;
		this.notifications = notifications;
		this.codes = codes;
	}

	public List<Reservation> reservations() {
		return moves.listMoveLines(null).stream().map(this::reservation).toList();
	}

	public Reservation reservation(MoveLine l) {
		String status = switch (l.getStatus()) {
			case "02" -> "이사 완료";
			case "03" -> "예약 취소";
			default -> "예약 완료";
		};
		return new Reservation(l.getReservationId(), status, l.getMemberName(), String.valueOf(l.getMemberId()),
				Fmt.yy(l.getAppliedAt()), l.getPhone(), codes.name("HOUSE_TYPE", l.getHouseType()),
				join(l.getFromAddress(), l.getFromDetail()), join(l.getToAddress(), l.getToDetail()), Fmt.yy(l.getMoveDate()),
				l.getMoveTime(), l.getReservationNo(), l.getQuoteNo(), l.getQuoteId(), l.getPayStatus(),
				Fmt.won(l.getDepositAmount()), "Y".equals(l.getLadderUse()) ? "필요" : "불필요",
				"Y".equals(l.getElevatorUse()) ? "있음" : "없음",
				l.getVolumeTon() == null ? "-" : l.getVolumeTon() + "톤", l.getMoveMemo(), l.getMoveDate());
	}

	public List<Notification> notifications() {
		return notifications.adminRecent().stream().map(n -> new Notification(n.getNotificationId(), n.getNtfTitle(),
				n.getNtfContent(), epoch(n.getCreatedAt()), NotificationService.icon(n.getNtfDetail()),
				"Y".equals(n.getIsImportant()), n.getLinkUrl() == null ? "/admin" : n.getLinkUrl())).toList();
	}

	/** 알림을 "최근 7일" / "최근 30일" 그룹으로 묶음 (최신순) */
	public static Map<String, List<Notification>> grouped(List<Notification> list) {
		Map<String, List<Notification>> grouped = new LinkedHashMap<>();
		grouped.put("최근 7일", new ArrayList<>());
		grouped.put("최근 30일", new ArrayList<>());
		list.stream().sorted(Comparator.comparingLong(Notification::minutesAgo)).filter(n -> n.group() != null)
				.forEach(n -> grouped.get(n.group()).add(n));
		grouped.values().removeIf(List::isEmpty);
		return grouped;
	}

	public List<Member> members() {
		LocalDate weekAgo = LocalDate.now().minusDays(7);
		return members.listForAdmin().stream().map((MemberListRow r) -> new Member(r.getId(), r.getName(),
				String.valueOf(Math.abs(r.getId())), "Y".equals(r.getIsAdmin()) ? "관리자" : "고객",
				r.getJoinedAt() != null && !r.getJoinedAt().toLocalDate().isBefore(weekAgo), r.getPhone(),
				r.getJoinedAt() == null ? "" : Fmt.dot(r.getJoinedAt().toLocalDate()).replace(" ", ""),
				Fmt.region(r.getLastAddress()))).toList();
	}

	public List<Review> reviews() {
		return moves.listReviews(null, null).stream().map((com.codemove.move.Review r) -> new Review(r.getReviewId(),
				r.getMemberName(), String.valueOf(r.getMemberId()), Fmt.dash(r.getCreatedAt().toLocalDate()), reviewType(r),
				r.getUsageCount(), r.getRating(), r.getContent(), r.getFirstPhoto(), r.getReplyContent() != null,
				r.getReplyContent())).toList();
	}

	/** 원룸이사 · 가정이사 · 기업이사, 보관이면 보관이사 */
	private String reviewType(com.codemove.move.Review r) {
		return "03".equals(r.getPackType()) ? "보관이사" : codes.name("HOUSE_TYPE", r.getHouseType());
	}

	static long epoch(LocalDateTime t) {
		return t == null ? 0 : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static String join(String a, String b) {
		return b == null || b.isBlank() ? (a == null ? "" : a) : a + " " + b;
	}
}
