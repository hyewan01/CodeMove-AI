package com.codemove.customer.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고객 화면 개발 단계용 목업 데이터 (프로토타입 App.tsx 의 하드코딩 값).
 * 백엔드 개발 시 각 항목을 Service 조회 결과로 교체합니다. 괄호 안은 대응 테이블입니다.
 */
public final class CustomerMockData {

	private CustomerMockData() {
	}

	public record HomeReview(String name, String meta, String moveType, String stars, String content) {
	}

	public record Booking(String typeLabel, String dday, String dateLabel, String timeLabel,
			String fromArea, String fromDetail, String toArea, String toDetail) {
	}

	public record EasyStatus(String eyebrow, String title, String description, String button, String next, String icon) {
	}

	/** 주거형태·포장방식 선택지 (TBL_MOVE_REQUEST.HOUSE_TYPE / PACK_TYPE, 코드값은 코드 정의 확정 필요) */
	public record MoveOption(String code, String label, String icon, String description) {
	}

	public record ItemOption(String name, String icon, double baseCbm) {
	}

	/** 홈 칭찬후기 (TBL_REVIEW) */
	public static List<HomeReview> homeReviews() {
		return List.of(
				new HomeReview("김코드", "이용 2회차 · 어제", "원룸(반포장)", "★★★★★",
						"친절하게 응대해주시고, 주의사항이나 필요한 부분 꼼꼼히 체크해주셔서 이사 잘 할 수 있었습니다."),
				new HomeReview("박이사", "이용 1회차 · 3일 전", "아파트(일반포장)", "★★★★★",
						"영상 견적 서비스 덕분에 방문 없이 바로 가격을 받을 수 있어서 너무 편했어요. 당일 기사님도 시간 약속 철저히 지켜주시고 물건 하나도 흠집 없이 옮겨주셨습니다."),
				new HomeReview("이무브", "이용 3회차 · 1주 전", "빌라(반포장)", "★★★★☆",
						"가격 대비 정말 만족스러웠어요. 다음 이사도 명품익스프레스로 할 것 같습니다. 앱이 직관적이어서 견적 받는 것도 금방 끝났어요."));
	}

	/** 홈 예약 카드 (TBL_RESERVATION + TBL_MOVE_REQUEST) */
	public static Booking booking() {
		return new Booking("원룸 · 일반포장", "D-30", "3월 18일 (수)", "오전 10:00",
				"강남구", "테헤란로 123, 301호", "서초구", "서초대로 456, 804호");
	}

	/** 쉬운 모드 홈 상태 카드 */
	public static EasyStatus easyStatus(String estimateStatus) {
		return switch (estimateStatus) {
			case "confirmed" -> new EasyStatus("예약 완료", "3월 18일 오전 10시에 이사해요", "원룸 · 일반포장",
					"예약 자세히 보기", "/move/reservation", "/assets/reservation-confirmed-illustration.svg");
			case "approved" -> new EasyStatus("견적 도착", "이사 견적이 준비됐어요", "비용과 작업 내용을 확인해주세요.",
					"견적서 보기", "/move", "/assets/estimate-approved-illustration.svg");
			case "waiting-approval" -> new EasyStatus("관리자 확인 중", "견적 요청이 접수됐어요",
					"담당자 확인이 끝나면 견적 정보를 알려드려요.", "진행 상황 보기", "/more/estimates",
					"/assets/estimate-requested-illustration.svg");
			default -> null;
		};
	}

	public static List<MoveOption> houseTypes() {
		return List.of(
				new MoveOption("1", "원룸 / 오피스텔", "easy-home-studio.svg", null),
				new MoveOption("2", "아파트", "easy-home-apartment.svg", null),
				new MoveOption("3", "빌라 / 주택", "easy-home-house.svg", null),
				new MoveOption("4", "사무실", "easy-home-office.svg", null));
	}

	public static List<MoveOption> packTypes() {
		return List.of(
				new MoveOption("1", "일반포장", "easy-pack-standard.svg", "직접 포장"),
				new MoveOption("2", "반포장", "easy-pack-partial.svg", "큰 짐은 업체가 포장"));
	}

	/** 품목 종류 선택지 */
	public static List<ItemOption> itemOptions() {
		return List.of(
				new ItemOption("냉장고", "item-fridge.svg", 1.8),
				new ItemOption("침대", "item-bed.svg", 2.4),
				new ItemOption("소파", "item-sofa.svg", 2.1),
				new ItemOption("이사박스", "item-box.svg", 0.3),
				new ItemOption("세탁기", "item-washer.svg", 1.2),
				new ItemOption("테이블", "item-table.svg", 0.85));
	}

	/** AI 인식 품목 (TBL_REQUEST_ITEM) - JS 로 넘기므로 Map 사용 */
	public static List<Map<String, Object>> recognizedItems() {
		return List.of(
				item("fridge", "냉장고", 1, 1.8, "item-fridge.svg", false),
				item("bed", "침대", 1, 2.4, "item-bed.svg", false),
				item("sofa", "소파", 1, 2.1, "item-sofa.svg", true),
				item("boxes", "이사박스", 12, 0.3, "item-box.svg", false),
				item("washer", "세탁기", 1, 1.2, "item-washer.svg", false),
				item("table", "테이블", 2, 0.85, "item-table.svg", true));
	}

