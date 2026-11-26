package com.codemove.admin.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.codemove.common.Fmt;
import com.codemove.move.EstimateDoc;
import com.codemove.move.QuoteLine;
import com.codemove.move.QuoteService;

import lombok.Data;

/**
 * 견적 관리 목록 행 · 견적서 등록 단계 (SCR-A-001~004).
 * 사진·PDF 는 AI(SheetReader)가 대표님 수기 견적서를 읽어 칸을 채우고, 관리자가 고친 뒤 등록하면 TBL_QUOTE 에 저장합니다.
 */
public final class AdminEstimates {

	private AdminEstimates() {
	}

	/** 옵션 비용 행 (견적서 양식, 템플릿 optionRows) */
	public static final String[][] OPTION_ROWS = EstimateDoc.OPTION_ROWS;

	/** 견적 관리 목록 한 줄 (TBL_QUOTE + 요청 영상) */
	@Data
	public static class EstimateRecord {
		private long id;
		private String status;
		private String name;
		private String date;
		private String approvedDate;
		private String phone;
		private String companyName;
		private String quoteNo;
		private String videoUrl;
		private String videoFileName;
		private String videoUploadedAt;

		public static EstimateRecord of(QuoteLine q) {
			EstimateRecord r = new EstimateRecord();
			r.id = q.getQuoteId();
			r.status = QuoteService.statusLabel(q.getStatus());
			r.name = q.getContactName();
			r.date = Fmt.yy(q.getEstimateDate());
			r.approvedDate = q.getApprovedAt() == null ? null : Fmt.yy(q.getApprovedAt());
			r.phone = q.getContactPhone();
			r.companyName = q.getCompanyName();
			r.quoteNo = q.getQuoteNo();
			// 팀원 앱이 넣은 경로는 앞에 / 가 없음 (uploads/videos/…)
			String path = q.getVideoPath();
			r.videoUrl = path == null || path.startsWith("/") || path.startsWith("http") ? path : "/" + path;
			r.videoFileName = q.getVideoName();
			if (q.getVideoUploadedAt() != null) {
				int h = q.getVideoUploadedAt().getHour();
				r.videoUploadedAt = Fmt.yy(q.getVideoUploadedAt()) + (h < 12 ? " 오전 " : " 오후 ") + (h % 12 == 0 ? 12 : h % 12)
						+ ":" + String.format("%02d", q.getVideoUploadedAt().getMinute()) + " 업로드";
			}
			return r;
		}

		/** 분류 배지 색 (red 견적 검토 / blue 견적 승인 / green 견적 확정) */
		public String badgeTone() {
			return switch (status) {
				case "견적 승인" -> "bg-[#edf3f8] text-[#4b759b]";
				case "견적 확정" -> "bg-[#edf4ed] text-[#346538]";
				default -> "bg-[#fbefed] text-[#b9574f]";
			};
		}
	}

	/** 오늘 날짜 YY.MM.DD (프로토타입 getTodayShortDate) */
	public static String today() {
		LocalDate d = LocalDate.now();
		return String.format("%02d.%02d.%02d", d.getYear() % 100, d.getMonthValue(), d.getDayOfMonth());
	}

	/* ---------- 견적서 등록 (SCR-A-003~004) ---------- */

	/** 확인이 필요한 칸 버튼 한 개: 화면 이름 · 폼 칸 이름(누르면 그 칸으로 이동) */
	public record ReviewChip(String label, String field) {
	}

	private static final java.util.Map<String, String> FIELD_LABELS = java.util.Map.ofEntries(
			java.util.Map.entry("customer", "고객명"), java.util.Map.entry("phone", "연락처"),
			java.util.Map.entry("estimateDate", "견적일"), java.util.Map.entry("moveDate", "포장일"),
			java.util.Map.entry("transportDate", "운송일"), java.util.Map.entry("origin", "출발지"),
			java.util.Map.entry("destination", "도착지"), java.util.Map.entry("volume", "이사물량"),
			java.util.Map.entry("movingCost", "이사비용"), java.util.Map.entry("total", "총비용"),
			java.util.Map.entry("deposit", "계약금"), java.util.Map.entry("balance", "잔금"),
			java.util.Map.entry("departureFloor", "출발지 층"), java.util.Map.entry("destinationFloor", "도착지 층"),
			java.util.Map.entry("departureLadder", "출발지 사다리"), java.util.Map.entry("destinationLadder", "도착지 사다리"),
			java.util.Map.entry("ladderSubtotal", "사다리 소계"), java.util.Map.entry("workMethod", "작업내용"),
			java.util.Map.entry("note", "비고"));

