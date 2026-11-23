package com.codemove.move;

import lombok.Data;

/** TBL_REQUEST_ITEM (AI 인식·고객 추가 품목) */
@Data
public class RequestItem {
	private Long reItemId;
	private Long requestId;
	private String category;
	private String itemName;
	private String itemSize;
	private Integer quantity;
	private Double volume;
	private Integer confidence;
	private String needsReview;
	private String source;
	/** V4: AI 원본 인식명 · 매핑방식(MAPPING_TYPE 01~05) — 규격ID(SPEC_ID)는 저장할 때 품목명·크기로 찾음 */
	private String detectedName;
	private String mappingType;
}
