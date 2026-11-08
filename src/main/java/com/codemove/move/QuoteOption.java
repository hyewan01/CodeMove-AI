package com.codemove.move;

import lombok.Data;

/** TBL_QUOTE_OPTION (견적 옵션, OPTION_CODE = QUOTE_OPTION 공통코드) */
@Data
public class QuoteOption {
	private Long qtOptionId;
	private Long quoteId;
	private String optionCode;
	private String choice1Yn;
	private String choice2Yn;
	private Long optionCost;
}
