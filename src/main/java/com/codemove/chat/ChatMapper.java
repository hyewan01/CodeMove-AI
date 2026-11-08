package com.codemove.chat;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 채팅방 · 메시지 · 챗봇 설정 · 주제/로어북 · 퀵메뉴 (mapper/ChatMapper.xml) */
@Mapper
public interface ChatMapper {

	/* ---------- 채팅방 · 메시지 ---------- */

	/** 종료되지 않은 방 */
	List<ChatRoomLine> listRooms();

	ChatRoomLine findRoom(long roomId);

	/** 그 회원의 종료되지 않은 가장 최근 방 */
	ChatRoomLine findOpenRoom(long memberId);

	/** 새 방 번호는 room.roomId 로 돌려줌 */
	void insertRoom(@Param("memberId") long memberId, @Param("room") ChatRoomLine room);

	/** status 02 이면 WAITING_AT, 03 이면 담당 관리자도 기록 */
	void updateRoomStatus(@Param("roomId") long roomId, @Param("status") String status, @Param("adminId") Long adminId);

	List<ChatMessage> listMessages(@Param("roomId") long roomId, @Param("after") long after);

	void insertMessage(ChatMessage m);

	void markCustomerRead(long roomId);

	/* ---------- 설정 ---------- */

	ChatSetting findSetting();

	void insertSetting();

	void updateSetting(@Param("s") ChatSetting s, @Param("adminId") Long adminId);

	/* ---------- 주제 · 로어북 ---------- */

	List<Topic> listTopics();

	List<Lorebook> listLorebooks();

	void insertTopic(Topic t);

	void updateTopic(Topic t);

	void deleteTopic(long topicId);

	void deleteLorebooks(long topicId);

	void insertLorebook(Lorebook l);

	void deleteActions(long topicId);

	void clearMessageTopic(long topicId);

	/* ---------- 퀵메뉴 ---------- */

	List<QuickMenu> listQuickMenus();

	void insertQuickMenu(@Param("topicId") long topicId, @Param("name") String name, @Param("sortOrder") int sortOrder,
			@Param("adminId") Long adminId);

	void deleteQuickMenu(long menuId);

	void deleteQuickMenusByTopic(long topicId);
}
