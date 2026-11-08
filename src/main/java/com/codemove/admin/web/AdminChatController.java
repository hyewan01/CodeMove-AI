package com.codemove.admin.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * 채팅 관리 (SCR-A-005) · 채팅 목록 (006) · 채팅 상세 (007) · 채팅 운영 설정 (008)
 * · 첫 화면/퀵메뉴 설정 (009) · 답변 설정 관리 (010)
 */
@Controller
@RequestMapping("/admin")
public class AdminChatController {

	/* ---------- 채팅 관리 홈 ---------- */

	@GetMapping("/chat-management")
	public String home(Model model) {
		model.addAttribute("waitingCount", AdminChat.ROOMS.stream().filter(r -> r.waiting()).count());
		model.addAttribute("unreadCount", AdminChat.ROOMS.stream().mapToInt(r -> r.unread()).sum());
		return "admin/chat-home";
	}

	/* ---------- 채팅 목록 · 상세 ---------- */

	/** filter: all|waiting|unread, sort: latest|unread, from=management 면 채팅 관리로 돌아가는 버튼 표시 */
	@GetMapping("/chats")
	public String list(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "") String from, Model model) {
		Comparator<AdminChat.ChatRoom> byActivity = Comparator.comparingInt(r -> -r.activity());
		Comparator<AdminChat.ChatRoom> order = "unread".equals(sort)
				? Comparator.<AdminChat.ChatRoom>comparingInt(r -> -r.unread()).thenComparing(byActivity)
				: byActivity;
		List<AdminChat.ChatRoom> rooms = AdminChat.ROOMS.stream()
				.filter(r -> "waiting".equals(filter) ? r.waiting() : !"unread".equals(filter) || r.unread() > 0)
				.sorted(order)
				.toList();
		model.addAttribute("rooms", rooms);
		model.addAttribute("filter", filter);
		model.addAttribute("sort", sort);
		model.addAttribute("from", from);
		return "admin/chat-list";
	}

	@GetMapping("/chats/{id}")
	public String detail(@PathVariable String id, @RequestParam(defaultValue = "") String from, HttpSession session,
			Model model) {
		AdminChat.ChatRoom room = AdminChat.room(id);
		model.addAttribute("room", room);
		model.addAttribute("messages", AdminState.of(session).messagesOf(room.id()));
		model.addAttribute("from", from);
		model.addAttribute("backUrl", "management".equals(from) ? "/admin/chat-management" : "/admin/chats");
		return "admin/chat-detail";
	}

	@PostMapping("/chats/{id}/messages")
	public String send(@PathVariable String id, @RequestParam String text, @RequestParam(defaultValue = "") String replyTo,
			@RequestParam(defaultValue = "") String from, HttpSession session) {
		if (!text.isBlank()) {
			AdminChat.ChatRoom room = AdminChat.room(id);
			AdminState.of(session).messagesOf(room.id()).add(new AdminChat.Message(System.currentTimeMillis(), "admin",
					text.trim(), false, replyTo.isBlank() ? null : replyTo));
		}
		return "redirect:/admin/chats/" + id + (from.isEmpty() ? "" : "?from=" + from);
	}

	/* ---------- 채팅 운영 설정 ---------- */

	@GetMapping("/chat-management/operation")
	public String operation(HttpSession session, Model model) {
		model.addAttribute("settings", AdminState.of(session).getChatSettings());
		return "admin/chat-operation";
	}

	/** 바꾼 항목만 전달 (토글·시간·대응 규칙) */
	@PostMapping("/chat-management/operation")
	public String saveOperation(@RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) Boolean operatingHoursEnabled, @RequestParam(required = false) String startTime,
			@RequestParam(required = false) String endTime, @RequestParam(required = false) String fallbackMessage,
			HttpSession session) {
		AdminChat.Settings s = AdminState.of(session).getChatSettings();
		if (enabled != null) s.setEnabled(enabled);
		if (operatingHoursEnabled != null) s.setOperatingHoursEnabled(operatingHoursEnabled);
		if (startTime != null && startTime.matches("\\d{2}:\\d{2}")) s.setStartTime(startTime);
		if (endTime != null && endTime.matches("\\d{2}:\\d{2}")) s.setEndTime(endTime);
		if (fallbackMessage != null && !fallbackMessage.isBlank()) s.setFallbackMessage(fallbackMessage.trim());
		return "redirect:/admin/chat-management/operation";
	}

	/* ---------- 첫 화면 / 퀵메뉴 설정 ---------- */

	@GetMapping("/chat-management/entry")
	public String entry(HttpSession session, Model model) {
		AdminState state = AdminState.of(session);
		model.addAttribute("greeting", state.getChatGreeting());
		model.addAttribute("quickMenus", state.getQuickMenus());
		model.addAttribute("candidates", state.getAnswerTopics().stream()
				.map(t -> new AdminChat.QuickMenu("topic-" + t.getId(), t.getTitle(), t.getIntroduction())).toList());
		model.addAttribute("settings", state.getChatSettings());
		return "admin/chat-entry";
	}

	@PostMapping("/chat-management/entry/greeting")
	public String saveGreeting(@RequestParam String greeting, HttpSession session) {
		if (!greeting.isBlank()) {
			AdminState.of(session).setChatGreeting(greeting.trim());
		}
		return "redirect:/admin/chat-management/entry";
	}

	@PostMapping("/chat-management/entry/menus")
	public String addMenu(@RequestParam String topicId, HttpSession session) {
		AdminState state = AdminState.of(session);
		List<AdminChat.QuickMenu> menus = state.getQuickMenus();
		if (menus.size() < 4 && menus.stream().noneMatch(m -> m.getId().equals(topicId))) {
			state.getAnswerTopics().stream().filter(t -> ("topic-" + t.getId()).equals(topicId)).findFirst()
					.ifPresent(t -> menus.add(new AdminChat.QuickMenu(topicId, t.getTitle(), t.getIntroduction())));
		}
		return "redirect:/admin/chat-management/entry";
	}

	@PostMapping("/chat-management/entry/menus/delete")
	public String deleteMenu(@RequestParam String menuId, HttpSession session) {
		AdminState.of(session).getQuickMenus().removeIf(m -> m.getId().equals(menuId));
		return "redirect:/admin/chat-management/entry";
	}

	/* ---------- 답변 설정 관리 ---------- */

	/** edit: 주제 ID 또는 new (편집 화면 열기) */
	@GetMapping("/chat-management/answers")
	public String answers(@RequestParam(required = false) String edit, @RequestParam(required = false) Long open,
			HttpSession session, Model model) {
		List<AdminChat.Topic> topics = AdminState.of(session).getAnswerTopics();
		model.addAttribute("topics", topics);
		model.addAttribute("expandedId", open != null ? open : (topics.isEmpty() ? null : topics.get(0).getId()));
		if ("new".equals(edit)) {
			AdminChat.Topic draft = new AdminChat.Topic();
			draft.getLorebooks().add(new AdminChat.Lorebook());
			model.addAttribute("draft", draft);
			model.addAttribute("isNew", true);
		} else if (edit != null) {
			topics.stream().filter(t -> String.valueOf(t.getId()).equals(edit)).findFirst().ifPresent(t -> {
				model.addAttribute("draft", t);
				model.addAttribute("isNew", false);
			});
		}
		return "admin/chat-answers";
	}

	@PostMapping("/chat-management/answers")
	public String saveTopic(@ModelAttribute AdminChat.Topic form, HttpSession session) {
		if (form.getTitle().isBlank() || form.getIntroduction().isBlank()) {
			return "redirect:/admin/chat-management/answers";
		}
		List<AdminChat.Topic> topics = AdminState.of(session).getAnswerTopics();
		form.setTitle(form.getTitle().trim());
		form.setIntroduction(form.getIntroduction().trim());
		long now = System.currentTimeMillis();
		for (int i = 0; i < form.getLorebooks().size(); i++) {
			AdminChat.Lorebook lb = form.getLorebooks().get(i);
			if (lb.getId() == 0) lb.setId(now + i);
			if (lb.getKeywords() == null) lb.setKeywords("");
			if (lb.getContent() == null) lb.setContent("");
		}
		if (form.getId() == 0) {
			form.setId(now);
			topics.add(0, form);
		} else {
			topics.replaceAll(t -> t.getId() == form.getId() ? form : t);
		}
		return "redirect:/admin/chat-management/answers?open=" + form.getId();
	}

	@PostMapping("/chat-management/answers/{id}/delete")
	public String deleteTopic(@PathVariable long id, HttpSession session) {
		AdminState.of(session).getAnswerTopics().removeIf(t -> t.getId() == id);
		return "redirect:/admin/chat-management/answers";
	}
}
