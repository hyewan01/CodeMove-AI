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
}
