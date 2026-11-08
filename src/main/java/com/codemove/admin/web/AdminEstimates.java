package com.codemove.admin.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import lombok.Data;

/**
 * 견적 관리 · 견적서 등록 모델 (프로토타입 EstimateRecord / EstimateDocumentData / getEstimateDocument).
 * 백엔드 개발 시 TBL_QUOTE · TBL_QUOTE_ITEM · TBL_QUOTE_OPTION 으로 대체합니다.
 */
public final class AdminEstimates {

	private AdminEstimates() {
	}

	/** 옵션 비용 행: 라벨, 선택지 2개 (견적서 양식 순서) */
	public static final String[][] OPTION_ROWS = { { "피아노", "일반", "그랜드" }, { "돌침대", "분해", "조립" },
			{ "조립장", "분해", "조립" }, { "벽걸이TV", "탈착", "설치" }, { "홈씨어터", "분리", "설치" }, { "수작업", "계단", "승강기" },
			{ "안마의자", "분리", "설치" }, { "에어컨", "탈착", "설치" } };

	/** 견적서 양식 데이터 (items 는 행 순서대로 4칸씩: itemNames[i] / itemQuantities[i]) */
	@Data
	public static class EstimateDoc {
		private String companyName = "명품익스프레스";
		private String customer = "";
		private String phone = "010-1234-5678";
		private String estimateDate = "";
		private String moveDate = "";
		private String transportDate = "";
		private String moveType = "포장이사";
		private String origin = "서울 강남구 테헤란로 123";
		private String destination = "경기 성남시 분당구 판교로 45";
		private String volume = "3.5톤";
		private String note = "파손 주의 물품은 별도 포장하며, 현장 상황에 따라 추가 비용이 발생할 수 있습니다.";
		private String movingCost = "1,050,000";
		private String extraCost = "270,000";
		private String total = "1,320,000";
		private String deposit = "132,000";
		private String balance = "1,188,000";
		private List<String> itemGroups = new ArrayList<>(List.of("전자기기", "가구", "", ""));
		private List<String> optionCosts = new ArrayList<>(Collections.nCopies(8, ""));
		private List<String> optionSelections = new ArrayList<>(Collections.nCopies(8, ""));
		private String workMethod = "포장";
		/** 운반방법: "" | 전 | 후 */
		private String ladder = "전";
		private String elevator = "후";
		private String stairs = "";
		private String departureLadder = "150,000";
		private String destinationLadder = "120,000";
		private String ladderSubtotal = "270,000";
		private int itemRowCount = 8;
		private List<String> itemNames = new ArrayList<>(List.of("냉장고", "세탁기", "침대", "책상", "의자", "박스"));
		private List<String> itemQuantities = new ArrayList<>(List.of("1대", "1대", "1개", "2개", "4개", "25개"));

		public String itemName(int index) {
			return index < itemNames.size() && itemNames.get(index) != null ? itemNames.get(index) : "";
		}

		public String itemQuantity(int index) {
			return index < itemQuantities.size() && itemQuantities.get(index) != null ? itemQuantities.get(index) : "";
		}

		/** 폼 바인딩 후 크기·빈값 정리 */
		public EstimateDoc normalized() {
			itemRowCount = Math.min(14, Math.max(1, itemRowCount));
			itemGroups = fit(itemGroups, 4);
			optionCosts = fit(optionCosts, 8);
			optionSelections = fit(optionSelections, 8);
			itemNames = fit(itemNames, itemRowCount * 4);
			itemQuantities = fit(itemQuantities, itemRowCount * 4);
			return this;
		}

