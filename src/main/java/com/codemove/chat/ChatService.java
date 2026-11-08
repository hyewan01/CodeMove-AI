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
import com.codemove.common.Fmt;
import com.codemove.member.Member;
import com.codemove.member.MemberMapper;
import com.codemove.move.MoveService;
import com.codemove.move.MoveService.CurrentMove;
import com.codemove.notification.NotificationService;

/**
 * 상담 채팅. 고객 메시지는 챗봇이 먼저 답하고(로어북 키워드 → 예약·견적 상태 규칙), "상담사 연결 요청"이면 상담 대기로 바꿔
 * 관리자 채팅 관리에 올립니다. 관리자가 답장하면 상담 중이 되고 챗봇은 답하지 않습니다.
 * CHAT_STATUS 01 챗봇 응대 / 02 대기 중 / 03 상담 중 / 04 종료
 */
@Service
public class ChatService {

	public static final String BOT = "01", WAITING = "02", CONSULTING = "03";
	private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
	/** 관리자 답장 인용 (밀어서 답장): 내용 첫 줄에 붙여 저장 */
	private static final String REPLY_MARK = "↪ ";

	private final ChatMapper chats;
	private final MemberMapper members;
	private final MoveService moves;
	private final NotificationService notifications;
	private final FileStore files;

