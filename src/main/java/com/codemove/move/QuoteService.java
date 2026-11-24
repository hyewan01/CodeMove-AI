package com.codemove.move;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.common.Fmt;
import com.codemove.estimate.EstimateCalculator;

/**
 * 견적서 (관리자 견적 관리 · 수정 · 등록, 고객 견적 확인).
 * 화면의 견적서 양식(EstimateDoc) ↔ TBL_QUOTE · TBL_QUOTE_ITEM · TBL_QUOTE_OPTION 변환을 맡습니다.
 * 변환 규칙은 docs/erd/ERD_CHANGES.md "화면 값 ↔ 컬럼" 참고.
 */
@Service
public class QuoteService {

	/** QUOTE_STATUS 공통코드 */
	public static final String REVIEWING = "01", APPROVED = "02", CONFIRMED = "03", EXPIRED = "04";

	/** 견적서 화면 상태 이름 ↔ 코드 */
	public static String statusLabel(String code) {
		return switch (code) {
			case APPROVED -> "견적 승인";
			case CONFIRMED -> "견적 확정";
			case EXPIRED -> "만료";
			default -> "견적 검토";
		};
	}

	public static String statusCode(String label) {
		return switch (label) {
			case "견적 승인" -> APPROVED;
			case "견적 확정" -> CONFIRMED;
			default -> REVIEWING;
		};
	}

	/** 품목 칸 제목 → ITEM_CATEGORY (01 가구, 02 전자기기, 03 생활용품, 04 기타) */
	private static String category(String group) {
		String g = group == null ? "" : group.trim();
		return switch (g) {
			case "가구" -> "01";
			case "전자기기", "가전" -> "02";
			case "생활용품" -> "03";
			default -> "04";
		};
	}

	/** 작업내용 포장·반포장·일반·보관 ↔ PACK_TYPE 04·02·01·03 (대표님 견적서 4가지) */
	private static String workTypeCode(String method) {
		return switch (method == null ? "" : method) {
			case "포장" -> "04";
			case "반포장" -> "02";
			case "보관" -> "03";
			case "일반" -> "01";
			default -> null;
		};
	}

	static String workMethod(String code) {
		return switch (code == null ? "" : code) {
			case "04" -> "포장";
			case "02" -> "반포장";
			case "03" -> "보관";
			case "01" -> "일반";
			default -> "";
		};
	}

	private static final Pattern QUANTITY = Pattern.compile("^\\s*(\\d+)\\s*(.*)$");

	private final QuoteMapper quotes;

	public QuoteService(QuoteMapper quotes) {
		this.quotes = quotes;
	}

	public List<QuoteLine> listLines() {
		return quotes.listQuoteLines();
	}

	public Quote find(long quoteId) {
		return quotes.findQuote(quoteId);
	}

	public Quote findByRequest(long requestId) {
		return quotes.findQuoteByRequest(requestId);
	}

	public List<QuoteItem> items(long quoteId) {
		return quotes.listItems(quoteId);
	}

	public List<QuoteOption> options(long quoteId) {
		return quotes.listOptions(quoteId);
	}

	/* ---------- DB → 양식 ---------- */

