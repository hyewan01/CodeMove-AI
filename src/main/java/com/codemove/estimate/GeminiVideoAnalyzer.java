package com.codemove.estimate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.codemove.estimate.VideoAnalysis.Usage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gemini API 어댑터: 영상 업로드(Files API) → 처리 대기 → generateContent(JSON 스키마 강제) → 업로드 파일 삭제.
 * API 키는 환경변수 GEMINI_API_KEY(또는 application-local.yml codemove.ai.gemini.api-key)로만 받습니다.
 * 이 클래스는 AI 호출만 하고, 응답 해석·매핑·계산은 VideoAnalysisService 가 합니다.
 */
@Component
public class GeminiVideoAnalyzer {

	/** AI 호출 실패 (code = ANALYSIS_ERROR 공통코드) */
	public static class AnalyzerException extends RuntimeException {

		private final String code;

		public AnalyzerException(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	/** 호출 결과: 모델이 돌려준 JSON 글자 + 사용량 + 단계별 시간 */
	public record Response(String text, Usage usage, long uploadMs, long apiMs, String model) {
	}

	private static final String BASE = "https://generativelanguage.googleapis.com";

	private final String apiKey;
	private final String model;
	private final Duration timeout;
	private final JsonMapper json;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
			.followRedirects(HttpClient.Redirect.NORMAL).build();

	private final String thinkingLevel;
	/** 주 모델이 혼잡(5xx)하거나 한도(429)일 때 차례로 쓰는 모델 */
	private final List<String> fallbackModels;

	public GeminiVideoAnalyzer(String apiKey, String model, int timeoutSec, String thinkingLevel, JsonMapper json) {
		this(apiKey, model, timeoutSec, thinkingLevel, "", json);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public GeminiVideoAnalyzer(@Value("${codemove.ai.gemini.api-key:${GEMINI_API_KEY:}}") String apiKey,
			@Value("${codemove.ai.gemini.model:gemini-4-hydrogen}") String model,
			@Value("${codemove.ai.gemini.timeout-sec:180}") int timeoutSec,
			@Value("${codemove.ai.gemini.thinking-level:low}") String thinkingLevel,
			@Value("${codemove.ai.gemini.fallback-models:}") String fallbackModels, JsonMapper json) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.model = model;
		this.fallbackModels = java.util.Arrays.stream((fallbackModels == null ? "" : fallbackModels).split(","))
				.map(s -> s.strip()).filter(s -> !s.isEmpty() && !s.equals(model)).toList();
		this.thinkingLevel = thinkingLevel == null ? "" : thinkingLevel.trim();
		this.timeout = Duration.ofSeconds(timeoutSec);
		this.json = json;
	}

	public boolean configured() {
		return !apiKey.isEmpty();
	}

	public String model() {
		return model;
	}

	public String thinkingLevel() {
		return thinkingLevel;
	}

	public Response analyze(Path video, String mimeType, String prompt, Map<String, Object> schema) {
		return analyze(video, mimeType, prompt, schema, true);
	}

	/**
	 * @param fallbackOnBusy 주 모델이 혼잡(5xx)할 때도 예비 모델로 넘어갈지. 예비 모델은 짐을 많이 빠뜨려서
	 *                       (같은 영상 F1 42~64% vs 주 모델 90%) 재시도는 주 모델로 먼저 합니다.
	 *                       한도 초과(429)는 기다려도 풀리지 않으므로 항상 예비 모델로 넘어갑니다.
	 */
	public Response analyze(Path video, String mimeType, String prompt, Map<String, Object> schema,
			boolean fallbackOnBusy) {
		return analyzeVotes(video, mimeType, prompt, schema, fallbackOnBusy, 1).get(0);
	}

	/**
	 * 영상을 한 번 올리고 같은 질문을 times 번 동시에 보냅니다(다수결용). 걸리는 시간은 1번과 거의 같고 비용은 times 배.
	 * 일부만 실패하면 성공한 응답만 돌려주고, 모두 실패하면 첫 오류를 던집니다.
	 */
	public List<Response> analyzeVotes(Path video, String mimeType, String prompt, Map<String, Object> schema,
			boolean fallbackOnBusy, int times) {
		if (!configured()) {
			throw new AnalyzerException(VideoAnalysis.ERR_NO_KEY, "GEMINI_API_KEY 가 설정되지 않았습니다");
		}
		long t0 = System.currentTimeMillis();
		JsonNode file = upload(video, mimeType);
		String name = file.path("name").asString();
		try {
			file = waitActive(file);
			long uploadMs = System.currentTimeMillis() - t0;
			String uri = file.path("uri").asString();
			int n = Math.max(1, times);
			if (n == 1) {
				return List.of(generateOnce(fileParts(uri, mimeType), prompt, schema, fallbackOnBusy, uploadMs));
			}
			List<Response> ok = new java.util.ArrayList<>();
			AnalyzerException first = null;
			try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
				List<java.util.concurrent.Future<Response>> futures = new java.util.ArrayList<>();
				for (int i = 0; i < n; i++) {
					futures.add(pool.submit(() -> generateOnce(fileParts(uri, mimeType), prompt, schema, fallbackOnBusy, uploadMs)));
				}
				for (var f : futures) {
					try {
						ok.add(f.get());
					} catch (java.util.concurrent.ExecutionException e) {
						AnalyzerException a = e.getCause() instanceof AnalyzerException ae ? ae
								: new AnalyzerException(VideoAnalysis.ERR_API, String.valueOf(e.getCause()));
						first = first == null ? a : first;
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new AnalyzerException(VideoAnalysis.ERR_TIMEOUT, "중단됨");
					}
				}
			}
			if (ok.isEmpty()) {
				throw first != null ? first : new AnalyzerException(VideoAnalysis.ERR_API, "분석 응답이 없습니다");
			}
			return ok;
		} finally {
			delete(name);
		}
	}

