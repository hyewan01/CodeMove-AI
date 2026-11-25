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
	private final com.codemove.notification.NotificationService notifications;

	public AdminChatController(ChatService chats, com.codemove.notification.NotificationService notifications) {
		this.chats = chats;
		this.notifications = notifications;
	}

	private List<AdminChat.ChatRoom> rooms() {
		return chats.rooms().stream().map(AdminChat.ChatRoom::of).toList();
	}

	/* ---------- 채팅 관리 홈 ---------- */

	@GetMapping("/chat-management")
	public String home(Model model) {
		List<AdminChat.ChatRoom> rooms = rooms();
		// 실시간 상담 현황 = 고객이 상담을 요청하고 아직 연결되지 않은 채팅
		model.addAttribute("waitingCount", rooms.stream().filter(r -> r.waiting()).count());
		model.addAttribute("unreadCount", rooms.stream().mapToInt(r -> r.unread()).sum());
		return "admin/chat-home";
	}

	/**
	 * 하단 챗봇 점 · 종 점 · 상단 알림용.
	 * requests: 연결을 기다리는 상담 요청 (최근 순), latest: 상담 중인 방의 새 고객 메시지. key 가 처음 보는 것이면 화면이 알림을 띄움
	 */
	@GetMapping("/badges")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> badges(org.springframework.web.context.request.WebRequest request) {
		List<ChatRoomLine> rooms = chats.rooms();
		java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
		out.put("chat", rooms.stream().mapToInt(r -> r.getUnread() == null ? 0 : r.getUnread()).sum());
		out.put("bell", notifications.hasUnreadAdmin());
		String base = request.getContextPath() + "/admin/chats/";
		out.put("requests", rooms.stream().filter(r -> "waiting".equals(ChatService.modeOf(r)))
				.sorted(Comparator.comparing((ChatRoomLine r) -> String.valueOf(r.getWaitingAt())).reversed())
				.map(r -> java.util.Map.of("key", "req:" + r.getRoomId() + "@" + r.getWaitingAt(), "roomId", r.getRoomId(),
						"title", name(r) + "님이 상담을 요청하셨습니다",
						"message", AdminChat.preview(r).isBlank() || ChatService.isConsultRequest(AdminChat.preview(r))
								? "누르면 바로 상담을 시작해요" : AdminChat.preview(r),
						"href", base + r.getRoomId(), "start", base + r.getRoomId() + "/start"))
				.toList());
		rooms.stream().filter(r -> "counselor".equals(ChatService.modeOf(r)) && r.getUnread() != null && r.getUnread() > 0
				&& r.getLastCustomerAt() != null)
				.max((x, y) -> x.getLastCustomerAt().compareTo(y.getLastCustomerAt()))
				.ifPresent(r -> out.put("latest", java.util.Map.of(
						"key", r.getRoomId() + "@" + r.getLastCustomerAt(),
						"roomId", r.getRoomId(),
						"title", name(r) + " 고객님 메시지",
						"message", AdminChat.preview(r).isBlank() ? "새 메시지가 왔어요" : AdminChat.preview(r),
						"href", base + r.getRoomId())));
		return out;
	}

	private static String name(ChatRoomLine r) {
		return r.getMemberName() == null ? "고객" : r.getMemberName();
	}

	/** 상담 시작: AI 를 끄고 고객 메시지를 관리자에게만 (상담 요청 알림 · 상담 상태 버튼) */
	@PostMapping("/chats/{id}/start")
	public String startConsult(@PathVariable long id, @RequestParam(defaultValue = "") String from, HttpSession session) {
		chats.startConsult(id, AdminSession.adminId(session));
		return back(id, from);
	}

	/** 상담 종료: 다시 AI 가 답함 (고객에게 안내 메시지) */
	@PostMapping("/chats/{id}/end")
	public String endConsult(@PathVariable long id, @RequestParam(defaultValue = "") String from) {
		chats.endConsult(id);
		return back(id, from);
	}

	private static String back(long id, String from) {
		return "redirect:/admin/chats/" + id + ("management".equals(from) ? "?from=management" : "");
	}

	/** 채팅방을 보는 동안: 새 메시지 · 수정 · 삭제 · 상담 상태가 바뀌었는지(version)와 고객이 아직 안 읽은 관리자 메시지 */
	@GetMapping("/chats/{id}/state")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> state(@PathVariable long id) {
		ChatRoomLine line = chats.room(id);
		List<ChatMessage> messages = line == null ? List.of() : chats.peek(id);
		return java.util.Map.of("version", version(line, messages), "unread",
				messages.stream().filter(m -> "03".equals(m.getSenderType()) && !"Y".equals(m.getIsRead()))
						.map(m -> m.getMessageId()).toList());
	}

	private static String version(ChatRoomLine line, List<ChatMessage> messages) {
		StringBuilder b = new StringBuilder(ChatService.modeOf(line));
		for (ChatMessage m : messages) {
			b.append('|').append(m.getMessageId()).append(m.getEditedAt()).append(m.getDeletedAt());
		}
		return Integer.toHexString(b.toString().hashCode()) + "-" + messages.size();
	}

	/* ---------- 채팅 목록 · 상세 ---------- */

	/** filter: all(관리자와 주고받은 채팅)|waiting|unread|ai(AI 하고만 대화), sort: latest|unread, from=management 면 채팅 관리로 돌아가는 버튼 표시 */
	@GetMapping("/chats")
	public String list(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "latest") String sort,
			@RequestParam(defaultValue = "") String from, Model model) {
		Comparator<AdminChat.ChatRoom> byActivity = Comparator.comparingLong(r -> -r.activity());
		Comparator<AdminChat.ChatRoom> order = "unread".equals(sort)
				? Comparator.<AdminChat.ChatRoom>comparingInt(r -> -r.unread()).thenComparing(byActivity)
				: byActivity;
		List<AdminChat.ChatRoom> rooms = rooms().stream()
				.filter(r -> switch (filter) {
					case "waiting" -> r.waiting();
					case "unread" -> r.unread() > 0;
					case "ai" -> !r.talked();
					default -> r.talked();
				})
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
		notifications.markAdminChatRead("/admin/chats/" + id); // 이 방의 채팅 알림은 읽음
		model.addAttribute("mode", ChatService.modeOf(line));
		model.addAttribute("version", version(line, messages));
		// 답변이 필요한 고객 메시지 중 그 뒤에 관리자 답장이 없는 것 = 붉게
		int lastAdmin = -1;
		for (int i = 0; i < messages.size(); i++) {
			if ("03".equals(messages.get(i).getSenderType())) lastAdmin = i;
		}
		List<AdminChat.Message> views = new java.util.ArrayList<>();
		for (int i = 0; i < messages.size(); i++) {
			ChatMessage m = messages.get(i);
			views.add(AdminChat.Message.of(m, ChatService.toAdmin(m) && "Y".equals(m.getNeedsReply()) && i > lastAdmin));
		}
		model.addAttribute("room", room);
		model.addAttribute("messages", views);
		model.addAttribute("from", from);
		// 화면설계서 SCR-A-007 ①: 뒤로 → 채팅 목록 (채팅 관리에서 왔으면 목록에서도 채팅 관리로 돌아갈 수 있게 from 유지)
		model.addAttribute("backUrl", "management".equals(from) ? "/admin/chats?from=management" : "/admin/chats");
		return "admin/chat-detail";
	}

	/** 답장 (사진 · 동영상 · PDF 첨부 가능) */
	@PostMapping("/chats/{id}/messages")
	public String send(@PathVariable long id, @RequestParam(defaultValue = "") String text,
			@RequestParam(defaultValue = "") String replyTo, @RequestParam(defaultValue = "") String from,
			@RequestParam(required = false) org.springframework.web.multipart.MultipartFile file, HttpSession session,
			org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
		try {
			chats.adminSend(id, text, replyTo, AdminSession.adminId(session), file);
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("chatError", e.getMessage());
		}
		return "redirect:/admin/chats/" + id + (from.isEmpty() ? "" : "?from=" + from);
	}

	/** 관리자 메시지 고치기 (보낸 지 하루 안) */
	@PostMapping("/chats/{id}/messages/{messageId}/edit")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> edit(@PathVariable long id, @PathVariable long messageId,
			@RequestParam(defaultValue = "") String text) {
		return chats.adminEdit(id, messageId, text);
	}

	/** 관리자 메시지 지우기 (보낸 지 하루 안 · 고객이 아직 안 읽음) */
	@PostMapping("/chats/{id}/messages/{messageId}/delete")
	@org.springframework.web.bind.annotation.ResponseBody
	public java.util.Map<String, Object> delete(@PathVariable long id, @PathVariable long messageId) {
		return chats.adminDelete(id, messageId);
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
		model.addAttribute("maxMenus", ChatService.MAX_QUICK_MENUS);
		return "admin/chat-entry";
	}

	/** 미리보기: 고객 채팅 첫 화면을 같은 조각으로 그림 (첫화면 설정의 iframe) */
	@GetMapping("/chat-management/entry/preview")
	public String entryPreview(Model model) {
		model.addAttribute("greeting", chats.settings().getGreeting());
		model.addAttribute("topics", chats.quickMenuTitles());
		model.addAttribute("hours", chats.consultHours());
		return "customer/chat/preview";
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
