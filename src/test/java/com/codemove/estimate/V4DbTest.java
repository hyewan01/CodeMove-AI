package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.move.MoveMapper;
import com.codemove.move.RequestItem;

/**
 * TEAM03 에 적용한 V4 테이블로 분석 기록 · 품목 저장 SQL 이 동작하는지 (공용 DB 라 끝나면 모두 되돌림).
 */
@SpringBootTest
@Transactional
class V4DbTest {

	@Autowired
	V4Schema schema;
	@Autowired
	AnalysisLogMapper logs;
	@Autowired
	MoveMapper moves;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void analysisLogInsertFindListAndLink() {
		assertTrue(schema.ready(), "V4 가 적용되어 있어야 함");
		AnalysisRow row = new AnalysisRow();
		row.setTestYn("Y");
		row.setModelName("gemini-4-hydrogen");
		row.setPromptVersion("v2");
		row.setInputTokens(8042L);
		row.setOutputTokens(3229L);
		row.setTotalTokens(12723L);
		row.setUsageJson("{\"thoughtTokens\":1452}");
		row.setPriceIn(1.5);
		row.setPriceOut(7.5);
		row.setPriceDate(LocalDate.of(2026, 11, 23));
		row.setCurrency("USD");
		row.setEstCost(0.0472);
		row.setTotalMs(34000L);
		row.setRetryCount(0);
		row.setIsSuccess("Y");
		row.setItemCount(32);
		row.setMappedCount(32);
		row.setReviewCount(4);
		row.setRawJson("{\"video_ok\":true}");
		row.setResultJson("{\"video\":\"v.mp4\",\"items\":[]}");
		logs.insert(row);
		assertNotNull(row.getAnalysisId());

		AnalysisRow found = logs.find(row.getAnalysisId());
		assertEquals("gemini-4-hydrogen", found.getModelName());
		assertEquals("{\"video_ok\":true}", found.getRawJson());
		assertTrue(logs.list(5).stream().anyMatch(r -> r.getAnalysisId().equals(row.getAnalysisId())));

		Long requestId = jdbc.queryForObject("SELECT MAX(REQUEST_ID) FROM TBL_MOVE_REQUEST", Long.class);
		assertEquals(1, logs.linkRequest(row.getAnalysisId(), requestId, null));
		assertEquals("N", logs.find(row.getAnalysisId()).getTestYn());
	}

	@Test
	void requestItemKeepsSpecDetectedNameAndMapping() {
		Long requestId = jdbc.queryForObject("SELECT MAX(REQUEST_ID) FROM TBL_MOVE_REQUEST", Long.class);
		RequestItem i = new RequestItem();
		i.setRequestId(requestId);
		i.setCategory("02");
		i.setItemName("냉장고");
		i.setItemSize("03");
		i.setQuantity(1);
		i.setVolume(1.49);
		i.setConfidence(90);
		i.setNeedsReview("N");
		i.setSource("01");
		i.setDetectedName("4도어 냉장고");
		i.setMappingType("01");
		moves.insertRequestItemV4(i);
		Long spec = jdbc.queryForObject("SELECT SPEC_ID FROM TBL_REQUEST_ITEM WHERE REQUEST_ID = ? AND DETECTED_NAME = ?",
				Long.class, requestId, "4도어 냉장고");
		assertNotNull(spec, "품목명 · 크기로 표준 규격을 찾아 SPEC_ID 를 채움");
		assertEquals(1.492, jdbc.queryForObject("SELECT CBM FROM TBL_ITEM_SPEC WHERE SPEC_ID = ?", Double.class, spec),
				1e-9);
	}
}
