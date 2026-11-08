package com.codemove.admin.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 관리자 프로토타입(명품익스프레스 관리자.zip) 목업 데이터.
 * 백엔드 개발 시 TBL_RESERVATION · TBL_QUOTE · TBL_NOTIFICATION 조회로 대체합니다.
 */
public final class AdminMockData {

	private AdminMockData() {
	}

	/* ---------- 관리자 계정 (TBL_ADMIN) ---------- */

	public record AdminAccount(String name, String phone) {
	}

	/**
	 * 관리자도 고객과 같은 로그인 화면을 쓰되, 휴대전화 인증으로만 로그인합니다 (소셜 로그인은 고객 전용).
	 * 인증한 번호가 관리자 번호면 관리자 화면으로 보냅니다.
	 * 목업: 아래 번호가 관리자. 백엔드 개발 시 TBL_ADMIN.PHONE (+ IS_ACTIVE = 'Y') 조회로 대체
	 */
	public static AdminAccount findAdminByPhone(String phoneDigits) {
		AdminAccount admin = new AdminAccount("김선국", "01094458224");
		return admin.phone().equals(phoneDigits) ? admin : null;
	}

	/* ---------- 예약 ---------- */

	public record Reservation(int id, String status, String name, String userId, String appliedDate, String phone,
			String service, String origin, String destination, String movingDate, String movingTime,
			String consultationDate, String consultationTime, String ladder, String elevator, String volume,
			String items, String memo) {

		/** 홈 · 최근 예약 내역 배지 (프로토타입 getReservationDisplayStatus) */
		public String displayStatus() {
			return "반려".equals(status) ? "예약 취소" : "예약 완료";
		}

		public String initial() {
			return name.substring(0, 1);
		}
	}

	private static final String[][] CUSTOMERS = {
			{ "홍길동", "luxury", "010-1234-5678" }, { "김명품", "luxury2", "010-2244-8811" },
			{ "박철수", "park789", "010-7788-2200" }, { "최지우", "choi00", "010-3377-9021" },
			{ "이서준", "seojun82", "010-5892-1144" }, { "정하윤", "hayoon5", "010-9834-1420" },
			{ "강민준", "minjun77", "010-4471-3302" }, { "윤서아", "seoa11", "010-8842-3199" },
			{ "송지호", "jiho90", "010-1166-4201" }, { "한유진", "yujin26", "010-6673-9920" },
			{ "오도현", "dohyun3", "010-2207-6134" }, { "문지안", "jian8", "010-7318-4490" },
			{ "임지수", "jisoo19", "010-9901-7722" }, { "김도윤", "doyun24", "010-3142-6678" },
			{ "이지민", "jimin31", "010-5521-9034" }, { "박서연", "seoyeon6", "010-7713-2088" },
			{ "최현우", "hyunwoo88", "010-4055-7291" }, { "정수빈", "subin12", "010-6320-1847" },
			{ "강준호", "junho75", "010-9081-3362" }, { "윤채원", "chaewon4", "010-2466-8103" },
			{ "송민재", "minjae21", "010-8642-5179" }, { "한예린", "yerin09", "010-1937-6405" },
			{ "오승민", "seungmin5", "010-7254-3916" } };

	private static final String[] STATUSES = { "반려", "승인 대기", "이사 완료", "예약 확정", "견적 협의", "견적 확정" };

	private static final String[] ADDRESSES = { "서울 강남구 테헤란로 521", "서울 중구 세종대로 110", "서울 송파구 올림픽로 300",
			"서울 용산구 한강대로 405", "서울 마포구 월드컵로 212", "서울 서초구 남부순환로 2584", "경기 성남시 분당구 판교역로 166",
			"경기 수원시 팔달구 효원로 241", "경기 고양시 덕양구 고양시청로 10", "인천 남동구 정각로 29", "대전 서구 둔산로 100",
			"광주 서구 내방로 111", "부산 연제구 중앙대로 1001" };

