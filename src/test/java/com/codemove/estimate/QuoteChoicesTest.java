package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.EstimateCalculator.Conditions;
import com.codemove.estimate.EstimateCalculator.Fee;
import com.codemove.estimate.EstimateCalculator.Line;
import com.codemove.estimate.EstimateCalculator.OptionChoice;
import com.codemove.estimate.EstimateCalculator.Site;

/** 견적 화면 개편(2026-11-24): 상세주소 층수 · 고객이 고른 추가 작업 · 공휴일 할증 */
class QuoteChoicesTest {

	@Test
	void floorComesFromTheDetailAddress() {
		assertEquals(3, Floors.of("3층 301호"));
		assertEquals(12, Floors.of("101동 1203호"));
		assertEquals(3, Floors.of("301호"));
		assertEquals(2, Floors.of("2층"));
		assertEquals(15, Floors.of("가동 15층"));
		assertNull(Floors.of("지하 1층"));
		assertNull(Floors.of("B1"));
		assertNull(Floors.of("101동"), "동 번호는 층이 아님");
		assertNull(Floors.of(""));
		assertNull(Floors.of(null));
	}

	private static List<Line> lines() {
		return List.of(Line.of("냉장고", "02", 1), Line.of("에어컨", "02", 1), Line.of("안마의자", "02", 1));
	}

	private static Conditions with(List<OptionChoice> options) {
		return new Conditions(EstimateCalculator.FULL, 10.0, null, Site.UNKNOWN, Site.UNKNOWN, null, options);
	}

	@Test
	void withoutChoicesOptionsAreGuessedFromItems() {
		var e = EstimateCalculator.estimate(lines(), with(null));
		assertTrue(e.fees().stream().anyMatch(f -> f.label().startsWith("에어컨")));
		assertTrue(e.fees().stream().anyMatch(f -> f.label().startsWith("안마의자")));
	}

	@Test
	void chosenOptionsReplaceTheGuessAndOtherCompanyIsOnlyNoted() {
		var e = EstimateCalculator.estimate(lines(), with(List.of(
				new OptionChoice("08", false, false, 1, true), // 에어컨: 다른 업체
				new OptionChoice("07", true, false, 1, false), // 안마의자: 분리만
				new OptionChoice("01", false, true, 1, false), // 피아노 그랜드
				new OptionChoice("06", true, false, 1, false)))); // 수작업은 받지 않음
		List<String> labels = e.fees().stream().map(f -> f.label()).toList();
		assertFalse(labels.stream().anyMatch(l -> l.startsWith("에어컨")), labels.toString());
		Fee massage = e.fees().stream().filter(f -> f.label().startsWith("안마의자")).findFirst().orElseThrow();
		assertEquals("안마의자 분리", massage.label());
		assertEquals(50_000, massage.min());
		Fee piano = e.fees().stream().filter(f -> f.label().startsWith("피아노")).findFirst().orElseThrow();
		assertEquals(300_000, piano.min());
		assertFalse(labels.stream().anyMatch(l -> l.startsWith("수작업")));
		assertTrue(e.notes().contains("에어컨은 다른 업체에 맡기기로 하셔서 견적에서 뺐어요."), e.notes().toString());
	}

	@Test
	void countMultipliesAndWallTvUsesTheInchTable() {
		var tv = List.of(new Line("TV", "02", 1, 65, true));
		var e = EstimateCalculator.estimate(tv, with(List.of(new OptionChoice("04", true, true, 2, false))));
		Fee f = e.fees().stream().filter(x -> x.label().startsWith("벽걸이 TV")).findFirst().orElseThrow();
		assertEquals("벽걸이 TV 탈착·설치 × 2", f.label());
		// 탈착 3만 + 65인치 설치 7~10만 (fee-tv.csv), 2대
		assertEquals((30_000 + 70_000) * 2, f.min());
		assertEquals((30_000 + 100_000) * 2, f.max());
	}

	@Test
	void holidaysAreChargedLikeWeekends() {
		var t = EstimateCalculator.tables();
		LocalDate christmas = LocalDate.of(2026, 12, 25); // 금요일 · 공휴일
		assertEquals(10, EstimateCalculator.surchargePct(christmas, t));
		assertEquals(0, EstimateCalculator.surchargePct(LocalDate.of(2026, 12, 10), t), "평일");
	}

	@Test
	void josa() {
		assertTrue(EstimateCalculator.hasBatchim("안마의자 · 피아노 · 돌침대 · 조립장"));
		assertFalse(EstimateCalculator.hasBatchim("에어컨 · 홈씨어터"));
	}
}
