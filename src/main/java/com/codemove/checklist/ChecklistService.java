package com.codemove.checklist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codemove.move.MoveService;
import com.codemove.move.MoveService.CurrentMove;

/**
 * 이사 준비 체크리스트.
 * <ul>
 * <li>기본 할 일: checklist/move-checklist.csv (코드 · 단계 · 할 일 · 설명 · 어디서 · 바로가기). 글은 우리 말로 새로 씀
 * (아정당 체크리스트는 어떤 일이 필요한지만 참고, 문구 · 이미지는 저작권이 있어 옮기지 않음)</li>
 * <li>고객이 체크한 상태와 직접 넣은 할 일은 TBL_MOVE_CHECKLIST 에, 이사(요청)마다 따로</li>
 * <li>이사 날짜가 있으면 D-day 로 "지금 할 일" 단계를 표시</li>
 * </ul>
 */
@Service
public class ChecklistService {

	/** 단계 코드 → 이름 · 언제 */
	public static final Map<String, String[]> PHASES = new LinkedHashMap<>();
	static {
		PHASES.put("01", new String[] { "한 달 전", "D-30" });
		PHASES.put("02", new String[] { "열흘 전", "D-10" });
		PHASES.put("03", new String[] { "사흘 전", "D-3" });
		PHASES.put("04", new String[] { "이사 전날", "D-1" });
		PHASES.put("05", new String[] { "이사 당일 · 출발지", "D-day" });
		PHASES.put("06", new String[] { "이사 당일 · 도착지", "D-day" });
		PHASES.put("07", new String[] { "이사 후", "14일 안" });
	}

	/** 기본 할 일 한 줄 (CSV) */
	public record Template(String code, String phase, String title, String detail, String where, String link) {
	}

	/** 화면의 할 일 한 줄. id: 기본 항목은 코드(M01), 고객 항목은 C + 번호 */
	public record Item(String id, String title, String detail, String where, String link, boolean done, boolean custom) {
	}

	public record Phase(String code, String label, String when, List<Item> items, long done, boolean now) {
	}

	/** dday: "D-3" · "D-day" · "D+2" · "" (날짜 없음) */
	public record View(List<Phase> phases, long done, int total, String dday, String moveDate, String nowPhase) {

		/** 지금 할 단계 이름 (날짜 없으면 "") */
		public String nowLabel() {
			return nowPhase == null ? "" : PHASES.get(nowPhase)[0];
		}

		public int percent() {
			return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
		}
	}

	private static volatile List<Template> templates;

	private final ChecklistMapper mapper;
	private final MoveService moves;

	public ChecklistService(ChecklistMapper mapper, MoveService moves) {
		this.mapper = mapper;
		this.moves = moves;
	}

	public View view(long memberId) {
		CurrentMove c = moves.current(memberId);
		long requestId = requestId(c);
		LocalDate date = moveDate(c);
		Map<String, ChecklistRow> byCode = new LinkedHashMap<>();
		List<ChecklistRow> custom = new ArrayList<>();
		for (ChecklistRow r : mapper.list(memberId, requestId)) {
			if (r.getItemCode() != null) {
				byCode.put(r.getItemCode(), r);
			} else {
				custom.add(r);
			}
		}
		String now = date == null ? null : phaseFor(ChronoUnit.DAYS.between(LocalDate.now(), date));
		List<Phase> phases = new ArrayList<>();
		long done = 0;
		int total = 0;
		for (var p : PHASES.entrySet()) {
			List<Item> items = new ArrayList<>();
			for (Template t : templates()) {
				if (t.phase().equals(p.getKey())) {
					ChecklistRow r = byCode.get(t.code());
					items.add(new Item(t.code(), t.title(), t.detail(), t.where(), t.link(),
							r != null && "Y".equals(r.getDoneYn()), false));
				}
			}
			for (ChecklistRow r : custom) {
				if (p.getKey().equals(r.getPhase())) {
					items.add(new Item("C" + r.getCheckId(), r.getTitle(), "", "", "", "Y".equals(r.getDoneYn()), true));
				}
			}
			long d = items.stream().filter(i -> i.done()).count();
			done += d;
			total += items.size();
			phases.add(new Phase(p.getKey(), p.getValue()[0], p.getValue()[1], items, d, p.getKey().equals(now)
					|| ("05".equals(now) && "06".equals(p.getKey()))));
		}
		return new View(phases, done, total, dday(date), date == null ? "" : com.codemove.common.Fmt.dot(date), now);
	}

