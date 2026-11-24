package com.codemove.notification;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 알림 · 알림 설정 (mapper/NotificationMapper.xml) */
@Mapper
public interface NotificationMapper {

	void insert(Notification n);

	/** 관리자 수신 알림 (최근 days 일) */
	List<Notification> listAdmin(int days);

	int countUnreadAdmin();

	void markAllAdminRead();

	List<Notification> listMember(long memberId);

	int countUnreadMember(long memberId);

	void markMemberRead(@Param("memberId") long memberId, @Param("id") long id);

	void markAllMemberRead(long memberId);

	/** 한 종류(NTF_DETAIL)의 안 읽은 알림 수 · 읽음 처리 (상담 답변 15 → 하단 채팅 점) */
	int countUnreadMemberDetail(@Param("memberId") long memberId, @Param("detail") String detail);

	void markMemberReadDetail(@Param("memberId") long memberId, @Param("detail") String detail);

	/** 알림 내역 (관리자·고객 수신 모두, 최근 순) */
	List<Notification> listHistory(int limit);

	NotificationSetting findSetting();

	void insertSetting();

	void updateSetting(NotificationSetting s);
}
