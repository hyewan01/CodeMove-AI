package com.codemove.checklist;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 이사 준비 체크리스트 (mapper/ChecklistMapper.xml, TBL_MOVE_CHECKLIST) */
@Mapper
public interface ChecklistMapper {

	List<ChecklistRow> list(@Param("memberId") long memberId, @Param("requestId") long requestId);

	void insert(ChecklistRow row);

	int setDone(@Param("checkId") long checkId, @Param("memberId") long memberId, @Param("done") String done);

	int deleteCustom(@Param("checkId") long checkId, @Param("memberId") long memberId);
}