	public EstimateDoc toDoc(Quote q) {
		EstimateDoc d = new EstimateDoc();
		d.setCompanyName(nz(q.getCompanyName()));
		d.setCustomer(nz(q.getContactName()));
		d.setPhone(nz(q.getContactPhone()));
		d.setEstimateDate(Fmt.yy(q.getEstimateDate()));
		d.setMoveDate(Fmt.yy(q.getWorkDate()));
		d.setTransportDate(Fmt.yy(q.getTransportDate()));
		d.setOrigin(nz(q.getFromAddress()));
		d.setDestination(nz(q.getToAddress()));
		d.setVolume(q.getVolumeTon() == null ? "" : trimZero(q.getVolumeTon()) + "톤");
		d.setNote(nz(q.getNote()));
		long fromLadder = nz(q.getFromLadderCost()), toLadder = nz(q.getToLadderCost());
		d.setMovingCost(Fmt.money(q.getMovingCost()));
		d.setExtraCost(Fmt.money(fromLadder + toLadder));
		d.setTotal(Fmt.money(q.getTotalAmount()));
		d.setDeposit(Fmt.money(q.getDepositAmount()));
		d.setBalance(Fmt.money(q.getBalanceAmount()));
		d.setDepartureFloor(q.getFromLadderFloor() == null ? "" : String.valueOf(q.getFromLadderFloor()));
		d.setDestinationFloor(q.getToLadderFloor() == null ? "" : String.valueOf(q.getToLadderFloor()));
		d.setDepartureLadder(Fmt.money(fromLadder));
		d.setDestinationLadder(Fmt.money(toLadder));
		d.setLadderSubtotal(Fmt.money(fromLadder + toLadder));
		d.setWorkMethod(workMethod(q.getWorkType()));
		d.setLadder(side(q.getFromLadderUse(), q.getToLadderUse()));
		d.setElevator(side(q.getFromElevatorUse(), q.getToElevatorUse()));
		d.setStairs(side(q.getFromStairsUse(), q.getToStairsUse()));
		d.setItemGroups(new ArrayList<>(List.of(nz(q.getItemGroup1()), nz(q.getItemGroup2()), nz(q.getItemGroup3()),
				nz(q.getItemGroup4()))));

		List<QuoteItem> items = quotes.listItems(q.getQuoteId());
		int maxSort = items.stream().mapToInt(i -> i.getSortOrder()).max().orElse(-1);
		d.setItemRowCount(Math.min(14, Math.max(8, maxSort / 4 + 1)));
		List<String> names = new ArrayList<>(Collections.nCopies(d.getItemRowCount() * 4, ""));
		List<String> qty = new ArrayList<>(Collections.nCopies(d.getItemRowCount() * 4, ""));
		for (QuoteItem i : items) {
			if (i.getSortOrder() < names.size()) {
				names.set(i.getSortOrder(), i.getItemName());
				qty.set(i.getSortOrder(), i.getQuantity() + nz(i.getUnit()));
			}
		}
		d.setItemNames(names);
		d.setItemQuantities(qty);

		List<String> costs = new ArrayList<>(Collections.nCopies(8, ""));
		List<String> selections = new ArrayList<>(Collections.nCopies(8, ""));
		for (QuoteOption o : quotes.listOptions(q.getQuoteId())) {
			int idx = Integer.parseInt(o.getOptionCode().trim()) - 1;
			if (idx < 0 || idx >= 8) {
				continue;
			}
			String[] row = EstimateDoc.OPTION_ROWS[idx];
			// 양식은 한 가지만 고르므로, 둘 다 Y 면 두 번째(설치·조립 등)를 표시
			selections.set(idx, "Y".equals(o.getChoice2Yn()) ? row[2] : "Y".equals(o.getChoice1Yn()) ? row[1] : "");
			costs.set(idx, o.getOptionCost() == null || o.getOptionCost() == 0 ? "" : Fmt.money(o.getOptionCost()));
		}
		d.setOptionCosts(costs);
		d.setOptionSelections(selections);
		return d.normalized();
	}

	/* ---------- 양식 → DB ---------- */

