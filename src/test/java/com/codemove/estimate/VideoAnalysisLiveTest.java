package com.codemove.estimate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.codemove.common.FileStore;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Gemini 호출 테스트 (가이드 7단계 반복 테스트용). 키와 영상이 있을 때만 실행됩니다.
 * <pre>
 * $env:GEMINI_API_KEY="..."; $env:CODEMOVE_TEST_VIDEO="C:\...\집 영상.mp4"
 * .\gradlew.bat test --tests "*VideoAnalysisLiveTest" -i
 * </pre>
 * 결과 기록은 build/ai-live/ai-logs/날짜/분석ID.json 에 남습니다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CODEMOVE_TEST_VIDEO", matches = ".+")
class VideoAnalysisLiveTest {

	@Test
	void analyzeRealVideo() throws Exception {
		JsonMapper json = JsonMapper.builder().build();
		String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-4-hydrogen");
		String thinking = System.getenv().getOrDefault("GEMINI_THINKING", "low");
		String fallback = System.getenv().getOrDefault("GEMINI_FALLBACK",
				"gemini-3.5-flash,gemini-flash-latest,gemini-3.8-flash");
		GeminiVideoAnalyzer ai = new GeminiVideoAnalyzer(System.getenv("GEMINI_API_KEY"), model, 300, thinking,
				fallback, json);
		Path out = Path.of("build", "ai-live");
		Files.createDirectories(out);
		VideoAnalysisService service = new VideoAnalysisService(ai, new FileStore(out.toString()), json, 1.50, 7.50,
				"2026-11-23", out.resolve("ai-logs").toString());

		String prompt = System.getenv().getOrDefault("GEMINI_PROMPT", AnalysisPrompt.VERSION);
		VideoAnalysis.Result r = service.analyze(Path.of(System.getenv("CODEMOVE_TEST_VIDEO")), prompt);

		System.out.printf("성공=%s 오류=%s %s%n", r.success(), r.errorCode(), r.message());
		System.out.printf("프롬프트=%s ", r.promptVersion());
		System.out.printf("모델=%s 토큰 입력=%d 출력=%d 생각=%d 비용=$%.4f 시간=%dms%n", r.model(), r.usage().inputTokens(),
				r.usage().outputTokens(), r.usage().thoughtTokens(), r.costUsd(), r.totalMs());
		for (VideoAnalysis.MappedItem i : r.items()) {
			System.out.printf("  %-8s %s ×%d  %.3f CBM  [%s] %s%s%n", i.name(), i.sizeCode(), i.count(), i.cbm(),
					i.room(), i.detectedName(), i.needsReview() ? "  (확인 필요)" : "");
		}
		System.out.printf("총 %.2f CBM, 메모: %s%n", r.totalCbm(), r.memo());
		assertTrue(r.success(), r.message());

		String truth = System.getenv("CODEMOVE_TEST_TRUTH");
		if (truth != null && GroundTruth.SETS.contains(truth)) {
			GroundTruth.Score s = GroundTruth.score(truth, r.items());
			System.out.printf("정답(%s) 정밀도 %.0f%% 재현율 %.0f%% F1 %.0f%% · CBM 정답 %.1f / AI %.1f (%+.0f%%)%n", truth,
					s.precision() * 100, s.recall() * 100, s.f1() * 100, s.expectedCbm(), s.detectedCbm(),
					s.cbmErrorPct());
			s.rows().forEach(row -> System.out.printf("    %-8s 정답 %2d  AI %2d%n", row.name(), row.expected(),
					row.detected()));
		}
		EstimateCalculator.Estimate e = EstimateCalculator.estimate(
				r.items().stream().map(i -> i.line()).toList(),
				EstimateCalculator.Conditions.basic(EstimateCalculator.FULL));
		System.out.printf("견적(포장) 물품 %.1f → 적재 %.1f CBM → %s (%s) → %,d ~ %,d원%n", e.itemCbm(), e.loadCbm(),
				e.tonText(), e.truckLabel(), e.totalMin(), e.totalMax());
		e.fees().forEach(f -> System.out.printf("    %s %,d ~ %,d%n", f.label(), f.min(), f.max()));
	}
}
