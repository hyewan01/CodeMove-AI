package com.codemove.admin.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.codemove.common.Fmt;
import com.codemove.move.EstimateDoc;
import com.codemove.move.QuoteLine;
import com.codemove.move.QuoteService;

import lombok.Data;

/**
 * 견적 관리 목록 행 · 견적서 등록 단계 (SCR-A-001~004).
 * 사진·PDF 인식은 AI 모듈 연동 전이라 시연값을 채우고, 관리자가 고친 뒤 등록하면 TBL_QUOTE 에 저장합니다.
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
			r.videoUrl = q.getVideoPath();
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

	/** 등록 진행 상태: kind photo|pdf, step 1~4 */
	@Data
	public static class Registration implements java.io.Serializable {
		private String kind;
		private String fileName;
		/** 올린 견적서 파일 (/uploads/quotes/...) → TBL_QUOTE.DOCUMENT_PATH */
		private String filePath;
		private int step = 1;
		private EstimateDoc doc;
		private String name = "";
		private String date = "";
		private String status = "견적 검토";
	}

	private static final String[][] RECOGNIZED_ITEMS = { { "모니터", "6" }, { "컴퓨터", "3" }, { "공기청정기", "1" },
			{ "소형 냉장고", "1" }, { "전자레인지", "1" }, { "프린터/복합기", "1" }, { "TV", "1" }, { "의자", "6" }, { "테이블", "2" },
			{ "원형 테이블", "1" }, { "파티션", "3" }, { "대형 캐비넷", "2" }, { "책상", "2" }, { "데스크 수납함", "3" }, { "보조 서랍장", "3" } };

	/** 사진 견적서 AI 인식 결과 (프로토타입 analyzeDocument → registrationDocumentData, 목업) */
	public static EstimateDoc recognizePhoto() {
		Random random = new Random();
		String[] customers = { "김민준", "이서연", "박도윤", "최지우", "정하린" };
		EstimateDoc d = new EstimateDoc();
		d.setCompanyName("명품익스프레스");
		d.setCustomer(customers[random.nextInt(customers.length)]);
		d.setPhone(String.format("010-%04d-%04d", 1000 + random.nextInt(9000), 1000 + random.nextInt(9000)));
		d.setEstimateDate(today());
		d.setMoveDate(today());
		d.setTransportDate(today());
		d.setOrigin("출발지 미인식");
		d.setDestination("도착지 미인식");
		d.setVolume("3.5톤");
		d.setNote("이미지에서 인식된 견적 정보입니다.");
		d.setMovingCost("0");
		d.setExtraCost("0");
		d.setTotal("1,320,000");
		d.setDeposit("132,000");
		d.setBalance("1,188,000");
		d.setDepartureLadder("0");
		d.setDestinationLadder("0");
		d.setLadderSubtotal("0");
		d.setOptionCosts(new ArrayList<>(Collections.nCopies(8, "0")));
		List<String> names = new ArrayList<>();
		List<String> quantities = new ArrayList<>();
		for (String[] item : RECOGNIZED_ITEMS) {
			names.add(item[0]);
			quantities.add(item[1]);
		}
		d.setItemNames(names);
		d.setItemQuantities(quantities);
		d.setItemRowCount(Math.max(8, (int) Math.ceil(RECOGNIZED_ITEMS.length / 4.0)));
		return d.normalized();
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