	/** 양식 값을 견적서에 옮김 (상태·요청 연결 등은 그대로) */
	private void applyDoc(Quote q, EstimateDoc d) {
		q.setCompanyName(blankTo(d.getCompanyName(), "명품익스프레스"));
		q.setContactName(blankTo(d.getCustomer(), "고객"));
		q.setContactPhone(d.getPhone() == null || d.getPhone().isBlank() ? null : Fmt.phone(d.getPhone()));
		LocalDate estimate = Fmt.parseDate(d.getEstimateDate());
		q.setEstimateDate(estimate != null ? estimate : q.getEstimateDate() != null ? q.getEstimateDate() : LocalDate.now());
		q.setWorkDate(Fmt.parseDate(d.getMoveDate()));
		q.setTransportDate(Fmt.parseDate(d.getTransportDate()));
		q.setFromAddress(blankTo(d.getOrigin(), null));
		q.setToAddress(blankTo(d.getDestination(), null));
		q.setVolumeTon(Fmt.parseDecimal(d.getVolume()));
		q.setNote(blankTo(d.getNote(), null));
		q.setWorkType(workTypeCode(d.getWorkMethod()));
		q.setFromLadderUse(yn("전".equals(d.getLadder())));
		q.setToLadderUse(yn("후".equals(d.getLadder())));
		q.setFromElevatorUse(yn("전".equals(d.getElevator())));
		q.setToElevatorUse(yn("후".equals(d.getElevator())));
		q.setFromStairsUse(yn("전".equals(d.getStairs())));
		q.setToStairsUse(yn("후".equals(d.getStairs())));
		q.setMovingCost(Fmt.parseMoney(d.getMovingCost()));
		q.setFromLadderFloor(floor(d.getDepartureFloor()));
		q.setToLadderFloor(floor(d.getDestinationFloor()));
		q.setFromLadderCost(nz(Fmt.parseMoney(d.getDepartureLadder())));
		q.setToLadderCost(nz(Fmt.parseMoney(d.getDestinationLadder())));
		q.setTotalAmount(Fmt.parseMoney(d.getTotal()));
		q.setDepositAmount(Fmt.parseMoney(d.getDeposit()));
		q.setBalanceAmount(Fmt.parseMoney(d.getBalance()));
		List<String> g = d.getItemGroups();
		q.setItemGroup1(blankTo(g.get(0), null));
		q.setItemGroup2(blankTo(g.get(1), null));
		q.setItemGroup3(blankTo(g.get(2), null));
		q.setItemGroup4(blankTo(g.get(3), null));
	}

	/** 품목·옵션은 지우고 다시 넣음 */
	private void replaceLines(long quoteId, EstimateDoc d) {
		quotes.deleteItems(quoteId);
		for (int idx = 0; idx < d.getItemNames().size(); idx++) {
			String name = d.itemName(idx).trim();
			if (name.isEmpty()) {
				continue;
			}
			QuoteItem item = new QuoteItem();
			item.setQuoteId(quoteId);
			item.setItemName(name.length() > 50 ? name.substring(0, 50) : name);
			item.setCategory(category(d.getItemGroups().get(idx % 4)));
			Matcher m = QUANTITY.matcher(d.itemQuantity(idx));
			if (m.matches()) {
				item.setQuantity(Math.min(999, Integer.parseInt(m.group(1))));
				item.setUnit(blankTo(m.group(2).trim(), "개"));
			} else {
				item.setQuantity(1);
				item.setUnit(blankTo(d.itemQuantity(idx).trim(), "개"));
			}
			if (item.getUnit().length() > 5) {
				item.setUnit(item.getUnit().substring(0, 5));
			}
			item.setSortOrder(idx);
			quotes.insertItem(item);
		}
		quotes.deleteOptions(quoteId);
		for (int idx = 0; idx < 8; idx++) {
			String sel = d.getOptionSelections().get(idx);
			Long cost = Fmt.parseMoney(d.getOptionCosts().get(idx));
			if ((sel == null || sel.isBlank()) && (cost == null || cost == 0)) {
				continue;
			}
			String[] row = EstimateDoc.OPTION_ROWS[idx];
			QuoteOption o = new QuoteOption();
			o.setQuoteId(quoteId);
			o.setOptionCode(String.format("%02d", idx + 1));
			o.setChoice1Yn(yn(row[1].equals(sel)));
			o.setChoice2Yn(yn(row[2].equals(sel)));
			o.setOptionCost(nz(cost));
			quotes.insertOption(o);
		}
	}

