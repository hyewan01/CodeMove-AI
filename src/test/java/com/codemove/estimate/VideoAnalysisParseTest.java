package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.VideoAnalysis.RawResult;
import com.codemove.estimate.VideoAnalysis.Usage;

import tools.jackson.databind.json.JsonMapper;

class VideoAnalysisParseTest {

	private final VideoAnalysisService service = new VideoAnalysisService(null, null, JsonMapper.builder().build(), 0.30,
			2.50, "2026-11-23");

	@Test
	void parsesSchemaResponse() {
		RawResult r = service.parse("""
				{"video_ok": true, "reject_reason": null, "rooms": ["거실", "주방"],
				 "items": [{"item_name": "냉장고", "size_code": "03", "quantity": 1, "detected_name": "4도어 냉장고",
				            "room": "주방", "evidence": "문 4개", "needs_review": false}],
				 "memo": "팬트리 잔짐 많음"}
				""");
		assertTrue(r.videoOk());
		assertNull(r.rejectReason());
		assertEquals(2, r.rooms().size());
		assertEquals("냉장고", r.items().get(0).itemName());
		assertEquals("팬트리 잔짐 많음", r.memo());
	}

	@Test
	void stripsMarkdownFenceAndFlagsBadQuantity() {
		RawResult r = service.parse("""
				```json
				{"video_ok": true, "rooms": [], "items": [{"item_name": "의자", "size_code": "01", "quantity": "많음",
				  "detected_name": "의자", "room": "", "needs_review": false}]}
				```""");
		assertEquals(1, r.items().get(0).quantity());
		assertTrue(r.items().get(0).needsReview());
	}

	@Test
	void rejectedVideo() {
		RawResult r = service.parse("{\"video_ok\": false, \"reject_reason\": \"너무 어두움\", \"rooms\": [], \"items\": []}");
		assertFalse(r.videoOk());
		assertEquals("너무 어두움", r.rejectReason());
	}

	@Test
	void costUsesOutputPriceForThinkingTokens() {
		// 입력 100만 × 0.30 + (출력 1만 + 생각 1만) × 2.50 / 100만
		assertEquals(0.30 + 0.05, service.cost(new Usage(1_000_000, 10_000, 10_000, 0)), 1e-9);
	}
}
