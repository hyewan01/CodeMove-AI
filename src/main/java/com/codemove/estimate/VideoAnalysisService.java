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
import org.springframework.beans.factory.annotation.Autowired;
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
	/** V4 적용 후 DB 기록 (없으면 파일) */
	private final AnalysisLogMapper logs;
	private final V4Schema schema;
	/** 같은 영상을 몇 번 분석해 다수결할지 (1 이면 한 번만) */
	private final int votes;

	/** 파일 기록만 쓰는 생성자 (실제 API 테스트용) */
	public VideoAnalysisService(GeminiVideoAnalyzer ai, FileStore files, JsonMapper json, double priceIn,
			double priceOut, String priceDate, String logDir) {
		this(ai, files, json, priceIn, priceOut, priceDate, logDir, 1);
	}

	/** 파일 기록만 쓰고 다수결 횟수를 정하는 생성자 (실제 API 테스트용) */
	public VideoAnalysisService(GeminiVideoAnalyzer ai, FileStore files, JsonMapper json, double priceIn,
			double priceOut, String priceDate, String logDir, int votes) {
		this(ai, files, json, priceIn, priceOut, priceDate, logDir, votes, null, null);
	}

	@Autowired
	public VideoAnalysisService(GeminiVideoAnalyzer ai, FileStore files, JsonMapper json,
			@Value("${codemove.ai.gemini.price-in-per-1m:1.50}") double priceIn,
			@Value("${codemove.ai.gemini.price-out-per-1m:7.50}") double priceOut,
			@Value("${codemove.ai.gemini.price-date:2026-11-23}") String priceDate,
			@Value("${codemove.ai.log-dir:ai-logs}") String logDir,
			@Value("${codemove.ai.gemini.votes:3}") int votes, AnalysisLogMapper logs, V4Schema schema) {
		this.votes = Math.max(1, votes);
		this.logs = logs;
		this.schema = schema;
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
		return analyze(video, AnalysisPrompt.VERSION);
	}

	/** @param promptVersion AnalysisPrompt.VERSIONS 중 하나 (관리자 테스트에서 버전끼리 비교) */
	public Result analyze(Path video, String promptVersion) {
		String version = AnalysisPrompt.VERSIONS.contains(promptVersion) ? promptVersion : AnalysisPrompt.VERSION;
		String id = UUID.randomUUID().toString().substring(0, 8);
		long t0 = System.currentTimeMillis();
		String name = video.getFileName().toString().toLowerCase(Locale.ROOT);
		String mime = MIME.get(name.contains(".") ? name.substring(name.lastIndexOf('.')) : "");
		if (mime == null) {
			return save(fail(id, VideoAnalysis.ERR_FORMAT, "동영상 파일(mp4·mov·webm)만 올릴 수 있어요", t0, 0, version), video);
		}
		String prompt = AnalysisPrompt.text(version);
		int retries = 0;
		while (true) {
			try {
				// 혼잡하면 주 모델로 다시 해 보고, 마지막 시도에서만 예비 모델 허용
				List<GeminiVideoAnalyzer.Response> res = ai.analyzeVotes(video, mime, prompt, AnalysisPrompt.schema(),
						retries >= MAX_BUSY_RETRIES, votes);
				return save(interpret(id, res, t0, retries, version), video);
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
				return save(fail(id, e.code(), e.getMessage(), t0, retries, version), video);
			}
		}
	}

	static final int MAX_BUSY_RETRIES = 2;
	static final long BUSY_WAIT_MS = 10_000;

	/** 예비 모델로 분석했을 때 메모 앞에 붙임 (예비 모델은 짐을 더 많이 빠뜨림) */
	static final String FALLBACK_NOTE = "AI 서버가 혼잡해 예비 모델로 분석했어요. 빠진 짐이 있을 수 있으니 목록을 꼭 확인해 주세요.";

	static String withFallbackNote(String memo, String usedModel, String primaryModel) {
		if (usedModel == null || usedModel.equals(primaryModel)) {
			return memo;
		}
		return memo == null || memo.isBlank() ? FALLBACK_NOTE : FALLBACK_NOTE + " " + memo;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** 모델 응답 글자 → 검사 · 매핑 */
	Result interpret(String id, GeminiVideoAnalyzer.Response res, long t0, int retries, String version) {
		return interpret(id, List.of(res), t0, retries, version);
	}

	/**
	 * 응답 여러 개(다수결) → 검사 · 매핑. JSON 을 못 읽은 응답은 표에서 빼고, 모두 못 읽으면 오류.
	 * 비용 · 토큰은 모든 응답의 합, 원본은 "분석 1/2/3" 으로 이어 붙여 둡니다.
	 */
	Result interpret(String id, List<GeminiVideoAnalyzer.Response> responses, long t0, int retries, String version) {
		// 일부만 한도 초과로 예비 모델이 답했으면 예비 모델 답은 투표에서 뺌 (예비 모델은 짐을 많이 빠뜨림)
		String primary = ai == null ? null : ai.model();
		List<GeminiVideoAnalyzer.Response> mine = responses.stream().filter(r -> primary == null || primary.equals(r.model()))
				.toList();
		List<GeminiVideoAnalyzer.Response> voters = mine.isEmpty() ? responses : mine;
		List<RawResult> raws = new ArrayList<>();
		List<GeminiVideoAnalyzer.Response> parsed = new ArrayList<>();
		RuntimeException bad = null;
		for (GeminiVideoAnalyzer.Response r : voters) {
			try {
				raws.add(parse(r.text()));
				parsed.add(r);
			} catch (RuntimeException e) {
				bad = e;
			}
		}
		if (raws.isEmpty()) {
			throw new AnalyzerException(VideoAnalysis.ERR_JSON,
					"AI 응답 JSON 을 읽지 못했습니다: " + (bad == null ? "" : bad.getMessage()));
		}
		VoteMerger.Merged merged = VoteMerger.merge(raws);
		RawResult raw = merged.result();
		GeminiVideoAnalyzer.Response base = parsed.get(merged.baseIndex());
		Usage usage = new Usage(sum(responses, Usage::inputTokens), sum(responses, Usage::outputTokens),
				sum(responses, Usage::thoughtTokens), sum(responses, Usage::totalTokens));
		long apiMs = responses.stream().mapToLong(GeminiVideoAnalyzer.Response::apiMs).max().orElse(0);
		String usedModel = voters.stream().map(GeminiVideoAnalyzer.Response::model)
				.filter(m -> m != null && !m.equals(ai.model())).findFirst().orElse(base.model());
		String rawText = base.text();
		if (responses.size() > 1) {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < responses.size(); i++) {
				b.append(i == 0 ? "" : "\n\n").append("== 분석 ").append(i + 1).append('/').append(responses.size())
						.append(" (").append(responses.get(i).model()).append(") ==\n").append(responses.get(i).text());
			}
			rawText = b.toString();
		}
		GeminiVideoAnalyzer.Response res = new GeminiVideoAnalyzer.Response(rawText, usage, base.uploadMs(), apiMs,
				usedModel);
		double cost = cost(res.usage());
		long total = System.currentTimeMillis() - t0;
		if (!raw.videoOk() || raw.items().isEmpty()) {
			String reason = raw.rejectReason() == null ? "" : raw.rejectReason();
			String code = reason.matches(".*(어두|흔들|흐릿|blur|dark).*") ? VideoAnalysis.ERR_DARK
					: VideoAnalysis.ERR_NOT_HOME;
			String msg = VideoAnalysis.ERR_DARK.equals(code) ? "영상이 어둡거나 흔들려서 짐을 알아보기 어려워요"
					: "집 안 가구·가전이 보이게 다시 찍어 주세요";
			return new Result(id, false, code, msg + (reason.isBlank() ? "" : " (" + reason + ")"), res.model(),
					version, List.of(), raw.rooms(), raw.memo(), res.usage(), cost, res.uploadMs(),
					res.apiMs(), total, retries, res.text());
		}
		return new Result(id, true, null, null, res.model(), version, ItemMatcher.map(raw.items()),
				raw.rooms(), withFallbackNote(raw.memo(), res.model(), ai.model()), res.usage(), cost, res.uploadMs(), res.apiMs(), total, retries, res.text());
	}

	private static long sum(List<GeminiVideoAnalyzer.Response> rs, java.util.function.ToLongFunction<Usage> f) {
		return rs.stream().mapToLong(r -> f.applyAsLong(r.usage())).sum();
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

	private boolean db() {
		return logs != null && schema != null && schema.ready();
	}

	/** 최근 기록부터 (DB 기록 + V4 적용 전 파일 기록) */
	public List<Logged> history(int limit) {
		List<Logged> all = new ArrayList<>();
		if (db()) {
			logs.list(limit).forEach(row -> {
				Logged l = fromRow(row);
				if (l != null) {
					all.add(l);
				}
			});
		}
		Path dir = logRoot;
		if (Files.isDirectory(dir)) {
			try (var s = Files.walk(dir, 2)) {
				s.filter(p -> p.toString().endsWith(".json"))
						.sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
						.limit(limit).map(p -> read(p)).filter(l -> l != null).forEach(l -> all.add(l));
			} catch (IOException e) {
				// 파일 기록은 보조라 무시
			}
		}
		all.sort((a, b) -> b.at().compareTo(a.at()));
		return all.size() > limit ? all.subList(0, limit) : all;
	}

	/** 고객이 이사 요청을 마치면 분석 기록을 요청 · 영상과 연결 (DB 기록일 때만) */
	public void linkRequest(String analysisId, long requestId, Long videoId) {
		if (analysisId != null && analysisId.matches("\\d+") && db()) {
			logs.linkRequest(Long.parseLong(analysisId), requestId, videoId);
		}
	}

	public Logged find(String analysisId) {
		if (analysisId != null && analysisId.matches("\\d{1,18}")) {
			return db() ? fromRow(logs.find(Long.parseLong(analysisId))) : null;
		}
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

	/** DB 행 → 기록 (RESULT_JSON 에 파일 기록과 같은 내용, RAW_JSON 은 따로) */
	private Logged fromRow(AnalysisRow row) {
		if (row == null || row.getResultJson() == null) {
			return null;
		}
		try {
			Logged l = parse(json.readTree(row.getResultJson()));
			return new Logged(String.valueOf(row.getAnalysisId()), row.getCreatedAt().toString(), l.video(),
					l.videoMb(), l.model(), l.promptVersion(), l.success(), l.errorCode(), l.message(), l.usage(),
					l.costUsd(), l.uploadMs(), l.apiMs(), l.totalMs(), l.retries(), l.items(), l.totalCbm(),
					l.reviewCount(), l.unmappedCount(), row.getRawJson());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private Logged read(Path p) {
		try {
			return parse(json.readTree(Files.readAllBytes(p))); // 바이트로 읽어야 BOM 있는 파일도 읽힘
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private Logged parse(JsonNode n) {
		{
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
		}
	}

	double cost(Usage u) {
		// 생각(thinking) 토큰은 출력 단가로 과금
		return (u.inputTokens() * priceIn + (u.outputTokens() + u.thoughtTokens()) * priceOut) / 1_000_000.0;
	}

	private Result fail(String id, String code, String message, long t0, int retries, String version) {
		return new Result(id, false, code, message, ai.model(), version, List.of(), List.of(), null,
				Usage.NONE, 0, 0, 0, System.currentTimeMillis() - t0, retries, null);
	}

	/** TBL_AI_ANALYSIS 에 저장하고 분석 ID 를 DB 번호로 바꿈 */
	private Result saveRow(Result r, Map<String, Object> rec) {
		AnalysisRow row = new AnalysisRow();
		row.setTestYn("Y"); // 고객이 이사 요청을 마치면 linkRequest 가 N 으로
		row.setModelName(r.model());
		row.setPromptVersion(r.promptVersion());
		row.setFileMb((Double) rec.get("videoMb"));
		row.setProcessOption("thinking=" + ai.thinkingLevel() + " votes=" + votes);
		row.setInputTokens(r.usage().inputTokens());
		row.setOutputTokens(r.usage().outputTokens());
		row.setTotalTokens(r.usage().totalTokens());
		row.setUsageJson(json.writeValueAsString(r.usage()));
		row.setPriceIn(priceIn);
		row.setPriceOut(priceOut);
		row.setPriceDate(LocalDate.parse(priceDate));
		row.setCurrency("USD");
		row.setEstCost(r.costUsd());
		row.setUploadMs(r.uploadMs());
		row.setApiMs(r.apiMs());
		row.setTotalMs(r.totalMs());
		row.setRetryCount(r.retries());
		row.setIsSuccess(r.success() ? "Y" : "N");
		row.setErrorCode(r.errorCode());
		String msg = r.message();
		row.setErrorMessage(msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg);
		row.setItemCount(r.items().size());
		row.setMappedCount((int) r.items().stream().filter(i -> !VideoAnalysis.UNMAPPED.equals(i.mappingType())).count());
		row.setReviewCount((int) r.reviewCount());
		row.setRawJson(r.rawJson());
		row.setResultJson(json.writeValueAsString(rec));
		logs.insert(row);
		String id = String.valueOf(row.getAnalysisId());
		return new Result(id, r.success(), r.errorCode(), r.message(), r.model(), r.promptVersion(), r.items(),
				r.rooms(), r.memo(), r.usage(), r.costUsd(), r.uploadMs(), r.apiMs(), r.totalMs(), r.retries(),
				r.rawJson());
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
			rec.put("votes", votes);
			rec.put("itemCount", r.items().size());
			rec.put("reviewCount", r.reviewCount());
			rec.put("unmappedCount",
					r.items().stream().filter(i -> VideoAnalysis.UNMAPPED.equals(i.mappingType())).count());
			rec.put("totalCbm", Math.round(r.totalCbm() * 1000) / 1000.0);
			rec.put("items", r.items());
			if (db()) {
				r = saveRow(r, rec);
			} else {
				rec.put("rawJson", r.rawJson());
				Path dir = logRoot.resolve(LocalDate.now().toString().replace("-", ""));
				Files.createDirectories(dir);
				Files.writeString(dir.resolve(r.analysisId() + ".json"),
						json.writerWithDefaultPrettyPrinter().writeValueAsString(rec));
			}
		} catch (IOException | RuntimeException e) {
			log.warn("분석 기록 저장 실패 {}: {}", r.analysisId(), e.getMessage());
		}
		log.info("영상 분석 {} success={} code={} items={} cbm={} tokens={} cost=${} {}ms", r.analysisId(),
				r.success(), r.errorCode(), r.items().size(), String.format("%.2f", r.totalCbm()),
				r.usage().totalTokens(), String.format("%.4f", r.costUsd()), r.totalMs());
		return r;
	}
}