	/** 견적 수정 저장 */
	@Transactional
	public void save(long quoteId, EstimateDoc doc) {
		Quote q = quotes.findQuote(quoteId);
		if (q == null) {
			return;
		}
		EstimateDoc d = doc.normalized();
		applyDoc(q, d);
		quotes.updateQuote(q);
		replaceLines(quoteId, d);
	}

	/** 견적 승인 @return 승인한 견적 (이미 승인됐으면 null) */
	@Transactional
	public Quote approve(long quoteId, Long adminId) {
		return quotes.approve(quoteId, adminId) > 0 ? quotes.findQuote(quoteId) : null;
	}

	@Transactional
	public Quote delete(long quoteId) {
		Quote q = quotes.findQuote(quoteId);
		if (q != null) {
			quotes.deleteItems(quoteId);
			quotes.deleteOptions(quoteId);
			quotes.deleteQuote(quoteId);
		}
		return q;
	}

	/**
	 * 견적서 등록 (SCR-A-003~004): 사진·PDF 로 받은 견적서를 견적 요청 없이 등록.
	 * @param sourceType QUOTE_SOURCE 02 사진 / 03 PDF
	 */
	@Transactional
	public Quote register(EstimateDoc doc, String sourceType, String documentPath, String status, Long adminId) {
		EstimateDoc d = doc.normalized();
		Quote q = new Quote();
		q.setSourceType(sourceType);
		q.setStatus(status);
		q.setAdminId(adminId);
		q.setDocumentPath(documentPath);
		applyDoc(q, d);
		quotes.insertQuote(q);
		quotes.updateQuote(q);
		replaceLines(q.getQuoteId(), d);
		return quotes.findQuote(q.getQuoteId());
	}

	/** 고객 견적 요청에 대한 첫 견적서 (관리자 검토용, 요청 내용으로 채움) */
	@Transactional
	public Quote createForRequest(MoveRequest r, String customerName, String customerPhone, List<RequestItem> items,
			EstimateCalculator.Estimate est) {
		return createForRequest(r, customerName, customerPhone, items, est,
				EstimateCalculator.Conditions.basic(r.getPackType()));
	}

	public Quote createForRequest(MoveRequest r, String customerName, String customerPhone, List<RequestItem> items,
			EstimateCalculator.Estimate est, EstimateCalculator.Conditions cond) {
		return createForRequest(r, customerName, customerPhone, items, est, cond, null);
	}

