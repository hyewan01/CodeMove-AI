package com.codemove.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.EstimateCalculator;

/** 고객 견적 요청 → 관리자 검토용 견적서 초안: 톤수 · 이사비용 · 옵션 칸 (DB 없이 매퍼를 흉내 냄) */
class QuoteServiceDraftTest {

	@Test
	void draftHasTonsMovingCostAndOptions() {
		Quote[] saved = new Quote[1];
		List<QuoteOption> options = new ArrayList<>();
		QuoteMapper mapper = (QuoteMapper) Proxy.newProxyInstance(QuoteMapper.class.getClassLoader(),
				new Class<?>[] { QuoteMapper.class }, (proxy, m, args) -> switch (m.getName()) {
					case "insertQuote" -> {
						((Quote) args[0]).setQuoteId(1L);
						saved[0] = (Quote) args[0];
						yield null;
					}
					case "findQuote" -> saved[0];
					case "updateQuote" -> {
						saved[0] = (Quote) args[0];
						yield 1;
					}
					case "insertOption" -> {
						options.add((QuoteOption) args[0]);
						yield null;
					}
					case "listItems", "listOptions" -> List.of();
					default -> m.getReturnType() == int.class ? 0 : null;
				});
		QuoteService service = new QuoteService(mapper);

		List<RequestItem> items = new ArrayList<>();
		items.add(item("이사박스", "02", 350)); // 바구니 26.95 + 에어컨 0.405 = 27.36 CBM → 적재 54.7 → 7톤
		items.add(item("에어컨", "02", 1));
		MoveRequest r = new MoveRequest();
		r.setRequestId(10L);
		r.setMemberId(20L);
		r.setPackType("04");
		EstimateCalculator.Estimate est = MoveService.estimate(items, "04", null);

		service.createForRequest(r, "홍길동", "010-1234-5678", items, est);

		Quote q = saved[0];
		assertNotNull(q);
		assertEquals(0, new BigDecimal("7").compareTo(new BigDecimal(q.getVolumeTon().toString())), "7톤");
		assertEquals("04", q.getWorkType());
		QuoteOption aircon = options.stream().filter(o -> "08".equals(o.getOptionCode())).findFirst().orElseThrow();
		assertEquals(250_000L, aircon.getOptionCost());
		assertEquals("Y", aircon.getChoice2Yn());
		assertEquals(MoveService.midpoint(est), q.getTotalAmount(), "견적서 합계 = 고객 화면 가운데 금액");
		assertEquals(q.getTotalAmount() / 10, q.getDepositAmount(), "계약금 10%");
		assertTrue(q.getNote().contains("5톤 1대 + 1톤 2대"));
	}

	private static RequestItem item(String name, String size, int qty) {
		RequestItem i = new RequestItem();
		i.setItemName(name);
		i.setItemSize(size);
		i.setQuantity(qty);
		return i;
	}
}
