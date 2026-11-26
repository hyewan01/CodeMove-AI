package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.codemove.move.EstimateDoc;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Gemini 로 수기 견적서 읽기 (키와 사진이 있을 때만). 출력 · 기록 모두 이름 · 번호를 가립니다.
 * <pre>
 * $env:GEMINI_API_KEY="..."; $env:CODEMOVE_TEST_SHEET="C:\...\0626.jpg"
 * .\gradlew.bat test --tests "*SheetReaderLiveTest" -i
 * </pre>
 * 기록은 build/ai-live/ai-logs/sheets/날짜/ID.json 에 남습니다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CODEMOVE_TEST_SHEET", matches = ".+")
class SheetReaderLiveTest {

	@Test
	void readRealSheet() {
		JsonMapper json = JsonMapper.builder().build();
		GeminiVideoAnalyzer ai = new GeminiVideoAnalyzer(System.getenv("GEMINI_API_KEY"),
				System.getenv().getOrDefault("GEMINI_MODEL", "gemini-4-hydrogen"), 180,
				System.getenv().getOrDefault("GEMINI_THINKING", "low"), "", json);
		SheetReader reader = new SheetReader(ai, json, Path.of("build", "ai-live", "ai-logs").toString(), 1.50, 7.50);
		Path sheet = Path.of(System.getenv("CODEMOVE_TEST_SHEET"));
		SheetReader.Result r = reader.read(sheet, sheet.getFileName().toString());
		EstimateDoc d = r.doc();
		System.out.printf("성공=%s 기록=%s%n안내: %s%n", r.success(), r.logId(), r.notice());
		System.out.printf("고객 %s · TEL %s · 견적일 %s · 포장일 %s · 운송일 %s%n", SheetReader.maskName(d.getCustomer()),
				SheetReader.maskText(d.getPhone(), null), d.getEstimateDate(), d.getMoveDate(), d.getTransportDate());
		System.out.printf("출발 %s → 도착 %s · %s · 작업 %s · 사다리 %s 승강기 %s 계단 %s%n", d.getOrigin(), d.getDestination(),
				d.getVolume(), d.getWorkMethod(), d.getLadder(), d.getElevator(), d.getStairs());
		System.out.printf("이사비용 %s · 별도 %s · 총 %s · 계약금 %s · 잔금 %s · 사다리 %s층 %s / %s층 %s%n", d.getMovingCost(),
				d.getExtraCost(), d.getTotal(), d.getDeposit(), d.getBalance(), d.getDepartureFloor(),
				d.getDepartureLadder(), d.getDestinationFloor(), d.getDestinationLadder());
		for (int i = 0; i < EstimateDoc.OPTION_ROWS.length; i++) {
			if (!d.getOptionSelections().get(i).isEmpty() || !d.getOptionCosts().get(i).isEmpty()) {
				System.out.printf("옵션 %s %s %s%n", EstimateDoc.OPTION_ROWS[i][0], d.getOptionSelections().get(i),
						d.getOptionCosts().get(i));
			}
		}
		for (int c = 0; c < 4; c++) {
			StringBuilder b = new StringBuilder("[" + d.getItemGroups().get(c) + "] ");
			for (int row = 0; row < d.getItemRowCount(); row++) {
				int idx = row * 4 + c;
				if (!d.itemName(idx).isEmpty()) {
					b.append(d.itemName(idx)).append('=').append(d.itemQuantity(idx))
							.append(d.needsReview("itemQuantities[" + idx + "]") ? "(?)" : "").append(" · ");
				}
			}
			System.out.println(b);
		}
		System.out.printf("비고: %s%n확인 필요: %s%n", SheetReader.maskText(d.getNote(), d.getCustomer()), d.getReviewFields());
		assertTrue(r.success(), r.notice());
	}
}
