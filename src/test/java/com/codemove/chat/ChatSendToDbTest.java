package com.codemove.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 누구에게 보낸 메시지인지 저장되고, 다시 열어도 그대로인지 (공용 DB 라 끝나면 모두 되돌림) */
@SpringBootTest
@Transactional
class ChatSendToDbTest {

	@Autowired
	ChatService chats;
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void recipientIsKept() {
		Long memberId = jdbc.queryForObject("SELECT MAX(m.MEMBER_ID) FROM TBL_MEMBER m WHERE NOT EXISTS "
				+ "(SELECT 1 FROM TBL_CHAT_ROOM c WHERE c.MEMBER_ID = m.MEMBER_ID AND c.STATUS != '04')", Long.class);
		assertNotNull(memberId, "열린 채팅방이 없는 회원이 필요함");
		assertEquals("ai", chats.customerAsk(memberId, "안녕하세요", null, "ai").get("to"));
		assertEquals("counselor", chats.customerAsk(memberId, "이사 날짜 상의할게요", null, "counselor").get("to"));
		assertEquals("counselor", chats.customerAsk(memberId, "상담사 연결", null, "ai").get("to"));

		List<Object> kept = chats.customerThread(memberId).stream().filter(v -> "user".equals(v.get("role")))
				.map((Map<String, Object> v) -> v.get("to")).toList();
		assertEquals(List.of("ai", "counselor", "counselor"), kept);
	}

	@Test
	void oldMessagesHaveNoRecipient() {
		assertEquals("", ChatService.toOf(new ChatMessage()));
	}
}
