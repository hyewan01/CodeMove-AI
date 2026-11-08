package com.codemove.customer.web;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.codemove.common.CodeService;
import com.codemove.common.Fmt;
import com.codemove.member.Member;
import com.codemove.member.MemberService;
import com.codemove.move.EstimateDoc;
import com.codemove.move.MoveLine;
import com.codemove.move.MoveRequest;
import com.codemove.move.MoveService;
import com.codemove.move.MoveService.CurrentMove;
import com.codemove.move.Quote;
import com.codemove.move.QuoteOption;
import com.codemove.move.QuoteService;
import com.codemove.move.Review;
import com.codemove.notification.Notification;
import com.codemove.notification.NotificationService;

/** 고객 화면 표시용 값 (DB → 화면 글자). 템플릿이 쓰는 이름은 프로토타입 목업과 같게 유지 */
@Component
public class CustomerViews {

	/** 모든 고객 화면 공통 (모델 이름 cs) */
	public record CustomerView(boolean loggedIn, String userName, String phone, boolean easy, String estimateStatus,
			int notificationCount, String requestNote, String joinedLabel, List<String> providers) {
	}

	public record HomeReview(String name, String meta, String moveType, String stars, String content) {
	}

	public record Booking(String typeLabel, String dday, String dateLabel, String timeLabel, String fromArea,
			String fromDetail, String toArea, String toDetail, String fromFull, String toFull, String scheduleLabel,
			String reservationNo, String shortSchedule) {
	}

	public record EasyStatus(String eyebrow, String title, String description, String button, String next, String icon) {
	}

	public record MoveOption(String code, String label, String icon, String description) {
	}

	public record QuoteRow(String label, String sub, String value) {
	}

	public record MoveQuote(String totalLabel, String typeLabel, String scheduleLabel, String routeLabel, List<QuoteRow> rows) {
	}

	/** 업체 견적서 원본 (TBL_QUOTE + 품목 + 옵션) */
	public record VendorDoc(String quoteNo, String manager, String managerPhone, String quoteDate, String fromAddress,
			String toAddress, String workDate, String transportDate, List<String> groups, List<List<String>> items,
			List<String> options, String workLine, String transportLine, String volume, String note, String movingCost,
			String ladderCost, String total, String fromFloor, String fromLadder, String toFloor, String toLadder,
			String ladderSubtotal, String deposit, String balance) {
	}

	public record DoneMove(String date, String from, String to, String meta) {
	}

	public record DetectedItem(String icon, String name, int count, String cbm) {
	}

	/** 견적내역 · 예약내역 목록 행 */
	public record HistoryRow(String status, String statusLabel, String date, String route, String meta) {
	}

	public record MyReview(String stars, String date, String route, String content, String moveType, int photoCount,
			boolean justNow) {
	}

	public record Alert(long id, String type, String title, String body, String time, boolean unread, String day) {
	}

	/** 현재 이사 화면 묶음 (내 이사 · 견적 상세 · 예약 상세 · 예약내역 · 견적내역) */
	public record MoveView(String status, Booking booking, MoveQuote quote, VendorDoc vendorDoc,
			List<QuoteRow> receiptRows, List<QuoteRow> moveInfoRows, List<QuoteRow> paymentRows,
			List<DetectedItem> detectedItems, String cbmLabel, String analysisSec, String confidence, String issuedLabel,
			String totalLabel, String routeLabel, String scheduleLabel, String depositLabel, String balanceLabel,
			String expiresLabel, String dday, String arrivedSummary) {
	}

	/** 후기 작성 대상 이사 */
	public record ReviewTarget(String monthDay, String fromArea, String toArea, String usage, String moveType, String date) {
	}

	private final CodeService codes;
	private final MemberService members;
	private final MoveService moves;
	private final QuoteService quotes;
	private final NotificationService notifications;

	public CustomerViews(CodeService codes, MemberService members, MoveService moves, QuoteService quotes,
			NotificationService notifications) {
		this.codes = codes;
		this.members = members;
		this.moves = moves;
		this.quotes = quotes;
		this.notifications = notifications;
	}

	/* ---------- 공통 ---------- */