	/**
	 * @param cond 층수 · 나르는 방법 (견적서 운반방법 · 사다리 칸) · 고객이 고른 추가 작업 (옵션 칸)
	 * @param memo 고객이 남긴 귀중품 · 요청 (견적서 비고)
	 */
	@Transactional
	public Quote createForRequest(MoveRequest r, String customerName, String customerPhone, List<RequestItem> items,
			EstimateCalculator.Estimate est, EstimateCalculator.Conditions cond, String memo) {
		Quote q = new Quote();
		q.setRequestId(r.getRequestId());
		q.setMemberId(r.getMemberId());
		q.setSourceType("01");
		q.setStatus(REVIEWING);
		q.setCompanyName("명품익스프레스");
		q.setContactName(customerName);
		q.setEstimateDate(LocalDate.now());
		quotes.insertQuote(q);

		EstimateDoc d = new EstimateDoc();
		d.setCustomer(customerName);
		d.setPhone(customerPhone);
		d.setEstimateDate(Fmt.yy(LocalDate.now()));
		// 작업일 · 운송일은 담당자가 정해 적는 칸이라 비워 둠 (고객이 고른 날짜는 예약 · 요청에 있음)
		d.setMoveDate("");
		d.setTransportDate("");
		d.setOrigin(join(r.getFromAddress(), r.getFromDetail()));
		d.setDestination(join(r.getToAddress(), r.getToDetail()));
		// 이사물량(톤) · 이사비용 · 옵션은 EstimateCalculator (대표님 방식: 적재 CBM → 5톤 + 1톤 조합 → 톤별 요금)
		d.setVolume(est.tonText());
		// 비고: 고객 메모(귀중품 · 요청)를 맨 앞에, 다른 업체에 맡긴 작업, AI 계산 요약 (500자 칸)
		List<EstimateCalculator.OptionChoice> chosen = cond.options() == null ? List.of() : cond.options();
		String others = String.join("·", chosen.stream().filter(c -> c.other()).map(c -> c.name()).toList());
		String note = (memo == null || memo.isBlank() ? "" : memo.strip() + " / ")
				+ (others.isEmpty() ? "" : "다른 업체: " + others + " / ")
				+ "AI 예상: 물품 " + trimZero(est.itemCbm()) + " CBM → 적재 " + trimZero(est.loadCbm()) + " CBM → "
				+ est.truckLabel() + ". 범위 " + Fmt.money(est.totalMin()) + "~" + Fmt.money(est.totalMax())
				+ "원. 현장 여건에 따라 별도 비용이 발생할 수 있습니다.";
		d.setNote(note.length() > 500 ? note.substring(0, 500) : note);
		d.setWorkMethod(workMethod(r.getPackType()));
		// 운반방법: 양식은 칸마다 전(출발)·후(도착) 하나만 고르므로 출발지를 먼저 표시
		EstimateCalculator.Site from = cond.from() == null ? EstimateCalculator.Site.UNKNOWN : cond.from();
		EstimateCalculator.Site to = cond.to() == null ? EstimateCalculator.Site.UNKNOWN : cond.to();
		d.setLadder(side(from.has(EstimateCalculator.Carry.LADDER), to.has(EstimateCalculator.Carry.LADDER)));
		d.setElevator(side(from.has(EstimateCalculator.Carry.ELEVATOR), to.has(EstimateCalculator.Carry.ELEVATOR)));
		d.setStairs(side(from.has(EstimateCalculator.Carry.STAIRS), to.has(EstimateCalculator.Carry.STAIRS)));
		d.setDepartureFloor(from.floor() == null ? "" : String.valueOf(from.floor()));
		d.setDestinationFloor(to.floor() == null ? "" : String.valueOf(to.floor()));
		long fromLadder = feeMid(est, "출발지 사다리차"), toLadder = feeMid(est, "도착지 사다리차");
		d.setDepartureLadder(Fmt.money(fromLadder));
		d.setDestinationLadder(Fmt.money(toLadder));
		d.setLadderSubtotal(Fmt.money(fromLadder + toLadder));
		d.setExtraCost(Fmt.money(fromLadder + toLadder));
		long moving = 0, options = 0;
		List<String> costs = new ArrayList<>(Collections.nCopies(8, ""));
		List<String> selections = new ArrayList<>(Collections.nCopies(8, ""));
		for (EstimateCalculator.Fee f : est.fees()) {
			long mid = Math.round((f.min() + f.max()) / 2.0 / 10_000) * 10_000;
			int idx = optionIndex(f.label());
			if (f.label().contains("사다리차")) {
				continue; // 사다리 칸(별도비용)에 들어감
			}
			if (f.label().contains(" 계단 ")) {
				idx = 5; // 수작업 · 계단
			}
			if (idx < 0) {
				moving += mid;
			} else {
				options += mid;
				long prev = Fmt.parseMoney(costs.get(idx)) == null ? 0 : Fmt.parseMoney(costs.get(idx));
				costs.set(idx, Fmt.money(prev + mid));
				// 양식은 한 가지만 표시: 고객이 고른 쪽 (둘 다면 두 번째), 고르지 않았으면 피아노 일반 · 수작업 계단 · 나머지 설치
				final int row = idx;
				EstimateCalculator.OptionChoice c = chosen.stream().filter(x -> x.index() == row).findFirst()
						.orElse(null);
				int pick = c != null ? (c.second() ? 2 : 1) : idx == 0 || idx == 5 ? 1 : 2;
				selections.set(idx, EstimateDoc.OPTION_ROWS[idx][pick]);
			}
		}
		long amount = moving + options + fromLadder + toLadder;
		d.setMovingCost(Fmt.money(moving));
		d.setOptionCosts(costs);
		d.setOptionSelections(selections);
		d.setTotal(Fmt.money(amount));
		d.setDeposit(Fmt.money(amount / 10));
		d.setBalance(Fmt.money(amount - amount / 10));
		// AI 인식 품목을 가전 / 가구 / 생활용품 칸에 나눠 담음
		d.setItemGroups(new ArrayList<>(List.of("전자기기", "가구", "생활용품", "기타")));
		List<String> names = new ArrayList<>(Collections.nCopies(14 * 4, ""));
		List<String> qty = new ArrayList<>(Collections.nCopies(14 * 4, ""));
		int[] rowOf = new int[4];
		int maxRow = 0;
		for (RequestItem i : items) {
			int col = switch (nz(i.getCategory())) { case "02" -> 0; case "01" -> 1; case "03" -> 2; default -> 3; };
			if (rowOf[col] >= 14) {
				continue;
			}
			names.set(rowOf[col] * 4 + col, i.getItemName());
			qty.set(rowOf[col] * 4 + col, String.valueOf(i.getQuantity()));
			maxRow = Math.max(maxRow, ++rowOf[col]);
		}
		d.setItemRowCount(Math.max(8, maxRow));
		d.setItemNames(names);
		d.setItemQuantities(qty);
		save(q.getQuoteId(), d);
		return quotes.findQuote(q.getQuoteId());
	}