		private static List<String> fit(List<String> list, int size) {
			List<String> out = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				String v = list != null && i < list.size() ? list.get(i) : null;
				out.add(v != null ? v : "");
			}
			return out;
		}
	}

	/** 견적 기록 (견적 관리 목록 한 줄) */
	@Data
	public static class EstimateRecord {
		private long id;
		private String status;
		private String name;
		private String date;
		private String approvedDate;
		private String phone;
		private String companyName;
		private String videoUrl;
		private String videoFileName;
		private String videoUploadedAt;
		/** 등록·수정한 견적서. 없으면 기본 견적서(getEstimateDocument) */
		private EstimateDoc doc;

		EstimateRecord(long id, String status, String name, String date, String approvedDate, String companyName) {
			this.id = id;
			this.status = status;
			this.name = name;
			this.date = date;
			this.approvedDate = approvedDate;
			this.companyName = companyName;
		}

		/** 분류 배지 색 (red 견적 검토 / blue 견적 승인 / green 견적 확정) */
		public String badgeTone() {
			return switch (status) {
				case "견적 승인" -> "bg-[#edf3f8] text-[#4b759b]";
				case "견적 확정" -> "bg-[#edf4ed] text-[#346538]";
				default -> "bg-[#fbefed] text-[#b9574f]";
			};
		}

		/** 프로토타입 getEstimateDocument */
		public EstimateDoc document() {
			if (doc != null) {
				return doc;
			}
			EstimateDoc d = new EstimateDoc();
			d.setCompanyName(companyName != null ? companyName : "명품익스프레스");
			d.setCustomer(name);
			d.setPhone(phone != null ? phone : "010-1234-5678");
			d.setEstimateDate(date);
			d.setMoveDate(date);
			d.setTransportDate(date);
			return d.normalized();
		}
	}

	public static List<EstimateRecord> initialRecords() {
		List<EstimateRecord> list = new ArrayList<>(List.of(
				new EstimateRecord(1, "견적 검토", "홍길동", "26.03.25", null, null),
				new EstimateRecord(2, "견적 승인", "김명품", "25.11.08", "25.11.10", "한빛테크"),
				new EstimateRecord(3, "견적 확정", "이서준", "25.04.17", "25.04.19", null),
				new EstimateRecord(4, "견적 검토", "박서준", "24.12.02", null, null),
				new EstimateRecord(5, "견적 승인", "최유진", "24.06.21", "24.06.24", "세림물류"),
				new EstimateRecord(6, "견적 확정", "정도현", "23.09.14", "23.09.16", null),
				new EstimateRecord(7, "견적 검토", "한지민", "23.02.28", null, null),
				new EstimateRecord(8, "견적 승인", "윤서아", "22.10.05", "22.10.07", "더블유컴퍼니"),
				new EstimateRecord(9, "견적 확정", "강민준", "22.01.19", "22.01.21", null),
				new EstimateRecord(10, "견적 검토", "송하윤", "21.07.30", null, null),
				new EstimateRecord(11, "견적 승인", "임지호", "21.03.12", "21.03.15", "미래오피스")));
		list.get(0).setVideoUrl("https://assets.mixkit.co/videos/15149/15149-360.mp4");
		list.get(0).setVideoFileName("거실_주방_이삿짐.mp4");
		list.get(0).setVideoUploadedAt("26.03.25 오후 2:18 업로드");
		list.get(1).setVideoUrl("https://assets.mixkit.co/videos/2302/2302-360.mp4");
		list.get(1).setVideoFileName("대형가구_이동동선.mp4");
		list.get(1).setVideoUploadedAt("25.11.08 오전 10:42 업로드");
		return list;
	}

	/** 오늘 날짜 YY.MM.DD (프로토타입 getTodayShortDate) */
	public static String today() {
		LocalDate d = LocalDate.now();
		return String.format("%02d.%02d.%02d", d.getYear() % 100, d.getMonthValue(), d.getDayOfMonth());
	}

	/* ---------- 견적서 등록 (SCR-A-003~004) ---------- */

	/** 등록 진행 상태: kind photo|pdf, step 1~4 */
	@Data
	public static class Registration {
		private String kind;
		private String fileName;
		private int step = 1;
		private EstimateDoc doc;
		private String name = "";
		private String date = "";
		private String status = "견적 검토";
	}

	private static final String[][] RECOGNIZED_ITEMS = { { "모니터", "6" }, { "컴퓨터", "3" }, { "공기청정기", "1" },
			{ "소형 냉장고", "1" }, { "전자레인지", "1" }, { "프린터/복합기", "1" }, { "TV", "1" }, { "의자", "6" }, { "테이블", "2" },
			{ "원형 테이블", "1" }, { "파티션", "3" }, { "대형 캐비넷", "2" }, { "책상", "2" }, { "데스크 수납함", "3" }, { "보조 서랍장", "3" } };

	/** 사진 견적서 AI 인식 결과 (프로토타입 analyzeDocument → registrationDocumentData, 목업) */
	public static EstimateDoc recognizePhoto() {
		Random random = new Random();
		String[] customers = { "김민준", "이서연", "박도윤", "최지우", "정하린" };
		EstimateDoc d = new EstimateDoc();
		d.setCompanyName("명품익스프레스");
		d.setCustomer(customers[random.nextInt(customers.length)]);
		d.setPhone(String.format("010-%04d-%04d", 1000 + random.nextInt(9000), 1000 + random.nextInt(9000)));
		d.setEstimateDate("26.09.04");
		d.setMoveDate("2026.09.04");
		d.setTransportDate("26.09.04");
		d.setOrigin("출발지 미인식");
		d.setDestination("도착지 미인식");
		d.setVolume("3.5톤");
		d.setNote("이미지에서 인식된 견적 정보입니다.");
		d.setMovingCost("0");
		d.setExtraCost("0");
		d.setTotal("1,320,000");
		d.setDeposit("132,000");
		d.setBalance("1,188,000");
		d.setDepartureLadder("0");
		d.setDestinationLadder("0");
		d.setLadderSubtotal("0");
		d.setOptionCosts(new ArrayList<>(Collections.nCopies(8, "0")));
		List<String> names = new ArrayList<>();
		List<String> quantities = new ArrayList<>();
		for (String[] item : RECOGNIZED_ITEMS) {
			names.add(item[0]);
			quantities.add(item[1]);
		}
		d.setItemNames(names);
		d.setItemQuantities(quantities);
		d.setItemRowCount(Math.max(8, (int) Math.ceil(RECOGNIZED_ITEMS.length / 4.0)));
		return d.normalized();
	}

	/**
	 * PDF 첨부 (프로토타입 registrationDocumentData, 입력값 없음). 이름·날짜는 등록 단계에서 입력합니다.
	 * 프로토타입은 PDF 글자를 읽어 품목·담당자를 찾지만, 목업에서는 인식 결과 없이 진행합니다.
	 */
	public static EstimateDoc pdfDocument() {
		EstimateDoc d = new EstimateDoc();
		d.setCustomer("담당자 미인식");
		d.setPhone("010-0000-0000");
		d.setEstimateDate("견적일 미인식");
		d.setMoveDate("견적일 미인식");
		d.setTransportDate("견적일 미인식");
		d.setOrigin("출발지 미인식");
		d.setDestination("도착지 미인식");
		d.setVolume("0톤");
		d.setNote("이미지에서 인식된 견적 정보입니다.");
		d.setMovingCost("0");
		d.setExtraCost("0");
		d.setTotal("0");
		d.setDeposit("0");
		d.setBalance("0");
		d.setDepartureLadder("0");
		d.setDestinationLadder("0");
		d.setLadderSubtotal("0");
		d.setItemNames(new ArrayList<>());
		d.setItemQuantities(new ArrayList<>());
		return d.normalized();
	}
}
