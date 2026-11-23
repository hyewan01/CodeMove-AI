package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.codemove.estimate.EstimateCalculator.Conditions;
import com.codemove.estimate.EstimateCalculator.Estimate;
import com.codemove.estimate.EstimateCalculator.Fee;
import com.codemove.estimate.EstimateCalculator.Line;

class EstimateCalculatorTest {

	private static List<Double> trucks(double load) {
		return EstimateCalculator.pickTrucks(load, EstimateCalculator.tables().trucks());
	}

	@Test
	void dadsHouseExample55CbmIsFiveTonPlusTwoOneTon() {
		// 대표님: 우리 집 = 5톤 1대 + 1톤 2대 = 7톤 = 55 CBM
		assertEquals(List.of(5.0, 1.0, 1.0), trucks(55));
	}

	@Test
	void smallLoadsUseOneTruck() {
		assertEquals(List.of(1.0), trucks(0.5));
		assertEquals(List.of(1.0), trucks(10));
		assertEquals(List.of(1.0, 1.0), trucks(18), "1톤 2대(2톤 분량)가 2.5톤보다 가벼움");
		assertEquals(List.of(3.5), trucks(24));
		assertEquals(List.of(5.0), trucks(35));
		assertEquals(List.of(5.0, 5.0), trucks(70));
		assertEquals(List.of(5.0, 5.0, 1.0), trucks(72));
	}

	@Test
	void loadFactorDoublesItemVolume() {
		// 바구니(이사박스 중) 65개 = 물품 0.077 × 65 = 5.0 CBM → 적재 10 CBM → 1톤 (대표님: 1톤에 바구니 50~70개)
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("이사박스", "02", 65)), Conditions.basic("04"));
		assertEquals(10.0, e.loadCbm(), 0.11);
		assertEquals(1.0, e.ton());
	}

	@Test
	void fullPackingOneTonIsFiftyToSixtyManWon() {
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("이사박스", "02", 30)), Conditions.basic("04"));
		assertEquals(500_000, e.totalMin());
		assertEquals(600_000, e.totalMax());
		assertEquals(50_000 + 60_000, e.deposit() * 2, 10_000, "계약금은 가운데 금액의 10%");
	}

	@Test
	void sevenTonExtrapolatesFromTheTable() {
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("이사박스", "02", 357)), Conditions.basic("04"));
		assertEquals(7.0, e.ton());
		assertEquals("5톤 1대 + 1톤 2대", e.truckLabel());
		// 포장: 2.5톤 100~110만, 5톤 140~160만 → 7톤은 같은 기울기로 늘림
		assertEquals(1_720_000, e.totalMin());
		assertEquals(2_000_000, e.totalMax());
	}

	@Test
	void distanceAddsPerFiftyKm() {
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("이사박스", "02", 30)),
				new Conditions("04", 120.0, null, null, null));
		Fee dist = e.fees().stream().filter(f -> f.label().startsWith("거리")).findFirst().orElseThrow();
		assertEquals(300_000, dist.min(), "1톤 100~150km = 50km마다 15만 원 × 2");
	}

	@Test
	void airconAndMassageChairAndWallTvOptions() {
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("에어컨", "02", 1), Line.of("에어컨", "03", 1),
				Line.of("안마의자", "02", 1), new Line("TV", "03", 1, 85, true), new Line("TV", "02", 1, 55, false)),
				Conditions.basic("04"));
		Fee aircon = e.fees().stream().filter(f -> f.label().startsWith("에어컨")).findFirst().orElseThrow();
		assertEquals(250_000 * 3, aircon.min(), "스탠드 1 + 2in1(2대) = 3대 × 25만");
		Fee massage = e.fees().stream().filter(f -> f.label().startsWith("안마의자")).findFirst().orElseThrow();
		assertEquals(100_000, massage.min());
		Fee tv = e.fees().stream().filter(f -> f.label().startsWith("벽걸이")).findFirst().orElseThrow();
		assertEquals(230_000, tv.min());
		assertEquals(250_000, tv.max());
		assertEquals(1, e.fees().stream().filter(f -> f.label().startsWith("벽걸이")).count(), "스탠드 TV 는 옵션 아님");
	}

	@Test
	void storageUsesFullPackingFeeAndNotes() {
		Estimate e = EstimateCalculator.estimate(List.of(Line.of("이사박스", "02", 30)), Conditions.basic("03"));
		assertEquals(500_000, e.totalMin());
		assertTrue(e.notes().stream().anyMatch(n -> n.contains("보관료")));
	}

	@Test
	void weekendSurchargeOnBaseFee() {
		LocalDate saturday = LocalDate.of(2026, 12, 5);
		int pct = EstimateCalculator.surchargePct(saturday, EstimateCalculator.tables());
		assertTrue(pct >= 10);
	}
}