	/**
	 * 사진 여러 장(JPEG)을 올리지 않고 요청에 바로 담아 한 번 질문합니다(손글씨 견적서용).
	 * 이 모델은 사진 한 장을 크기와 상관없이 약 1,100 토큰으로 줄여 보므로(해상도 설정도 무시, 2026-11-27 확인),
	 * 글씨를 크게 보이려면 사진을 조각내 여러 장으로 보냅니다.
	 */
	public Response analyzeImages(List<byte[]> jpegs, String prompt, Map<String, Object> schema) {
		if (!configured()) {
			throw new AnalyzerException(VideoAnalysis.ERR_NO_KEY, "GEMINI_API_KEY 가 설정되지 않았습니다");
		}
		List<Map<String, Object>> parts = new java.util.ArrayList<>();
		for (byte[] j : jpegs) {
			parts.add(Map.of("inline_data",
					Map.of("mime_type", "image/jpeg", "data", java.util.Base64.getEncoder().encodeToString(j))));
		}
		return generateOnce(parts, prompt, schema, true, 0);
	}

	private static List<Map<String, Object>> fileParts(String uri, String mimeType) {
		return List.of(Map.of("file_data", Map.of("mime_type", mimeType, "file_uri", uri)));
	}

	/** 올려 둔 영상(또는 사진 묶음)으로 한 번 질문 (주 모델 → 혼잡·한도면 예비 모델 차례로) */
	private Response generateOnce(List<Map<String, Object>> media, String prompt, Map<String, Object> schema,
			boolean fallbackOnBusy, long uploadMs) {
		long t1 = System.currentTimeMillis();
		JsonNode res = null;
		String used = null;
		AnalyzerException last = null;
		List<String> models = new java.util.ArrayList<>();
		models.add(model);
		models.addAll(fallbackModels);
		for (String m : models) {
			try {
				res = generate(m, media, prompt, schema);
				used = m;
				break;
			} catch (AnalyzerException e) {
				boolean quota = VideoAnalysis.ERR_QUOTA.equals(e.code());
				boolean busy = VideoAnalysis.ERR_BUSY.equals(e.code()) && (fallbackOnBusy || !m.equals(model));
				if (!busy && !quota) {
					throw e;
				}
				last = e;
			}
		}
		if (res == null) {
			throw last != null ? last : new AnalyzerException(VideoAnalysis.ERR_API, "분석할 모델이 없습니다");
		}
		long apiMs = System.currentTimeMillis() - t1;
		StringBuilder text = new StringBuilder();
		for (JsonNode part : res.path("candidates").path(0).path("content").path("parts")) {
			if (!part.path("thought").asBoolean(false)) {
				text.append(part.path("text").asString(""));
			}
		}
		if (text.isEmpty()) {
			String reason = res.path("candidates").path(0).path("finishReason").asString("");
			throw new AnalyzerException(VideoAnalysis.ERR_JSON, "빈 응답 (finishReason=" + reason + ")");
		}
		JsonNode u = res.path("usageMetadata");
		Usage usage = new Usage(u.path("promptTokenCount").asLong(0), u.path("candidatesTokenCount").asLong(0),
				u.path("thoughtsTokenCount").asLong(0), u.path("totalTokenCount").asLong(0));
		return new Response(text.toString(), usage, uploadMs, apiMs, used);
	}

	/* ---------- Files API ---------- */

