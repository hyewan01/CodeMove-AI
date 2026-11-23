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
 * 결과 기록은 build/ai-live/ai-analysis/날짜/분석ID.json 에 남습니다.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CODEMOVE_TEST_VIDEO", matches = ".+")
class VideoAnalysisLiveTest {

	@Test
	void analyzeRealVideo() throws Exception {
		JsonMapper json = JsonMapper.builder().build();
		String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-2.5-flash");
		GeminiVideoAnalyzer ai = new GeminiVideoAnalyzer(System.getenv("GEMINI_API_KEY"), model, 300, json);
		Path out = Path.of("build", "ai-live");
		Files.createDirectories(out);
		VideoAnalysisService service = new VideoAnalysisService(ai, new FileStore(out.toString()), json, 0.30, 2.50,
				"2026-11-23");

		VideoAnalysis.Result r = service.analyze(Path.of(System.getenv("CODEMOVE_TEST_VIDEO")));

		System.out.printf("성공=%s 오류=%s %s%n", r.success(), r.errorCode(), r.message());
		System.out.printf("모델=%s 토큰 입력=%d 출력=%d 생각=%d 비용=$%.4f 시간=%dms%n", r.model(), r.usage().inputTokens(),
				r.usage().outputTokens(), r.usage().thoughtTokens(), r.costUsd(), r.totalMs());
		for (VideoAnalysis.MappedItem i : r.items()) {
			System.out.printf("  %-8s %s ×%d  %.3f CBM  [%s] %s%s%n", i.name(), i.sizeCode(), i.count(), i.cbm(),
					i.room(), i.detectedName(), i.needsReview() ? "  (확인 필요)" : "");
		}
		System.out.printf("총 %.2f CBM, 메모: %s%n", r.totalCbm(), r.memo());
		assertTrue(r.success(), r.message());
	}
}
