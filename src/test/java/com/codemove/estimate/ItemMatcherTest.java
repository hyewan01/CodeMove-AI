package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.VideoAnalysis.MappedItem;
import com.codemove.estimate.VideoAnalysis.RawItem;

class ItemMatcherTest {

	private static RawItem raw(String name, String size, int qty, String detected, boolean review) {
		return new RawItem(name, size, qty, detected, "거실", "", review);
	}

	@Test
	void exactNameKeepsSizeAndUsesCatalogCbm() {
		MappedItem m = ItemMatcher.map(raw("냉장고", "03", 1, "4도어 냉장고", false));
		assertEquals("냉장고", m.name());
		assertEquals("03", m.sizeCode());
		assertEquals(VideoAnalysis.EXACT, m.mappingType());
		assertEquals(1.492, m.unitCbm(), 1e-9);
		assertFalse(m.needsReview());
	}

	@Test
	void badSizeFallsBackToMediumAndNeedsReview() {
		MappedItem m = ItemMatcher.map(raw("소파", "5", 1, "소파", false));
		assertEquals("02", m.sizeCode());
		assertEquals(VideoAnalysis.AI_GUESS, m.mappingType());
		assertTrue(m.needsReview());
	}

	@Test
	void aliasFromDetectedNameFillsSize() {
		MappedItem m = ItemMatcher.map(raw("기타", "", 1, "양문형 냉장고", false));
		assertEquals("냉장고", m.name());
		assertEquals("03", m.sizeCode());
		assertEquals(VideoAnalysis.ALIAS, m.mappingType());
		assertFalse(m.needsReview());
	}

	@Test
	void aliasContainedInLongerName() {
		MappedItem m = ItemMatcher.map(raw("기타", "01", 2, "흰색 드럼 세탁기", false));
		assertEquals("세탁기", m.name());
		assertEquals("02", m.sizeCode(), "별칭(드럼 세탁기)에 크기가 있으면 별칭 크기를 씀");
		assertEquals(2, m.count());
	}

	@Test
	void unknownIsUnmappedWithZeroVolume() {
		MappedItem m = ItemMatcher.map(raw("기타", "02", 1, "빨래 건조대", false));
		assertEquals("빨래 건조대", m.name());
		assertEquals(VideoAnalysis.UNMAPPED, m.mappingType());
		assertEquals(0, m.cbm());
		assertTrue(m.needsReview());
	}

	@Test
	void quantityOutOfRangeIsClampedAndReviewed() {
		MappedItem zero = ItemMatcher.map(raw("의자", "01", 0, "의자", false));
		assertEquals(1, zero.count());
		assertTrue(zero.needsReview());
		MappedItem huge = ItemMatcher.map(raw("이사박스", "02", 999, "박스", false));
		assertEquals(ItemMatcher.MAX_QTY, huge.count());
		assertTrue(huge.needsReview());
	}

	@Test
	void totalCbmIsUnitTimesCount() {
		MappedItem m = ItemMatcher.map(raw("의자", "01", 4, "식탁 의자", false));
		assertEquals(0.191 * 4, m.cbm(), 1e-9);
	}
}
