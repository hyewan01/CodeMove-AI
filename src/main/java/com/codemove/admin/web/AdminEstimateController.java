package com.codemove.admin.web;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.admin.web.AdminEstimates.EstimateDoc;
import com.codemove.admin.web.AdminEstimates.EstimateRecord;
import com.codemove.admin.web.AdminEstimates.Registration;

import jakarta.servlet.http.HttpSession;

/** 견적 관리 (SCR-A-001) · 견적 수정 (SCR-A-002) · 견적서 등록 (SCR-A-003~004) */
@Controller
@RequestMapping("/admin/estimates")
public class AdminEstimateController {

	private static Optional<EstimateRecord> find(HttpSession session, long id) {
		return AdminState.of(session).getEstimates().stream().filter(e -> e.getId() == id).findFirst();
	}

	/* ---------- 견적 관리 · 견적 수정 ---------- */

	@GetMapping
	public String list(@RequestParam(defaultValue = "전체") String status, @RequestParam(defaultValue = "") String date,
			@RequestParam(defaultValue = "1") int page, @RequestParam(required = false) Long id,
			@RequestParam(defaultValue = "form") String tab, @RequestParam(defaultValue = "false") boolean editing,
			@RequestParam(required = false) Long del, HttpSession session, Model model) {
		List<EstimateRecord> all = AdminState.of(session).getEstimates();
		String digits = date.replaceAll("\\D", "");
		List<EstimateRecord> filtered = all.stream()
				.filter(e -> "전체".equals(status) || e.getStatus().equals(status))
				.filter(e -> digits.isEmpty() || e.getDate().replaceAll("\\D", "").startsWith(digits))
				.sorted((a, b) -> b.getDate().compareTo(a.getDate()))
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
			find(session, id).ifPresent(e -> {
				model.addAttribute("editingRecord", e);
				model.addAttribute("doc", e.document());
			});
			model.addAttribute("tab", tab);
			model.addAttribute("editing", editing);
		}
		if (del != null) {
			find(session, del).ifPresent(e -> model.addAttribute("deletingRecord", e));
		}
		model.addAttribute("optionRows", AdminEstimates.OPTION_ROWS);
		return "admin/estimates";
	}

	/** 견적서 수정 저장: 고객명·연락처·견적일도 목록에 반영 */
	@PostMapping("/{id}/document")
	public String saveDocument(@PathVariable long id, @ModelAttribute EstimateDoc doc,
			@RequestParam(defaultValue = "") String back, HttpSession session) {
		find(session, id).ifPresent(e -> {
			EstimateDoc saved = doc.normalized();
			e.setDoc(saved);
			e.setName(saved.getCustomer());
			e.setPhone(saved.getPhone());
			e.setDate(saved.getEstimateDate());
		});
		return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates?id=" + id);
	}

	@PostMapping("/{id}/approve")
	public String approve(@PathVariable long id, HttpSession session) {
		AdminState state = AdminState.of(session);
		find(session, id).ifPresent(e -> {
			e.setStatus("견적 승인");
			e.setApprovedDate(AdminEstimates.today());
			state.fire("견적 관리 · 견적 승인");
		});
		return "redirect:/admin/estimates";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable long id, @RequestParam(defaultValue = "") String back, HttpSession session) {
		AdminState state = AdminState.of(session);
		if (state.getEstimates().removeIf(e -> e.getId() == id)) {
			state.fire("견적 관리 · 행 삭제 시");
		}
		return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates");
	}

	/** 견적 상세 (일정 캘린더 > 상세 보기): 읽기 전용 견적서 */
	@GetMapping("/{id}/document")
	public String document(@PathVariable long id, @RequestParam(defaultValue = "") String back, HttpSession session,
			Model model) {
		Optional<EstimateRecord> record = find(session, id);
		if (record.isEmpty()) {
			return "redirect:" + AdminPageController.safeBack(back, "/admin/estimates");
		}
		model.addAttribute("record", record.get());
		model.addAttribute("doc", record.get().document());
		model.addAttribute("back", AdminPageController.safeBack(back, "/admin/schedule"));
		model.addAttribute("optionRows", AdminEstimates.OPTION_ROWS);
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
		model.addAttribute("optionRows", AdminEstimates.OPTION_ROWS);
		return "admin/estimate-new";
	}

	/** Step 1 → 첨부 분석 (사진: AI 인식 목업 후 Step 2, PDF: Step 3) */
	@PostMapping("/new/analyze")
	public String analyze(@RequestParam String kind, @RequestParam(defaultValue = "") String fileName,
			HttpSession session) {
		Registration reg = new Registration();
		reg.setKind("pdf".equals(kind) ? "pdf" : "photo");
		reg.setFileName(fileName);
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

	/** Step 4 → 견적 관리에 등록 */
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
		EstimateRecord record = new EstimateRecord(System.currentTimeMillis(), status, finalName, finalDate,
				"견적 검토".equals(status) ? null : AdminEstimates.today(), null);
		record.setPhone(doc.getPhone());
		record.setDoc(doc);
		state.getEstimates().add(0, record);
		state.setRegistration(new Registration());
		state.fire("견적서 등록 · 견적서를 등록했을 시");
		return "redirect:/admin/estimates";
	}
}
