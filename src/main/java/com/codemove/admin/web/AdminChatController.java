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

import com.codemove.chat.ChatMessage;
import com.codemove.chat.ChatRoomLine;
import com.codemove.chat.ChatService;

import jakarta.servlet.http.HttpSession;

/**
 * 채팅 관리 (SCR-A-005) · 채팅 목록 (006) · 채팅 상세 (007) · 채팅 운영 설정 (008)
 * · 첫 화면/퀵메뉴 설정 (009) · 답변 설정 관리 (010)
 */
@Controller
@RequestMapping("/admin")
public class AdminChatController {

	private final ChatService chats;

	public AdminChatController(ChatService chats) {
		this.chats = chats;
	}

	private List<AdminChat.ChatRoom> rooms() {
		return chats.rooms().stream().map(AdminChat.ChatRoom::of).toList();
	}

	/* ---------- 채팅 관리 홈 ---------- */

	@GetMapping("/chat-management")
	public String home(Model model) {
		List<AdminChat.ChatRoom> rooms = rooms();
		model.addAttribute("waitingCount", rooms.stream().filter(AdminChat.ChatRoom::waiting).count());
		model.addAttribute("unreadCount", rooms.stream().mapToInt(AdminChat.ChatRoom::unread).sum());
		return "admin/chat-home";
	}

	/* ---------- 채팅 목록 · 상세 ---------- */

	/** filter: all|waiting|unread, sort: latest|unread, from=management 면 채팅 관리로 돌아가는 버튼 표시 */
	@GetMapping("/chats")
	public String list(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "") String from, Model model) {
		Comparator<AdminChat.ChatRoom> byActivity = Comparator.comparingLong(r -> -r.activity());
		Comparator<AdminChat.ChatRoom> order = "unread".equals(sort)
				? Comparator.<AdminChat.ChatRoom>comparingInt(r -> -r.unread()).thenComparing(byActivity)
				: byActivity;
		List<AdminChat.ChatRoom> rooms = rooms().stream()
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
	public String detail(@PathVariable long id, @RequestParam(defaultValue = "") String from, Model model) {
		ChatRoomLine line = chats.room(id);
		if (line == null) {
			return "redirect:/admin/chats";
		}
		AdminChat.ChatRoom room = AdminChat.ChatRoom.of(line);
		List<ChatMessage> messages = chats.openRoom(id);
		long lastCustomer = messages.stream().filter(m -> "01".equals(m.getSenderType()))
				.mapToLong(ChatMessage::getMessageId).max().orElse(-1);
		model.addAttribute("room", room);
		model.addAttribute("messages", messages.stream()
				.map(m -> AdminChat.Message.of(m, room.waiting() && m.getMessageId() == lastCustomer)).toList());
		model.addAttribute("from", from);
		// 화면설계서 SCR-A-007 ①: 뒤로 → 채팅 목록 (채팅 관리에서 왔으면 목록에서도 채팅 관리로 돌아갈 수 있게 from 유지)
		model.addAttribute("backUrl", "management".equals(from) ? "/admin/chats?from=management" : "/admin/chats");
		return "admin/chat-detail";
	}

	@PostMapping("/chats/{id}/messages")
	public String send(@PathVariable long id, @RequestParam String text, @RequestParam(defaultValue = "") String replyTo,
			@RequestParam(defaultValue = "") String from, HttpSession session) {
		chats.adminSend(id, text, replyTo, AdminSession.adminId(session));
		return "redirect:/admin/chats/" + id + (from.isEmpty() ? "" : "?from=" + from);
	}

	/* ---------- 채팅 운영 설정 ---------- */

	@GetMapping("/chat-management/operation")
	public String operation(Model model) {
		model.addAttribute("settings", AdminChat.Settings.of(chats.settings()));
		return "admin/chat-operation";
	}

	/** 바꾼 항목만 전달 (토글·시간·대응 규칙) */
	@PostMapping("/chat-management/operation")
	public String saveOperation(@RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) Boolean operatingHoursEnabled, @RequestParam(required = false) String startTime,
			@RequestParam(required = false) String endTime, @RequestParam(required = false) String fallbackMessage,
			HttpSession session) {
		chats.updateSettings(enabled, operatingHoursEnabled, startTime, endTime, fallbackMessage, null,
				AdminSession.adminId(session));
		return "redirect:/admin/chat-management/operation";
	}

	/* ---------- 첫 화면 / 퀵메뉴 설정 ---------- */

	@GetMapping("/chat-management/entry")
	public String entry(Model model) {
		model.addAttribute("greeting", chats.settings().getGreeting());
		model.addAttribute("quickMenus", chats.quickMenus().stream()
				.map(q -> new AdminChat.QuickMenu(String.valueOf(q.getMenuId()), q.getMenuName(), q.getIntro())).toList());
		model.addAttribute("candidates", chats.topics().stream()
				.map(t -> new AdminChat.QuickMenu("topic-" + t.getTopicId(), t.getTitle(), t.getIntro())).toList());
		model.addAttribute("usedTopics", chats.quickMenus().stream().map(q -> "topic-" + q.getTopicId()).toList());
		model.addAttribute("settings", AdminChat.Settings.of(chats.settings()));
		return "admin/chat-entry";
	}

	@PostMapping("/chat-management/entry/greeting")
	public String saveGreeting(@RequestParam String greeting, HttpSession session) {
		chats.updateSettings(null, null, null, null, null, greeting, AdminSession.adminId(session));
		return "redirect:/admin/chat-management/entry";
	}

	/** topicId: topic-주제번호 */
	@PostMapping("/chat-management/entry/menus")
	public String addMenu(@RequestParam String topicId, HttpSession session) {
		String id = topicId.replace("topic-", "");
		if (id.matches("\\d+")) {
			chats.addQuickMenu(Long.parseLong(id), AdminSession.adminId(session));
		}
		return "redirect:/admin/chat-management/entry";
	}

	@PostMapping("/chat-management/entry/menus/delete")
	public String deleteMenu(@RequestParam String menuId) {
		if (menuId.matches("\\d+")) {
			chats.deleteQuickMenu(Long.parseLong(menuId));
		}
		return "redirect:/admin/chat-management/entry";
	}

	/* ---------- 답변 설정 관리 ---------- */

	/** edit: 주제 ID 또는 new (편집 화면 열기) */
	@GetMapping("/chat-management/answers")
	public String answers(@RequestParam(required = false) String edit, @RequestParam(required = false) Long open,
			Model model) {
		List<AdminChat.Topic> topics = chats.topics().stream().map(AdminChat.Topic::of).toList();
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
		if (form.getTitle() == null || form.getTitle().isBlank() || form.getIntroduction() == null
				|| form.getIntroduction().isBlank()) {
			return "redirect:/admin/chat-management/answers";
		}
		long id = chats.saveTopic(form.getId(), form.getTitle(), form.getIntroduction(),
				form.getLorebooks().stream().map(l -> new String[] { l.getKeywords(), l.getContent() }).toList(),
				AdminSession.adminId(session));
		return "redirect:/admin/chat-management/answers?open=" + id;
	}

	@PostMapping("/chat-management/answers/{id}/delete")
	public String deleteTopic(@PathVariable long id) {
		chats.deleteTopic(id);
		return "redirect:/admin/chat-management/answers";
	}
}
