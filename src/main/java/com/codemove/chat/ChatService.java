package com.codemove.chat;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.FileStore;
import com.codemove.member.Member;
import com.codemove.member.MemberMapper;
import com.codemove.notification.NotificationService;

/**
 * 상담 채팅. 고객 메시지는 챗봇이 로어북 키워드로 답하고, 맞는 로어북이 없으면 채팅 운영 설정의 대응 규칙을 보내며
 * '답변 필요'(05)로 관리자 채팅 목록에 올립니다. "상담사 연결"이면 '대기 중'(02). 대기 중이어도 고객은 계속 질문할 수 있고
 * 챗봇도 계속 답합니다. 관리자가 답장하면 상담 중(03)이 되고 그때부터 챗봇은 답하지 않습니다.
 * CHAT_STATUS 01 챗봇 응대 / 02 대기 중(상담사 요청) / 03 상담 중 / 04 종료 / 05 답변 필요(챗봇 미응답)
 */
@Service
public class ChatService {

	public static final String BOT = "01", WAITING = "02", CONSULTING = "03", UNANSWERED = "05";
	/** 고객 첫 화면 퀵메뉴: 관리자가 등록하는 5개 + 마지막 '상담사 연결' = 6개 */
	public static final int MAX_QUICK_MENUS = 5;
	public static final String CONSULT_MENU = "상담사 연결";
	private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
	/** 관리자 답장 인용 (밀어서 답장): 내용 첫 줄에 붙여 저장 */
	private static final String REPLY_MARK = "↪ ";

	private final ChatMapper chats;
	private final MemberMapper members;
	private final NotificationService notifications;
	private final FileStore files;

	public ChatService(ChatMapper chats, MemberMapper members, NotificationService notifications,
			FileStore files) {
		this.chats = chats;
		this.members = members;
		this.notifications = notifications;
		this.files = files;
	}

	/* ---------- 설정 · 주제 · 퀵메뉴 ---------- */

	@Transactional
	public ChatSetting settings() {
		ChatSetting s = chats.findSetting();
		if (s == null) {
			chats.insertSetting();
			s = chats.findSetting();
		}
		return s;
	}

	/** 바꾼 항목만 (null 은 그대로) */
	@Transactional
	public void updateSettings(Boolean botEnabled, Boolean consultEnabled, String start, String end, String delay,
			String greeting, Long adminId) {
		ChatSetting s = settings();
		if (botEnabled != null) s.setBotEnabled(botEnabled ? "Y" : "N");
		if (consultEnabled != null) s.setConsultEnabled(consultEnabled ? "Y" : "N");
		if (start != null && start.matches("\\d{2}:\\d{2}")) s.setStartTime(start);
		if (end != null && end.matches("\\d{2}:\\d{2}")) s.setEndTime(end);
		if (delay != null && !delay.isBlank()) s.setDelayMessage(cut(delay.trim(), 500));
		if (greeting != null && !greeting.isBlank()) s.setGreeting(cut(greeting.trim(), 500));
		chats.updateSetting(s, adminId);
	}

	/** 주제 + 로어북 (최근 수정 순) */
	public List<Topic> topics() {
		List<Topic> topics = chats.listTopics();
		List<Lorebook> books = chats.listLorebooks();
		for (Topic t : topics) {
			t.setLorebooks(new ArrayList<>(books.stream().filter(b -> b.getTopicId().equals(t.getTopicId())).toList()));
		}
		return topics;
	}

