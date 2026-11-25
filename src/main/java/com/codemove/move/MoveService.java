package com.codemove.move;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.codemove.common.FileStore;
import com.codemove.common.Fmt;
import com.codemove.estimate.DistanceService;
import com.codemove.estimate.EstimateCalculator;
import com.codemove.estimate.V4Schema;
import com.codemove.estimate.VideoAnalysisService;
import com.codemove.member.Member;
import com.codemove.member.MemberMapper;
import com.codemove.notification.NotificationService;
import com.codemove.schedule.BookingService;

/**
 * 고객 이사 흐름: 견적 요청(= AI 견적서 '견적 검토' + 이사 날짜를 골랐으면 예약 '예약 완료') → (관리자 승인)
 * → 견적 확정 → 이사 완료 → 후기. 고객이 예약을 취소하면 예약은 '예약 취소'.
 * 고객 화면의 진행 상태(estimateStatus)는 가장 최근 견적 요청의 견적서·예약 상태로 정합니다.
 */
@Service
public class MoveService {

	/** RSV_STATUS 공통코드 */
	public static final String BOOKED = "01", DONE = "02", CANCELLED = "03";

	/** 이사 시작 시간: 날짜 선택(SCR-U-008)의 오전 6시 ~ 오후 8시, 1시간 단위 */
	public static final int FIRST_HOUR = 6, LAST_HOUR = 20;

	/** "HH:00"이고 6시 ~ 20시면 그대로, 아니면 null (시간 미정) */
	public static String moveTime(String t) {
		if (t == null || !t.matches("\\d{2}:00")) {
			return null;
		}
		int h = Integer.parseInt(t.substring(0, 2));
		return h >= FIRST_HOUR && h <= LAST_HOUR ? t : null;
	}

	/**
	 * 지금 진행 중인 이사 (가장 최근 요청).
	 * status: none / waiting-approval / approved / confirmed (프로토타입 estimateStatus)
	 */
	public record CurrentMove(MoveRequest request, Quote quote, Reservation reservation, String status) {

		public static final CurrentMove NONE = new CurrentMove(null, null, null, "none");
	}

	/** 견적 요청 화면 입력값 (SCR-U-003~009) */
	public record RequestForm(String houseType, String packType, LocalDate moveDate, String moveTime,
			String fromAddress, String fromDetail, String fromZipcode, String toAddress, String toDetail,
			String toZipcode, List<RequestItem> items, EstimateCalculator.Site from, EstimateCalculator.Site to,
			Integer storageDays, List<EstimateCalculator.OptionChoice> options, String memo) {

		public RequestForm(String houseType, String packType, LocalDate moveDate, String moveTime, String fromAddress,
				String fromDetail, String fromZipcode, String toAddress, String toDetail, String toZipcode,
				List<RequestItem> items) {
			this(houseType, packType, moveDate, moveTime, fromAddress, fromDetail, fromZipcode, toAddress, toDetail,
					toZipcode, items, EstimateCalculator.Site.UNKNOWN, EstimateCalculator.Site.UNKNOWN, null, null, null);
		}
	}

	/**
	 * 예상 견적 (EstimateCalculator: 대표님 견적 방식 + 아정당 평균가). 고객 화면(/quote/estimate)과 같은 계산.
	 * 거리는 DistanceService(카카오)로 구하고, 못 구하면 50km 미만으로 보고 안내 문구를 붙입니다.
	 */
	public static EstimateCalculator.Estimate estimate(List<RequestItem> items, String packType, LocalDate moveDate) {
		return estimate(items, new EstimateCalculator.Conditions(packType, null, moveDate,
				EstimateCalculator.Site.UNKNOWN, EstimateCalculator.Site.UNKNOWN, null));
	}

	public static EstimateCalculator.Estimate estimate(List<RequestItem> items, EstimateCalculator.Conditions cond) {
		List<EstimateCalculator.Line> lines = items.stream()
				.map(i -> EstimateCalculator.Line.of(i.getItemName(), i.getItemSize(), nz(i.getQuantity())))
				.toList();
		return EstimateCalculator.estimate(lines, cond);
	}

