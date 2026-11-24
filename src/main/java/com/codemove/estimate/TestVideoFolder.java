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

	/** 폴더 영상 한 개. truthError 는 정답 파일을 읽지 못했을 때 그 이유 */
	public record Video(String name, double mb, boolean hasTruth, String truthError) {

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
			Double f1Max, double cbmAvg, double cbmMin, double cbmMax, Double truthCbm, String lastId, String truthKey) {
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
				if (truth != null) {
					try {
						GroundTruth.read(truth);
					} catch (RuntimeException e) {
						err = e.getMessage();
					}
				}
				out.add(new Video(p.getFileName().toString(), Math.round(Files.size(p) / 104857.6) / 10.0,
						truth != null, err));
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
			out.add(new Summary(first.video(), first.promptVersion(), first.model(), g.size(),
					f1.isEmpty() ? null : f.getAverage(), f1.isEmpty() ? null : f.getMin(),
					f1.isEmpty() ? null : f.getMax(), cbm.getAverage(), cbm.getMin(), cbm.getMax(), truthCbm,
					first.analysisId(), key));
		}
		out.sort((a, b) -> a.video().equals(b.video()) ? a.prompt().compareTo(b.prompt()) * -1
				: a.video().compareTo(b.video()));
		return out;
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