	/**
	 * AI가 글씨가 흐리다고 본 칸 → 수정 화면 위 "확인이 필요한 칸" 버튼 (고객 영상 분석의 "확인이 필요한 품목"과 같은 역할).
	 * 품목은 이름 · 수량 두 칸을 한 버튼으로(수량 칸으로 이동).
	 */
	public static List<ReviewChip> reviewChips(EstimateDoc d) {
		List<ReviewChip> out = new ArrayList<>();
		if (d == null || d.getReviewFields() == null) {
			return out;
		}
		java.util.Set<Integer> items = new java.util.LinkedHashSet<>();
		for (String f : d.getReviewFields()) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("item(?:Names|Quantities)\\[(\\d+)]").matcher(f);
			if (m.matches()) {
				items.add(Integer.parseInt(m.group(1)));
			} else if (FIELD_LABELS.containsKey(f)) {
				out.add(new ReviewChip(FIELD_LABELS.get(f) + (blank(d, f) ? "(빈칸)" : ""), f));
			}
		}
		for (int i : items) {
			String name = d.itemName(i).isBlank() ? (i / 4 + 1) + "행 품목" : d.itemName(i);
			out.add(new ReviewChip(name, "itemQuantities[" + i + "]"));
		}
		return out;
	}

	/** 견적에 꼭 필요한 칸이 비었는지 (SheetReader 가 빈 필수 칸을 확인 대상에 넣음) */
	private static boolean blank(EstimateDoc d, String f) {
		String v = switch (f) {
			case "customer" -> d.getCustomer();
			case "volume" -> d.getVolume();
			case "total" -> d.getTotal();
			default -> "x";
		};
		return v == null || v.isBlank();
	}

	/** 수정 화면 위 요약: 품목 수 (이름이 있는 칸) */
	public static long itemCount(EstimateDoc d) {
		return d == null ? 0 : d.getItemNames().stream().filter(n -> n != null && !n.isBlank()).count();
	}

	/** 등록 진행 상태: kind photo|pdf, step 1~4 */
	@Data
	public static class Registration implements java.io.Serializable {
		private String kind;
		private String fileName;
		/** 올린 견적서 파일 (/uploads/quotes/...) → TBL_QUOTE.DOCUMENT_PATH */
		private String filePath;
		private int step = 1;
		private EstimateDoc doc;
		/** AI가 수기 견적서를 읽었는지 (PDF 도 읽었으면 사진처럼 수정 단계로) */
		private boolean recognized;
		/** 수정 화면 위 안내 (AI 결과 · 실패 이유) */
		private String notice;
		private String name = "";
		private String date = "";
		private String status = "견적 검토";
	}

	/**
	 * PDF 첨부 (프로토타입 registrationDocumentData, 입력값 없음). 이름·날짜는 등록 단계에서 입력합니다.
	 * 프로토타입은 PDF 글자를 읽어 품목·담당자를 찾지만, 목업에서는 인식 결과 없이 진행합니다.
	 */
	public static EstimateDoc pdfDocument() {
		EstimateDoc d = new EstimateDoc();
		d.setCustomer("담당자 미인식");
		d.setPhone("010-0000-0000");
		d.setEstimateDate("견적일 미인식");
		d.setMoveDate("견적일 미인식");
		d.setTransportDate("견적일 미인식");
		d.setOrigin("출발지 미인식");
		d.setDestination("도착지 미인식");
		d.setVolume("0톤");
		d.setNote("이미지에서 인식된 견적 정보입니다.");
		d.setMovingCost("0");
		d.setExtraCost("0");
		d.setTotal("0");
		d.setDeposit("0");
		d.setBalance("0");
		d.setDepartureLadder("0");
		d.setDestinationLadder("0");
		d.setLadderSubtotal("0");
		d.setItemNames(new ArrayList<>());
		d.setItemQuantities(new ArrayList<>());
		return d.normalized();
	}
}
