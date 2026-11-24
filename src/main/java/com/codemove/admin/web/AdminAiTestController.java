package com.codemove.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.codemove.common.FileStore;
import com.codemove.estimate.AnalysisPrompt;
import com.codemove.estimate.EstimateCalculator;
import com.codemove.estimate.GeminiVideoAnalyzer;
import com.codemove.estimate.GroundTruth;
import com.codemove.estimate.TestVideoFolder;
import com.codemove.estimate.VideoAnalysis;
import com.codemove.estimate.VideoAnalysisService;

/**
 * AI 영상 분석 테스트 (가이드 2·7단계, 개발·관리자 전용).
 * 영상을 올려 분석하고, 기록별로 품목 · 매핑 · CBM · 토큰 · 비용 · 시간 · 정답 비교를 봅니다.
 * 고객 견적 흐름과 같은 VideoAnalysisService 를 쓰므로 여기서 검증한 결과가 실제 견적에도 그대로 적용됩니다.
 */
@Controller
@RequestMapping("/admin/ai-test")
public class AdminAiTestController {

	private final VideoAnalysisService analysis;
	private final GeminiVideoAnalyzer ai;
	private final FileStore files;
	private final TestVideoFolder folder;

	public AdminAiTestController(VideoAnalysisService analysis, GeminiVideoAnalyzer ai, FileStore files,
			TestVideoFolder folder) {
		this.analysis = analysis;
		this.ai = ai;
		this.files = files;
		this.folder = folder;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("configured", ai.configured());
		model.addAttribute("model", ai.model());
		model.addAttribute("promptVersion", AnalysisPrompt.VERSION);
		model.addAttribute("promptVersions", AnalysisPrompt.VERSIONS);
		model.addAttribute("sets", folder.truthNames());
		model.addAttribute("history", analysis.history(30));
		model.addAttribute("folderConfigured", folder.configured());
		model.addAttribute("folderDir", folder.dir());
		model.addAttribute("folderVideos", folder.videos());
		model.addAttribute("batch", folder.batch());
		model.addAttribute("summaries", folder.summaries(300));
		return "admin/ai-test";
	}

	/** 테스트 영상 폴더의 영상 한 개 분석 (정답 파일이 있으면 바로 비교) */
	@PostMapping("/folder")
	public String runFolder(@RequestParam String name, @RequestParam(defaultValue = "") String prompt,
			RedirectAttributes ra) {
		java.nio.file.Path p = folder.path(name);
		if (p == null) {
			ra.addFlashAttribute("error", "폴더에서 영상을 찾지 못했어요: " + name);
			return "redirect:/admin/ai-test";
		}
		VideoAnalysis.Result r = analysis.analyze(p, prompt);
		String truth = folder.truthFor(name);
		if (!truth.isEmpty()) {
			ra.addAttribute("truth", truth);
		}
		return "redirect:/admin/ai-test/" + r.analysisId();
	}

	/** 폴더 영상 전체를 뒤에서 하나씩 분석 */
	@PostMapping("/folder/all")
	public String runFolderAll(@RequestParam(defaultValue = "") String prompt, RedirectAttributes ra) {
		String v = AnalysisPrompt.VERSIONS.contains(prompt) ? prompt : AnalysisPrompt.VERSION;
		if (!folder.runAll(v)) {
			ra.addFlashAttribute("error", "이미 전체 분석이 돌고 있거나 폴더에 영상이 없어요");
		}
		return "redirect:/admin/ai-test";
	}

	@PostMapping
	public String run(@RequestParam MultipartFile video, @RequestParam(defaultValue = "") String truth,
			@RequestParam(defaultValue = "") String prompt, RedirectAttributes ra) {
		if (video == null || video.isEmpty()) {
			ra.addFlashAttribute("error", "영상 파일을 골라 주세요");
			return "redirect:/admin/ai-test";
		}
		String url = files.save("ai-test", video);
		VideoAnalysis.Result r = analysis.analyze(analysis.pathOf(url), prompt);
		if (!truth.isBlank()) {
			ra.addAttribute("truth", truth);
		}
		return "redirect:/admin/ai-test/" + r.analysisId();
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable String id, @RequestParam(defaultValue = "") String truth, Model model) {
		VideoAnalysisService.Logged log = analysis.find(id);
		if (log == null) {
			return "redirect:/admin/ai-test";
		}
		if (truth.isBlank()) {
			truth = folder.truthFor(log.video()); // 폴더 영상이면 그 정답과 바로 비교
		}
		model.addAttribute("log", log);
		model.addAttribute("sets", folder.truthNames());
		model.addAttribute("truth", truth);
		GroundTruth.Score score = truth.isBlank() ? null : folder.score(truth, log.items());
		if (score != null) {
			model.addAttribute("score", score);
		}
		// 인식 품목 그대로 낸 예상 견적 (포장 · 반포장 · 일반, 50km 미만 · 사다리차 없음)
		var lines = log.items().stream().map(i -> i.line()).toList();
		model.addAttribute("estimates", java.util.stream.Stream.of(EstimateCalculator.FULL, EstimateCalculator.HALF,
				EstimateCalculator.GENERAL).map(p -> EstimateCalculator.estimate(lines,
						EstimateCalculator.Conditions.basic(p))).toList());
		return "admin/ai-test-detail";
	}
}
