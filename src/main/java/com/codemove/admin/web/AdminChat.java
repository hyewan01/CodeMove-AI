package com.codemove.admin.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.codemove.chat.ChatMessage;
import com.codemove.chat.ChatRoomLine;
import com.codemove.chat.ChatService;
import com.codemove.chat.ChatSetting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 채팅 관리 화면 값 (프로토타입 ChatManagement.tsx 의 이름 유지). 데이터는 ChatService(TBL_CHAT_*) */
public final class AdminChat {

	private AdminChat() {
	}

	/**
	 * id: ROOM_ID, waiting: 상담 요청(02), 아직 연결 전, talked: 관리자와 주고받은 적이 있거나 상담 중 · 요청 (아니면 AI 하고만 대화),
	 * time · activity: 관리자와 주고받은 마지막 시각 (채팅 목록 정렬 기준)
	 */
	public record ChatRoom(String id, String name, String message, String time, boolean waiting, boolean talked,
			int unread, long activity) {

		public String initial() {
			return name.substring(0, 1);
		}

		public static ChatRoom of(ChatRoomLine r) {
			LocalDateTime at = r.getLastAt() != null ? r.getLastAt() : r.getStartedAt();
			boolean waiting = "waiting".equals(ChatService.modeOf(r));
			return new ChatRoom(String.valueOf(r.getRoomId()), r.getMemberName(), preview(r), AdminChat.time(at),
					waiting, r.getLastAt() != null || !"ai".equals(ChatService.modeOf(r)),
					r.getUnread() == null ? 0 : r.getUnread(), AdminViews.epoch(at));
		}
	}

	/** 목록 · 알림에 보일 마지막 메시지 (답장 인용은 빼고) */
	static String preview(ChatRoomLine r) {
		return r.getLastMessage() == null ? "" : ChatService.splitReply(r.getLastMessage())[1];
	}

	/** 방금 / 8분 전 / 오후 3:30 / 어제 / 월요일 / 11.02 */
	static String time(LocalDateTime t) {
		if (t == null) return "";
		long minutes = java.time.Duration.between(t, LocalDateTime.now()).toMinutes();
		long days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now());
		if (minutes < 1) return "방금";
		if (minutes < 60 && days == 0) return minutes + "분 전";
		if (days == 0) return ChatService.clock(t);
		if (days == 1) return "어제";
		if (days < 7) return "일월화수목금토".charAt(t.getDayOfWeek().getValue() % 7) + "요일";
		return t.format(DateTimeFormatter.ofPattern("MM.dd"));
	}

	/** role: customer | ai | admin, waiting: 답변이 필요한데(챗봇 미응답·상담사 요청) 아직 관리자 답장이 없는 고객 메시지 (붉게 표시) */
	/**
	 * file · fileName · fileKind(image · video · file): 첨부, edited · deleted: 수정 · 삭제 표시,
	 * editBlock · deleteBlock: 관리자 메시지를 고치거나 지울 수 없는 이유 (가능하면 null), read: 고객이 읽었는지
	 */
	public record Message(long id, String role, String text, boolean waiting, String replyTo, String file,
			String fileName, String fileKind, boolean edited, boolean deleted, String editBlock, String deleteBlock,
			boolean read, String time, String day) {

		public static Message of(ChatMessage m, boolean waiting) {
			String[] parts = ChatService.splitReply(m.getContent());
			String role = switch (m.getSenderType()) { case "01" -> "customer"; case "03" -> "admin"; default -> "ai"; };
			boolean deleted = m.getDeletedAt() != null;
			boolean admin = "admin".equals(role);
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			return new Message(m.getMessageId(), role, deleted ? "" : parts[1], waiting, deleted ? null : parts[0],
					deleted ? null : m.getAttachmentPath(), deleted ? null : m.getAttachmentName(),
					deleted ? "" : ChatService.fileKind(m), m.getEditedAt() != null, deleted,
					admin ? ChatService.editBlockOf(m, now) : "고객 메시지는 고칠 수 없어요.",
					admin ? ChatService.deleteBlockOf(m, now) : "고객 메시지는 삭제할 수 없어요.", "Y".equals(m.getIsRead()),
					ChatService.clock(m.getSentAt()), ChatService.day(m.getSentAt()));
		}
	}

	/** 채팅 운영 설정 (TBL_CHAT_SETTING: BOT_ENABLED / CONSULT_ENABLED / START·END_TIME / DELAY_MESSAGE) */
	public record Settings(boolean enabled, boolean operatingHoursEnabled, String startTime, String endTime,
			String fallbackMessage) {

		public static Settings of(ChatSetting s) {
			return new Settings("Y".equals(s.getBotEnabled()), "Y".equals(s.getConsultEnabled()), s.getStartTime(),
					s.getEndTime(), s.getDelayMessage() == null ? "" : s.getDelayMessage());
		}
	}

	/** 첫 화면 퀵메뉴 · 후보 (id: 퀵메뉴는 MENU_ID, 후보는 topic-주제번호) */
	public record QuickMenu(String id, String title, String description) {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Lorebook {
		private long id;
		private String keywords = "";
		private String content = "";

		/** #견적#비용 → [견적, 비용] */
		public List<String> keywordList() {
			return java.util.Arrays.stream(keywords.split("#")).map(k -> k.trim()).filter(k -> !k.isEmpty()).toList();
		}
	}

	/** 답변 주제 (TBL_CHATBOT_TOPIC + TBL_LOREBOOK). 폼 바인딩용으로 기본 생성자 사용 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Topic {
		private long id;
		private String title = "";
		private String introduction = "";
		private List<Lorebook> lorebooks = new ArrayList<>();

		public static Topic of(com.codemove.chat.Topic t) {
			List<Lorebook> books = new ArrayList<>(t.getLorebooks().stream()
					.map(l -> new Lorebook(l.getLorebookId(), l.getKeywords(), l.getContent())).toList());
			return new Topic(t.getTopicId(), t.getTitle(), t.getIntro() == null ? "" : t.getIntro(), books);
		}
	}
}
