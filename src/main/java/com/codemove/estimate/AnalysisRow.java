package com.codemove.estimate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/** TBL_AI_ANALYSIS 한 행 (가이드 10장 기록 항목) */
@Data
public class AnalysisRow {
	private Long analysisId;
	private Long requestId;
	private Long videoId;
	private String testYn;
	private String modelName;
	private String promptVersion;
	private Double fileMb;
	private String processOption;
	private Long inputTokens;
	private Long outputTokens;
	private Long totalTokens;
	/** 생각 토큰 등 모델이 준 사용량 원본 */
	private String usageJson;
	private Double priceIn;
	private Double priceOut;
	private LocalDate priceDate;
	private String currency;
	private Double estCost;
	private Long uploadMs;
	private Long apiMs;
	private Long mappingMs;
	private Long totalMs;
	private Integer retryCount;
	private String isSuccess;
	private String errorCode;
	private String errorMessage;
	private Integer itemCount;
	private Integer mappedCount;
	private Integer reviewCount;
	private String rawJson;
	/** 매핑된 품목 목록 JSON (+ 영상 파일 이름 · 메모) */
	private String resultJson;
	private LocalDateTime createdAt;
}
