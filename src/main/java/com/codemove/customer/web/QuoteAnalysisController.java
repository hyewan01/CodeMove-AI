package com.codemove.customer.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.FileStore;
import com.codemove.common.Fmt;
import com.codemove.estimate.DistanceService;
import com.codemove.estimate.EstimateCalculator;
import com.codemove.estimate.VideoAnalysis;
import com.codemove.estimate.VideoAnalysisService;
import com.codemove.move.MoveService;

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
			ANALYSIS_SEC = "quote.analysisSec", ANALYSIS_ID = "quote.analysisId";

	private final VideoAnalysisService analysis;
	private final FileStore files;
	private final DistanceService distance;

	public QuoteAnalysisController(VideoAnalysisService analysis, FileStore files, DistanceService distance) {
		this.analysis = analysis;
		this.files = files;
		this.distance = distance;
	}

	/** 예상 견적 요청 (quote.js 6단계) */
	public record EstimateRequest(String packType, String moveDate, List<EstimateItem> items, String fromAddress,
			String toAddress, Integer fromFloor, String fromCarry, Integer toFloor, String toCarry,
			Integer storageDays, String fromDetail, String toDetail,
			List<CustomerPageController.QuoteOptionForm> options) {
	}

	public record EstimateItem(String name, String sizeCode, int count) {
	}

	/** 6단계 예상 견적: 서버의 EstimateCalculator 로 계산 (이사 요청 때 저장하는 금액과 같은 식) */
	@PostMapping("/quote/estimate")
	public Map<String, Object> estimate(@RequestBody EstimateRequest req) {
		List<EstimateCalculator.Line> lines = req.items() == null ? List.of()
				: req.items().stream().filter(i -> i != null && i.name() != null && i.count() > 0)
						.map(i -> EstimateCalculator.Line.of(i.name(), i.sizeCode(), Math.min(i.count(), 999))).toList();
		String pack = req.packType() != null && req.packType().matches("0[1-4]") ? req.packType()
				: EstimateCalculator.FULL;
		Double km = distance.km(req.fromAddress(), req.toAddress());
		EstimateCalculator.Estimate e = EstimateCalculator.estimate(lines,
				new EstimateCalculator.Conditions(pack, km, Fmt.parseDate(req.moveDate()),
						CustomerPageController.site(req.fromFloor(), req.fromCarry(), req.fromDetail()),
						CustomerPageController.site(req.toFloor(), req.toCarry(), req.toDetail()),
						CustomerPageController.storageDays(pack, req.storageDays()),
						req.options() == null ? null : CustomerPageController.options(req.options())));
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("distanceKm", km);
		out.put("min", e.totalMin());
		out.put("max", e.totalMax());
		out.put("amount", MoveService.midpoint(e));
		out.put("deposit", e.deposit());
		out.put("ton", e.tonText());
		out.put("trucks", e.truckLabel());
		out.put("itemCbm", e.itemCbm());
		out.put("loadCbm", e.loadCbm());
		out.put("workers", e.workers());
		out.put("fees", e.fees());
		out.put("notes", e.notes());
		return out;
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
		session.setAttribute(ANALYSIS_ID, r.analysisId());
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
			// 추가 작업 단계에서 벽걸이 TV 를 미리 골라 둠
			m.put("wallMounted", i.wallMounted());
			m.put("inch", i.inch());
			if (VideoAnalysis.UNMAPPED.equals(i.mappingType())) {
				m.put("baseCbm", 0);
			}
			items.add(m);
		}
		out.put("items", items);
		return out;
	}
}