	@Transactional
	public void updateStatus(long quoteId, String status) {
		quotes.updateStatus(quoteId, status);
	}

	/* ---------- 도우미 ---------- */

	private static String side(boolean from, boolean to) {
		return from ? "전" : to ? "후" : "";
	}

	/** 이름이 prefix 로 시작하는 요금들의 가운데 값 합 (만 원 단위) */
	private static long feeMid(EstimateCalculator.Estimate est, String prefix) {
		return est.fees().stream().filter(f -> f.label().startsWith(prefix))
				.mapToLong(f -> Math.round((f.min() + f.max()) / 2.0 / 10_000) * 10_000).sum();
	}

	/** 계산기 요금 이름 → 견적서 옵션 칸 (QUOTE_OPTION - 1), 옵션이 아니면 -1 */
	private static int optionIndex(String label) {
		if (label.startsWith("피아노")) return 0;
		if (label.startsWith("돌침대")) return 1;
		if (label.startsWith("조립장")) return 2;
		if (label.startsWith("벽걸이 TV")) return 3;
		if (label.startsWith("홈씨어터")) return 4;
		if (label.startsWith("안마의자")) return 6;
		if (label.startsWith("에어컨")) return 7;
		return -1;
	}

	private static String side(String from, String to) {
		return "Y".equals(from) ? "전" : "Y".equals(to) ? "후" : "";
	}

	private static String yn(boolean b) {
		return b ? "Y" : "N";
	}

	private static Integer floor(String s) {
		String digits = s == null ? "" : s.replaceAll("\\D", "");
		return digits.isEmpty() ? null : Math.min(999, Integer.parseInt(digits));
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static long nz(Long v) {
		return v == null ? 0 : v;
	}

	private static String blankTo(String s, String fallback) {
		return s == null || s.isBlank() ? fallback : s.trim();
	}

	private static String join(String a, String b) {
		return b == null || b.isBlank() ? nz(a) : nz(a) + ", " + b.trim();
	}

	static String trimZero(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}
}