	/** 주제 저장 (topicId 없으면 새로), 로어북은 다시 넣음 @return 주제 번호 */
	@Transactional
	public long saveTopic(Long topicId, String title, String intro, List<String[]> lorebooks, Long adminId) {
		Topic t = new Topic();
		t.setTitle(cut(title.trim(), 50));
		t.setIntro(cut(intro.trim(), 200));
		if (topicId == null || topicId == 0) {
			chats.insertTopic(t);
		} else {
			t.setTopicId(topicId);
			chats.updateTopic(t);
			chats.renameQuickMenus(topicId, t.getTitle());
			chats.deleteLorebooks(topicId);
		}
		int order = 0;
		for (String[] kv : lorebooks) {
			if ((kv[0] == null || kv[0].isBlank()) && (kv[1] == null || kv[1].isBlank())) {
				continue;
			}
			Lorebook l = new Lorebook();
			l.setTopicId(t.getTopicId());
			l.setKeywords(cut(kv[0] == null ? "" : kv[0].trim(), 200));
			l.setContent(cut(kv[1] == null ? "" : kv[1].trim(), 1000));
			if (l.getKeywords().isEmpty()) l.setKeywords("#" + t.getTitle());
			if (l.getContent().isEmpty()) l.setContent(t.getIntro());
			l.setSortOrder(++order);
			chats.insertLorebook(l);
		}
		return t.getTopicId();
	}

	@Transactional
	public void deleteTopic(long topicId) {
		chats.deleteQuickMenusByTopic(topicId);
		chats.deleteLorebooks(topicId);
		chats.deleteActions(topicId);
		chats.clearMessageTopic(topicId);
		chats.deleteTopic(topicId);
	}

	public List<QuickMenu> quickMenus() {
		return chats.listQuickMenus();
	}

	/** 고객 첫 화면 버튼 순서 (관리자 미리보기도 같은 목록) */
	public List<String> quickMenuTitles() {
		List<String> titles = new ArrayList<>(quickMenus().stream().limit(MAX_QUICK_MENUS).map(m -> m.getMenuName()).toList());
		titles.add(CONSULT_MENU);
		return titles;
	}

	/** 상담 운영시간 안내 (운영시간 사용을 끄면 null) */
	public String consultHours() {
		ChatSetting s = settings();
		return "Y".equals(s.getConsultEnabled()) ? s.getStartTime() + "–" + s.getEndTime() : null;
	}

	/** 퀵메뉴는 최대 5개(+ 고정 '상담사 연결'), 같은 주제는 한 번만 */
	@Transactional
	public void addQuickMenu(long topicId, Long adminId) {
		List<QuickMenu> menus = quickMenus();
		if (menus.size() >= MAX_QUICK_MENUS || menus.stream().anyMatch(m -> m.getTopicId() == topicId)) {
			return;
		}
		topics().stream().filter(t -> t.getTopicId() == topicId).findFirst().ifPresent(t -> chats.insertQuickMenu(topicId,
				t.getTitle(), menus.stream().mapToInt(m -> m.getSortOrder()).max().orElse(0) + 1, adminId));
	}

	@Transactional
	public void deleteQuickMenu(long menuId) {
		chats.deleteQuickMenu(menuId);
	}

	/* ---------- 관리자 채팅 ---------- */

	public List<ChatRoomLine> rooms() {
		return chats.listRooms();
	}

	public ChatRoomLine room(long roomId) {
		return chats.findRoom(roomId);
	}

	/** 방을 열면 고객 메시지를 읽음으로 */
	@Transactional
	public List<ChatMessage> openRoom(long roomId) {
		chats.markCustomerRead(roomId);
		return chats.listMessages(roomId, 0);
	}

	/** 관리자 답장 → 상담 중 (챗봇 미응답이었으면 챗봇 응대로 복귀), 고객에게 알림 */
	public void adminSend(long roomId, String text, String replyTo, Long adminId) {
		adminSend(roomId, text, replyTo, adminId, null);
	}