	/** 주소 검색 목업 (추후 주소 API 연동) */
	public static List<Map<String, Object>> addresses() {
		String[][] rows = {
				{ "서울 강남구 테헤란로 123", "강남구 역삼동 123-45" },
				{ "서울 강남구 테헤란로 152", "강남구 역삼동 678-9" },
				{ "서울 강남구 강남대로 396", "강남구 역삼동 831-2" },
				{ "서울 서초구 서초대로 456", "서초구 서초동 456-78" },
				{ "서울 서초구 반포대로 201", "서초구 반포동 201-3" },
				{ "서울 송파구 올림픽로 300", "송파구 방이동 300-1" },
				{ "서울 마포구 합정로 100", "마포구 합정동 100-22" },
				{ "서울 용산구 이태원로 200", "용산구 이태원동 200-5" },
				{ "서울 성동구 왕십리로 50", "성동구 성수동 50-11" },
				{ "서울 영등포구 여의대로 24", "영등포구 여의도동 24-3" },
				{ "경기 성남시 분당구 판교역로 235", "성남시 삼평동 235-6" },
				{ "경기 수원시 영통구 광교중앙로 145", "수원시 이의동 145-2" } };
		return java.util.Arrays.stream(rows).map(r -> map("road", r[0], "jibun", r[1])).toList();
	}

	/** AI 예상 견적 (TBL_MOVE_REQUEST.AI_EST_MIN / AI_EST_MAX) */
	public static Map<String, Object> estimate() {
		return map("amount", 410000, "min", 400000, "max", 480000);
	}

	/* ---------- 내 이사 · 견적 상세 · 예약 상세 ---------- */

	public record QuoteRow(String label, String sub, String value) {
	}

	public record MoveQuote(String totalLabel, String typeLabel, String scheduleLabel, String routeLabel, List<QuoteRow> rows) {
	}

	/** 업체 견적서 원본 (TBL_ESTIMATE + TBL_ESTIMATE_ITEM) */
	public record VendorDoc(String quoteNo, String manager, String managerPhone, String quoteDate,
			String fromAddress, String toAddress, String workDate, List<List<String>> items) {
	}

	public record DoneMove(String date, String from, String to, String meta) {
	}

	/** AI 인식 품목 결과 (TBL_REQUEST_ITEM) */
	public record DetectedItem(String icon, String name, int count, String cbm) {
	}

	/** 견적내역 · 예약내역 목록 행 */
	public record HistoryRow(String status, String statusLabel, String date, String route, String meta) {
	}

	/** 내가 쓴 후기 (TBL_REVIEW) - 세션에 보관하므로 Serializable */
	public record MyReview(String stars, String date, String route, String content, String moveType,
			int photoCount, boolean justNow) implements java.io.Serializable {
	}

	/** 알림 (TBL_NOTIFICATION) */
	public record Alert(int id, String type, String title, String body, String time) {
	}

	/** 내 이사 > 견적 정보 (TBL_ESTIMATE) */
	public static MoveQuote moveQuote() {
		return new MoveQuote("410,000원", "원룸 · 일반포장", "2026. 03. 18 오전 10:00", "강남구 → 서초구", List.of(
				new QuoteRow("기본 운송비", "기본 거리 10km 포함", "250,000원"),
				new QuoteRow("포장·인력 등 작업비", null, "140,000원"),
				new QuoteRow("도착지 사다리차 비용 (8층)", null, "20,000원")));
	}

	public static VendorDoc vendorDoc() {
		return new VendorDoc("QT-20260310-0042", "김선국", "010-9445-8224", "2026. 03. 10.",
				"서울 강남구 테헤란로 123, 301호", "서울 서초구 서초대로 456, 804호", "2026. 03. 18.", List.of(
						List.of("냉장고", "1", "침대", "1", "소파", "1", "이사박스", "12"),
						List.of("세탁기", "1", "옷장", "1", "테이블", "1", "행거", "2"),
						List.of("TV", "1", "서랍장", "1", "의자", "2", "화분", "3"),
						List.of("전자레인지", "1", "책장", "1", "책상", "1", "생활짐", "1식")));
	}

	public static List<DoneMove> doneMoves() {
		return List.of(new DoneMove("2025. 11. 04", "마포구 합정동", "서대문구 홍은동", "원룸 · 반포장 · 320,000원"));
	}

	/** 견적 상세 > 견적 금액 영수증 */
	public static List<QuoteRow> receiptRows() {
		return List.of(
				new QuoteRow("기본 운송비", null, "250,000원"),
				new QuoteRow("포장·인력 등 작업비", null, "140,000원"),
				new QuoteRow("도착지 사다리차 비용 (8층)", null, "20,000원"));
	}

