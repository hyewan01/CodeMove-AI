package com.codemove.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 챗봇이 인사 · 거절 · 상담사 연결 요청을 알아듣는지, 방 상태별로 누가 답하는지 */
class ChatRulesTest {

	@Test
	void greetings() {
		assertTrue(ChatService.isGreeting("안녕하신가요"));
		assertTrue(ChatService.isGreeting("안녕하세요!"));
		assertTrue(ChatService.isGreeting("Hello"));
		assertFalse(ChatService.isGreeting("에어컨 설치는 어떻게 해?"));
		assertFalse(ChatService.isGreeting("안녕하세요 이사 날짜를 바꾸고 싶은데 어떻게 해야 하나요"));
	}

	@Test
	void declines() {
		assertTrue(ChatService.isDecline("괜찮아요"));
		assertTrue(ChatService.isDecline("아니요."));
		assertFalse(ChatService.isDecline("괜찮은 날짜가 있나요?"));
	}

	@Test
	void consultRequests() {
		assertTrue(ChatService.isConsultRequest("상담사 연결"));
		assertTrue(ChatService.isConsultRequest("상담원 요청할게요"));
		assertFalse(ChatService.isConsultRequest("상담 시간이 언제예요?"));
	}

	@Test
	void datesInQuestions() {
		java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 25);
		assertEquals(java.time.LocalDate.of(2026, 11, 26), ChatService.askedDate("11월 26일에 예약 가능해?", today));
		assertEquals(java.time.LocalDate.of(2026, 11, 28), ChatService.askedDate("28일에 이사 가능해?", today));
		assertEquals(java.time.LocalDate.of(2026, 12, 3), ChatService.askedDate("3일에 예약 돼?", today)); // 지났으면 다음 달
		assertEquals(java.time.LocalDate.of(2027, 2, 14), ChatService.askedDate("2월 14일 가능해요?", today)); // 지난 달이면 내년
		assertEquals(java.time.LocalDate.of(2026, 11, 26), ChatService.askedDate("내일 이사 가능해?", today));
		assertEquals(null, ChatService.askedDate("11월에 예약 가능한 날짜 언제야?", today));
		assertEquals(null, ChatService.askedDate("2월 30일 가능해?", today));

		assertEquals(java.time.LocalDate.of(2026, 12, 1), ChatService.askedMonth("다음달에 예약 가능한 날짜 언제야?", today));
		assertEquals(java.time.LocalDate.of(2027, 1, 1), ChatService.askedMonth("다다음 달은?", today));
		assertEquals(java.time.LocalDate.of(2026, 11, 1), ChatService.askedMonth("11월에 예약 가능한 날짜 언제야?", today));
		assertEquals(java.time.LocalDate.of(2027, 3, 1), ChatService.askedMonth("3월에 이사 가능해?", today));
		assertEquals(java.time.LocalDate.of(2026, 11, 1), ChatService.askedMonth("예약 가능한 날 알려줘", today));
	}

	@Test
	void dayRanges() {
		assertEquals("1~3일, 5일, 7~8일", ChatService.ranges(java.util.List.of(1, 2, 3, 5, 7, 8)));
		assertEquals("25~30일", ChatService.ranges(java.util.List.of(25, 26, 27, 28, 29, 30)));
		assertEquals("", ChatService.ranges(java.util.List.of()));
	}

	@Test
	void clockLikeKakaoTalk() {
		assertEquals("오후 12:13", ChatService.clock(java.time.LocalDateTime.of(2026, 11, 25, 12, 13)));
		assertEquals("오전 12:05", ChatService.clock(java.time.LocalDateTime.of(2026, 11, 25, 0, 5)));
		assertEquals("오후 11:59", ChatService.clock(java.time.LocalDateTime.of(2026, 11, 25, 23, 59)));
	}

	@Test
	void dayLabel() {
		assertEquals("2026년 11월 25일 수요일", ChatService.day(java.time.LocalDateTime.of(2026, 11, 25, 0, 0)));
		assertEquals("2026년 11월 29일 일요일", ChatService.day(java.time.LocalDateTime.of(2026, 11, 29, 23, 59)));
	}

	@Test
	void whoAnswers() {
		ChatRoomLine room = new ChatRoomLine();
		room.setStatus(ChatService.BOT);
		assertEquals("ai", ChatService.modeOf(room));
		room.setStatus(ChatService.UNANSWERED);
		assertEquals("ai", ChatService.modeOf(room));
		room.setStatus(ChatService.WAITING);
		assertEquals("waiting", ChatService.modeOf(room));
		room.setStatus(ChatService.CONSULTING);
		assertEquals("counselor", ChatService.modeOf(room));
		assertEquals("ai", ChatService.modeOf(null));
	}
}