	public CustomerView view(CustomerSession cs, CurrentMove current) {
		if (!cs.isLoggedIn()) {
			return new CustomerView(false, "", "", cs.isEasy(), "none", 0, "", "", List.of());
		}
		Member m = members.find(cs.getMemberId());
		String memo = current.reservation() != null && current.reservation().getMoveMemo() != null
				? current.reservation().getMoveMemo() : "";
		return new CustomerView(true, m.getMemberName(), m.getPhone(), cs.isEasy(), current.status(),
				notifications.unreadCount(cs.getMemberId()), memo, Fmt.dot(m.getJoinedAt().toLocalDate()),
				members.connectedProviders(cs.getMemberId()));
	}

	/** 원룸 · 일반포장 */
	public String typeLabel(String houseType, String packType) {
		String house = switch (houseType == null ? "" : houseType) {
			case "01" -> "원룸";
			case "02" -> "아파트";
			case "03" -> "빌라";
			case "04" -> "사무실";
			default -> "";
		};
		return house + " · " + codes.userName("PACK_TYPE", packType);
	}

	public List<MoveOption> houseTypes() {
		return List.of(new MoveOption("01", "원룸 / 오피스텔", "easy-home-studio.svg", null),
				new MoveOption("02", "아파트", "easy-home-apartment.svg", null),
				new MoveOption("03", "빌라 / 주택", "easy-home-house.svg", null),
				new MoveOption("04", "사무실", "easy-home-office.svg", null));
	}

	public List<MoveOption> packTypes() {
		return List.of(new MoveOption("01", "일반포장", "easy-pack-standard.svg", "직접 포장"),
				new MoveOption("02", "반포장", "easy-pack-partial.svg", "큰 짐은 업체가 포장"));
	}

	/* ---------- 홈 ---------- */

	public List<HomeReview> homeReviews() {
		return moves.praiseReviews(3).stream().map(r -> new HomeReview(r.getMemberName(),
				"이용 " + r.getUsageCount() + "회차 · " + ago(r.getCreatedAt()),
				houseName(r.getHouseType()) + "(" + codes.userName("PACK_TYPE", r.getPackType()) + ")", stars(r.getRating()),
				r.getContent())).toList();
	}

	public EasyStatus easyStatus(MoveView mv) {
		return switch (mv.status()) {
			case "confirmed" -> new EasyStatus("예약 완료", mv.booking().shortSchedule() + "에 이사해요", mv.booking().typeLabel(),
					"예약 자세히 보기", "/move/reservation", "/assets/reservation-confirmed-illustration.svg");
			case "approved" -> new EasyStatus("견적 도착", "이사 견적이 준비됐어요", "비용과 작업 내용을 확인해주세요.", "견적서 보기", "/move",
					"/assets/estimate-approved-illustration.svg");
			case "waiting-approval" -> new EasyStatus("관리자 확인 중", "견적 요청이 접수됐어요", "담당자 확인이 끝나면 견적 정보를 알려드려요.",
					"진행 상황 보기", "/more/estimates", "/assets/estimate-requested-illustration.svg");
			default -> null;
		};
	}

	/* ---------- 현재 이사 ---------- */

