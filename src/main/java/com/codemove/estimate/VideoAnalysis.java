package com.codemove.estimate;

import java.util.List;

/**
 * 영상 분석 결과 (AI 원본 응답을 검사·매핑한 뒤의 값).
 * AI 는 품목·수량·크기만 답하고, 부피·톤·금액은 서버가 표준 규격표로 계산합니다.
 */
public final class VideoAnalysis {

	private VideoAnalysis() {
	}

	/** MAPPING_TYPE 공통코드 (docs/estimate/AI_VIDEO_ESTIMATE.md 2.3) */
	public static final String EXACT = "01", ALIAS = "02", AI_GUESS = "03", UNMAPPED = "04", USER_EDIT = "05";

	/** ANALYSIS_ERROR 공통코드 (AI_VIDEO_ESTIMATE.md 6장) */
	public static final String ERR_FORMAT = "01", ERR_SIZE = "02", ERR_DARK = "03", ERR_NOT_HOME = "04",
			ERR_TIMEOUT = "05", ERR_QUOTA = "06", ERR_JSON = "07", ERR_API = "08", ERR_NO_KEY = "09";

	/** AI 가 답한 품목 한 줄 (응답 JSON 그대로) */
	public record RawItem(String itemName, String sizeCode, int quantity, String detectedName, String room,
			String evidence, boolean needsReview) {
	}

	/** AI 응답 전체 */
	public record RawResult(boolean videoOk, String rejectReason, List<String> rooms, List<RawItem> items,
			String memo) {
	}

	/** 표준 품목에 맞춘 품목 (화면 · TBL_REQUEST_ITEM 으로 감) */
	public record MappedItem(String name, String sizeCode, int count, double unitCbm, String detectedName,
			String room, String evidence, String mappingType, boolean needsReview) {

		public double cbm() {
			return unitCbm * count;
		}
	}

	/** API 사용량 (usageMetadata) */
	public record Usage(long inputTokens, long outputTokens, long thoughtTokens, long totalTokens) {

		public static final Usage NONE = new Usage(0, 0, 0, 0);
	}

	/**
	 * 분석 한 번의 결과.
	 * @param errorCode 성공이면 null
	 * @param costUsd   적용 단가로 계산한 추정 비용(달러)
	 */
	public record Result(String analysisId, boolean success, String errorCode, String message, String model,
			String promptVersion, List<MappedItem> items, List<String> rooms, String memo, Usage usage,
			double costUsd, long uploadMs, long apiMs, long totalMs, int retries, String rawJson) {

		public double totalCbm() {
			return items == null ? 0 : items.stream().mapToDouble(MappedItem::cbm).sum();
		}

		public long reviewCount() {
			return items == null ? 0 : items.stream().filter(MappedItem::needsReview).count();
		}
	}
}
