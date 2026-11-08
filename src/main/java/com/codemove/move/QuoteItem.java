package com.codemove.move;

import lombok.Data;

/** TBL_QUOTE_ITEM (견적 품목, SORT_ORDER = 행 × 4 + 칸) */
@Data
public class QuoteItem {
	private Long qtItemId;
	private Long quoteId;
	private String category;
	private String itemName;
	private Integer quantity;
	private String unit;
	private Integer sortOrder;
}
