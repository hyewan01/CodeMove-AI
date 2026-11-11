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
	@Transactional
	public void adminSend(long roomId, String text, String replyTo, Long adminId) {
		ChatRoomLine room = chats.findRoom(roomId);
		if (room == null || text == null || text.isBlank()) {
			return;
		}
		ChatMessage m = new ChatMessage();
		m.setRoomId(roomId);
		m.setAdminId(adminId);
		m.setSenderType("03");
		m.setMessageType("01");
		String body = text.trim();
		if (replyTo != null && !replyTo.isBlank()) {
			body = REPLY_MARK + cut(replyTo.replace("\n", " ").trim(), 80) + "\n" + body;
		}
		m.setContent(cut(body, 1000));
		m.setIsRead("Y");
		chats.insertMessage(m);
		chats.markCustomerRead(roomId);
		// 챗봇이 못 한 답을 관리자가 채웠으면 다시 챗봇 응대, 상담사 요청(또는 상담 중)이면 상담 중
		chats.updateRoomStatus(roomId, UNANSWERED.equals(room.getStatus()) ? BOT : CONSULTING, adminId);
		notifications.toMember(room.getMemberId(), "15", "상담 답변이 도착했어요", cut(text.trim(), 100), "/chat");
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
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("id", m.getMessageId());
		v.put("role", switch (m.getSenderType()) { case "01" -> "user"; case "03" -> "admin"; default -> "bot"; });
		v.put("text", splitReply(m.getContent())[1]);
		v.put("file", m.getAttachmentName());
		v.put("time", m.getSentAt().format(HM));
		return v;
	}

	/** 진행 중인 상담 내용 */
	public List<Map<String, Object>> customerThread(long memberId) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		return room == null ? List.of() : chats.listMessages(room.getRoomId(), 0).stream().map(this::view).toList();
	}

	/** 상담사 답장만 (after 이후) */
	public List<Map<String, Object>> customerUpdates(long memberId, long after) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		return room == null ? List.of()
				: chats.listMessages(room.getRoomId(), after).stream().filter(m -> "03".equals(m.getSenderType()))
						.map(this::view).toList();
	}

	/**
	 * 고객 메시지 저장 → 챗봇 답변.
	 * <ul>
	 * <li>상담사 연결(요청) → 대기 중(02), 관리자에게 알림</li>
	 * <li>상담 중(03, 관리자가 답장한 뒤) → 챗봇은 답하지 않음</li>
	 * <li>로어북 키워드가 맞으면 그 내용으로 답변</li>
	 * <li>맞는 로어북이 없거나 챗봇이 꺼져 있으면 대응 규칙을 보내고 답변 필요(05)</li>
	 * </ul>
	 * @return { id: 고객 메시지 번호, reply: { text, actions } | null, lastId }
	 */
	@Transactional
	public Map<String, Object> customerAsk(long memberId, String text, MultipartFile file) {
		ChatRoomLine room = chats.findOpenRoom(memberId);
		if (room == null) {
			room = new ChatRoomLine();
			chats.insertRoom(memberId, room);
			room.setStatus(BOT);
		}
		long roomId = room.getRoomId();
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
		} else if (consulting) {
			reply = null;
		} else if (m.getAttachmentName() != null && value.isEmpty()) {
			reply = new Reply(m.getAttachmentName() + " 파일을 받았어요.\n상담사가 확인할 수 있도록 함께 전달할게요.",
					List.of(action(CONSULT_MENU, CONSULT_MENU, null)));
		} else {
			Lorebook hit = "Y".equals(s.getBotEnabled()) ? matchLorebook(value) : null;
			if (hit != null) {
				reply = new Reply(hit.getContent(), List.of(action(CONSULT_MENU, CONSULT_MENU, null)), hit.getTopicId());
			} else {
				// 대응 규칙: 챗봇이 답하지 못한 질문 → 관리자 채팅 목록에 '대기 중'으로
				m.setNeedsReply("Y");
				if (BOT.equals(room.getStatus())) {
					chats.updateRoomStatus(roomId, UNANSWERED, null);
					notifications.toAdmin("13", "채팅 대기", name + " 고객님의 질문에 챗봇이 답하지 못했습니다. 확인이 필요합니다.",
							"/admin/chats/" + roomId, false);
				}
				String fallback = s.getDelayMessage() == null || s.getDelayMessage().isBlank()
						? "문의하신 내용은 상담사가 확인한 뒤 답변드릴게요." : s.getDelayMessage();
				reply = new Reply(fallback, List.of(action(CONSULT_MENU, CONSULT_MENU, null)));
			}
		}
		chats.insertMessage(m);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", m.getMessageId());
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
			result.put("reply", Map.of("text", reply.text(), "actions", reply.actions()));
		} else {
			result.put("lastId", m.getMessageId());
		}
		return result;
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