	private JsonNode upload(Path video, String mimeType) {
		try {
			long size = Files.size(video);
			HttpRequest start = HttpRequest.newBuilder(URI.create(BASE + "/upload/v1beta/files"))
					.timeout(Duration.ofSeconds(30)).header("x-goog-api-key", apiKey)
					.header("X-Goog-Upload-Protocol", "resumable").header("X-Goog-Upload-Command", "start")
					.header("X-Goog-Upload-Header-Content-Length", String.valueOf(size))
					.header("X-Goog-Upload-Header-Content-Type", mimeType).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"file\":{\"display_name\":\"codemove-video\"}}"))
					.build();
			HttpResponse<String> s = send(start);
			String url = s.headers().firstValue("x-goog-upload-url")
					.orElseThrow(() -> new AnalyzerException(VideoAnalysis.ERR_API, "업로드 주소를 받지 못했습니다"));
			HttpRequest put = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
					.header("X-Goog-Upload-Offset", "0").header("X-Goog-Upload-Command", "upload, finalize")
					.POST(HttpRequest.BodyPublishers.ofFile(video)).build();
			return json.readTree(send(put).body()).path("file");
		} catch (IOException e) {
			throw new AnalyzerException(VideoAnalysis.ERR_BUSY, "영상 업로드 연결 불안정: " + e.getMessage());
		}
	}

	/** 영상은 업로드 직후 PROCESSING 상태라 ACTIVE 가 될 때까지 기다림 */
	private JsonNode waitActive(JsonNode file) {
		String name = file.path("name").asString();
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (!"ACTIVE".equals(file.path("state").asString())) {
			if ("FAILED".equals(file.path("state").asString())) {
				throw new AnalyzerException(VideoAnalysis.ERR_FORMAT, "영상을 처리하지 못했습니다 (형식 확인)");
			}
			if (System.currentTimeMillis() > deadline) {
				throw new AnalyzerException(VideoAnalysis.ERR_TIMEOUT, "영상 처리 대기 시간 초과");
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AnalyzerException(VideoAnalysis.ERR_TIMEOUT, "중단됨");
			}
			HttpRequest get = HttpRequest.newBuilder(URI.create(BASE + "/v1beta/" + name))
					.timeout(Duration.ofSeconds(30)).header("x-goog-api-key", apiKey).GET().build();
			file = json.readTree(send(get).body());
		}
		return file;
	}

	private void delete(String name) {
		if (name == null || name.isBlank()) {
			return;
		}
		try {
			http.send(HttpRequest.newBuilder(URI.create(BASE + "/v1beta/" + name)).timeout(Duration.ofSeconds(20))
					.header("x-goog-api-key", apiKey).DELETE().build(), HttpResponse.BodyHandlers.discarding());
		} catch (IOException | InterruptedException e) {
			// Gemini 업로드 파일은 48시간 뒤 자동 삭제되므로 실패해도 진행
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/* ---------- generateContent ---------- */

	private JsonNode generate(String model, List<Map<String, Object>> media, String prompt, Map<String, Object> schema) {
		List<Map<String, Object>> parts = new java.util.ArrayList<>(media);
		parts.add(Map.of("text", prompt));
		Map<String, Object> body = Map.of(
				"contents", List.of(Map.of("role", "user", "parts", parts)),
				"generationConfig", generationConfig(schema));
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/v1beta/models/" + model + ":generateContent"))
				.timeout(timeout).header("x-goog-api-key", apiKey).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
		return json.readTree(send(req).body());
	}

	/** 온도 0 · JSON 스키마 강제 · 생각 수준(Gemini 3 이후 모델: minimal/low/medium/high, 비우면 모델 기본값) */
	private Map<String, Object> generationConfig(Map<String, Object> schema) {
		Map<String, Object> c = new java.util.LinkedHashMap<>();
		c.put("temperature", 0);
		c.put("responseMimeType", "application/json");
		c.put("responseSchema", schema);
		if (!thinkingLevel.isBlank()) {
			c.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
		}
		return c;
	}

	private HttpResponse<String> send(HttpRequest req) {
		try {
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			int code = res.statusCode();
			if (code == 429) {
				throw new AnalyzerException(VideoAnalysis.ERR_QUOTA, "AI 사용량 한도 초과 (429)");
			}
			if (code == 500 || code == 502 || code == 503 || code == 504) {
				throw new AnalyzerException(VideoAnalysis.ERR_BUSY, "AI 서버 혼잡 (" + code + ")");
			}
			if (code >= 400) {
				String msg = res.body() == null ? "" : res.body();
				throw new AnalyzerException(VideoAnalysis.ERR_API,
						"Gemini 오류 " + code + ": " + (msg.length() > 300 ? msg.substring(0, 300) : msg));
			}
			return res;
		} catch (HttpTimeoutException e) {
			throw new AnalyzerException(VideoAnalysis.ERR_TIMEOUT, "AI 응답 시간 초과");
		} catch (IOException e) {
			// 연결 끊김(Connection reset 등)은 일시적이라 혼잡과 같이 재시도 · 예비 모델 대상
			throw new AnalyzerException(VideoAnalysis.ERR_BUSY, "AI 서버 연결 불안정: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AnalyzerException(VideoAnalysis.ERR_TIMEOUT, "중단됨");
		}
	}
}
