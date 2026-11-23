package com.codemove.estimate;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** TBL_AI_ANALYSIS (mapper/AnalysisLogMapper.xml). V4 적용 후에만 호출 (V4Schema) */
@Mapper
public interface AnalysisLogMapper {

	void insert(AnalysisRow row);

	List<AnalysisRow> list(@Param("limit") int limit);

	AnalysisRow find(@Param("analysisId") long analysisId);

	/** 고객이 이사 요청을 마치면 요청 · 영상과 연결 */
	int linkRequest(@Param("analysisId") long analysisId, @Param("requestId") long requestId,
			@Param("videoId") Long videoId);
}