	/** 견적 상세 > 이사 정보 */
	public static List<QuoteRow> moveInfoRows() {
		return List.of(
				new QuoteRow("견적 번호", null, "QT-20260310-0042"),
				new QuoteRow("이사일", null, "2026. 03. 18 오전 10:00"),
				new QuoteRow("이사 유형", null, "원룸 · 일반포장"),
				new QuoteRow("출발지", null, "서울 강남구 테헤란로 123, 301호"),
				new QuoteRow("도착지", null, "서울 서초구 서초대로 456, 804호"));
	}

	public static List<DetectedItem> detectedItems() {
		return List.of(
				new DetectedItem("item-sofa.svg", "소파 3인용", 1, "1.2"),
				new DetectedItem("item-bed.svg", "퀸 침대 프레임", 1, "1.8"),
				new DetectedItem("item-tv.svg", "TV 55인치", 1, "0.4"),
				new DetectedItem("item-table.svg", "식탁 + 의자 4개", 1, "0.9"),
				new DetectedItem("item-wardrobe.svg", "장롱 (3단)", 1, "1.6"),
				new DetectedItem("item-box.svg", "기타 박스류", 12, "1.8"));
	}

	/** 예약 상세 > 결제 금액 (TBL_PAYMENT) */
	public static List<QuoteRow> paymentRows() {
		return List.of(
				new QuoteRow("기본 운반 (원룸)", null, "250,000원"),
				new QuoteRow("일반포장", null, "120,000원"),
				new QuoteRow("사다리차 (도착지)", null, "40,000원"));
	}

	/* ---------- 더보기 ---------- */

	public static List<HistoryRow> estimateHistory() {
		return List.of(
				new HistoryRow("reviewing", "검토 중", "2026. 06. 10", "서초구 → 마포구", "투룸 · 반포장"),
				new HistoryRow("expired", "만료", "2026. 01. 24", "강남구 → 송파구", "원룸 · 일반포장"));
	}

	/** 지난 이사 (연도별 묶음, TBL_RESERVATION) */
	public static Map<String, List<HistoryRow>> pastReservationsByYear() {
		Map<String, List<HistoryRow>> byYear = new LinkedHashMap<>();
		byYear.put("2025년", List.of(
				new HistoryRow("completed", "완료", "2025. 11. 02", "송파구 → 성동구", "원룸 · 반포장"),
				new HistoryRow("completed", "완료", "2025. 02. 14", "마포구 → 용산구", "원룸 · 일반포장")));
		byYear.put("2024년", List.of(
				new HistoryRow("cancelled", "취소", "2024. 08. 21", "강동구 → 광진구", "원룸")));
		return byYear;
	}

	public static MyReview pastReview() {
		return new MyReview("★★★★★", "2025. 11. 03", "송파구 → 성동구",
				"친절하게 응대해주시고 꼼꼼히 챙겨주셔서 편하게 이사했습니다.", "원룸 · 반포장", 0, false);
	}

	/* ---------- 상담 · 알림 ---------- */

	/** 챗봇 퀵메뉴 (TBL_QUICK_MENU) */
	public static List<String> chatTopics() {
		return List.of("이사 견적 알아보기", "예약 및 일정 확인", "영상 분석 견적", "이사 준비 체크리스트", "결제 및 환불 문의", "상담사 연결");
	}

	public static List<Alert> alerts() {
		return List.of(
				new Alert(1, "예약/일정 정보", "이사 예약이 확정되었어요", "3월 18일 오전 10시 예약과 방문 주소를 확인해주세요.", "14시간 전"),
				new Alert(2, "이야기", "새로운 이야기가 등록됐어요", "이사 전날 꼭 확인해야 할 준비 체크리스트를 살펴보세요.", "하루 전"),
				new Alert(3, "견적 정보", "새로운 견적이 도착했어요", "회원님의 이사 조건에 맞는 업체 견적을 비교해보세요.", "2일 전"),
				new Alert(4, "예약/일정 정보", "이사 일정이 가까워지고 있어요", "이사까지 30일 남았어요. 준비 체크리스트를 확인해주세요.", "3일 전"),
				new Alert(5, "결제 정보", "예약금 결제가 완료되었어요", "결제 내역과 남은 결제 일정을 확인할 수 있어요.", "5일 전"),
				new Alert(6, "이야기", "새로운 이사 팁이 도착했어요", "주소 이전과 공과금 정산을 미리 준비해보세요.", "7일 전"));
	}

	public static List<String> alertCategories() {
		return List.of("전체알림", "예약/일정 정보", "견적 정보", "결제 정보", "이야기");
	}

	private static Map<String, Object> item(String id, String name, int count, double baseCbm, String icon, boolean needsReview) {
		Map<String, Object> m = map("id", id, "name", name, "count", count, "baseCbm", baseCbm, "size", "중형", "icon", icon);
		m.put("needsReview", needsReview);
		return m;
	}

	static Map<String, Object> map(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}
}
