package com.codemove.estimate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 테스트 영상 폴더 (codemove.ai.test-video-dir, 개인 경로라 application-local.yml 에 둠).
 * <ul>
 * <li>폴더에 영상(mp4·mov·webm)을 넣으면 관리자 AI 테스트 화면에 바로 나타납니다.</li>
 * <li>영상과 같은 이름의 .csv(예: "테스트 1.csv")가 있으면 그 영상의 정답 목록으로 씁니다.
 * 형식은 docs/estimate/testset/*.csv 와 같습니다 (공간,품목명,크기코드,수량,...).</li>
 * <li>"전체 분석"은 한 번에 하나씩 뒤에서 돌고, 결과는 일반 분석 기록으로 남습니다.</li>
 * </ul>
 */
@Component
public class TestVideoFolder {

	private static final Logger log = LoggerFactory.getLogger(TestVideoFolder.class);

	/** 정답 선택값 앞에 붙여 저장소 테스트셋(house-01 등)과 구분 */
	public static final String PREFIX = "폴더:";
	private static final List<String> EXT = List.of(".mp4", ".mov", ".webm");

	private final Path dir;
	private final VideoAnalysisService analysis;
	private final ExecutorService runner = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ai-test-folder");
		t.setDaemon(true);
		return t;
	});
	private volatile Batch batch;

	/** 폴더 영상 한 개. truthError 는 정답 파일을 읽지 못했을 때 그 이유, truthTon 은 정답 파일의 대표님 판단 톤 */
	public record Video(String name, double mb, boolean hasTruth, String truthError, Double truthTon,
			boolean hasReview) {

		public String truthKey() {
			return hasTruth && truthError == null ? PREFIX + base(name) : "";
		}
	}

	/** 전체 분석 진행 상황 */
	public record Batch(String prompt, int total, int done, String current, List<String> ids, String stopReason,
			boolean running) {
	}

	/** 같은 영상 · 프롬프트 · 모델로 돌린 결과 묶음 (안정성 확인용) */
	public record Summary(String video, String prompt, String model, int runs, Double f1Avg, Double f1Min,
			Double f1Max, double cbmAvg, double cbmMin, double cbmMax, Double truthCbm, String lastId, String truthKey,
			List<String> tons, Double truthTon) {

		/** 기록마다 AI 짐 톤 (포장 기준, 최근 것부터) */
		public String tonsText() {
			return String.join(" · ", tons);
		}
	}

	public TestVideoFolder(@Value("${codemove.ai.test-video-dir:}") String dir, VideoAnalysisService analysis) {
		this.dir = dir == null || dir.isBlank() ? null : Path.of(dir.strip());
		this.analysis = analysis;
	}

	public boolean configured() {
		return dir != null && Files.isDirectory(dir);
	}

	public String dir() {
		return dir == null ? "" : dir.toString();
	}

	/** 폴더의 영상 (이름순) */
	public List<Video> videos() {
		if (!configured()) {
			return List.of();
		}
		List<Video> out = new ArrayList<>();
		try (var s = Files.list(dir)) {
			for (Path p : s.filter(p -> Files.isRegularFile(p) && isVideo(p)).sorted().toList()) {
				Path truth = truthFile(p.getFileName().toString());
				String err = null;
				Double ton = null;
				if (truth != null) {
					try {
						GroundTruth.read(truth);
						ton = GroundTruth.readTon(truth);
					} catch (RuntimeException e) {
						err = e.getMessage();
					}
				}
				out.add(new Video(p.getFileName().toString(), Math.round(Files.size(p) / 104857.6) / 10.0,
						truth != null, err, ton,
						Files.exists(dir.resolve(base(p.getFileName().toString()) + REVIEW_SUFFIX + ".csv"))));
			}
		} catch (IOException e) {
			log.warn("테스트 영상 폴더를 읽지 못함: {}", e.getMessage());
		}
		return out;
	}

	/** 목록에 있는 이름만 받음 (경로 조작 방지) */
	public Path path(String name) {
		return videos().stream().filter(v -> v.name().equals(name)).findFirst().map(v -> dir.resolve(v.name()))
				.orElse(null);
	}

	/** 정답 비교 선택지: 저장소 테스트셋 + 정답 파일이 있는 폴더 영상 */
	public List<String> truthNames() {
		List<String> names = new ArrayList<>(GroundTruth.SETS);
		videos().stream().map(v -> v.truthKey()).filter(k -> !k.isEmpty()).forEach(k -> names.add(k));
		return names;
	}

	/** 정답 이름 → 점수. 모르는 이름이면 null */
	public GroundTruth.Score score(String truth, List<VideoAnalysis.MappedItem> items) {
		if (GroundTruth.SETS.contains(truth)) {
			return GroundTruth.score(truth, items);
		}
		if (truth == null || !truth.startsWith(PREFIX) || !configured()) {
			return null;
		}
		String base = truth.substring(PREFIX.length());
		for (Video v : videos()) {
			if (base(v.name()).equals(base) && v.hasTruth() && v.truthError() == null) {
				return GroundTruth.score(truth, GroundTruth.read(truthFile(v.name())), items);
			}
		}
		return null;
	}

	/** 분석 기록의 영상 이름이 폴더 영상이면 그 정답 이름 */
	public String truthFor(String videoName) {
		return videos().stream().filter(v -> v.name().equals(videoName)).findFirst().map(v -> v.truthKey())
				.orElse("");
	}

	/* ---------- 전체 분석 ---------- */

	public Batch batch() {
		return batch;
	}

	/** @return 이미 돌고 있거나 영상이 없으면 false */
	public synchronized boolean runAll(String prompt) {
		if (batch != null && batch.running()) {
			return false;
		}
		List<Video> list = videos();
		if (list.isEmpty()) {
			return false;
		}
		batch = new Batch(prompt, list.size(), 0, list.get(0).name(), List.of(), null, true);
		runner.submit(() -> {
			List<String> ids = new ArrayList<>();
			String stop = null;
			int done = 0;
			for (Video v : list) {
				batch = new Batch(prompt, list.size(), done, v.name(), List.copyOf(ids), null, true);
				Path p = dir.resolve(v.name());
				if (!Files.exists(p)) {
					done++;
					continue;
				}
				VideoAnalysis.Result r = analysis.analyze(p, prompt);
				ids.add(r.analysisId());
				done++;
				// 한도 초과 · 키 없음은 나머지도 똑같이 실패하므로 멈춤
				if (VideoAnalysis.ERR_QUOTA.equals(r.errorCode()) || VideoAnalysis.ERR_NO_KEY.equals(r.errorCode())) {
					stop = r.message();
					break;
				}
			}
			batch = new Batch(prompt, list.size(), done, null, List.copyOf(ids), stop, false);
		});
		return true;
	}

	/** 폴더 영상별 기록 묶음 (최근 기록 limit 건 안에서) */
	public List<Summary> summaries(int limit) {
		Map<String, Video> byName = new LinkedHashMap<>();
		videos().forEach(v -> byName.put(v.name(), v));
		Map<String, List<VideoAnalysisService.Logged>> groups = new LinkedHashMap<>();
		for (VideoAnalysisService.Logged l : analysis.history(limit)) {
			if (l.success() && byName.containsKey(l.video())) {
				groups.computeIfAbsent(l.video() + "|" + l.promptVersion() + "|" + l.model(), k -> new ArrayList<>())
						.add(l);
			}
		}
		List<Summary> out = new ArrayList<>();
		for (List<VideoAnalysisService.Logged> g : groups.values()) {
			VideoAnalysisService.Logged first = g.get(0); // 최근 것
			String key = byName.get(first.video()).truthKey();
			List<Double> f1 = new ArrayList<>();
			Double truthCbm = null;
			for (VideoAnalysisService.Logged l : g) {
				GroundTruth.Score s = key.isEmpty() ? null : score(key, l.items());
				if (s != null) {
					f1.add(s.f1() * 100);
					truthCbm = s.expectedCbm();
				}
			}
			var cbm = g.stream().mapToDouble(l -> l.totalCbm()).summaryStatistics();
			var f = f1.stream().mapToDouble(d -> d).summaryStatistics();
			List<String> tons = g.stream().map(l -> EstimateCalculator.estimate(
					l.items().stream().map(i -> i.line()).toList(),
					EstimateCalculator.Conditions.basic(EstimateCalculator.FULL)).tonText()).toList();
			out.add(new Summary(first.video(), first.promptVersion(), first.model(), g.size(),
					f1.isEmpty() ? null : f.getAverage(), f1.isEmpty() ? null : f.getMin(),
					f1.isEmpty() ? null : f.getMax(), cbm.getAverage(), cbm.getMin(), cbm.getMax(), truthCbm,
					first.analysisId(), key, tons, byName.get(first.video()).truthTon()));
		}
		out.sort((a, b) -> a.video().equals(b.video()) ? a.prompt().compareTo(b.prompt()) * -1
				: a.video().compareTo(b.video()));
		return out;
	}

	/* ---------- 대표님 검토용 파일 ---------- */

	public static final String REVIEW_SUFFIX = "_AI검토";

	/**
	 * AI 결과를 정답 파일과 같은 형식으로 (엑셀에서 열리게 UTF-8 BOM).
	 * 위쪽 "#" 줄에 AI 견적 요약 · 고치는 법 · 대표님 판단 톤 칸, 아래에 품목표, 맨 아래 크기코드 참고.
	 * 대표님이 고친 파일을 "영상이름.csv" 로 저장하면 그대로 정답이 됩니다 ("#" 줄은 읽을 때 건너뜀).
	 */
	public static String reviewCsv(VideoAnalysisService.Logged l) {
		var lines = l.items().stream().map(i -> i.line()).toList();
		EstimateCalculator.Estimate full = EstimateCalculator.estimate(lines,
				EstimateCalculator.Conditions.basic(EstimateCalculator.FULL));
		EstimateCalculator.Estimate half = EstimateCalculator.estimate(lines,
				EstimateCalculator.Conditions.basic(EstimateCalculator.HALF));
		EstimateCalculator.Estimate general = EstimateCalculator.estimate(lines,
				EstimateCalculator.Conditions.basic(EstimateCalculator.GENERAL));
		String base = base(l.video());
		StringBuilder sb = new StringBuilder("﻿");
		note(sb, "# 대표님 검토용 · " + l.video() + " · AI 분석 " + l.at().substring(0, 16).replace('T', ' ') + " ("
				+ l.model() + " · 프롬프트 " + l.promptVersion() + " · 분석 " + l.analysisId() + ")");
		note(sb, String.format("# AI 견적: 물품 %.1f CBM × 적재계수 %.1f = 적재 %.1f CBM → 짐 %s (%s)", full.itemCbm(),
				full.loadFactor(), full.loadCbm(), full.tonText(), full.truckLabel()));
		note(sb, "# 포장이사 " + won(full) + " · 반포장 " + won(half) + " · 일반이사 " + won(general)
				+ " (50km 미만 · 사다리차 없음 · 날짜 할증 없음 기준)");
		note(sb, "# 고치는 법: 아래 표에서 수량·크기코드를 고치고, 빠진 짐은 줄을 추가하고, 없는 짐은 줄을 지워 주세요."
				+ " 잔짐은 이사박스 02(바구니 1개) 개수로 적습니다. 크기코드는 맨 아래 참고.");
		sb.append(Csv.cell("# " + GroundTruth.TON_LABEL)).append(",,")
				.append(Csv.cell("← 바로 옆(B) 칸에 대표님이 보신 짐 톤수를 적어 주세요 (예: 7)")).append("\r\n");
		note(sb, "# 다 고치면 파일 이름을 '" + base + ".csv' 로 저장하면 정답 목록으로 쓰입니다.");
		if (l.items().isEmpty()) {
			note(sb, "# AI 가 찾은 품목이 없습니다: " + (l.message() == null ? "" : l.message()));
		}
		sb.append("공간,품목명,크기코드,수량,본 물건,영상 시각,확실도,비고,크기 설명,AI CBM\r\n");
		java.util.Set<String> names = new java.util.LinkedHashSet<>();
		for (VideoAnalysis.MappedItem i : l.items()) {
			boolean other = VideoAnalysis.UNMAPPED.equals(i.mappingType());
			com.codemove.move.ItemCatalog.Item item = other ? null : com.codemove.move.ItemCatalog.find(i.name());
			if (item != null) {
				names.add(item.name());
			}
			String remark = (i.evidence() == null ? "" : i.evidence())
					+ (i.inch() != null ? " · " + i.inch() + "인치" : "") + (i.wallMounted() ? " · 벽걸이" : "");
			sb.append(String.join(",", Csv.cell(i.room()), Csv.cell(other ? "기타" : i.name()),
					Csv.cell(other ? "" : i.sizeCode()), String.valueOf(i.count()),
					Csv.cell(other ? i.name() + (i.detectedName() == null || i.detectedName().equals(i.name()) ? ""
							: " (" + i.detectedName() + ")") : i.detectedName()),
					"", i.needsReview() ? "낮" : "높", Csv.cell(remark.strip().replaceFirst("^· ", "")),
					Csv.cell(item == null ? "" : item.size(i.sizeCode()).name()),
					other ? "" : String.format("%.3f", i.cbm()))).append("\r\n");
		}
		note(sb, "# 크기코드 참고 (코드 이름 · 1개 CBM)");
		for (String n : names) {
			var item = com.codemove.move.ItemCatalog.find(n);
			note(sb, "# " + n + ": " + String.join(" / ", item.sizes().stream()
					.map(s -> s.code() + " " + s.name() + " " + String.format("%.2f", s.cbm())).toList()));
		}
		return sb.toString();
	}

	/** 폴더 영상의 분석이면 검토용 파일을 폴더에 저장 (이미 있으면 덮지 않고 번호를 붙임). 저장한 파일 이름 */
	public String saveReview(VideoAnalysisService.Logged l) {
		if (path(l.video()) == null) {
			return null;
		}
		String base = base(l.video()) + REVIEW_SUFFIX;
		Path p = dir.resolve(base + ".csv");
		for (int n = 2; Files.exists(p); n++) {
			p = dir.resolve(base + "(" + n + ").csv");
		}
		try {
			Files.writeString(p, reviewCsv(l), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		return p.getFileName().toString();
	}

	private static void note(StringBuilder sb, String text) {
		sb.append(Csv.cell(text)).append("\r\n");
	}

	private static String won(EstimateCalculator.Estimate e) {
		return String.format("%d만~%d만원", Math.round(e.totalMin() / 10000.0), Math.round(e.totalMax() / 10000.0));
	}

	@PreDestroy
	void stop() {
		runner.shutdownNow();
	}

	private Path truthFile(String videoName) {
		Path p = dir.resolve(base(videoName) + ".csv");
		return Files.isRegularFile(p) ? p : null;
	}

	private static boolean isVideo(Path p) {
		String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
		return EXT.stream().anyMatch(e -> n.endsWith(e));
	}

	static String base(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? fileName : fileName.substring(0, dot);
	}
}
