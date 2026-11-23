package com.codemove.customer.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.FileStore;
import com.codemove.estimate.VideoAnalysis;
import com.codemove.estimate.VideoAnalysisService;

import jakarta.servlet.http.HttpSession;

/**
 * 견적 요청 2단계(SCR-U-004): 고객이 고른 영상을 올려 AI 로 품목을 찾음.
 * 올린 영상은 세션에 기억해 두고, 마지막 '이사 요청' 때 다시 올리지 않고 그 영상을 견적 요청에 붙입니다.
 * GEMINI_API_KEY 가 없으면 시연 품목(AiDemo)을 돌려줍니다.
 */
@RestController
public class QuoteAnalysisController {

	/** 세션: 분석에 쓴 영상 주소 · 원본 이름 · 분석 시간(초) */
	public static final String VIDEO_URL = "quote.videoUrl", VIDEO_NAME = "quote.videoName",
			ANALYSIS_SEC = "quote.analysisSec";

	private final VideoAnalysisService analysis;
	private final FileStore files;

	public QuoteAnalysisController(VideoAnalysisService analysis, FileStore files) {
		this.analysis = analysis;
		this.files = files;
	}

	@PostMapping("/quote/analyze")
	public Map<String, Object> analyze(@RequestParam MultipartFile video, HttpSession session) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (video == null || video.isEmpty()) {
			out.put("ok", false);
			out.put("message", "영상 파일을 다시 선택해 주세요");
			return out;
		}
		String url = files.save("videos", video);
		String name = video.getOriginalFilename() == null ? "video" : video.getOriginalFilename();
		session.setAttribute(VIDEO_URL, url);
		session.setAttribute(VIDEO_NAME, name.length() > 200 ? name.substring(name.length() - 200) : name);

		if (!analysis.configured()) {
			session.setAttribute(ANALYSIS_SEC, 0);
			out.put("ok", true);
			out.put("demo", true);
			out.put("message", "AI 키가 설정되지 않아 시연 품목을 보여드려요");
			out.put("items", AiDemo.recognizedItems());
			out.put("rooms", List.of());
			out.put("memo", "");
			return out;
		}

		VideoAnalysis.Result r = analysis.analyze(analysis.pathOf(url));
		session.setAttribute(ANALYSIS_SEC, (int) Math.max(1, Math.round(r.totalMs() / 1000.0)));
		out.put("ok", r.success());
		out.put("code", r.errorCode());
		out.put("message", r.message());
		out.put("analysisId", r.analysisId());
		out.put("sec", Math.round(r.totalMs() / 1000.0));
		out.put("rooms", r.rooms());
		out.put("memo", r.memo() == null ? "" : r.memo());
		List<Map<String, Object>> items = new ArrayList<>();
		int n = 0;
		for (VideoAnalysis.MappedItem i : r.items()) {
			Map<String, Object> m = AiDemo.map("id", "ai" + n++, "name", i.name(), "sizeCode", i.sizeCode(), "count",
					i.count());
			m.put("needsReview", i.needsReview());
			m.put("detectedName", i.detectedName());
			m.put("room", i.room());
			m.put("mapping", i.mappingType());
			if (VideoAnalysis.UNMAPPED.equals(i.mappingType())) {
				m.put("baseCbm", 0);
			}
			items.add(m);
		}
		out.put("items", items);
		return out;
	}
}