	/** 체크 / 해제. 처음 체크하는 기본 항목은 줄을 만듦. 바뀐 뒤 상태 */
	@Transactional
	public boolean toggle(long memberId, String id) {
		long requestId = requestId(moves.current(memberId));
		List<ChecklistRow> rows = mapper.list(memberId, requestId);
		if (id != null && id.startsWith("C")) {
			long checkId = Long.parseLong(id.substring(1));
			ChecklistRow r = rows.stream().filter(x -> x.getCheckId() == checkId).findFirst().orElse(null);
			if (r == null) {
				return false;
			}
			boolean on = !"Y".equals(r.getDoneYn());
			mapper.setDone(checkId, memberId, on ? "Y" : "N");
			return on;
		}
		Template t = templates().stream().filter(x -> x.code().equals(id)).findFirst().orElse(null);
		if (t == null) {
			return false;
		}
		ChecklistRow r = rows.stream().filter(x -> t.code().equals(x.getItemCode())).findFirst().orElse(null);
		if (r == null) {
			ChecklistRow n = new ChecklistRow();
			n.setMemberId(memberId);
			n.setRequestId(requestId);
			n.setItemCode(t.code());
			n.setPhase(t.phase());
			n.setDoneYn("Y");
			mapper.insert(n);
			return true;
		}
		boolean on = !"Y".equals(r.getDoneYn());
		mapper.setDone(r.getCheckId(), memberId, on ? "Y" : "N");
		return on;
	}

	/** 고객이 할 일 직접 추가 (100자까지) */
	@Transactional
	public void add(long memberId, String phase, String title) {
		String t = title == null ? "" : title.strip();
		if (t.isEmpty() || !PHASES.containsKey(phase)) {
			return;
		}
		ChecklistRow n = new ChecklistRow();
		n.setMemberId(memberId);
		n.setRequestId(requestId(moves.current(memberId)));
		n.setPhase(phase);
		n.setTitle(t.length() > 100 ? t.substring(0, 100) : t);
		n.setDoneYn("N");
		mapper.insert(n);
	}

	@Transactional
	public void delete(long memberId, long checkId) {
		mapper.deleteCustom(checkId, memberId);
	}

	/** 남은 날 → 지금 할 일 단계 */
	static String phaseFor(long daysLeft) {
		if (daysLeft >= 11) return "01";
		if (daysLeft >= 4) return "02";
		if (daysLeft >= 2) return "03";
		if (daysLeft == 1) return "04";
		if (daysLeft == 0) return "05";
		return "07";
	}

	static String dday(LocalDate date) {
		if (date == null) return "";
		long d = ChronoUnit.DAYS.between(LocalDate.now(), date);
		return d == 0 ? "D-day" : d > 0 ? "D-" + d : "D+" + (-d);
	}

	private static long requestId(CurrentMove c) {
		return c == null || c.request() == null ? 0 : c.request().getRequestId();
	}

	/** 예약 날짜 → 요청 때 고른 날짜 */
	private static LocalDate moveDate(CurrentMove c) {
		if (c == null || c.request() == null) return null;
		if (c.reservation() != null && c.reservation().getMoveDate() != null
				&& MoveService.BOOKED.equals(c.reservation().getStatus())) {
			return c.reservation().getMoveDate();
		}
		return c.request().getMoveDate();
	}

	public static List<Template> templates() {
		if (templates == null) {
			templates = load();
		}
		return templates;
	}

	private static List<Template> load() {
		InputStream in = ChecklistService.class.getResourceAsStream("/checklist/move-checklist.csv");
		if (in == null) {
			throw new IllegalStateException("checklist/move-checklist.csv 가 없습니다");
		}
		List<Template> out = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			r.readLine();
			for (String line; (line = r.readLine()) != null;) {
				if (line.isBlank()) continue;
				List<String> c = split(line);
				out.add(new Template(c.get(0).trim(), c.get(1).trim(), c.get(2).trim(), c.size() > 3 ? c.get(3).trim() : "",
						c.size() > 4 ? c.get(4).trim() : "", c.size() > 5 ? c.get(5).trim() : ""));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return List.copyOf(out);
	}

	/** 큰따옴표 안 쉼표를 지키는 한 줄 분리 */
	static List<String> split(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cell.append('"');
					i++;
				} else if (ch == '"') {
					quoted = false;
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				out.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		out.add(cell.toString());
		return out;
	}
}