	public MoveView move(CurrentMove c) {
		MoveRequest r = c.request();
		Quote q = c.quote();
		if (r == null) {
			return new MoveView("none", null, null, null, List.of(), List.of(), List.of(), List.of(), "", "", "", "", "",
					"", "", "", "", "", "", "");
		}
		LocalDate date = c.reservation() != null ? c.reservation().getMoveDate()
				: q != null && q.getWorkDate() != null ? q.getWorkDate() : r.getMoveDate();
		String time = c.reservation() != null && c.reservation().getMoveTime() != null ? c.reservation().getMoveTime()
				: q != null && q.getWorkTime() != null ? q.getWorkTime() : r.getMoveTime();
		String type = typeLabel(r.getHouseType(), r.getPackType());
		String fromArea = Fmt.district(r.getFromAddress()), toArea = Fmt.district(r.getToAddress());
		String route = fromArea + " → " + toArea;
		String schedule = date == null ? "일정 미정" : Fmt.dot(date) + " (" + Fmt.weekday(date) + ") " + Fmt.ampm(time);
		Booking booking = new Booking(type, date == null ? "" : Fmt.dday(date), date == null ? "일정 미정" : Fmt.monthDay(date),
				Fmt.ampm(time), fromArea, streetPart(r.getFromAddress(), r.getFromDetail()), toArea,
				streetPart(r.getToAddress(), r.getToDetail()), full(r.getFromAddress(), r.getFromDetail()),
				full(r.getToAddress(), r.getToDetail()), schedule,
				c.reservation() != null ? c.reservation().getReservationNo() : "",
				date == null ? "" : date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 " + Fmt.ampm(time));

		List<DetectedItem> detected = moves.requestItems(r.getRequestId()).stream()
				.map(i -> new DetectedItem(icon(i.getItemName()), i.getItemName(), i.getQuantity(),
						String.format("%.1f", i.getVolume() == null ? 0 : i.getVolume())))
				.toList();
		String cbm = r.getAiCbm() == null ? "-" : String.format("%.1f CBM", r.getAiCbm());

		if (q == null) {
			return new MoveView(c.status(), booking, null, null, List.of(), List.of(), List.of(), detected, cbm, "", "", "",
					"", route, schedule, "", "", "", booking.dday(), "");
		}
		long total = nz(q.getTotalAmount());
		long fromLadder = nz(q.getFromLadderCost()), toLadder = nz(q.getToLadderCost());
		long options = quotes.options(q.getQuoteId()).stream().mapToLong(o -> nz(o.getOptionCost())).sum();
		long work = Math.max(0, total - nz(q.getMovingCost()) - fromLadder - toLadder - options);
		List<QuoteRow> rows = new ArrayList<>();
		rows.add(new QuoteRow("기본 운송비", "이사물량 " + (q.getVolumeTon() == null ? "-" : trim(q.getVolumeTon()) + "톤"),
				Fmt.won(q.getMovingCost())));
		if (work > 0) rows.add(new QuoteRow("포장·인력 등 작업비", null, Fmt.won(work)));
		if (options > 0) rows.add(new QuoteRow("옵션 작업비", null, Fmt.won(options)));
		if (fromLadder > 0) rows.add(new QuoteRow("출발지 사다리차 비용" + floorLabel(q.getFromLadderFloor()), null, Fmt.won(fromLadder)));
		if (toLadder > 0) rows.add(new QuoteRow("도착지 사다리차 비용" + floorLabel(q.getToLadderFloor()), null, Fmt.won(toLadder)));
		List<QuoteRow> receipt = rows.stream().map(x -> new QuoteRow(x.label(), null, x.value())).toList();
		String totalLabel = Fmt.won(total);
		MoveQuote mq = new MoveQuote(totalLabel, type, schedule, route, rows);
		List<QuoteRow> info = List.of(new QuoteRow("견적 번호", null, q.getQuoteNo()), new QuoteRow("이사일", null, schedule),
				new QuoteRow("이사 유형", null, type), new QuoteRow("출발지", null, booking.fromFull()),
				new QuoteRow("도착지", null, booking.toFull()));
		boolean paid = c.reservation() != null && "02".equals(c.reservation().getPayStatus());
		String deposit = Fmt.won(q.getDepositAmount()) + (paid ? " · 결제 완료" : " · 결제 대기");
		String balance = Fmt.won(q.getBalanceAmount()) + " · 이사 당일 결제";
		String expires = "";
		if (q.getExpiresAt() != null) {
			Duration left = Duration.between(LocalDateTime.now(), q.getExpiresAt());
			expires = left.isNegative() ? "기한 만료" : left.toDays() + "일 " + left.toHoursPart() + "시간 남음";
		}
		String arrived = (date == null ? "일정 미정" : Fmt.monthDay(date)) + " 이사 · " + Fmt.money(total) + "원";
		return new MoveView(c.status(), booking, mq, vendorDoc(q), receipt, info, rows, detected, cbm,
				r.getAiAnalysisSec() == null ? "-" : r.getAiAnalysisSec() + "초",
				r.getAiConfidence() == null ? "-" : r.getAiConfidence() + "%",
				Fmt.dot(q.getEstimateDate()) + " 발행", totalLabel, route, schedule, deposit, balance, expires,
				booking.dday(), arrived);
	}

	public VendorDoc vendorDoc(Quote q) {
		EstimateDoc d = quotes.toDoc(q);
		List<List<String>> rows = new ArrayList<>();
		for (int row = 0; row < d.getItemRowCount(); row++) {
			List<String> cells = new ArrayList<>();
			boolean any = false;
			for (int col = 0; col < 4; col++) {
				String name = d.itemName(row * 4 + col);
				cells.add(name);
				cells.add(d.itemQuantity(row * 4 + col));
				any |= !name.isEmpty();
			}
			if (any) rows.add(cells);
		}
		List<String> options = new ArrayList<>();
		for (QuoteOption o : quotes.options(q.getQuoteId())) {
			int idx = Integer.parseInt(o.getOptionCode().trim()) - 1;
			String[] opt = EstimateDoc.OPTION_ROWS[idx];
			options.add(opt[0] + "　" + opt[1] + " " + box("Y".equals(o.getChoice1Yn())) + " / " + opt[2] + " "
					+ box("Y".equals(o.getChoice2Yn())) + "　₩ " + Fmt.money(nz(o.getOptionCost())));
		}
		if (options.isEmpty()) options.add("선택한 옵션 작업 없음");
		String work = d.getWorkMethod();
		String workLine = "포장 " + box("포장".equals(work)) + "　/　일반 " + box("일반".equals(work)) + "　/　보관 " + box("보관".equals(work));
		String transport = "사다리차 " + side(q.getFromLadderUse(), q.getToLadderUse()) + "　·　승강기 "
				+ side(q.getFromElevatorUse(), q.getToElevatorUse()) + "　·　계단작업 " + side(q.getFromStairsUse(), q.getToStairsUse());
		long fromLadder = nz(q.getFromLadderCost()), toLadder = nz(q.getToLadderCost());
		List<String> groups = new ArrayList<>();
		for (String g : d.getItemGroups()) groups.add(g == null || g.isBlank() ? "기타" : g);
		return new VendorDoc(q.getQuoteNo(), "김선국", "010-9445-8224", Fmt.dot(q.getEstimateDate()) + ".",
				nz(q.getFromAddress()), nz(q.getToAddress()), q.getWorkDate() == null ? "미정" : Fmt.dot(q.getWorkDate()) + ".",
				q.getTransportDate() == null ? "미정" : Fmt.dot(q.getTransportDate()) + ".", groups, rows, options, workLine,
				transport, q.getVolumeTon() == null ? "-" : trim(q.getVolumeTon()) + " 톤(ton)", blank(q.getNote(), "-"),
				won(q.getMovingCost()), won(fromLadder + toLadder), won(q.getTotalAmount()),
				q.getFromLadderFloor() == null ? "　" : String.valueOf(q.getFromLadderFloor()), won(fromLadder),
				q.getToLadderFloor() == null ? "　" : String.valueOf(q.getToLadderFloor()), won(toLadder),
				won(fromLadder + toLadder), won(q.getDepositAmount()), won(q.getBalanceAmount()));
	}

	/* ---------- 내역 ---------- */

	/** 견적내역: 요청마다 한 줄 (status: reviewing / arrived / confirmed / expired) */
	public List<HistoryRow> estimateHistory(long memberId) {
		List<HistoryRow> rows = new ArrayList<>();
		for (MoveRequest r : moves.requests(memberId)) {
			Quote q = quotes.findByRequest(r.getRequestId());
			String code = q == null ? QuoteService.REVIEWING : q.getStatus();
			String[] st = switch (code) {
				case QuoteService.APPROVED -> new String[] { "arrived", "견적 도착" };
				case QuoteService.CONFIRMED -> new String[] { "confirmed", "확정" };
				case QuoteService.EXPIRED -> new String[] { "expired", "만료" };
				default -> new String[] { "reviewing", "검토 중" };
			};
			rows.add(new HistoryRow(st[0], st[1], Fmt.dot(r.getRequestedAt().toLocalDate()),
					Fmt.district(r.getFromAddress()) + " → " + Fmt.district(r.getToAddress()),
					typeLabel(r.getHouseType(), r.getPackType())));
		}
		return rows;
	}

	/** 지난 이사 (이사 완료 · 취소) 연도별 */
	public Map<String, List<HistoryRow>> pastReservations(long memberId) {
		Map<String, List<HistoryRow>> byYear = new LinkedHashMap<>();
		moves.reservations(memberId).stream()
				.filter(l -> !MoveService.BOOKED.equals(l.getStatus()))
				.sorted((a, b) -> b.getMoveDate().compareTo(a.getMoveDate()))
				.forEach(l -> byYear.computeIfAbsent(l.getMoveDate().getYear() + "년", k -> new ArrayList<>())
						.add(new HistoryRow(MoveService.DONE.equals(l.getStatus()) ? "completed" : "cancelled",
								MoveService.DONE.equals(l.getStatus()) ? "완료" : "취소", Fmt.dot(l.getMoveDate()),
								Fmt.district(l.getFromAddress()) + " → " + Fmt.district(l.getToAddress()),
								typeLabel(l.getHouseType(), l.getPackType()))));
		return byYear;
	}

	public List<DoneMove> doneMoves(long memberId) {
		return moves.reservations(memberId).stream()
				.filter(l -> MoveService.DONE.equals(l.getStatus()))
				.sorted((a, b) -> b.getMoveDate().compareTo(a.getMoveDate()))
				.map(l -> new DoneMove(Fmt.dot(l.getMoveDate()), Fmt.region(l.getFromAddress()), Fmt.region(l.getToAddress()),
						typeLabel(l.getHouseType(), l.getPackType()) + " · " + Fmt.won(l.getTotalAmount())))
				.toList();
	}

	/* ---------- 후기 ---------- */

	public ReviewTarget reviewTarget(long memberId) {
		MoveLine t = moves.reviewTarget(memberId);
		if (t == null) {
			return null;
		}
		long usage = moves.reservations(memberId).stream()
				.filter(l -> MoveService.DONE.equals(l.getStatus()) && !l.getMoveDate().isAfter(t.getMoveDate())).count();
		return new ReviewTarget(t.getMoveDate().getMonthValue() + "월 " + t.getMoveDate().getDayOfMonth() + "일",
				Fmt.district(t.getFromAddress()), Fmt.district(t.getToAddress()), "이용 " + usage + "회차",
				houseName(t.getHouseType()) + "(" + codes.userName("PACK_TYPE", t.getPackType()) + ")", Fmt.dot(t.getMoveDate()));
	}

	public List<MyReview> myReviews(long memberId, boolean justSubmitted) {
		List<Review> list = moves.myReviews(memberId);
		List<MyReview> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			Review r = list.get(i);
			out.add(new MyReview(stars(r.getRating()), Fmt.dot(r.getCreatedAt().toLocalDate()),
					Fmt.district(r.getFromAddress()) + " → " + Fmt.district(r.getToAddress()), r.getContent(),
					typeLabel(r.getHouseType(), r.getPackType()), r.getPhotoCount(), justSubmitted && i == 0));
		}
		return out;
	}

