package com.codemove.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 상담 요청 → 연결(AI 꺼짐) → 종료, 관리자에게 온 메시지만 세기 (공용 DB 라 끝나면 모두 되돌림) */
@SpringBootTest
@Transactional
class ChatConsultDbTest {

	@Autowired
	ChatService chats;
	@Autowired
	JdbcTemplate jdbc;
	long memberId;

	@BeforeEach
	void memberWithoutRoom() {
		memberId = jdbc.queryForObject("SELECT MAX(m.MEMBER_ID) FROM TBL_MEMBER m WHERE NOT EXISTS "
				+ "(SELECT 1 FROM TBL_CHAT_ROOM c WHERE c.MEMBER_ID = m.MEMBER_ID AND c.STATUS != '04')", Long.class);
	}

	private Map<String, Object> ask(String text, String to) {
		return chats.customerAsk(memberId, text, null, to);
	}

	private ChatRoomLine room() {
		return chats.rooms().stream().filter(r -> r.getMemberId() == memberId).findFirst().orElseThrow();
	}

	private int alerts() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM TBL_NOTIFICATION WHERE RECEIVER_TYPE = '02' AND LINK_URL = ?",
				Integer.class, "/admin/chats/" + room().getRoomId());
	}

	@Test
	void talkingToAiIsInvisibleToAdmin() {
		ask("안녕하세요", "ai");
		ask("우주선으로 이사할 수 있나요", "ai");
		assertEquals(0, room().getUnread());
		assertNull(room().getLastMessage());
		assertNull(room().getLastAt());
		assertEquals(0, alerts());
		assertEquals("ai", ChatService.modeOf(room()));
	}

	@Test
	void requestThenConnectThenEnd() {
		ask("안녕하세요", "ai");
		ask("상담사 연결", "ai");
		assertEquals("waiting", ChatService.modeOf(room()));
		assertEquals(1, room().getUnread());
		assertEquals("상담사 연결", room().getLastMessage());
		assertEquals(1, alerts());
		ask("상담사 연결", "ai");
		assertEquals(1, alerts(), "이미 요청했으면 알림을 또 만들지 않음");
		assertNotNull(ask("안녕하세요", "ai").get("reply"), "연결 전에는 AI 에게 물어볼 수 있음");

		long id = room().getRoomId();
		chats.startConsult(id, null);
		assertEquals("counselor", ChatService.modeOf(room()));
		Map<String, Object> r = ask("안녕하세요", "ai");
		assertFalse(r.containsKey("reply"), "연결되면 AI 를 골라도 답하지 않음");
		assertEquals("counselor", r.get("to"));
		assertFalse(ask("상담사 연결", "ai").containsKey("reply"));

		int before = chats.peek(id).size();
		chats.startConsult(id, null);
		assertEquals(before, chats.peek(id).size(), "두 번 눌러도 안내는 한 번");

		chats.endConsult(id);
		assertEquals("ai", ChatService.modeOf(room()));
		before = chats.peek(id).size();
		chats.endConsult(id);
		assertEquals(before, chats.peek(id).size());
		assertNotNull(ask("안녕하세요", "ai").get("reply"));
	}

	@Test
	void adminCanStartWithoutARequestAndReadMarksFollow() {
		ask("안녕하세요", "ai");
		long id = room().getRoomId();
		chats.startConsult(id, null);
		ask("이사 날짜 상의할게요", "counselor");
		assertEquals(1, room().getUnread());
		assertTrue(chats.customerThread(memberId).stream()
				.anyMatch(v -> "counselor".equals(v.get("to")) && Boolean.FALSE.equals(v.get("read"))));
		chats.openRoom(id);
		assertEquals(0, room().getUnread());
		assertTrue(chats.customerThread(memberId).stream().filter(v -> "counselor".equals(v.get("to")))
				.allMatch(v -> Boolean.TRUE.equals(v.get("read"))));

		chats.adminSend(id, "네, 말씀하세요", "이사 날짜 상의할게요", null);
		assertTrue(room().getLastMessage().endsWith("네, 말씀하세요"));
		assertEquals(0, room().getUnread(), "관리자 답장은 안 읽은 수에 안 들어감");
		ChatMessage sent = chats.peek(id).stream().filter(m -> "03".equals(m.getSenderType())).findFirst().orElseThrow();
		assertEquals("N", sent.getIsRead());
		chats.customerThread(memberId);
		assertEquals("Y", chats.peek(id).stream().filter(m -> m.getMessageId().equals(sent.getMessageId())).findFirst().orElseThrow().getIsRead());
	}
}
