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
import com.codemove.estimate.GeminiVideoAnalyzer;
import com.codemove.estimate.GroundTruth;
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

	public AdminAiTestController(VideoAnalysisService analysis, GeminiVideoAnalyzer ai, FileStore files) {
		this.analysis = analysis;
		this.ai = ai;
		this.files = files;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("configured", ai.configured());
		model.addAttribute("model", ai.model());
		model.addAttribute("promptVersion", AnalysisPrompt.VERSION);
		model.addAttribute("sets", GroundTruth.SETS);
		model.addAttribute("history", analysis.history(30));
		return "admin/ai-test";
	}

	@PostMapping
	public String run(@RequestParam MultipartFile video, @RequestParam(defaultValue = "") String truth,
			RedirectAttributes ra) {
		if (video == null || video.isEmpty()) {
			ra.addFlashAttribute("error", "영상 파일을 골라 주세요");
			return "redirect:/admin/ai-test";
		}
		String url = files.save("ai-test", video);
		VideoAnalysis.Result r = analysis.analyze(analysis.pathOf(url));
		return "redirect:/admin/ai-test/" + r.analysisId() + (truth.isBlank() ? "" : "?truth=" + truth);
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable String id, @RequestParam(defaultValue = "") String truth, Model model) {
		VideoAnalysisService.Logged log = analysis.find(id);
		if (log == null) {
			return "redirect:/admin/ai-test";
		}
		model.addAttribute("log", log);
		model.addAttribute("sets", GroundTruth.SETS);
		model.addAttribute("truth", truth);
		if (GroundTruth.SETS.contains(truth)) {
			model.addAttribute("score", GroundTruth.score(truth, log.items()));
		}
		return "admin/ai-test-detail";
	}
}