	/** @param file 사진 · 영상 · PDF (없으면 null). 고객이 상담 화면을 보면 읽음이 됨 (그 전까지만 삭제 가능) */
	@Transactional
	public void adminSend(long roomId, String text, String replyTo, Long adminId, MultipartFile file) {
		ChatRoomLine room = chats.findRoom(roomId);
		boolean hasFile = file != null && !file.isEmpty();
		if (room == null || (text == null || text.isBlank()) && !hasFile) {
			return;
		}
		if (hasFile && fileProblem(file) != null) {
			throw new IllegalArgumentException(fileProblem(file));
		}
		ChatMessage m = new ChatMessage();
		m.setRoomId(roomId);
		m.setAdminId(adminId);
		m.setSenderType("03");
		m.setMessageType(hasFile ? "02" : "01");
		String body = text == null ? "" : text.trim();
		if (!body.isEmpty() && replyTo != null && !replyTo.isBlank()) {
			body = REPLY_MARK + cut(replyTo.replace("\n", " ").trim(), 80) + "\n" + body;
		}
		m.setContent(body.isEmpty() ? null : cut(body, 1000));
		if (hasFile && file != null) {
			String original = file.getOriginalFilename();
			m.setAttachmentPath(files.save("chat", file));
			m.setAttachmentName(cut(original == null ? "첨부파일" : original, 100));
		}
		m.setIsRead("N");
		chats.insertMessage(m);
		chats.markCustomerRead(roomId);
		// 관리자가 한 번이라도 말하면 상담 중: 고객 답장은 관리자에게 가고 AI 는 끼어들지 않음
		// (상담 종료 버튼 또는 관리자 마지막 메시지 뒤 24시간이 지나면 다시 AI)
		chats.updateRoomStatus(roomId, CONSULTING, adminId);
		notifications.markAdminChatRead("/admin/chats/" + roomId);
		String preview = text == null || text.isBlank()
				? (isVideo(m.getAttachmentName()) ? "동영상을 보냈어요." : isImage(m.getAttachmentName()) ? "사진을 보냈어요." : "파일을 보냈어요.")
				: cut(text.trim(), 100);
		notifications.toMember(room.getMemberId(), "15", "상담 답변이 도착했어요", preview, "/chat");
	}

	/* ---------- 메시지 수정 · 삭제 · 첨부 ---------- */

	/** 보낸 지 이 시간 안에만 고치거나 지울 수 있음 */
	static final java.time.Duration CHANGE_WINDOW = java.time.Duration.ofHours(24);
	/** 채팅 첨부 한 개 최대 크기 */
	static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

	/** 고칠 수 없는 이유, 고칠 수 있으면 null */
	static String editBlock(ChatMessage m, java.time.LocalDateTime now) {
		if (m.getDeletedAt() != null) return "삭제한 메시지예요.";
		if (m.getContent() == null || splitReply(m.getContent())[1].isBlank()) return "글이 없는 메시지는 고칠 수 없어요.";
		if (m.getSentAt().plus(CHANGE_WINDOW).isBefore(now)) return "보낸 지 하루가 지나 고칠 수 없어요.";
		return null;
	}

	/** 지울 수 없는 이유, 지울 수 있으면 null */
	static String deleteBlock(ChatMessage m, java.time.LocalDateTime now) {
		if (m.getDeletedAt() != null) return "이미 삭제한 메시지예요.";
		if (m.getSentAt().plus(CHANGE_WINDOW).isBefore(now)) return "보낸 지 하루가 지나 삭제할 수 없어요.";
		if ("Y".equals(m.getIsRead())) return "상대방이 이미 읽어서 삭제할 수 없어요.";
		return null;
	}

	/** 관리자 화면용 (다른 패키지) */
	public static String editBlockOf(ChatMessage m, java.time.LocalDateTime now) {
		return editBlock(m, now);
	}

	public static String deleteBlockOf(ChatMessage m, java.time.LocalDateTime now) {
		return deleteBlock(m, now);
	}

	/** 사진 · 영상 · PDF 만, 100MB 까지. 문제없으면 null */
	public static String fileProblem(MultipartFile file) {
		String type = file.getContentType() == null ? "" : file.getContentType();
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		boolean ok = type.startsWith("image/") || type.startsWith("video/") || "application/pdf".equals(type)
				|| isImage(name) || isVideo(name) || name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
		if (!ok) return "사진 · 동영상 · PDF 파일만 보낼 수 있어요.";
		if (file.getSize() > MAX_FILE_BYTES) return "100MB 보다 작은 파일만 보낼 수 있어요.";
		return null;
	}

