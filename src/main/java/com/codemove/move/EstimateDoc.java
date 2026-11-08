package com.codemove.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Data;

/**
 * 견적서 / 계약서 양식 값 (관리자 견적 수정·등록 화면의 폼 바인딩, 프로토타입 EstimateDocumentData).
 * 금액·날짜는 화면 글자 그대로 담고, QuoteService 가 TBL_QUOTE · TBL_QUOTE_ITEM · TBL_QUOTE_OPTION 으로 바꿉니다.
 * 품목은 행 순서대로 4칸씩: itemNames[i] / itemQuantities[i]
 */
@Data
public class EstimateDoc implements java.io.Serializable {

	/** 옵션 비용 행: 라벨, 선택지 2개 (견적서 양식 순서 = QUOTE_OPTION 공통코드 01~08) */
	public static final String[][] OPTION_ROWS = { { "피아노", "일반", "그랜드" }, { "돌침대", "분해", "조립" },
			{ "조립장", "분해", "조립" }, { "벽걸이TV", "탈착", "설치" }, { "홈씨어터", "분리", "설치" }, { "수작업", "계단", "승강기" },
			{ "안마의자", "분리", "설치" }, { "에어컨", "탈착", "설치" } };

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
	/** 출발지·도착지 사다리 ( )층 */
	private String departureFloor = "";
	private String destinationFloor = "";
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
