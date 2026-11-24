package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnalysisPromptTest {

	@Test
	void everyVersionLoadsWithTheCatalog() {
		for (String v : AnalysisPrompt.VERSIONS) {
			String text = AnalysisPrompt.text(v);
			assertFalse(text.contains("{{CATALOG}}"), v);
			assertTrue(text.contains("냉장고 | 가전"), v + " 에 품목표가 들어가야 함");
		}
	}

	@Test
	void defaultIsTheNewestVersion() {
		assertEquals(AnalysisPrompt.VERSIONS.get(0), AnalysisPrompt.VERSION);
		assertTrue(AnalysisPrompt.text().contains("선반이 있는 벽 면 수"), "v3 의 선반 세는 법");
	}

	@Test
	void unknownVersionIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> AnalysisPrompt.text("v9"));
	}
}
