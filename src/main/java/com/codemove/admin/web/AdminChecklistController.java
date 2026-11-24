package com.codemove.admin.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.codemove.checklist.ChecklistItem;
import com.codemove.checklist.ChecklistService;

/**
 * 체크리스트 관리 (채팅 관리처럼 첫 화면 → 고객별 진행 현황 · 항목 관리).
 * 고객별 진행 현황: 고객을 누르면 단계별로 나눠서 무엇을 체크했는지 봄.
 * 항목 관리: 단계별로 나눠 기본 할 일을 고치기 · 더하기 · 숨기기 · 지우기.
 */
@Controller
@RequestMapping("/admin/checklist")
public class AdminChecklistController {

	private final ChecklistService checklist;

	public AdminChecklistController(ChecklistService checklist) {
		this.checklist = checklist;
	}

	/** 단계 한 묶음 (항목 관리 화면) */
	public record PhaseItems(String code, String label, String when, List<ChecklistItem> items) {

		public long hidden() {
			return items.stream().filter(i -> !i.isUsed()).count();
		}
	}

	@GetMapping
	public String home(Model model) {
		List<ChecklistService.Progress> people = checklist.progress();
		List<ChecklistItem> items = checklist.items();
		model.addAttribute("peopleCount", people.size());
		model.addAttribute("avgPercent", people.isEmpty() ? 0
				: Math.round(people.stream().mapToInt(p -> p.view().percent()).average().orElse(0)));
		model.addAttribute("doneCount", people.stream().filter(p -> p.view().percent() == 100).count());
		model.addAttribute("itemCount", items.stream().filter(i -> i.isUsed()).count());
		model.addAttribute("hiddenCount", items.stream().filter(i -> !i.isUsed()).count());
		return "admin/checklist-home";
	}

	@GetMapping("/customers")
	public String customers(@RequestParam(required = false) Long open, Model model) {
		model.addAttribute("people", checklist.progress());
		model.addAttribute("openId", open);
		return "admin/checklist-customers";
	}

	@GetMapping("/items")
	public String items(@RequestParam(required = false) String edit, @RequestParam(required = false) String phase,
			@RequestParam(required = false) String open, Model model) {
		List<ChecklistItem> all = checklist.items();
		model.addAttribute("phases", ChecklistService.PHASES.entrySet().stream()
				.map(p -> new PhaseItems(p.getKey(), p.getValue()[0], p.getValue()[1],
						all.stream().filter(i -> i.getPhase().equals(p.getKey())).toList()))
				.toList());
		model.addAttribute("phaseNames", ChecklistService.PHASES);
		model.addAttribute("openPhase", open);
		if ("new".equals(edit)) {
			ChecklistItem draft = new ChecklistItem();
			draft.setPhase(ChecklistService.PHASES.containsKey(phase) ? phase : "01");
			model.addAttribute("draft", draft);
			model.addAttribute("isNew", true);
		} else if (edit != null) {
			ChecklistItem draft = checklist.item(edit);
			if (draft != null) {
				model.addAttribute("draft", draft);
				model.addAttribute("isNew", false);
			}
		}
		return "admin/checklist-items";
	}

	@PostMapping("/items")
	public String save(@RequestParam(defaultValue = "") String code, @RequestParam String phase, @RequestParam String title,
			@RequestParam(defaultValue = "") String detail, @RequestParam(defaultValue = "") String whereText,
			@RequestParam(defaultValue = "") String linkUrl, RedirectAttributes flash) {
		try {
			checklist.save(code, phase, title, detail, whereText, linkUrl);
			flash.addFlashAttribute("notice", "저장했어요. 고객 체크리스트에 바로 보여요.");
			return "redirect:/admin/checklist/items?open=" + phase;
		} catch (IllegalArgumentException e) {
			flash.addFlashAttribute("formError", e.getMessage());
			return code.isBlank() ? "redirect:/admin/checklist/items?edit=new&phase=" + phase
					: "redirect:/admin/checklist/items?edit=" + code;
		}
	}

	@PostMapping("/items/{code}/use")
	public String toggleUse(@PathVariable String code, RedirectAttributes flash) {
		ChecklistItem i = checklist.item(code);
		boolean shown = checklist.toggleUse(code);
		flash.addFlashAttribute("notice", shown ? "고객 화면에 다시 보여요." : "고객 화면에서 숨겼어요.");
		return "redirect:/admin/checklist/items" + (i == null ? "" : "?open=" + i.getPhase());
	}

	@PostMapping("/items/{code}/delete")
	public String delete(@PathVariable String code, RedirectAttributes flash) {
		ChecklistItem i = checklist.item(code);
		checklist.deleteItem(code);
		flash.addFlashAttribute("notice", "항목을 지웠어요.");
		return "redirect:/admin/checklist/items" + (i == null ? "" : "?open=" + i.getPhase());
	}
}