	static boolean isImage(String name) {
		return name != null && name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpe?g|png|gif|webp|heic|bmp)$");
	}

	static boolean isVideo(String name) {
		return name != null && name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|mov|webm|m4v|avi)$");
	}

	/** image · video · file · "" (첨부 없음) */
	public static String fileKind(ChatMessage m) {
		if (m.getAttachmentPath() == null) return "";
		return isImage(m.getAttachmentName()) ? "image" : isVideo(m.getAttachmentName()) ? "video" : "file";
	}

	/** 결과 { ok, message } */
	private static Map<String, Object> result(boolean ok, String message) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("ok", ok);
		r.put("message", message);
		return r;
	}

	/** 고객이 자기 메시지 고치기 */
	@Transactional
	public Map<String, Object> customerEdit(long memberId, long messageId, String text) {
		ChatMessage m = chats.findMessage(messageId);
		if (m == null) {
			return result(false, "고칠 수 없는 메시지예요.");
		}
		ChatRoomLine room = chats.findRoom(m.getRoomId());
		if (room == null || room.getMemberId() != memberId || !"01".equals(m.getSenderType())) {
			return result(false, "고칠 수 없는 메시지예요.");
		}
		return edit(m, text);
	}

	@Transactional
	public Map<String, Object> customerDelete(long memberId, long messageId) {
		ChatMessage m = chats.findMessage(messageId);
		if (m == null) {
			return result(false, "삭제할 수 없는 메시지예요.");
		}
		ChatRoomLine room = chats.findRoom(m.getRoomId());
		if (room == null || room.getMemberId() != memberId || !"01".equals(m.getSenderType())) {
			return result(false, "삭제할 수 없는 메시지예요.");
		}
		return delete(m);
	}

	/** 관리자가 관리자 메시지 고치기 */
	@Transactional
	public Map<String, Object> adminEdit(long roomId, long messageId, String text) {
		ChatMessage m = chats.findMessage(messageId);
		if (m == null || m.getRoomId() != roomId || !"03".equals(m.getSenderType())) {
			return result(false, "고칠 수 없는 메시지예요.");
		}
		return edit(m, text);
	}

	@Transactional
	public Map<String, Object> adminDelete(long roomId, long messageId) {
		ChatMessage m = chats.findMessage(messageId);
		if (m == null || m.getRoomId() != roomId || !"03".equals(m.getSenderType())) {
			return result(false, "삭제할 수 없는 메시지예요.");
		}
		return delete(m);
	}

	private Map<String, Object> edit(ChatMessage m, String text) {
		String block = editBlock(m, java.time.LocalDateTime.now());
		String t = text == null ? "" : text.trim();
		if (block != null) return result(false, block);
		if (t.isEmpty()) return result(false, "내용을 입력해 주세요.");
		// 답장 인용은 그대로 두고 본문만 바꿈
		String[] parts = splitReply(m.getContent());
		String body = parts[0] == null ? t : REPLY_MARK + parts[0] + "\n" + t;
		chats.editMessage(m.getMessageId(), cut(body, 1000));
		return result(true, "메시지를 고쳤어요.");
	}

	private Map<String, Object> delete(ChatMessage m) {
		String block = deleteBlock(m, java.time.LocalDateTime.now());
		if (block != null) return result(false, block);
		return chats.deleteMessage(m.getMessageId()) > 0 ? result(true, "메시지를 삭제했어요.")
				: result(false, "상대방이 이미 읽어서 삭제할 수 없어요.");
	}

	/** 저장된 내용 → [인용, 본문] */
	public static String[] splitReply(String content) {
		if (content != null && content.startsWith(REPLY_MARK) && content.contains("\n")) {
			int nl = content.indexOf('\n');
			return new String[] { content.substring(REPLY_MARK.length(), nl), content.substring(nl + 1) };
		}
		return new String[] { null, content == null ? "" : content };
	}

	/* ---------- 고객 채팅 ---------- */

	private Map<String, Object> view(ChatMessage m) {
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		boolean mine = "01".equals(m.getSenderType());
		boolean deleted = m.getDeletedAt() != null;
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", m.getMessageId());
		v.put("role", switch (m.getSenderType()) { case "01" -> "user"; case "03" -> "admin"; default -> "bot"; });
		v.put("text", deleted ? "" : splitReply(m.getContent())[1]);
		v.put("file", deleted ? null : m.getAttachmentName());
		v.put("fileUrl", deleted ? null : m.getAttachmentPath());
		v.put("fileKind", deleted ? "" : fileKind(m));
		v.put("time", m.getSentAt().format(HM));
		v.put("edited", m.getEditedAt() != null);
		v.put("deleted", deleted);
		v.put("mine", mine);
		// 고객 자기 메시지: 고치기 · 지우기가 안 되면 그 이유 (되면 null)
		v.put("editBlock", mine ? editBlock(m, now) : "내 메시지만 고칠 수 있어요.");
		v.put("deleteBlock", mine ? deleteBlock(m, now) : "내 메시지만 삭제할 수 있어요.");
		return v;
	}

	/** 진행 중인 상담 내용 (보는 순간 상담사 메시지는 읽음) */
	@Transactional
	public List<Map<String, Object>> customerThread(long memberId) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		if (room == null) return List.of();
		chats.markAdminRead(room.getRoomId());
		return chats.listMessages(room.getRoomId(), 0).stream().map(this::view).toList();
	}

	/** 상담사 새 답장(after 이후)과 모든 메시지의 지금 상태 (수정 · 삭제 · 읽음 반영) */
	@Transactional
	public Map<String, Object> customerUpdates(long memberId, long after) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		Map<String, Object> out = new LinkedHashMap<>();
		if (room == null) {
			out.put("messages", List.of());
			out.put("states", List.of());
			return out;
		}
		chats.markAdminRead(room.getRoomId());
		List<Map<String, Object>> all = chats.listMessages(room.getRoomId(), 0).stream().map(this::view).toList();
		// 번호 순서를 믿지 않고(예시 데이터 번호가 더 큼) 고객이 아닌 메시지를 모두 보냄. 화면이 이미 그린 번호는 건너뜀
		out.put("messages", all.stream().filter(v -> !"user".equals(v.get("role"))).toList());
		out.put("states", all);
		out.put("mode", modeOf(room));
		return out;
	}

	public Map<String, Object> customerAsk(long memberId, String text, MultipartFile file) {
		return customerAsk(memberId, text, file, null);
	}

	/**
	 * 고객 메시지 저장 → 받는 쪽에 따라 처리. 고객이 입력창 위에서 [AI 도우미 | 상담사]를 고름.
	 * <ul>
	 * <li>to=counselor: AI 는 답하지 않음. 상담이 안 열려 있으면 대기 중(02)으로 열고 "전달했어요" 안내 + 관리자 알림,
	 * 이미 상담 중이면 관리자 알림만 (방마다 안 읽은 알림 한 줄)</li>
	 * <li>to=ai: 상담 중이어도 AI 가 답함 (로어북 → 인사 → 거절 → "상담사 연결해 드릴까요?")</li>
	 * <li>"상담사 연결" 이라고 보내면 어느 쪽이든 상담 요청</li>
	 * <li>to 가 없으면(예전 화면) 방 상태로 정함</li>
	 * </ul>
	 * @return { id: 고객 메시지 번호, reply: { id, text, actions } | null, lastId, mode }
	 */
	@Transactional
	public Map<String, Object> customerAsk(long memberId, String text, MultipartFile file, String to) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		if (room == null) {
			room = new ChatRoomLine();
			chats.insertRoom(memberId, room);
			room.setStatus(BOT);
		}
		long roomId = room.getRoomId();
		if (file != null && !file.isEmpty() && fileProblem(file) != null) {
			Map<String, Object> bad = new LinkedHashMap<>();
			bad.put("error", fileProblem(file));
			return bad;
		}
		String value = text == null ? "" : text.trim();
		ChatMessage m = new ChatMessage();
		m.setRoomId(roomId);
		m.setSenderType("01");
		m.setMessageType(file != null && !file.isEmpty() ? "02" : "01");
		m.setContent(value.isEmpty() ? null : cut(value, 1000));
		if (file != null && !file.isEmpty()) {
			m.setAttachmentPath(files.save("chat", file));
			m.setAttachmentName(cut(file.getOriginalFilename() == null ? "첨부파일" : file.getOriginalFilename(), 100));
		}

		Member member = members.findMember(memberId);
		String name = member.getMemberName();
		ChatSetting s = settings();
		Reply reply;
		boolean consulting = CONSULTING.equals(room.getStatus());
		boolean open = consulting || WAITING.equals(room.getStatus());
		boolean toCounselor = to == null || to.isBlank() ? open : "counselor".equals(to);
		if (isConsultRequest(value)) {
			m.setNeedsReply("Y");
			if (!consulting) {
				chats.updateRoomStatus(roomId, WAITING, null);
				notifications.toAdmin("13", "채팅 대기", name + " 고객님이 상담사 연결을 요청했습니다. 응답이 필요합니다.",
						"/admin/chats/" + roomId, true);
			}
			String hours = consultHours();
			reply = new Reply("상담사 연결 요청을 접수했어요." + (hours == null ? "" : "\n상담 운영시간(" + hours + ")에 순서대로 답변드려요.")
					+ "\n기다리시는 동안에도 궁금한 내용을 편하게 물어보세요.", List.of());
		} else if (toCounselor) {
			// 상담사에게: AI 는 조용히. 상담이 안 열려 있으면 열고 한 번 안내, 관리자 알림 (안 읽은 알림이 있으면 새로 안 만듦)
			m.setNeedsReply("Y");
			String preview = value.isEmpty() ? (m.getAttachmentName() == null ? "새 메시지" : "파일: " + m.getAttachmentName()) : cut(value, 80);
			if (!open) {
				chats.updateRoomStatus(roomId, WAITING, null);
				notifications.toAdmin("13", "채팅 대기", name + " 고객님이 상담사에게 메시지를 보냈습니다: " + preview,
						"/admin/chats/" + roomId, true);
				String hours = consultHours();
				reply = new Reply("상담사에게 전달했어요."
						+ (hours != null && outOfHours() ? "\n지금은 상담 시간이 아니라서 운영시간(" + hours + ")에 순서대로 답변드려요."
								: "\n확인하는 대로 이 채팅으로 답변드릴게요."), List.of());
			} else {
				reply = null;
				notifications.toAdminOnce("18", "고객 메시지", name + " 고객님: " + preview, "/admin/chats/" + roomId);
			}
		} else if (m.getAttachmentName() != null && value.isEmpty()) {
			reply = new Reply(m.getAttachmentName() + " 파일을 받았어요.\n상담사가 확인할 수 있도록 함께 전달할게요.",
					List.of(action(CONSULT_MENU, CONSULT_MENU, null)));
		} else {
			Lorebook hit = "Y".equals(s.getBotEnabled()) ? matchLorebook(value) : null;
			if (hit != null) {
				reply = new Reply(hit.getContent(), List.of(action(CONSULT_MENU, CONSULT_MENU, null)), hit.getTopicId());
			} else if (isGreeting(value)) {
				reply = new Reply("안녕하세요" + (name == null ? "" : ", " + name + " 고객님") + "! 명품익스프레스 AI 이사 도우미예요.\n"
						+ "견적, 예약, 이사 준비처럼 궁금한 걸 편하게 물어보세요.",
						List.of(action("견적 받기", null, "quote"), action(CONSULT_MENU, CONSULT_MENU, null)));
			} else if (isDecline(value)) {
				reply = new Reply("알겠어요. 다른 궁금한 점이 생기면 언제든 물어보세요.", List.of());
			} else {
				// 챗봇이 답하지 못한 질문: 상담사 연결을 먼저 물어봄. 관리자 목록에는 '미응답'으로 보이되 알림은 연결을 요청할 때만
				m.setNeedsReply("Y");
				if (BOT.equals(room.getStatus())) {
					chats.updateRoomStatus(roomId, UNANSWERED, null);
				}
				reply = new Reply("이 질문은 제가 정확히 답하기 어려워요.\n상담사를 연결해 드릴까요?",
						List.of(action("상담사 연결", CONSULT_MENU, null), action("괜찮아요", "괜찮아요", null)));
			}
		}
		chats.insertMessage(m);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", m.getMessageId());
		result.put("fileUrl", m.getAttachmentPath());
		result.put("fileKind", fileKind(m));
		if (reply != null) {
			ChatMessage bot = new ChatMessage();
			bot.setRoomId(roomId);
			bot.setSenderType("02");
			bot.setMessageType("01");
			bot.setContent(cut(reply.text(), 1000));
			bot.setTopicId(reply.topicId());
			bot.setIsRead("Y");
			chats.insertMessage(bot);
			result.put("lastId", bot.getMessageId());
			result.put("reply", Map.of("id", bot.getMessageId(), "text", reply.text(), "actions", reply.actions()));
		} else {
			result.put("lastId", m.getMessageId());
		}
		result.put("mode", modeOf(chats.findRoom(roomId)));
		return result;
	}

	/** 고객 화면 머리글: counselor(상담사와 대화 중) | ai */
	public static String modeOf(ChatRoomLine room) {
		return room != null && (CONSULTING.equals(room.getStatus()) || WAITING.equals(room.getStatus())) ? "counselor" : "ai";
	}

	public String customerMode(long memberId) {
		return modeOf(chats.findOpenRoom(memberId));
	}

	/** 지금이 상담 운영시간 밖인지 (운영시간 사용을 끄면 false). "22:00–06:00" 처럼 자정을 넘겨도 됨 */
	boolean outOfHours() {
		ChatSetting s = settings();
		if (!"Y".equals(s.getConsultEnabled())) {
			return false;
		}
		try {
			java.time.LocalTime now = java.time.LocalTime.now();
			java.time.LocalTime start = java.time.LocalTime.parse(s.getStartTime());
			java.time.LocalTime end = java.time.LocalTime.parse(s.getEndTime());
			boolean inside = start.isBefore(end) ? !now.isBefore(start) && now.isBefore(end) : !now.isBefore(start) || now.isBefore(end);
			return !inside;
		} catch (RuntimeException e) {
			return false; // 시간 형식이 이상하면 안내만 생략
		}
	}

	/** 관리자 "상담 종료": 다시 AI 가 답하고, 고객에게 안내 한 줄 */
	@Transactional
	public void endConsult(long roomId) {
		ChatRoomLine room = chats.findRoom(roomId);
		if (room == null || BOT.equals(room.getStatus())) {
			return;
		}
		chats.updateRoomStatus(roomId, BOT, null);
		ChatMessage bot = new ChatMessage();
		bot.setRoomId(roomId);
		bot.setSenderType("02");
		bot.setMessageType("01");
		bot.setContent("상담사와의 상담이 끝났어요. 이제 다시 AI 이사 도우미가 답해 드려요.\n상담사가 필요하면 언제든 '상담사 연결'이라고 보내 주세요.");
		bot.setIsRead("Y");
		chats.insertMessage(bot);
		notifications.markAdminChatRead("/admin/chats/" + roomId);
	}

	static boolean isGreeting(String value) {
		String v = value.toLowerCase().replaceAll("[\\s!?.~^ㅎㅋ]", "");
		return v.length() <= 12 && v.matches("(안녕|안뇽|하이|헬로|hi|hello|반가|처음뵙|좋은(아침|하루|저녁)).*");
	}

	static boolean isDecline(String value) {
		String v = value.replaceAll("[\\s!?.~]", "");
		return v.matches("(괜찮아요|괜찮습니다|괜찮아|아니요|아니오|아뇨|됐어요|아니에요|아니야|노|ㄴㄴ)");
	}

	/** '상담사 연결' 퀵메뉴, "상담사 연결해 주세요", "상담원 요청" 등 */
	static boolean isConsultRequest(String value) {
		String v = value.replace(" ", "");
		return (v.contains("상담사") || v.contains("상담원")) && (v.contains("연결") || v.contains("요청"));
	}

	/* ---------- 챗봇 ---------- */

	private record Reply(String text, List<Map<String, String>> actions, Long topicId) {
		Reply(String text, List<Map<String, String>> actions) {
			this(text, actions, null);
		}
	}

	/** link: move | quote | estimates (화면 이동), prompt: 이어서 질문 */
	private static Map<String, String> action(String label, String prompt, String link) {
		Map<String, String> a = new LinkedHashMap<>();
		a.put("label", label);
		if (prompt != null) a.put("prompt", prompt);
		if (link != null) a.put("link", link);
		return a;
	}

	/**
	 * 질문에 맞는 로어북 (없으면 null).
	 * <ol>
	 * <li>질문이 주제 제목과 같으면(퀵메뉴를 누른 경우) 그 주제의 첫 로어북</li>
	 * <li>아니면 로어북마다 질문에 들어 있는 키워드 수를 세어 가장 많이 맞은 로어북.
	 * 같은 수면 맞은 키워드 글자 수가 긴(더 구체적인) 쪽, 그래도 같으면 퀵메뉴 순서 → 최근 수정한 주제</li>
	 * </ol>
	 * 띄어쓰기와 영문 대소문자는 무시합니다 ("체크 리스트" = "체크리스트").
	 */
	Lorebook matchLorebook(String value) {
		String q = normalize(value);
		if (q.isEmpty()) {
			return null;
		}
		List<Topic> topics = topics();
		List<Long> menuOrder = quickMenus().stream().map(m -> m.getTopicId()).toList();
		for (Topic t : topics) {
			if (!t.getLorebooks().isEmpty() && normalize(t.getTitle()).equals(q)) {
				return t.getLorebooks().get(0);
			}
		}
		Lorebook best = null;
		int bestCount = 0, bestLength = 0, bestRank = Integer.MAX_VALUE;
		for (int ti = 0; ti < topics.size(); ti++) {
			Topic t = topics.get(ti);
			int menuIndex = menuOrder.indexOf(t.getTopicId());
			int rank = (menuIndex >= 0 ? menuIndex : 100 + ti);
			for (Lorebook l : t.getLorebooks()) {
				int count = 0, length = 0;
				for (String k : keywords(l.getKeywords())) {
					if (q.contains(k)) {
						count++;
						length += k.length();
					}
				}
				if (count == 0) continue;
				boolean better = count > bestCount || (count == bestCount && length > bestLength)
						|| (count == bestCount && length == bestLength && rank < bestRank);
				if (better) {
					best = l;
					bestCount = count;
					bestLength = length;
					bestRank = rank;
				}
			}
		}
		return best;
	}

	/** #견적#비용 → [견적, 비용] (띄어쓰기 제거, 소문자) */
	static List<String> keywords(String raw) {
		return raw == null ? List.of()
				: java.util.Arrays.stream(raw.split("#")).map(ChatService::normalize).filter(k -> !k.isEmpty()).distinct().toList();
	}

	private static String normalize(String s) {
		return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
	}

	private static String cut(String s, int max) {
		return s == null ? null : s.length() > max ? s.substring(0, max) : s;
	}
}
