package com.codemove.estimate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.codemove.move.ItemCatalog;

/**
 * 영상 분석 프롬프트와 응답 JSON 스키마.
 * 지시문은 resources/estimate/prompt-{버전}.txt, 품목표는 ItemCatalog 에서 만들어 {{CATALOG}} 자리에 넣습니다.
 * 프롬프트를 바꾸면 VERSION 을 올려 분석 기록에서 버전별 결과를 비교할 수 있게 합니다.
 */
public final class AnalysisPrompt {

	/**
	 * v1: 품목표 + 세는 규칙. v2: 대표님 견적 방식(바구니 환산 · 서랍 속 짐 · 렌탈 · 고가품 · 벽걸이 TV 인치).
	 * v3: 놓친 짐 줄이기(선반 칸 세기 · 어두운 방 · 의자 하나씩 · 방마다 다시 훑기 · 비슷한 가전 구분).
	 * v4: 대표님 견적서 적는 방식(홈에 들어간 냉장고 · 거실장=TV장 · 화장대 · 작은 가구는 바구니).
	 * v5: 냉장 가전 하나면 냉장고 · 허리 높이 이상 화분만.
	 * v6: 아기 침대는 침대가 아니라 바구니 2개(대표님 견적서 두 곳 모두 적지 않음).
	 * v7: 11-26 대표님 통화(선반은 칸마다 바구니 1 · 이어 붙인 책장은 1개 · 앵글 선반은 바구니 · 자개장=옷장 · 1인 소파 각각 · 정수기는 메모 · 누운 영상).
	 */
	public static final String VERSION = "v7";

	/** 관리자 테스트 화면에서 고를 수 있는 버전 (최신이 먼저) */
	public static final List<String> VERSIONS = List.of("v7", "v6", "v5", "v4", "v3", "v2", "v1");

	private static final Map<String, String> TEMPLATES = new java.util.concurrent.ConcurrentHashMap<>();

	private AnalysisPrompt() {
	}

	public static String text() {
		return text(VERSION);
	}

	/** 없는 버전이면 IllegalArgumentException */
	public static String text(String version) {
		if (!VERSIONS.contains(version)) {
			throw new IllegalArgumentException("없는 프롬프트 버전: " + version);
		}
		return TEMPLATES.computeIfAbsent(version, v -> load(v)).replace("{{CATALOG}}", catalogTable());
	}

	/** 품목명 | 분류 | 01 | 02 | 03 (크기명: 판정기준) */
	static String catalogTable() {
		StringBuilder sb = new StringBuilder("품목명 | 분류 | 01 | 02 | 03\n");
		for (ItemCatalog.Item i : ItemCatalog.items()) {
			sb.append(i.name()).append(" | ").append(ItemCatalog.CATEGORIES.getOrDefault(i.category(), ""));
			for (ItemCatalog.Size z : i.sizes()) {
				sb.append(" | ").append(z.name()).append(": ").append(z.rule().replace("[업체 확인]", "").trim());
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/** Gemini responseSchema (OpenAPI 부분집합) */
	public static Map<String, Object> schema() {
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("item_name", str("품목표의 품목명 그대로, 없으면 기타"));
		props.put("size_code", Map.of("type", "STRING", "enum", List.of("01", "02", "03")));
		props.put("quantity", Map.of("type", "INTEGER"));
		props.put("detected_name", str("영상에서 본 물건 이름"));
		props.put("room", str("물건이 있는 공간"));
		props.put("evidence", str("판단 근거 한 줄"));
		props.put("needs_review", Map.of("type", "BOOLEAN"));
		props.put("inch", Map.of("type", "INTEGER", "nullable", true, "description", "TV 일 때 추정 인치"));
		props.put("wall_mounted", Map.of("type", "BOOLEAN", "description", "벽에 걸린 TV 면 true"));
		Map<String, Object> item = obj(props,
				List.of("item_name", "size_code", "quantity", "detected_name", "room", "needs_review"));
		return obj(Map.of("video_ok", Map.of("type", "BOOLEAN"),
				"reject_reason", Map.of("type", "STRING", "nullable", true),
				"rooms", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
				"items", Map.of("type", "ARRAY", "items", item),
				"memo", str("방문 견적자에게 남길 메모")),
				List.of("video_ok", "rooms", "items"));
	}

	private static Map<String, Object> obj(Map<String, Object> props, List<String> required) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "OBJECT");
		m.put("properties", props);
		m.put("required", required);
		return m;
	}

	private static Map<String, Object> str(String description) {
		return Map.of("type", "STRING", "description", description);
	}

	private static String load(String version) {
		try (InputStream in = AnalysisPrompt.class.getResourceAsStream("/estimate/prompt-" + version + ".txt")) {
			if (in == null) {
				throw new IllegalStateException("estimate/prompt-" + version + ".txt 가 없습니다");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
