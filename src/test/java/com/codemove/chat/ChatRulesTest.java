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
	void whoAnswers() {
		ChatRoomLine room = new ChatRoomLine();
		room.setStatus(ChatService.BOT);
		assertEquals("ai", ChatService.modeOf(room));
		room.setStatus(ChatService.UNANSWERED);
		assertEquals("ai", ChatService.modeOf(room));
		room.setStatus(ChatService.WAITING);
		assertEquals("counselor", ChatService.modeOf(room));
		room.setStatus(ChatService.CONSULTING);
		assertEquals("counselor", ChatService.modeOf(room));
		assertEquals("ai", ChatService.modeOf(null));
	}
}