	/** 프로토타입 initialReservations (30건) */
	public static List<Reservation> reservations() {
		int[] weeklyDays = { 27, 26, 26, 25, 24, 27, 23, 22, 22, 21 };
		List<Reservation> list = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			String[] c = CUSTOMERS[i % CUSTOMERS.length];
			String month = i < 20 ? "09" : "08";
			int day = i < 10 ? weeklyDays[i] : i < 20 ? 20 - (i - 10) : 28 - (i - 20);
			list.add(new Reservation(i + 1, STATUSES[i % STATUSES.length], c[0], c[1],
					"26." + month + "." + pad(day), c[2],
					i % 4 == 0 ? "기업이사" : i % 3 == 0 ? "원룸이사" : "가정이사",
					ADDRESSES[i % ADDRESSES.length],
					ADDRESSES[(i + 5 + (i % 3)) % ADDRESSES.length],
					i < 10 ? "26.09." + pad(weeklyDays[i]) : "26.10." + pad((i % 20) + 1),
					pad(7 + (i % 9)) + ":00",
					"26.09." + pad((i % 20) + 1),
					i % 2 == 1 ? "오후 2시~4시" : "오전 10시~12시",
					i % 3 == 0 ? "필요" : "불필요",
					i % 2 == 0 ? "있음" : "없음",
					String.format("%.1f톤", 2.5 + (i % 4)),
					"냉장고 1대, 세탁기 1대, TV 1대, 침대 1개, 책상 2개, 의자 4개",
					i % 2 == 1 ? "파손 주의 물품이 있어 꼼꼼한 포장을 요청했습니다." : "주차 공간이 협소하므로 방문 전 연락이 필요합니다."));
		}
		return list;
	}

	public static List<Reservation> recentReservations(int limit) {
		return reservations().stream()
				.sorted(Comparator.comparing((Reservation r) -> r.appliedDate()).reversed())
				.limit(limit)
				.toList();
	}

	/* ---------- 견적 ---------- */

	public record Estimate(int id, String status, String name, String date, String approvedDate, String tone,
			String companyName) {
	}

	/** 프로토타입 App estimateRecords 초기값 */
	public static List<Estimate> estimates() {
		return List.of(
				new Estimate(1, "견적 검토", "홍길동", "26.03.25", null, "red", null),
				new Estimate(2, "견적 승인", "김명품", "25.11.08", "25.11.10", "blue", "한빛테크"),
				new Estimate(3, "견적 확정", "이서준", "25.04.17", "25.04.19", "green", null),
				new Estimate(4, "견적 검토", "박서준", "24.12.02", null, "red", null),
				new Estimate(5, "견적 승인", "최유진", "24.06.21", "24.06.24", "blue", "세림물류"),
				new Estimate(6, "견적 확정", "정도현", "23.09.14", "23.09.16", "green", null),
				new Estimate(7, "견적 검토", "한지민", "23.02.28", null, "red", null),
				new Estimate(8, "견적 승인", "윤서아", "22.10.05", "22.10.07", "blue", "더블유컴퍼니"),
				new Estimate(9, "견적 확정", "강민준", "22.01.19", "22.01.21", "green", null),
				new Estimate(10, "견적 검토", "송하윤", "21.07.30", null, "red", null),
				new Estimate(11, "견적 승인", "임지호", "21.03.12", "21.03.15", "blue", "미래오피스"));
	}

	/* ---------- 홈 대시보드 (SCR-A-016) ---------- */

	public record Stat(String label, long value, String icon, String href) {
	}

	public static List<Stat> stats() {
		List<Reservation> rs = reservations();
		return List.of(
				new Stat("전체 예약 신청", rs.size(), "calendar", "/admin/reservations"),
				new Stat("리뷰 등록 현황", 12, "star", "/admin/reviews"),
				new Stat("견적 검토", estimates().stream().filter(e -> "견적 검토".equals(e.status())).count(), "document",
						"/admin/estimates"),
				new Stat("예약 완료", rs.stream().filter(r -> "예약 완료".equals(r.displayStatus())).count(), "truck",
						"/admin/reservations"));
	}

	/* ---------- 알림 패널 (SCR-A-019) ---------- */

	public record Notification(int id, String category, String message, long minutesAgo, String icon,
			boolean important, String href) {

		/** 프로토타입 getNotificationGroup (일 단위 차이로 7일 / 30일) */
		public String group() {
			long days = minutesAgo / (60 * 24);
			return days < 7 ? "최근 7일" : days < 30 ? "최근 30일" : null;
		}

		/** 프로토타입 getRelativeNotificationTime */
		public String relativeTime() {
			long days = minutesAgo / (60 * 24);
			if (days == 1) return "어제";
			if (days > 1) return days + "일 전";
			if (minutesAgo < 1) return "방금";
			if (minutesAgo < 60) return minutesAgo + "분 전";
			return (minutesAgo / 60) + "시간 전";
		}
	}

	public static List<Notification> notifications() {
		java.time.LocalDate today = java.time.LocalDate.now();
		return List.of(
				new Notification(1, "채팅 대기", "강민주 고객님이 관리자 상담을 요청했습니다. 응답이 필요합니다.", 10, "chat", true,
						"/admin/chats/kang22"),
				new Notification(2, "오늘의 일정",
						"오늘(" + today.getMonthValue() + "/" + today.getDayOfMonth() + ")은 이서준 고객님의 '이사 확정' 일정이 있습니다.",
						2 * 60, "truck", false, "/admin/schedule"),
				new Notification(3, "이사 예약", "최지우 고객님이 신규 가정이사 예약을 신청했습니다.", 3 * 60, "calendar", false,
						"/admin/reservations"),
				new Notification(4, "견적서 등록", "홍길동 고객님의 신규 견적서가 접수되었습니다.", 26 * 60, "document", false,
						"/admin/estimates"),
				new Notification(5, "상태 변경", "김명품 고객님의 예약 상태가 '승인 대기'에서 '견적 협의'로 변경되었습니다.", 30 * 60, "refresh",
						false, "/admin/reservations"),
				new Notification(6, "내용 수정", "박철수 고객님의 이사 희망 일정이 관리자에 의해 수정되었습니다.", 8 * 24 * 60, "edit", false,
						"/admin/reservations"),
				new Notification(7, "신규 가입", "윤아름 고객님이 시스템에 신규 회원으로 등록되었습니다.", 12 * 24 * 60, "user", false,
						"/admin/members"),
				new Notification(8, "데이터 삭제", "퀵메뉴 설정에서 '결제 문의' 항목이 성공적으로 삭제되었습니다.", 15 * 24 * 60, "trash", false,
						"/admin/chat-management/entry"));
	}

	/** 알림을 "최근 7일" / "최근 30일" 그룹으로 묶음 (최신순) */
	public static Map<String, List<Notification>> groupedNotifications() {
		Map<String, List<Notification>> grouped = new java.util.LinkedHashMap<>();
		grouped.put("최근 7일", new ArrayList<>());
		grouped.put("최근 30일", new ArrayList<>());
		notifications().stream()
				.sorted(Comparator.comparingLong(n -> n.minutesAgo()))
				.filter(n -> n.group() != null)
				.forEach(n -> grouped.get(n.group()).add(n));
		grouped.values().removeIf(items -> items.isEmpty());
		return grouped;
	}

	/* ---------- 메뉴 (SCR-A-017 전체 메뉴 · SCR-A-018 통합 메뉴 검색) ---------- */

	public record Menu(String label, String description, String icon, String href) {
	}

	/** 전체 메뉴 (프로토타입 Drawer 순서) */
	public static List<Menu> drawerMenus() {
		return List.of(
				new Menu("견적 관리", null, "document", "/admin/estimates"),
				new Menu("예약 관리", null, "calendar", "/admin/reservations"),
				new Menu("채팅 관리", null, "chat", "/admin/chat-management"),
				new Menu("알림 관리", null, "bell", "/admin/notifications"),
				new Menu("리뷰 관리", null, "star", "/admin/reviews"),
				new Menu("회원 관리", null, "people", "/admin/members"),
				new Menu("일정 관리", null, "calendar", "/admin/schedule/manage"),
				new Menu("견적서 등록", null, "document", "/admin/estimates/new"));
	}

	/** 통합 메뉴 검색 대상 (프로토타입 globalSearchItems) */
	public static List<Menu> searchMenus() {
		return List.of(
				new Menu("견적 관리", "견적 기록 및 상태 관리", "document", "/admin/estimates"),
				new Menu("견적서 등록", "사진 또는 PDF 견적서 등록", "document", "/admin/estimates/new"),
				new Menu("예약 관리", "예약 내역 및 상태 관리", "calendar", "/admin/reservations"),
				new Menu("예약 일정", "월간 이사 일정 확인", "calendar", "/admin/schedule"),
				new Menu("일정 관리", "휴무일 및 예약 불가일 관리", "calendar", "/admin/schedule/manage"),
				new Menu("리뷰 관리", "고객 리뷰와 답변 관리", "star", "/admin/reviews"),
				new Menu("회원 관리", "고객 및 관리자 관리", "people", "/admin/members"),
				new Menu("홈", "관리자 대시보드", "home", "/admin"));
	}

	private static String pad(int n) {
		return String.format("%02d", n);
	}
}
