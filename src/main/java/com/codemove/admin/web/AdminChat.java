package com.codemove.admin.web;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅 관리 모델 (프로토타입 ChatManagement.tsx).
 * 백엔드 개발 시 TBL_CHAT_ROOM · TBL_CHAT_MESSAGE · TBL_CHAT_SETTING · TBL_QUICK_MENU · TBL_CHATBOT_TOPIC · TBL_LOREBOOK 으로 대체합니다.
 */
public final class AdminChat {

	private AdminChat() {
	}

	public record ChatRoom(String id, String name, String message, String time, boolean waiting, int unread,
			int activity) {

		public String initial() {
			return name.substring(0, 1);
		}
	}

	public static final List<ChatRoom> ROOMS = List.of(
			new ChatRoom("luxury", "홍길동", "혹시 사다리차 추가 비용이 발생하나요?", "방금", true, 3, 15),
			new ChatRoom("luxury2", "김명품", "엘리베이터를 사용하지 못하는데요?", "2분 전", true, 2, 14),
			new ChatRoom("park789", "박철수", "네, 알겠습니다. 내일 뵙겠습니다.", "오후 3:30", false, 0, 13),
			new ChatRoom("choi00", "최지우", "에어컨 이전 설치도 같이 되나요?", "8분 전", true, 4, 12),
			new ChatRoom("jung11", "정현우", "주소 변경은 어디서 하나요?", "어제", false, 1, 7),
			new ChatRoom("kang22", "강민주", "포장 박스는 언제 가져다 주시나요?", "12분 전", true, 2, 11),
			new ChatRoom("lee01", "이서준", "견적서 확인했습니다.", "오전 10:15", false, 0, 10),
			new ChatRoom("yoon44", "윤아름", "귀중품은 따로 챙겨야 하나요?", "18분 전", true, 1, 9),
			new ChatRoom("song90", "송지호", "이사 시작 시간은 몇 시인가요?", "어제", false, 0, 8),
			new ChatRoom("han26", "한유진", "계약금 입금 완료했습니다.", "어제", false, 0, 6),
			new ChatRoom("oh03", "오도현", "방문 견적 일정을 변경하고 싶어요.", "월요일", false, 1, 5),
			new ChatRoom("moon08", "문지안", "침대 분해와 조립도 부탁드려요.", "월요일", false, 0, 4),
			new ChatRoom("lim19", "임지수", "주차 공간 사진 보내드립니다.", "일요일", false, 0, 3),
			new ChatRoom("kim24", "김도윤", "카드 결제가 가능한가요?", "토요일", false, 0, 2),
			new ChatRoom("jimin31", "이지민", "친절한 안내 감사합니다.", "금요일", false, 0, 1));

	public static ChatRoom room(String id) {
		return ROOMS.stream().filter(r -> r.id().equals(id)).findFirst().orElse(ROOMS.get(0));
	}

	/** role: customer | ai | admin */
	public record Message(long id, String role, String text, boolean waiting, String replyTo) {
	}

	/** 프로토타입 roomMessages */
	public static List<Message> initialMessages(ChatRoom room) {
		List<Message> list = new ArrayList<>();
		list.add(new Message(1, "customer", "이번 주 금요일 이사 예정인데, 비가 오면 어떻게 되나요?", false, null));
		list.add(new Message(2, "ai", "우천 시에도 기본적으로 이사는 진행되며, 가구와 짐은 방수 포장 후 안전하게 운반해 드립니다.", false, null));
		list.add(new Message(3, "customer", room.message(), room.waiting(), null));
		return list;
	}

	/** 채팅 운영 설정 (TBL_CHAT_SETTING) */
	@Data
	@AllArgsConstructor
	public static class Settings {
		private boolean enabled;
		private boolean operatingHoursEnabled;
		private String startTime;
		private String endTime;
		private String fallbackMessage;
	}

	@Data
	@AllArgsConstructor
	public static class QuickMenu {
		private String id;
		private String title;
		private String description;
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
	}

	public static final String DEFAULT_GREETING = "안녕하세요! 명품익스프레스 AI 상담 챗봇입니다. 이사와 관련해 궁금한 내용을 편하게 물어보세요.";

	public static Settings defaultSettings() {
		return new Settings(true, true, "09:00", "18:00", "현재 상담원 연결이 지연되고 있습니다. 조금만 기다려주세요.");
	}

	public static List<Topic> defaultTopics() {
		return new ArrayList<>(List.of(
				new Topic(1, "이사 견적", "이사 비용과 방문 견적 과정을 안내합니다.", new ArrayList<>(List.of(
						new Lorebook(101, "#견적#비용#가격", "출발지와 도착지, 이사 날짜, 짐의 양을 알려주시면 예상 견적을 안내해 드립니다.")))),
				new Topic(2, "예약 방법", "상담부터 예약 확정까지의 절차를 안내합니다.", new ArrayList<>(List.of(
						new Lorebook(201, "#예약#신청#계약", "견적 확인 후 예약금을 결제하면 이사 일정이 최종 확정됩니다.")))),
				new Topic(3, "포장 이사", "포장 서비스 범위와 준비 사항을 확인합니다.", new ArrayList<>(List.of(
						new Lorebook(301, "#포장#박스#정리", "포장 이사는 전문 인력이 포장부터 운반, 기본 정리까지 진행합니다.")))),
				new Topic(4, "사다리차 이용", "사다리차 필요 여부와 추가 비용을 안내합니다.", new ArrayList<>(List.of(
						new Lorebook(401, "#사다리차#엘리베이터#고층", "현장 층수와 작업 환경에 따라 사다리차 필요 여부 및 비용이 달라집니다."))))));
	}

	public static List<QuickMenu> defaultQuickMenus() {
		return new ArrayList<>(List.of(
				new QuickMenu("topic-1", "이사 견적", "이사 비용과 방문 견적 과정을 안내합니다."),
				new QuickMenu("topic-2", "예약 방법", "상담부터 예약 확정까지의 절차를 안내합니다.")));
	}
}
