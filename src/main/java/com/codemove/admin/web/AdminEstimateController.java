package com.codemove.admin.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.admin.web.AdminEstimates.EstimateRecord;
import com.codemove.admin.web.AdminEstimates.Registration;
import com.codemove.common.FileStore;
import com.codemove.move.EstimateDoc;
import com.codemove.move.Quote;
import com.codemove.move.QuoteService;
import com.codemove.notification.NotificationService;

import jakarta.servlet.http.HttpSession;

/** 견적 관리 (SCR-A-001) · 견적 수정 (SCR-A-002) · 견적서 등록 (SCR-A-003~004) */
@Controller
@RequestMapping("/admin/estimates")
public class AdminEstimateController {

	private final QuoteService quotes;
	private final NotificationService notifications;
	private final FileStore files;

	public AdminEstimateController(QuoteService quotes, NotificationService notifications, FileStore files) {
		this.quotes = quotes;
		this.notifications = notifications;
		this.files = files;
	}

	private List<EstimateRecord> records() {
		return quotes.listLines().stream().map(EstimateRecord::of).toList();
	}

	/* ---------- 견적 관리 · 견적 수정 ---------- */

	@GetMapping
	public String list(@RequestParam(defaultValue = "전체") String status, @RequestParam(defaultValue = "") String date,
			@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) Long id,
			@RequestParam(defaultValue = "form") String tab, @RequestParam(defaultValue = "false") boolean editing,
			@RequestParam(required = false) Long del, Model model) {
		List<EstimateRecord> all = records();
		String digits = date.replaceAll("\\D", "");
		List<EstimateRecord> filtered = all.stream()
				.filter(e -> "전체".equals(status) || e.getStatus().equals(status))
				.filter(e -> digits.isEmpty() || e.getDate().replaceAll("\\D", "").startsWith(digits))
				.toList();
		AdminPageController.addPage(model, filtered, page);
		model.addAttribute("status", status);
		model.addAttribute("date", date);
		model.addAttribute("progress", List.of("견적 검토", "견적 승인", "견적 확정").stream().map(label -> {
			long count = all.stream().filter(e -> e.getStatus().equals(label)).count();
			long pct = all.isEmpty() ? 0 : Math.round(count * 100.0 / all.size());
			return new Object[] { label, count + "건 (" + pct + "%)", pct,
					switch (label) { case "견적 검토" -> "#d96c62"; case "견적 승인" -> "#d3904b"; default -> "#4f8b55"; } };
		}).toList());
		if (id != null) {
			Quote q = quotes.find(id);
			all.stream().filter(e -> e.getId() == id).findFirst().ifPresent(e -> model.addAttribute("editingRecord", e));
			if (q != null) {
				model.addAttribute("doc", quotes.toDoc(q));
			}
			model.addAttribute("tab", tab);
			model.addAttribute("editing", editing);
		}
		if (del != null) {
			all.stream().filter(e -> e.getId() == del).findFirst().ifPresent(e -> model.addAttribute("deletingRecord", e));
		}
		model.addAttribute("optionRows", EstimateDoc.OPTION_ROWS);
		return "admin/estimates";
	}

	/** 견적서 수정 저장 (고객명·연락처·견적일도 목록에 반영) */
	@PostMapping("/{id}/document")
	public String saveDocument(@PathVariable long id, @ModelAttribute EstimateDoc doc,
			@RequestParam(defaultValue = "") String back) {
		quotes.save(id, doc);
		return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates?id=" + id);
	}

	/** 견적 승인 → 고객에게 "견적 도착" */
	@PostMapping("/{id}/approve")
	public String approve(@PathVariable long id, HttpSession session) {
		Quote q = quotes.approve(id, AdminSession.adminId(session));
		if (q != null && q.getMemberId() != null) {
			notifications.toMember(q.getMemberId(), "02", "새로운 견적이 도착했어요", "견적 금액과 작업 내용을 확인하고 확정해주세요.",
					"/move/estimate");
		}
		if (q != null) {
			notifications.toAdmin("16", "상태 변경", q.getContactName() + " 고객님의 견적 상태가 '견적 검토'에서 '견적 승인'으로 변경되었습니다.",
					"/admin/estimates", false);
		}
		return "redirect:/admin/estimates";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable long id, @RequestParam(defaultValue = "") String back) {
		Quote q = quotes.delete(id);
		if (q != null) {
			notifications.toAdmin("17", "데이터 삭제", q.getContactName() + " 고객님의 견적서(" + q.getQuoteNo() + ")가 삭제되었습니다.",
					"/admin/estimates", false);
		}
		return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates");
	}

	/** 견적 상세 (일정 캘린더 > 상세 보기): 읽기 전용 견적서 */
	@GetMapping("/{id}/document")
	public String document(@PathVariable long id, @RequestParam(defaultValue = "") String back, Model model) {
		Quote q = quotes.find(id);
		if (q == null) {
			return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates");
		}
		model.addAttribute("record", records().stream().filter(e -> e.getId() == id).findFirst().orElse(null));
		model.addAttribute("doc", quotes.toDoc(q));
		model.addAttribute("back", AdminPageController.safeBack(back, "/admin/schedule"));
		model.addAttribute("optionRows", EstimateDoc.OPTION_ROWS);
		return "admin/estimate-document";
	}

	/* ---------- 견적서 등록 ---------- */

	@GetMapping("/new")
	public String registration(@RequestParam(required = false) Integer step, HttpSession session, Model model) {
		Registration reg = AdminState.of(session).getRegistration();
		int current = step != null && reg.getDoc() != null ? step : (reg.getDoc() == null ? 1 : reg.getStep());
		if (current == 2 && "pdf".equals(reg.getKind())) {
			current = 3;
		}
		reg.setStep(current);
		model.addAttribute("reg", reg);
		model.addAttribute("step", current);
		model.addAttribute("pdfMode", "pdf".equals(reg.getKind()));
		model.addAttribute("doc", reg.getDoc());
		model.addAttribute("optionRows", EstimateDoc.OPTION_ROWS);
		return "admin/estimate-new";
	}

	/** Step 1 → 첨부 저장 · 분석 (사진: 인식 시연값 후 Step 2, PDF: Step 3) */
	@PostMapping("/new/analyze")
	public String analyze(@RequestParam String kind, @RequestParam(defaultValue = "") String fileName,
			@RequestParam(required = false) MultipartFile document, HttpSession session) {
		Registration reg = new Registration();
		reg.setKind("pdf".equals(kind) ? "pdf" : "photo");
		reg.setFileName(fileName);
		reg.setFilePath(files.save("quotes", document));
		reg.setDoc("pdf".equals(kind) ? AdminEstimates.pdfDocument() : AdminEstimates.recognizePhoto());
		reg.setStep("pdf".equals(kind) ? 3 : 2);
		AdminState.of(session).setRegistration(reg);
		return "redirect:/admin/estimates/new?step=" + reg.getStep();
	}

	/** Step 2 → 수정한 견적서 저장 후 미리보기 */
	@PostMapping("/new/draft")
	public String draft(@ModelAttribute EstimateDoc doc, HttpSession session) {
		AdminState.of(session).getRegistration().setDoc(doc.normalized());
		return "redirect:/admin/estimates/new?step=3";
	}

	/** 첨부부터 다시 */
	@PostMapping("/new/reset")
	public String reset(HttpSession session) {
		AdminState.of(session).setRegistration(new Registration());
		return "redirect:/admin/estimates/new";
	}

	/** Step 4 → TBL_QUOTE 등록 (SOURCE_TYPE 02 사진 / 03 PDF) */
	@PostMapping("/new/complete")
	public String complete(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String date,
			@RequestParam(defaultValue = "견적 검토") String status, HttpSession session) {
		AdminState state = AdminState.of(session);
		Registration reg = state.getRegistration();
		EstimateDoc doc = reg.getDoc();
		if (doc == null) {
			return "redirect:/admin/estimates/new";
		}
		boolean pdf = "pdf".equals(reg.getKind());
		String finalName = pdf ? name.trim() : doc.getCustomer();
		String finalDate = pdf ? date.trim() : doc.getEstimateDate();
		if (finalName.isEmpty() || finalDate.isEmpty()) {
			return "redirect:/admin/estimates/new?step=4";
		}
		if (pdf) {
			doc.setCustomer(finalName);
			doc.setEstimateDate(finalDate);
		}
		Quote q = quotes.register(doc, pdf ? "03" : "02", reg.getFilePath(), QuoteService.statusCode(status),
				AdminSession.adminId(session));
		state.setRegistration(new Registration());
		state.toast(notifications.fire("견적서 등록 · 견적서를 등록했을 시", q.getMemberId(), q.getContactName()));
		return "redirect:/admin/estimates";
	}
}
