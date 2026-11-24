package com.codemove.checklist;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 이사 준비 체크리스트 (mapper/ChecklistMapper.xml, TBL_MOVE_CHECKLIST · TBL_CHECKLIST_ITEM) */
@Mapper
public interface ChecklistMapper {

	List<ChecklistRow> list(@Param("memberId") long memberId, @Param("requestId") long requestId);

	void insert(ChecklistRow row);

	int setDone(@Param("checkId") long checkId, @Param("memberId") long memberId, @Param("done") String done);

	int deleteCustom(@Param("checkId") long checkId, @Param("memberId") long memberId);

	/* ---------- 기본 항목 (관리자) ---------- */

	List<ChecklistItem> items();

	int countItems();

	void insertItem(ChecklistItem item);

	int updateItem(ChecklistItem item);

	int setItemUse(@Param("code") String code, @Param("use") String use);

	int deleteItem(@Param("code") String code);

	/** 체크리스트를 한 번이라도 쓴 고객 (최근 순) */
	List<ChecklistMember> members();
}