	/* ---------- 알림 ---------- */

	public static final List<String> ALERT_CATEGORIES = List.of("전체알림", "예약/일정 정보", "견적 정보", "결제 정보", "이야기");

	/** NTF_DETAIL → 고객 알림 분류 */
	private static String alertType(String detail) {
		return switch (detail == null ? "" : detail.trim()) {
			case "01", "02", "03" -> "견적 정보";
			case "10" -> "결제 정보";
			case "04", "05", "06", "07", "08", "09" -> "예약/일정 정보";
			default -> "이야기";
		};
	}

	public List<Alert> alerts(long memberId) {
		return notifications.memberAlerts(memberId).stream().map((Notification n) -> new Alert(n.getNotificationId(),
				alertType(n.getNtfDetail()), n.getNtfTitle(), n.getNtfContent(), ago(n.getCreatedAt()), "N".equals(n.getIsRead()),
				dayGroup(n.getCreatedAt()))).toList();
	}

	/* ---------- 도우미 ---------- */

	private static String dayGroup(LocalDateTime t) {
		long days = ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now());
		return days == 0 ? "오늘" : days == 1 ? "어제" : days < 7 ? "이번 주" : "지난 알림";
	}

	/** 방금 / 10분 전 / 14시간 전 / 어제 / 3일 전 / 2주 전 / 2026. 01. 03 */
	public static String ago(LocalDateTime t) {
		if (t == null) return "";
		Duration d = Duration.between(t, LocalDateTime.now());
		long days = ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now());
		if (d.toMinutes() < 1) return "방금";
		if (d.toHours() < 1) return d.toMinutes() + "분 전";
		if (days == 0) return d.toHours() + "시간 전";
		if (days == 1) return "어제";
		if (days < 7) return days + "일 전";
		if (days < 30) return (days / 7) + "주 전";
		return Fmt.dot(t.toLocalDate());
	}

	private static String stars(Integer rating) {
		int r = rating == null ? 0 : rating;
		return "★".repeat(r) + "☆".repeat(5 - r);
	}

	private static String houseName(String code) {
		return switch (code == null ? "" : code) {
			case "01" -> "원룸";
			case "02" -> "아파트";
			case "03" -> "빌라";
			case "04" -> "사무실";
			default -> "";
		};
	}

	/** 품목 이름 → 아이콘 */
	static String icon(String name) {
		String n = name == null ? "" : name;
		if (n.contains("냉장고")) return "item-fridge.svg";
		if (n.contains("침대")) return "item-bed.svg";
		if (n.contains("소파")) return "item-sofa.svg";
		if (n.contains("세탁기")) return "item-washer.svg";
		if (n.contains("TV") || n.contains("티비")) return "item-tv.svg";
		if (n.contains("장롱") || n.contains("옷장")) return "item-wardrobe.svg";
		if (n.contains("테이블") || n.contains("식탁") || n.contains("책상")) return "item-table.svg";
		return "item-box.svg";
	}

	/** "서울 강남구 테헤란로 123" + "301호" → "테헤란로 123, 301호" */
	private static String streetPart(String address, String detail) {
		String a = address == null ? "" : address.trim();
		String[] parts = a.split("\\s+");
		// 구·군 다음부터 (경기 성남시 분당구 판교역로 166 → 판교역로 166)
		String district = Fmt.district(a);
		int from = 2;
		for (int i = 1; i < parts.length; i++) {
			if (parts[i].equals(district)) {
				from = i + 1;
				break;
			}
		}
		String street = parts.length > from ? String.join(" ", java.util.Arrays.copyOfRange(parts, from, parts.length)) : a;
		return detail == null || detail.isBlank() ? street : street + ", " + detail;
	}

	private static String full(String address, String detail) {
		return detail == null || detail.isBlank() ? (address == null ? "" : address) : address + ", " + detail;
	}

	private static String floorLabel(Integer floor) {
		return floor == null ? "" : " (" + floor + "층)";
	}

	private static String box(boolean on) {
		return on ? "■" : "□";
	}

	private static String side(String from, String to) {
		return "전 " + box("Y".equals(from)) + " / 후 " + box("Y".equals(to));
	}

	private static String won(Long v) {
		return "₩ " + Fmt.money(v == null ? 0 : v);
	}

	private static long nz(Long v) {
		return v == null ? 0 : v;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static String trim(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private static String blank(String s, String fallback) {
		return s == null || s.isBlank() ? fallback : s;
	}

	/** 고객이 고른 품목 크기 이름 → ITEM_SIZE */
	public static String sizeCode(String size) {
		return switch (size == null ? "" : size) {
			case "소형" -> "01";
			case "대형" -> "03";
			default -> "02";
		};
	}

	/** 품목 이름 → ITEM_CATEGORY (02 전자기기, 03 생활용품, 01 가구) */
	public static String categoryOf(String name) {
		String n = name == null ? "" : name;
		if (n.matches(".*(냉장고|세탁기|TV|티비|에어컨|전자레인지|건조기|모니터|컴퓨터).*")) return "02";
		if (n.contains("박스") || n.contains("짐")) return "03";
		return "01";
	}
}
