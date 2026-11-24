package com.codemove.customer.web;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.codemove.checklist.ChecklistService;
import com.codemove.move.MoveService;

import jakarta.servlet.http.HttpSession;

/** 이사 준비 체크리스트 (/more/** 라 고객 로그인 필요). 체크는 화면을 다시 그리지 않고 바로 저장 */
@Controller
public class ChecklistController {

	private final ChecklistService checklist;
	private final CustomerViews views;
	private final MoveService moves;

	public ChecklistController(ChecklistService checklist, CustomerViews views, MoveService moves) {
		this.checklist = checklist;
		this.views = views;
		this.moves = moves;
	}

	@GetMapping("/more/checklist")
	public String page(HttpSession session, Model model) {
		long memberId = memberId(session);
		model.addAttribute("cs", views.view(CustomerSession.of(session), moves.current(memberId)));
		model.addAttribute("cl", checklist.view(memberId));
		return "customer/more/checklist";
	}

	/** 체크 / 해제 → 바뀐 상태와 전체 진행 */
	@PostMapping("/more/checklist/toggle")
	@ResponseBody
	public Map<String, Object> toggle(@RequestParam String id, HttpSession session) {
		long memberId = memberId(session);
		boolean done = checklist.toggle(memberId, id);
		ChecklistService.View v = checklist.view(memberId);
		return Map.of("done", done, "doneCount", v.done(), "total", v.total(), "percent", v.percent());
	}

	@PostMapping("/more/checklist/items")
	public String add(@RequestParam String phase, @RequestParam String title, HttpSession session) {
		checklist.add(memberId(session), phase, title);
		return "redirect:/more/checklist#phase-" + phase;
	}

	@PostMapping("/more/checklist/items/{checkId}/delete")
	public String delete(@PathVariable long checkId, @RequestParam(defaultValue = "") String phase, HttpSession session) {
		checklist.delete(memberId(session), checkId);
		return "redirect:/more/checklist" + (phase.matches("0[1-7]") ? "#phase-" + phase : "");
	}

	private static long memberId(HttpSession session) {
		return CustomerSession.of(session).getMemberId();
	}
}
