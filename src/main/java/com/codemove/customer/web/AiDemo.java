package com.codemove.customer.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 영상 분석 시연값. AI 모듈(영상 → 품목·부피 인식) 연동 전까지 견적 요청 화면이 쓰는 인식 결과입니다.
 * 고객이 확인·수정한 품목은 TBL_REQUEST_ITEM 에 저장됩니다.
 */
public final class AiDemo {

	private AiDemo() {
	}

	public record ItemOption(String name, String icon, double baseCbm) {
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

	/** 주소 검색 예비 목록 (카카오 우편번호 스크립트를 못 불러올 때) */
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