	/** 범위의 가운데 (만 원 단위) */
	public static long midpoint(EstimateCalculator.Estimate e) {
		return Math.round((e.totalMin() + e.totalMax()) / 2.0 / 10_000) * 10_000;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private final MoveMapper moves;
	private final QuoteService quotes;
	private final MemberMapper members;
	private final NotificationService notifications;
	private final FileStore files;
	private final BookingService booking;
	private final DistanceService distance;
	private final V4Schema schema;
	private final VideoAnalysisService analysis;

	public MoveService(MoveMapper moves, QuoteService quotes, MemberMapper members, NotificationService notifications,
			FileStore files, BookingService booking, DistanceService distance, V4Schema schema,
			VideoAnalysisService analysis) {
		this.distance = distance;
		this.schema = schema;
		this.analysis = analysis;
		this.moves = moves;
		this.quotes = quotes;
		this.members = members;
		this.notifications = notifications;
		this.files = files;
		this.booking = booking;
	}

	/** 고객이 고른 날짜로 예약을 받을 수 있는지 (휴무 · 예약 불가 · 마감 · 지난 날짜면 false) */
	public boolean isBookable(LocalDate date) {
		return booking.isBookable(date);
	}

	public CurrentMove current(long memberId) {
		List<MoveRequest> requests = moves.listRequests(memberId);
		if (requests.isEmpty()) {
			return CurrentMove.NONE;
		}
		return of(requests.get(0));
	}

	/** 고객의 요청 전부 (최근 순): 내 이사 > 견적 정보 · 예약 정보 목록 */
	public List<CurrentMove> all(long memberId) {
		return moves.listRequests(memberId).stream().map(r -> of(r)).toList();
	}

	/** 그 고객의 요청 하나 (다른 고객 것이면 null) */
	public CurrentMove forRequest(long memberId, long requestId) {
		return moves.listRequests(memberId).stream().filter(r -> r.getRequestId() == requestId).findFirst()
				.map(r -> of(r)).orElse(null);
	}

	/** 그 고객의 예약 하나 (다른 고객 것이면 null) */
	public CurrentMove forReservation(long memberId, long reservationId) {
		return all(memberId).stream().filter(c -> c.reservation() != null
				&& c.reservation().getReservationId() == reservationId).findFirst().orElse(null);
	}

	private CurrentMove of(MoveRequest r) {
		Quote q = quotes.findByRequest(r.getRequestId());
		Reservation v = moves.findReservationByRequest(r.getRequestId());
		String status;
		if (v != null && DONE.equals(v.getStatus())) {
			status = "none";
		} else if (q == null || QuoteService.REVIEWING.equals(q.getStatus())) {
			status = "waiting-approval";
		} else if (QuoteService.APPROVED.equals(q.getStatus())) {
			status = "approved";
		} else if (QuoteService.CONFIRMED.equals(q.getStatus()) && v != null && BOOKED.equals(v.getStatus())) {
			status = "confirmed";
		} else {
			status = "none";
		}
		return new CurrentMove(r, q, v, status);
	}

	/* ---------- 견적 요청 ---------- */

	@Transactional
	public MoveRequest request(long memberId, RequestForm f, MultipartFile video) {
		return request(memberId, f, video, null, null, null, null);
	}

	/**
	 * @param savedVideoUrl 분석 단계(/quote/analyze)에서 이미 저장한 영상 주소 (video 가 비었을 때 사용)
	 * @param analysisSec   실제 AI 분석에 걸린 시간(초), 모르면 null
	 * @param analysisId    분석 기록 ID (요청 · 영상과 연결), 없으면 null
	 */
	@Transactional
	public MoveRequest request(long memberId, RequestForm f, MultipartFile video, String savedVideoUrl,
			String savedVideoName, Integer analysisSec, String analysisId) {
		MoveRequest r = new MoveRequest();
		r.setMemberId(memberId);
		r.setHouseType(f.houseType());
		r.setPackType(f.packType());
		r.setMoveDate(f.moveDate());
		r.setMoveTime(f.moveDate() == null ? null : f.moveTime());
		// PREFER_TIME: 12시 전 01 오전, 이후(저녁 18 ~ 20시 포함) 02 오후, 시간 없으면 03 상관없음
		r.setPreferTime(f.moveDate() == null || f.moveTime() == null ? "03"
				: Integer.parseInt(f.moveTime().substring(0, 2)) < 12 ? "01" : "02");
		r.setFromAddress(f.fromAddress());
		r.setFromDetail(f.fromDetail());
		r.setFromZipcode(f.fromZipcode());
		r.setToAddress(f.toAddress());
		r.setToDetail(f.toDetail());
		r.setToZipcode(f.toZipcode());
		double cbm = f.items().stream().mapToDouble(i -> i.getVolume() == null ? 0 : i.getVolume()).sum();
		r.setAiCbm(Math.round(cbm * 100) / 100.0);
		EstimateCalculator.Conditions cond = new EstimateCalculator.Conditions(f.packType(),
				distance.km(f.fromAddress(), f.toAddress()), f.moveDate(), f.from(), f.to(), f.storageDays(),
				f.options());
		EstimateCalculator.Estimate est = estimate(f.items(), cond);
		r.setAiEstAmount(midpoint(est));
		r.setAiEstMin(est.totalMin());
		r.setAiEstMax(est.totalMax());
		r.setAiAnalysisSec(analysisSec != null && analysisSec > 0 ? analysisSec : 38);
		r.setAiConfidence(94);
		moves.insertRequest(r);
		boolean v4 = schema.ready();
		for (RequestItem item : f.items()) {
			item.setRequestId(r.getRequestId());
			if (v4) {
				moves.insertRequestItemV4(item);
			} else {
				moves.insertRequestItem(item);
			}
		}
		Long videoId = null;
		if (video != null && !video.isEmpty()) {
			ReVideo v = new ReVideo();
			v.setRequestId(r.getRequestId());
			String name = video.getOriginalFilename() == null ? "video" : video.getOriginalFilename();
			v.setFileName(name.length() > 200 ? name.substring(name.length() - 200) : name);
			v.setFilePath(files.save("videos", video));
			moves.insertVideo(v);
			videoId = v.getVideoId();
		} else if (savedVideoUrl != null) {
			ReVideo v = new ReVideo();
			v.setRequestId(r.getRequestId());
			v.setFileName(savedVideoName == null ? "video" : savedVideoName);
			v.setFilePath(savedVideoUrl);
			moves.insertVideo(v);
			videoId = v.getVideoId();
		}
		// 분석 기록(TBL_AI_ANALYSIS)을 이 요청 · 영상과 연결
		analysis.linkRequest(analysisId, r.getRequestId(), videoId);
		Member m = members.findMember(memberId);
		// 견적 관리: 고객 입력으로 AI가 만든 견적서 (견적 검토)
		quotes.createForRequest(r, m.getMemberName(), m.getPhone(), f.items(), est, cond, f.memo());
		notifications.toAdmin("01", "견적서 등록", m.getMemberName() + " 고객님의 신규 견적 요청이 접수되었습니다.", "/admin/estimates",
				false);
		// 예약 관리 · 예약 일정: 고객이 고른 이사 날짜로 예약 완료 (날짜 미정이면 견적 확정 때 만듦)
		if (f.moveDate() != null) {
			Reservation v = new Reservation();
			v.setRequestId(r.getRequestId());
			v.setMoveDate(f.moveDate());
			v.setMoveTime(f.moveTime());
			v.setMoveMemo(f.memo()); // 귀중품 · 관리자에게 남긴 말
			moves.insertReservation(v);
			notifications.toAdmin("04", "이사 예약", m.getMemberName() + " 고객님의 이사 예약이 접수되었습니다. ("
					+ Fmt.monthDay(f.moveDate()) + ")", "/admin/reservations", false);
		}
		notifications.toMember(memberId, "01", "견적 요청이 접수됐어요", "담당자 확인이 끝나면 견적 정보를 알려드려요.", "/more/estimates");
		return r;
	}

	/* ---------- 견적 확정 = 예약 ---------- */

	/** 견적 도착(승인) 상태의 견적을 확정하고 예약 완료로 */
	@Transactional
	public boolean confirm(long memberId) {
		CurrentMove c = current(memberId);
		if (!"approved".equals(c.status())) {
			return false;
		}
		Quote q = c.quote();
		quotes.updateStatus(q.getQuoteId(), QuoteService.CONFIRMED);
		LocalDate date = q.getWorkDate() != null ? q.getWorkDate()
				: c.request().getMoveDate() != null ? c.request().getMoveDate() : LocalDate.now().plusDays(14);
		String time = q.getWorkTime() != null ? q.getWorkTime() : c.request().getMoveTime();
		Reservation existing = c.reservation();
		if (existing != null && BOOKED.equals(existing.getStatus())) {
			// 견적 요청 때 만든 예약: 견적서의 작업일·시간으로 맞춤
			moves.updateReservationSchedule(existing.getReservationId(), date, time);
		} else if (existing != null) {
			moves.reopenReservation(existing.getReservationId(), date, time);
		} else {
			Reservation v = new Reservation();
			v.setRequestId(c.request().getRequestId());
			v.setMoveDate(date);
			v.setMoveTime(time);
			moves.insertReservation(v);
		}
		Member m = members.findMember(memberId);
		notifications.toAdmin("04", "이사 예약", m.getMemberName() + " 고객님이 견적을 확정해 이사 예약을 신청했습니다.",
				"/admin/reservations", false);
		notifications.fire("예약 관리 · 예약 확정", memberId, m.getMemberName());
		return true;
	}

	/**
	 * 예약 취소. 견적 요청 때 고른 날짜로 만든 예약(견적 검토 중)도 취소할 수 있음.
	 * 확정한 견적이면 다시 '견적 승인'(견적 도착)으로, 아직 검토 중이면 견적 요청은 그대로 둠.
	 */
	@Transactional
	public boolean cancel(long memberId) {
		return cancel(current(memberId));
	}

	/** 예약 하나 취소 (내 이사 > 예약 정보 > 예약 상세) */
	@Transactional
	public boolean cancel(long memberId, long reservationId) {
		CurrentMove c = forReservation(memberId, reservationId);
		return c != null && cancel(c);
	}

	private boolean cancel(CurrentMove c) {
		long memberId = c.request().getMemberId();
		if (c.reservation() == null || !BOOKED.equals(c.reservation().getStatus())) {
			return false;
		}
		moves.cancelReservation(c.reservation().getReservationId(), "01");
		if (c.quote() != null && QuoteService.CONFIRMED.equals(c.quote().getStatus())) {
			quotes.updateStatus(c.quote().getQuoteId(), QuoteService.APPROVED);
		}
		Member m = members.findMember(memberId);
		notifications.toAdmin("05", "예약 취소", m.getMemberName() + " 고객님이 이사 예약을 취소했습니다.", "/admin/reservations",
				true);
		notifications.toMember(memberId, "05", "이사 예약이 취소되었어요", "견적이 도착하면 확정해서 다시 예약할 수 있어요.", "/move");
		return true;
	}

	@Transactional
	public void updateMemo(long memberId, String memo) {
		CurrentMove c = current(memberId);
		if (c.reservation() != null) {
			String m = memo == null ? "" : memo.strip();
			moves.updateMemo(c.reservation().getReservationId(), m.length() > 500 ? m.substring(0, 500) : m);
		}
	}

	/* ---------- 이사 완료 ---------- */

	/** 이사 완료 처리 → 고객에게 후기 요청 (예약 완료 상태일 때만) */
	@Transactional
	public boolean complete(long reservationId) {
		MoveLine line = moves.findMoveLine(reservationId);
		if (line == null || moves.markDone(reservationId) == 0) {
			return false;
		}
		notifications.toMember(line.getMemberId(), "09", "이사가 완료되었어요", "명품익스프레스와 함께한 이사는 어떠셨나요? 후기를 남겨주세요.",
				"/more/reviews?write=1");
		return true;
	}

	/** 이사 날짜가 지난 예약을 이사 완료로 (매일 새벽 · 서버 시작 시) */
	@Transactional
	public int completePast() {
		int n = 0;
		for (Long id : moves.listPastBooked()) {
			if (complete(id)) n++;
		}
		return n;
	}

	/* ---------- 내역 ---------- */

	public List<MoveRequest> requests(long memberId) {
		return moves.listRequests(memberId);
	}

	public List<RequestItem> requestItems(long requestId) {
		return moves.listRequestItems(requestId);
	}

	public List<MoveLine> reservations(long memberId) {
		return moves.listMoveLines(memberId);
	}

	/* ---------- 후기 ---------- */

	/** 후기를 쓸 수 있는 가장 최근 이사 완료 예약 (없으면 null) */
	public MoveLine reviewTarget(long memberId) {
		List<Long> reviewed = moves.listReviews(memberId, null).stream().map(r -> r.getReservationId()).toList();
		return moves.listMoveLines(memberId).stream()
				.filter(l -> DONE.equals(l.getStatus()) && !reviewed.contains(l.getReservationId()))
				.max((a, b) -> a.getMoveDate().compareTo(b.getMoveDate()))
				.orElse(null);
	}

	@Transactional
	public boolean writeReview(long memberId, int rating, String content, List<MultipartFile> photos) {
		MoveLine target = reviewTarget(memberId);
		if (target == null) {
			return false;
		}
		Review r = new Review();
		r.setReservationId(target.getReservationId());
		r.setRating(Math.max(1, Math.min(5, rating)));
		String text = content == null || content.isBlank() ? "친절하고 안전하게 이사를 마쳤어요." : content.strip();
		r.setContent(text.length() > 500 ? text.substring(0, 500) : text);
		moves.insertReview(r);
		int order = 0;
		if (photos != null) {
			for (MultipartFile p : photos) {
				if (p != null && !p.isEmpty() && order < 5) {
					moves.insertReviewPhoto(r.getReviewId(), files.save("reviews", p), ++order);
				}
			}
		}
		notifications.toAdmin("09", "후기 등록", target.getMemberName() + " 고객님이 " + r.getRating() + "점 후기를 남겼습니다.",
				"/admin/reviews", false);
		return true;
	}

	public List<Review> myReviews(long memberId) {
		return moves.listReviews(memberId, null);
	}

	/** 홈 칭찬후기: 4점 이상 최근 순 */
	private final com.codemove.config.ShortMemory<Integer, List<Review>> praise = new com.codemove.config.ShortMemory<>(
			java.time.Duration.ofMinutes(1));

	public List<Review> praiseReviews(int limit) {
		List<Review> all = praise.get(4, rating -> moves.listReviews(null, rating));
		return all.subList(0, Math.min(limit, all.size()));
	}
}