	public ChatService(ChatMapper chats, MemberMapper members, MoveService moves, NotificationService notifications,
			FileStore files) {
		this.chats = chats;
		this.members = members;
		this.moves = moves;
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

	public List<String> quickMenuTitles() {
		List<String> titles = new ArrayList<>(quickMenus().stream().map(QuickMenu::getMenuName).toList());
		titles.add("상담사 연결");
		return titles;
	}

	/** 퀵메뉴는 최대 4개, 같은 주제는 한 번만 */
	@Transactional
	public void addQuickMenu(long topicId, Long adminId) {
		List<QuickMenu> menus = quickMenus();
		if (menus.size() >= 4 || menus.stream().anyMatch(m -> m.getTopicId() == topicId)) {
			return;
		}
		topics().stream().filter(t -> t.getTopicId() == topicId).findFirst().ifPresent(t -> chats.insertQuickMenu(topicId,
				t.getTitle(), menus.stream().mapToInt(QuickMenu::getSortOrder).max().orElse(0) + 1, adminId));
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

	/** 관리자 답장 → 상담 중, 고객에게 알림 */
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
		chats.updateRoomStatus(roomId, CONSULTING, adminId);
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
	 * 고객 메시지 저장 → 챗봇 답변 (상담 중이면 답하지 않음).
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
		chats.insertMessage(m);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", m.getMessageId());
		Member member = members.findMember(memberId);
		Reply reply;
		if (value.contains("상담사") && value.contains("요청")) {
			if (!CONSULTING.equals(room.getStatus())) {
				chats.updateRoomStatus(roomId, WAITING, null);
				notifications.toAdmin("13", "채팅 대기", member.getMemberName() + " 고객님이 관리자 상담을 요청했습니다. 응답이 필요합니다.",
						"/admin/chats/" + roomId, true);
			}
			ChatSetting s = settings();
			reply = new Reply("상담사 연결 요청을 접수했어요.\n운영시간(" + s.getStartTime() + "–" + s.getEndTime()
					+ ")에는 순서대로 상담이 시작되며, 답변이 오면 알림으로 알려드릴게요.",
					List.of(action("자주 묻는 질문 보기", "자주 묻는 질문을 알려주세요", null)));
		} else if (CONSULTING.equals(room.getStatus())) {
			reply = null;
		} else if (!"Y".equals(settings().getBotEnabled())) {
			reply = new Reply(settings().getDelayMessage(), List.of(action("상담사 연결 요청", "상담사 연결 요청", null)));
		} else {
			reply = botReply(value, m.getAttachmentName(), member.getMemberName(), moves.current(memberId));
		}
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

	/** 프로토타입 getReply 규칙에 실제 예약·견적 값과 로어북을 더한 답변 */
	Reply botReply(String value, String fileName, String userName, CurrentMove c) {
		if (fileName != null) {
			return new Reply(fileName + " 파일을 확인 요청에 첨부했어요.\n개인정보가 포함된 문서는 필요한 부분만 남기고 가려서 보내주세요.",
					List.of(action("첨부 관련 문의", "첨부한 파일로 어떤 상담을 받을 수 있나요?", null), action("상담사 연결", "상담사 연결 요청", null)));
		}
		if (value.contains("상담사")) {
			ChatSetting s = settings();
			return new Reply("상담사에게 직접 문의할 수 있어요.\n상담 운영시간은 " + s.getStartTime() + "–" + s.getEndTime()
					+ "이며, 운영시간 외 요청은 다음 영업일에 확인합니다.", List.of(action("상담사 연결 요청", "상담사 연결 요청", null)));
		}
		// 관리자가 등록한 로어북 키워드 (#견적#비용)
		for (Topic t : topics()) {
			boolean title = !t.getTitle().isBlank() && value.contains(t.getTitle());
			for (Lorebook l : t.getLorebooks()) {
				boolean hit = title || java.util.Arrays.stream(l.getKeywords().split("#")).map(String::trim)
						.anyMatch(k -> !k.isEmpty() && value.contains(k));
				if (hit && !value.contains("예약") && !value.contains("견적")) {
					return new Reply(l.getContent(), List.of(action("상담사 연결", "상담사 연결 요청", null)), t.getTopicId());
				}
			}
		}
		String status = c.status();
		if (value.contains("예약") || value.contains("일정")) {
			if ("confirmed".equals(status)) {
				var r = c.request();
				var v = c.reservation();
				return new Reply(userName + "님의 이사 예약은 " + v.getMoveDate().getYear() + "년 " + Fmt.monthDay(v.getMoveDate()) + " "
						+ Fmt.ampm(v.getMoveTime()) + "로 확정되어 있어요.\n출발지는 " + Fmt.district(r.getFromAddress()) + ", 도착지는 "
						+ Fmt.district(r.getToAddress()) + "입니다.", List.of(action("예약 정보 보기", null, "move")));
			}
			return new Reply("아직 확정된 예약은 없어요.\n도착한 견적서를 확인하고 확정하면 예약 일정이 생성됩니다.",
					List.of(action("견적 정보 보기", null, "move"), action("예약 과정 안내", "견적 확정 후 예약 과정이 궁금해요", null)));
		}
		if (value.contains("영상") || value.contains("분석") || value.contains("CBM")) {
			return new Reply("집 안을 촬영한 영상을 올리면 AI가 가구와 짐의 부피를 분석해 예상 CBM과 견적을 계산해요.\n영상은 방 전체가 끊기지 않게 천천히 촬영하면 더 정확합니다.",
					List.of(action("영상 견적 시작", null, "quote"), action("촬영 방법 보기", "영상을 어떻게 촬영해야 하나요?", null)));
		}
		if (value.contains("견적") || value.contains("비용")) {
			String total = c.quote() == null ? "" : Fmt.won(c.quote().getTotalAmount());
			return switch (status) {
				case "approved" -> new Reply("담당자가 확인한 견적이 준비되어 있어요.\n현재 견적 금액은 " + total
						+ "이며, 견적서의 작업 내용과 별도 비용을 확인한 뒤 확정할 수 있습니다.", List.of(action("견적서 보기", null, "move")));
				case "waiting-approval" -> new Reply("요청하신 견적을 담당자가 확인하고 있어요.\n견적이 준비되면 홈 알림과 견적내역에서 바로 알려드릴게요.",
						List.of(action("견적내역 보기", null, "estimates")));
				case "confirmed" -> new Reply(total + " 견적을 확정해 예약이 완료된 상태예요.\n확정된 작업 내용은 예약 정보와 견적서에서 다시 확인할 수 있습니다.",
						List.of(action("예약 정보 보기", null, "move")));
				default -> new Reply("영상 견적을 요청하면 AI 예상 견적을 바탕으로 담당자가 상세 내용을 확인해요.",
						List.of(action("새 견적 받기", null, "quote")));
			};
		}
		if (value.contains("준비") || value.contains("체크리스트") || value.contains("촬영")) {
			return new Reply("이사 전에는 귀중품 별도 보관, 냉장고 음식 정리, 주소 이전, 가전 설치 일정을 먼저 확인해주세요.\n이사 전날에는 주차 공간과 엘리베이터 예약도 확인하는 것이 좋아요.",
					List.of(action("예약 일정 확인", "내 예약 일정 확인", null), action("추가 준비 팁", "이사 당일 준비 팁도 알려주세요", null)));
		}
		if (value.contains("결제") || value.contains("환불") || value.contains("예약금")) {
			return new Reply("견적 확정 후 안내된 예약금을 결제하면 예약이 최종 접수돼요.\n취소·환불 가능 금액은 작업일과 취소 시점에 따라 달라질 수 있어 상담사 확인이 필요합니다.",
					List.of(action("상담사에게 문의", "결제 및 환불 상담사 연결 요청", null), action("예약 정보 보기", null, "move")));
		}
		if (value.contains("자주 묻는")) {
			return new Reply("많이 묻는 내용은 견적 산정 방식, 영상 촬영 방법, 예약 변경, 사다리차와 추가 비용, 취소·환불 기준이에요.\n궁금한 항목을 메시지로 입력해주세요.",
					List.of(action("추가 비용", "추가 비용이 발생하는 경우를 알려주세요", null), action("예약 변경", "예약 일정을 변경하고 싶어요", null)));
		}
		return new Reply("문의 내용을 확인했어요.\n견적, 예약 일정, 영상 분석, 결제·환불 중 어떤 도움이 필요한지 조금 더 자세히 알려주세요.",
				List.of(action("견적 문의", "이사 견적 알아보기", null), action("예약 문의", "예약 및 일정 확인", null),
						action("상담사 연결", "상담사 연결", null)));
	}

	private static String cut(String s, int max) {
		return s == null ? null : s.length() > max ? s.substring(0, max) : s;
	}
}
