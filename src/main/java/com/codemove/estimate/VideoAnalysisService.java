package com.codemove.estimate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codemove.common.FileStore;
import com.codemove.estimate.GeminiVideoAnalyzer.AnalyzerException;
import com.codemove.estimate.VideoAnalysis.RawItem;
import com.codemove.estimate.VideoAnalysis.RawResult;
import com.codemove.estimate.VideoAnalysis.Result;
import com.codemove.estimate.VideoAnalysis.Usage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영상 분석 순서 (가이드 3장): 형식 검사 → AI 호출(실패 시 1회 재시도) → JSON 검사 → 표준 품목 매핑 → 기록.
 * 기록은 V4(TBL_AI_ANALYSIS) 적용 전까지 ai-logs/날짜/분석ID.json 파일로 남깁니다
 * (codemove.ai.log-dir, Git 제외. 공개 주소인 /uploads 밖에 둠).
 * 실패한 요청도 비용 계산에 들어가므로 모두 기록합니다.
 */
@Service
public class VideoAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(VideoAnalysisService.class);

	/** 허용 형식 → MIME */
	static final Map<String, String> MIME = Map.of(".mp4", "video/mp4", ".m4v", "video/mp4", ".mov",
			"video/quicktime", ".webm", "video/webm", ".3gp", "video/3gpp");

	private final GeminiVideoAnalyzer ai;
	private final FileStore files;
	private final JsonMapper json;
	private final double priceIn, priceOut;
	private final String priceDate;
	private final Path logRoot;

	public VideoAnalysisService(GeminiVideoAnalyzer ai, FileStore files, JsonMapper json,
			@Value("${codemove.ai.gemini.price-in-per-1m:1.50}") double priceIn,
			@Value("${codemove.ai.gemini.price-out-per-1m:7.50}") double priceOut,
			@Value("${codemove.ai.gemini.price-date:2026-11-23}") String priceDate,
			@Value("${codemove.ai.log-dir:ai-logs}") String logDir) {
		this.ai = ai;
		this.files = files;
		this.logRoot = Path.of(logDir).toAbsolutePath().normalize();
		this.json = json;
		this.priceIn = priceIn;
		this.priceOut = priceOut;
		this.priceDate = priceDate;
	}

	public boolean configured() {
		return ai.configured();
	}

	/** /uploads/... 주소 → 디스크 경로 */
	public Path pathOf(String uploadUrl) {
		return files.root().resolve(uploadUrl.substring(FileStore.URL_PREFIX.length())).normalize();
	}

	public Result analyze(Path video) {
		String id = UUID.randomUUID().toString().substring(0, 8);
		long t0 = System.currentTimeMillis();
		String name = video.getFileName().toString().toLowerCase(Locale.ROOT);
		String mime = MIME.get(name.contains(".") ? name.substring(name.lastIndexOf('.')) : "");
		if (mime == null) {
			return save(fail(id, VideoAnalysis.ERR_FORMAT, "동영상 파일(mp4·mov·webm)만 올릴 수 있어요", t0, 0), video);
		}
		String prompt = AnalysisPrompt.text();
		int retries = 0;
		while (true) {
			try {
				GeminiVideoAnalyzer.Response res = ai.analyze(video, mime, prompt, AnalysisPrompt.schema());
				return save(interpret(id, res, t0, retries), video);
			} catch (AnalyzerException e) {
				boolean busy = VideoAnalysis.ERR_BUSY.equals(e.code());
				boolean retryable = busy || VideoAnalysis.ERR_TIMEOUT.equals(e.code())
						|| VideoAnalysis.ERR_JSON.equals(e.code());
				// 시간 초과·JSON 오류는 1번, 서버 혼잡(5xx)은 잠깐 쉬었다가 2번까지
				if (retryable && retries < (busy ? MAX_BUSY_RETRIES : 1)) {
					retries++;
					log.warn("영상 분석 재시도 {} ({}회): {}", id, retries, e.getMessage());
					if (busy) {
						sleep(BUSY_WAIT_MS * retries);
					}
					continue;
				}
				return save(fail(id, e.code(), e.getMessage(), t0, retries), video);
			}
		}
	}

	static final int MAX_BUSY_RETRIES = 2;
	static final long BUSY_WAIT_MS = 5_000;

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** 모델 응답 글자 → 검사 · 매핑 */
	Result interpret(String id, GeminiVideoAnalyzer.Response res, long t0, int retries) {
		RawResult raw;
		try {
			raw = parse(res.text());
		} catch (RuntimeException e) {
			throw new AnalyzerException(VideoAnalysis.ERR_JSON, "AI 응답 JSON 을 읽지 못했습니다: " + e.getMessage());
		}
		double cost = cost(res.usage());
		long total = System.currentTimeMillis() - t0;
		if (!raw.videoOk() || raw.items().isEmpty()) {
			String reason = raw.rejectReason() == null ? "" : raw.rejectReason();
			String code = reason.matches(".*(어두|흔들|흐릿|blur|dark).*") ? VideoAnalysis.ERR_DARK
					: VideoAnalysis.ERR_NOT_HOME;
			String msg = VideoAnalysis.ERR_DARK.equals(code) ? "영상이 어둡거나 흔들려서 짐을 알아보기 어려워요"
					: "집 안 가구·가전이 보이게 다시 찍어 주세요";
			return new Result(id, false, code, msg + (reason.isBlank() ? "" : " (" + reason + ")"), ai.model(),
					AnalysisPrompt.VERSION, List.of(), raw.rooms(), raw.memo(), res.usage(), cost, res.uploadMs(),
					res.apiMs(), total, retries, res.text());
		}
		return new Result(id, true, null, null, ai.model(), AnalysisPrompt.VERSION, ItemMatcher.map(raw.items()),
				raw.rooms(), raw.memo(), res.usage(), cost, res.uploadMs(), res.apiMs(), total, retries, res.text());
	}

	/** 응답 JSON → RawResult. 품목 한 줄이 이상하면 그 줄만 확인 필요로 */
	RawResult parse(String text) {
		String t = text.strip();
		if (t.startsWith("```")) {
			t = t.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "");
		}
		JsonNode n = json.readTree(t);
		List<String> rooms = new ArrayList<>();
		n.path("rooms").forEach(r -> rooms.add(r.asString("")));
		List<RawItem> items = new ArrayList<>();
		for (JsonNode i : n.path("items")) {
			JsonNode q = i.path("quantity");
			boolean badQty = !q.isNumber();
			JsonNode inch = i.path("inch");
			items.add(new RawItem(i.path("item_name").asString(""), i.path("size_code").asString(""),
					badQty ? 1 : q.asInt(1), i.path("detected_name").asString(""), i.path("room").asString(""),
					i.path("evidence").asString(""), i.path("needs_review").asBoolean(false) || badQty,
					inch.isNumber() && inch.asInt() >= 20 && inch.asInt() <= 120 ? inch.asInt() : null,
					i.path("wall_mounted").asBoolean(false)));
		}
		JsonNode reason = n.path("reject_reason");
		return new RawResult(n.path("video_ok").asBoolean(!items.isEmpty()),
				reason.isNull() || reason.isMissingNode() ? null : reason.asString(""), rooms, items,
				n.path("memo").asString(""));
	}

	/* ---------- 분석 기록 조회 (관리자 AI 테스트 화면) ---------- */

	/** 기록 파일 한 개 */
	public record Logged(String analysisId, String at, String video, Double videoMb, String model,
			String promptVersion, boolean success, String errorCode, String message, Usage usage, double costUsd,
			long uploadMs, long apiMs, long totalMs, int retries, List<VideoAnalysis.MappedItem> items,
			double totalCbm, long reviewCount, long unmappedCount, String rawJson) {
	}

	/** 최근 기록부터 */
	public List<Logged> history(int limit) {
		Path dir = logRoot;
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (var s = Files.walk(dir, 2)) {
			return s.filter(p -> p.toString().endsWith(".json"))
					.sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified())).limit(limit)
					.map(p -> read(p)).filter(l -> l != null).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public Logged find(String analysisId) {
		if (analysisId == null || !analysisId.matches("[0-9a-f]{8}")) {
			return null;
		}
		Path dir = logRoot;
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (var s = Files.walk(dir, 2)) {
			return s.filter(p -> p.getFileName().toString().equals(analysisId + ".json")).findFirst().map(p -> read(p))
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private Logged read(Path p) {
		try {
			JsonNode n = json.readTree(Files.readAllBytes(p)); // 바이트로 읽어야 BOM 있는 파일도 읽힘
			List<VideoAnalysis.MappedItem> items = new ArrayList<>();
			for (JsonNode i : n.path("items")) {
				items.add(new VideoAnalysis.MappedItem(i.path("name").asString(""), i.path("sizeCode").asString("02"),
						i.path("count").asInt(1), i.path("unitCbm").asDouble(0), i.path("detectedName").asString(""),
						i.path("room").asString(""), i.path("evidence").asString(""),
						i.path("mappingType").asString(""), i.path("needsReview").asBoolean(false),
						i.path("inch").isNumber() ? i.path("inch").asInt() : null,
						i.path("wallMounted").asBoolean(false)));
			}
			JsonNode u = n.path("usage"), ms = n.path("ms");
			return new Logged(n.path("analysisId").asString(""), n.path("at").asString(""),
					n.path("video").asString(""), n.path("videoMb").isNumber() ? n.path("videoMb").asDouble() : null,
					n.path("model").asString(""), n.path("promptVersion").asString(""),
					n.path("success").asBoolean(false), n.path("errorCode").asString(null),
					n.path("message").asString(null),
					new Usage(u.path("inputTokens").asLong(0), u.path("outputTokens").asLong(0),
							u.path("thoughtTokens").asLong(0), u.path("totalTokens").asLong(0)),
					n.path("costUsd").asDouble(0), ms.path("upload").asLong(0), ms.path("api").asLong(0),
					ms.path("total").asLong(0), n.path("retries").asInt(0), items, n.path("totalCbm").asDouble(0),
					n.path("reviewCount").asLong(0), n.path("unmappedCount").asLong(0),
					n.path("rawJson").asString(null));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	double cost(Usage u) {
		// 생각(thinking) 토큰은 출력 단가로 과금
		return (u.inputTokens() * priceIn + (u.outputTokens() + u.thoughtTokens()) * priceOut) / 1_000_000.0;
	}

	private Result fail(String id, String code, String message, long t0, int retries) {
		return new Result(id, false, code, message, ai.model(), AnalysisPrompt.VERSION, List.of(), List.of(), null,
				Usage.NONE, 0, 0, 0, System.currentTimeMillis() - t0, retries, null);
	}

	/** 분석 기록 (가이드 10장 항목) */
	private Result save(Result r, Path video) {
		try {
			Map<String, Object> rec = new LinkedHashMap<>();
			rec.put("analysisId", r.analysisId());
			rec.put("at", LocalDateTime.now().toString());
			rec.put("video", video.getFileName().toString());
			rec.put("videoMb", Files.exists(video) ? Math.round(Files.size(video) / 104857.6) / 10.0 : null);
			rec.put("model", r.model());
			rec.put("promptVersion", r.promptVersion());
			rec.put("success", r.success());
			rec.put("errorCode", r.errorCode());
			rec.put("message", r.message());
			rec.put("usage", r.usage());
			rec.put("price", Map.of("inPer1m", priceIn, "outPer1m", priceOut, "currency", "USD", "date", priceDate));
			rec.put("costUsd", r.costUsd());
			rec.put("ms", Map.of("upload", r.uploadMs(), "api", r.apiMs(), "total", r.totalMs()));
			rec.put("retries", r.retries());
			rec.put("itemCount", r.items().size());
			rec.put("reviewCount", r.reviewCount());
			rec.put("unmappedCount",
					r.items().stream().filter(i -> VideoAnalysis.UNMAPPED.equals(i.mappingType())).count());
			rec.put("totalCbm", Math.round(r.totalCbm() * 1000) / 1000.0);
			rec.put("items", r.items());
			rec.put("rawJson", r.rawJson());
			Path dir = logRoot.resolve(LocalDate.now().toString().replace("-", ""));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(r.analysisId() + ".json"),
					json.writerWithDefaultPrettyPrinter().writeValueAsString(rec));
		} catch (IOException | RuntimeException e) {
			log.warn("분석 기록 저장 실패 {}: {}", r.analysisId(), e.getMessage());
		}
		log.info("영상 분석 {} success={} code={} items={} cbm={} tokens={} cost=${} {}ms", r.analysisId(),
				r.success(), r.errorCode(), r.items().size(), String.format("%.2f", r.totalCbm()),
				r.usage().totalTokens(), String.format("%.4f", r.costUsd()), r.totalMs());
		return r;
	}
}
