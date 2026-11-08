package com.codemove.move;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 견적서 · 견적 품목 · 견적 옵션 (mapper/QuoteMapper.xml) */
@Mapper
public interface QuoteMapper {

	/** 견적 관리 목록 (만료 제외) */
	List<QuoteLine> listQuoteLines();

	Quote findQuote(long quoteId);

	Quote findQuoteByRequest(long requestId);

	List<QuoteItem> listItems(long quoteId);

	List<QuoteOption> listOptions(long quoteId);

	/** quoteId 는 SEQ_QUOTE 로 채워지고 QUOTE_NO 는 QT-오늘-번호 */
	void insertQuote(Quote quote);

	/** 견적서 양식 값 (상태·승인일·요청 연결은 그대로) */
	int updateQuote(Quote quote);

	void deleteItems(long quoteId);

	void insertItem(QuoteItem item);

	void deleteOptions(long quoteId);

	void insertOption(QuoteOption option);

	int updateStatus(@Param("quoteId") long quoteId, @Param("status") String status);

	/** 견적 승인: STATUS 02, APPROVED_AT, 유효기간 30일 */
	int approve(@Param("quoteId") long quoteId, @Param("adminId") Long adminId);

	int deleteQuote(long quoteId);
}
