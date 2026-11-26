package com.codemove.estimate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.codemove.estimate.VideoAnalysis.Usage;
import com.codemove.move.EstimateDoc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 대표님 수기 견적서 사진 · PDF → 견적서 칸 (관리자 견적서 등록 1단계).
 * Gemini 가 글자를 읽고, 이 클래스가 앱 견적서(EstimateDoc) 칸에 맞춰 넣습니다. 읽기 어려운 칸은 reviewFields 로 표시해
 * 2단계 수정 화면에서 노랗게 보입니다. 바로 저장하지 않고 관리자가 고친 뒤 등록합니다.
 * <p>
 * 분석 기록(ai-logs/sheets/날짜/ID.json)에는 고객 이름 · 전화번호를 가려서 남깁니다(예: 김** · 010-****-****).
 */
@Component
public class SheetReader {

	private static final Logger log = LoggerFactory.getLogger(SheetReader.class);

	public static final String VERSION = "sheet-v1";

	/** 앱 견적서 품목 4묶음 (종이 양식 순서) */
	public static final List<String> GROUPS = List.of("방", "거실", "주방", "베란다·다용도실");

	/** 이보다 짧은 변(픽셀)의 사진은 글자를 잘못 읽을 수 있다고 알림 (1050×1400 사진은 사람도 일부만 읽힘) */
	static final int MIN_SHORT_SIDE = 1200;

	static final Map<String, String> MIME = Map.of(".jpg", "image/jpeg", ".jpeg", "image/jpeg", ".png", "image/png",
			".webp", "image/webp", ".heic", "image/heic", ".heif", "image/heif", ".pdf", "application/pdf");

	/**
	 * 읽은 결과.
	 * @param doc     견적서 칸 (실패하면 빈 견적서)
	 * @param notice  2단계 화면 위에 보여 줄 안내 (없으면 null)
	 */
	public record Result(boolean success, EstimateDoc doc, String notice, String logId) {
	}

	private final GeminiVideoAnalyzer ai;
	private final JsonMapper json;
	private final Path logRoot;
	private final double priceIn, priceOut;
	/** 같은 견적서를 몇 번 동시에 읽어 합칠지 (한 번만 읽으면 위쪽 칸을 통째로 비우는 회차가 있음) */
	private final int reads;

	public SheetReader(GeminiVideoAnalyzer ai, JsonMapper json, String logDir, double priceIn, double priceOut) {
		this(ai, json, logDir, priceIn, priceOut, 3);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public SheetReader(GeminiVideoAnalyzer ai, JsonMapper json,
			@Value("${codemove.ai.log-dir:ai-logs}") String logDir,
			@Value("${codemove.ai.gemini.price-in-per-1m:1.50}") double priceIn,
			@Value("${codemove.ai.gemini.price-out-per-1m:7.50}") double priceOut,
			@Value("${codemove.ai.sheet.reads:3}") int reads) {
		this.reads = Math.max(1, reads);
		this.ai = ai;
		this.json = json;
		this.logRoot = Path.of(logDir).toAbsolutePath().normalize().resolve("sheets");
		this.priceIn = priceIn;
		this.priceOut = priceOut;
	}

	public static String mimeOf(String fileName) {
		String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
		return MIME.get(n.contains(".") ? n.substring(n.lastIndexOf('.')) : "");
	}

	public Result read(Path file, String fileName) {
		String id = UUID.randomUUID().toString().substring(0, 8);
		long t0 = System.currentTimeMillis();
		String mime = mimeOf(fileName);
		if (mime == null) {
			mime = mimeOf(file.getFileName().toString());
		}
		if (mime == null) {
			return new Result(false, blank(), "사진(jpg · png · heic) 또는 PDF 만 읽을 수 있어요. 칸을 직접 채워 주세요.", null);
		}
		if (!ai.configured()) {
			return new Result(false, blank(), "AI 키가 설정되지 않아 견적서를 읽지 못했어요. 칸을 직접 채워 주세요.", null);
		}
		String small = smallPhotoNotice(file, mime);
		Map<String, Object> rec = new LinkedHashMap<>();
		rec.put("id", id);
		rec.put("at", LocalDateTime.now().toString());
		rec.put("promptVersion", VERSION);
		rec.put("mime", mime);
		try {
			// 사진은 전체 + 조각 6장으로(글씨를 크게), PDF · 자바가 못 여는 사진(HEIC 등)은 파일 그대로
			List<byte[]> tiles = mime.startsWith("image/") ? SheetImages.tiles(file) : null;
			rec.put("images", tiles == null ? 1 : tiles.size());
			rec.put("reads", reads);
			List<GeminiVideoAnalyzer.Response> answers = readTimes(file, mime, tiles);
			List<JsonNode> nodes = new ArrayList<>();
			for (GeminiVideoAnalyzer.Response a : answers) {
				try {
					nodes.add(json.readTree(strip(a.text())));
				} catch (RuntimeException bad) {
					log.warn("견적서 읽기 응답 JSON 오류 {}: {}", id, bad.getMessage());
				}
			}
			if (nodes.isEmpty()) {
				throw new IllegalStateException("AI 응답을 읽지 못했어요");
			}
			JsonNode n = json.valueToTree(merge(nodes.stream().map(x -> toMap(x)).toList()));
			GeminiVideoAnalyzer.Response res = answers.get(0);
			Usage usage = new Usage(answers.stream().mapToLong(a -> a.usage().inputTokens()).sum(),
					answers.stream().mapToLong(a -> a.usage().outputTokens()).sum(),
					answers.stream().mapToLong(a -> a.usage().thoughtTokens()).sum(),
					answers.stream().mapToLong(a -> a.usage().totalTokens()).sum());
			rec.put("model", res.model());
			rec.put("usage", usage);
			rec.put("costUsd", cost(usage));
			rec.put("ms", System.currentTimeMillis() - t0);
			String rawAll = answers.size() == 1 ? res.text()
					: String.join("\n\n== 다음 회차 ==\n", answers.stream().map(GeminiVideoAnalyzer.Response::text).toList());
			if (!n.path("readable").asBoolean(true)) {
				String reason = n.path("reject_reason").asString("");
				rec.put("success", false);
				rec.put("message", reason);
				save(id, rec, rawAll, null);
				return new Result(false, blank(),
						"견적서를 읽기 어려워요" + (reason.isBlank() ? "" : " (" + reason + ")")
								+ ". 견적서 전체가 나오게 가까이서 밝게 다시 찍거나, 칸을 직접 채워 주세요.", id);
			}
			EstimateDoc doc = toDoc(n);
			rec.put("success", true);
			rec.put("unsure", doc.getReviewFields());
			rec.put("doc", masked(doc));
			save(id, rec, rawAll, doc);
			String notice = "AI가 대표님 견적서를 읽어 칸을 채웠어요. 노란 칸은 글씨가 흐려 확인이 필요해요. 다른 칸도 한 번 훑어보고 고쳐 주세요.";
			return new Result(true, doc, small == null ? notice : notice + " " + small, id);
		} catch (IOException | RuntimeException e) {
			log.warn("견적서 읽기 실패 {}: {}", id, e.getMessage());
			rec.put("success", false);
			rec.put("message", maskText(String.valueOf(e.getMessage()), null));
			rec.put("ms", System.currentTimeMillis() - t0);
			save(id, rec, null, null);
			return new Result(false, blank(), "AI가 견적서를 읽지 못했어요(" + shortMessage(e) + "). 칸을 직접 채워 주세요.", id);
		}
	}

	/** reads 번 동시에 읽기. 일부만 실패하면 성공한 것만, 모두 실패하면 첫 오류 */
	private List<GeminiVideoAnalyzer.Response> readTimes(Path file, String mime, List<byte[]> tiles) {
		java.util.function.Supplier<GeminiVideoAnalyzer.Response> once = () -> tiles == null
				? ai.analyze(file, mime, prompt(), schema())
				: ai.analyzeImages(tiles, prompt() + SheetImages.promptNote(), schema());
		if (reads == 1) {
			return List.of(once.get());
		}
		List<GeminiVideoAnalyzer.Response> ok = new ArrayList<>();
		RuntimeException first = null;
		try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
			List<java.util.concurrent.Future<GeminiVideoAnalyzer.Response>> fs = new ArrayList<>();
			for (int i = 0; i < reads; i++) {
				fs.add(pool.submit(once::get));
			}
			for (var f : fs) {
				try {
					ok.add(f.get());
				} catch (java.util.concurrent.ExecutionException e) {
					RuntimeException r = e.getCause() instanceof RuntimeException re ? re
							: new IllegalStateException(String.valueOf(e.getCause()));
					first = first == null ? r : first;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("중단됨");
				}
			}
		}
		if (ok.isEmpty()) {
			throw first;
		}
		return ok;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(JsonNode n) {
		return json.convertValue(n, Map.class);
	}

	/** 한 줄 값 칸 (고객 · 날짜 · 주소 · 금액 · 층 · 작업내용 · 비고) */
	static final List<String> SCALARS = List.of("customer", "phone", "estimate_date", "pack_date", "transport_date",
			"origin", "destination", "volume", "moving_cost", "total", "deposit", "balance", "departure_floor",
			"destination_floor", "departure_ladder", "destination_ladder", "ladder_subtotal", "work_method",
			"ladder_truck", "elevator", "stairs", "note");

	/**
	 * 여러 번 읽은 결과 합치기.
	 * 한 줄 칸: 비어 있지 않은 값 중 가장 많이 나온 값(같으면 품목을 가장 많이 읽은 회차 값). 회차마다 다르면 확인 필요.
	 * 품목 · 옵션: 품목을 가장 많이 읽은 회차 것을 쓰고, 다른 회차 어디에도 없던 품목은 확인 필요.
	 * 절반 넘는 회차가 "못 읽음"이면 못 읽음.
	 */
	static Map<String, Object> merge(List<Map<String, Object>> runs) {
		if (runs.size() == 1) {
			return runs.get(0);
		}
		List<Map<String, Object>> readable = runs.stream()
				.filter(r -> !Boolean.FALSE.equals(r.get("readable"))).toList();
		if (readable.isEmpty() || readable.size() * 2 < runs.size()) {
			return runs.stream().filter(r -> Boolean.FALSE.equals(r.get("readable"))).findFirst().orElse(runs.get(0));
		}
		Map<String, Object> base = readable.get(0);
		for (Map<String, Object> r : readable) {
			if (items(r).size() > items(base).size()) {
				base = r;
			}
		}
		Map<String, Object> out = new LinkedHashMap<>(base);
		Set<String> unsure = new LinkedHashSet<>();
		if (base.get("unsure_fields") instanceof List<?> l) {
			l.forEach(x -> unsure.add(String.valueOf(x)));
		}
		for (String k : SCALARS) {
			Map<String, Integer> votes = new LinkedHashMap<>();
			for (Map<String, Object> r : readable) {
				String v = r.get(k) == null ? "" : String.valueOf(r.get(k)).strip();
				if (!v.isEmpty() && !"없음".equals(v)) {
					votes.merge(v, 1, Integer::sum);
				}
			}
			if (votes.isEmpty()) {
				continue;
			}
			String baseVal = base.get(k) == null ? "" : String.valueOf(base.get(k)).strip();
			String best = null;
			int bestVotes = -1;
			for (Map.Entry<String, Integer> e : votes.entrySet()) {
				if (e.getValue() > bestVotes || (e.getValue() == bestVotes && e.getKey().equals(baseVal))) {
					best = e.getKey();
					bestVotes = e.getValue();
				}
			}
			out.put(k, best);
			if (votes.size() > 1) {
				unsure.add(k);
			}
		}
		List<Object> merged = new ArrayList<>();
		for (Map<String, Object> it : items(base)) {
			Map<String, Object> copy = new LinkedHashMap<>(it);
			boolean elsewhere = false;
			for (Map<String, Object> r : readable) {
				if (r != base) {
					elsewhere |= items(r).stream().anyMatch(o -> same(o, it));
				}
			}
			if (!elsewhere) {
				copy.put("unsure", true);
			}
			merged.add(copy);
		}
		out.put("items", merged);
		out.put("unsure_fields", new ArrayList<>(unsure));
		return out;
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Map<String, Object> r) {
		return r.get("items") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
	}

	private static boolean same(Map<String, Object> a, Map<String, Object> b) {
		return group(str(a, "group")).equals(group(str(b, "group")))
				&& tidy(str(a, "name")).replace(" ", "").equals(tidy(str(b, "name")).replace(" ", ""))
				&& str(a, "room").equals(str(b, "room"));
	}

	private static String str(Map<String, Object> m, String k) {
		return m.get(k) == null ? "" : String.valueOf(m.get(k)).strip();
	}

	private static String shortMessage(Exception e) {
		String m = e.getMessage() == null ? "오류" : e.getMessage();
		// Gemini 오류 원문(JSON)은 기록에만 두고 화면엔 첫 부분만
		int cut = m.indexOf(':');
		m = m.startsWith("Gemini 오류") && cut > 0 ? m.substring(0, cut) : m;
		return m.length() > 60 ? m.substring(0, 60) + "…" : m;
	}

	/* ---------- 응답 → 견적서 칸 ---------- */

	/** Gemini 응답 JSON → 앱 견적서. 화면 칸 이름(EstimateDoc 필드)으로 읽기 어려운 칸을 reviewFields 에 */
	static EstimateDoc toDoc(JsonNode n) {
		EstimateDoc d = blank();
		Set<String> review = new LinkedHashSet<>();
		Map<String, String> fieldOf = Map.ofEntries(Map.entry("customer", "customer"), Map.entry("phone", "phone"),
				Map.entry("estimate_date", "estimateDate"), Map.entry("pack_date", "moveDate"),
				Map.entry("transport_date", "transportDate"), Map.entry("origin", "origin"),
				Map.entry("destination", "destination"), Map.entry("volume", "volume"),
				Map.entry("moving_cost", "movingCost"), Map.entry("total", "total"), Map.entry("deposit", "deposit"),
				Map.entry("balance", "balance"), Map.entry("departure_floor", "departureFloor"),
				Map.entry("destination_floor", "destinationFloor"), Map.entry("departure_ladder", "departureLadder"),
				Map.entry("destination_ladder", "destinationLadder"), Map.entry("ladder_subtotal", "ladderSubtotal"),
				Map.entry("work_method", "workMethod"), Map.entry("note", "note"));
		d.setCustomer(text(n, "customer"));
		d.setPhone(text(n, "phone"));
		d.setEstimateDate(text(n, "estimate_date"));
		d.setMoveDate(text(n, "pack_date"));
		d.setTransportDate(text(n, "transport_date"));
		d.setOrigin(text(n, "origin"));
		d.setDestination(text(n, "destination"));
		d.setVolume(text(n, "volume"));
		d.setMovingCost(text(n, "moving_cost"));
		d.setTotal(text(n, "total"));
		d.setDeposit(text(n, "deposit"));
		d.setBalance(text(n, "balance"));
		List<String> notes = new ArrayList<>();
		d.setDepartureFloor(floor(text(n, "departure_floor"), "출발지", notes));
		d.setDestinationFloor(floor(text(n, "destination_floor"), "도착지", notes));
		d.setDepartureLadder(text(n, "departure_ladder"));
		d.setDestinationLadder(text(n, "destination_ladder"));
		String ladderSum = text(n, "ladder_subtotal");
		d.setLadderSubtotal(ladderSum);
		d.setExtraCost(ladderSum);
		String method = text(n, "work_method");
		if ("고급".equals(method)) {
			notes.add("작업내용: 고급");
			method = "포장";
		}
		d.setWorkMethod(List.of("포장", "반포장", "일반", "보관").contains(method) ? method : "");
		d.setLadder(beforeAfter(text(n, "ladder_truck")));
		d.setElevator(beforeAfter(text(n, "elevator")));
		d.setStairs(beforeAfter(text(n, "stairs")));

		// 옵션: 앱 양식 8줄에 맞추고, 없는 줄(삼성그랑데 · LG워시타워 등)은 비고로
		List<String> costs = new ArrayList<>(d.getOptionCosts());
		List<String> picks = new ArrayList<>(d.getOptionSelections());
		for (JsonNode o : n.path("options")) {
			String label = o.path("label").asString("").replace(" ", "");
			String choice = o.path("choice").asString("").strip();
			String cost = o.path("cost").asString("").strip();
			int row = -1;
			for (int i = 0; i < EstimateDoc.OPTION_ROWS.length; i++) {
				String[] r = EstimateDoc.OPTION_ROWS[i];
				if (!label.isEmpty() && (label.contains(r[0]) || r[0].contains(label))) {
					row = i;
					break;
				}
			}
			if (row < 0) {
				if (!label.isEmpty()) {
					notes.add(("옵션 " + label + " " + choice + " " + cost).strip());
				}
				continue;
			}
			String[] r = EstimateDoc.OPTION_ROWS[row];
			picks.set(row, choice.equals(r[1]) || choice.equals(r[2]) ? choice : "");
			costs.set(row, cost);
		}
		d.setOptionCosts(costs);
		d.setOptionSelections(picks);

		// 품목: 4묶음 × 행 (방 품목은 이름 뒤에 방 이름). 한 묶음이 14행을 넘으면 다른 묶음의 빈 칸으로,
		// 그래도 모자라면 비고로
		List<List<String[]>> cols = new ArrayList<>();
		GROUPS.forEach(g -> cols.add(new ArrayList<>()));
		List<String[]> spill = new ArrayList<>();
		List<String> overflow = new ArrayList<>();
		for (JsonNode i : n.path("items")) {
			boolean guessed = (i.path("name").asString("") + i.path("quantity").asString("")).contains("?");
			String name = tidy(i.path("name").asString(""));
			String qty = tidy(i.path("quantity").asString(""));
			if (name.isEmpty()) {
				continue;
			}
			String room = i.path("room").asString("").strip();
			int g = GROUPS.indexOf(group(i.path("group").asString("")));
			String label = g == 0 && !room.isEmpty() && !"방".equals(room) ? name + "(" + room + ")" : name;
			String[] cell = { label, qty, i.path("unsure").asBoolean(false) || guessed ? "Y" : "" };
			if (cols.get(g).size() >= MAX_ROWS) {
				spill.add(cell);
			} else {
				cols.get(g).add(cell);
			}
		}
		for (String[] cell : spill) {
			List<String[]> free = cols.stream().filter(c -> c.size() < MAX_ROWS).findFirst().orElse(null);
			if (free == null) {
				overflow.add(cell[0] + " " + cell[1]);
			} else {
				free.add(cell);
			}
		}
		int rows = Math.max(8, cols.stream().mapToInt(List::size).max().orElse(0));
		List<String> names = new ArrayList<>();
		List<String> qtys = new ArrayList<>();
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < 4; c++) {
				String[] cell = r < cols.get(c).size() ? cols.get(c).get(r) : null;
				names.add(cell == null ? "" : cell[0]);
				qtys.add(cell == null ? "" : cell[1]);
				if (cell != null && "Y".equals(cell[2])) {
					review.add("itemNames[" + (r * 4 + c) + "]");
					review.add("itemQuantities[" + (r * 4 + c) + "]");
				}
			}
		}
		d.setItemGroups(new ArrayList<>(GROUPS));
		d.setItemRowCount(rows);
		d.setItemNames(names);
		d.setItemQuantities(qtys);
		if (!overflow.isEmpty()) {
			notes.add("칸이 모자라 옮긴 품목: " + String.join(", ", overflow));
		}
		String memo = text(n, "note");
		if (!notes.isEmpty()) {
			memo = (memo.isEmpty() ? "" : memo + " / ") + String.join(" / ", notes);
		}
		d.setNote(memo);
		for (JsonNode f : n.path("unsure_fields")) {
			String field = fieldOf.get(f.asString(""));
			if (field != null) {
				review.add(field);
			}
		}
		d.setReviewFields(review);
		return d.normalized();
	}

	/**
	 * 모델 버릇 정리: 닫는 괄호를 두 번 찍음("1 (킹))") → 짝이 맞을 때까지 하나씩 지움.
	 * 애매한 글자 뒤에 "?" 를 붙임("1 (킹?)") → 지움 (애매한 칸은 unsure 로 노랗게 표시하므로)
	 */
	static String tidy(String v) {
		String s = v == null ? "" : v.replace("?", "").strip();
		while (s.contains("))") && s.chars().filter(ch -> ch == ')').count() > s.chars().filter(ch -> ch == '(').count()) {
			s = s.replaceFirst("\\)\\)", ")");
		}
		return s;
	}

	/** 앱 견적서 품목 최대 행 (EstimateDoc.normalized) */
	static final int MAX_ROWS = 14;

	/** "6층 엘리베이터" → 6, 나머지 글은 비고로 */
	private static String floor(String v, String where, List<String> notes) {
		String digits = v.replaceAll("^\\D*(\\d+).*$", "$1");
		if (!digits.matches("\\d+")) {
			if (!v.isEmpty()) {
				notes.add(where + " " + v);
			}
			return "";
		}
		String rest = v.replaceFirst(digits, "").replace("층", "").strip();
		if (!rest.isEmpty()) {
			notes.add(where + " " + rest);
		}
		return digits;
	}

	private static String group(String g) {
		String s = g == null ? "" : g.replace(" ", "");
		if (s.contains("거실")) {
			return "거실";
		}
		if (s.contains("주방")) {
			return "주방";
		}
		if (s.contains("베란다") || s.contains("다용도")) {
			return "베란다·다용도실";
		}
		return "방";
	}

	private static String beforeAfter(String v) {
		return "전".equals(v) || "후".equals(v) ? v : "";
	}

	private static String text(JsonNode n, String key) {
		JsonNode v = n.path(key);
		return v.isMissingNode() || v.isNull() ? "" : v.asString("").strip();
	}

	/** 화면에 미리 채워 둔 예시값 없이 빈 견적서 */
	static EstimateDoc blank() {
		EstimateDoc d = new EstimateDoc();
		d.setPhone("");
		d.setOrigin("");
		d.setDestination("");
		d.setVolume("");
		d.setNote("");
		d.setMovingCost("");
		d.setExtraCost("");
		d.setTotal("");
		d.setDeposit("");
		d.setBalance("");
		d.setLadder("");
		d.setElevator("");
		d.setDepartureLadder("");
		d.setDestinationLadder("");
		d.setLadderSubtotal("");
		d.setWorkMethod("");
		d.setItemGroups(new ArrayList<>(GROUPS));
		d.setItemNames(new ArrayList<>());
		d.setItemQuantities(new ArrayList<>());
		return d.normalized();
	}

	/* ---------- 가리기 (기록용) ---------- */

	private static final Pattern PHONE = Pattern
			.compile("(?<!\\d)(0\\d{1,2})\\s*[-.)]?\\s*(\\d{3,4})\\s*[-.]?\\s*(\\d{4})(?!\\d)");

	/** 이름: 첫 글자만 (김선국 → 김**) */
	static String maskName(String name) {
		String s = name == null ? "" : name.strip();
		if (s.isEmpty()) {
			return "";
		}
		int first = s.codePointAt(0);
		int rest = Math.max(1, s.codePointCount(0, s.length()) - 1);
		return new String(Character.toChars(first)) + "*".repeat(rest);
	}

	/** 글 속 전화번호는 앞자리만 남기고, 고객 이름은 가림 */
	static String maskText(String text, String customer) {
		if (text == null) {
			return null;
		}
		String t = PHONE.matcher(text).replaceAll(m -> m.group(1) + "-****-****");
		String c = customer == null ? "" : customer.strip();
		if (c.length() >= 2) {
			t = t.replace(c, maskName(c));
		}
		return t;
	}

	private static Map<String, Object> masked(EstimateDoc d) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("customer", maskName(d.getCustomer()));
		m.put("phone", maskText(d.getPhone(), null));
		m.put("estimateDate", d.getEstimateDate());
		m.put("moveDate", d.getMoveDate());
		m.put("origin", d.getOrigin());
		m.put("destination", d.getDestination());
		m.put("volume", d.getVolume());
		m.put("workMethod", d.getWorkMethod());
		m.put("total", d.getTotal());
		m.put("note", maskText(d.getNote(), d.getCustomer()));
		List<String> items = new ArrayList<>();
		for (int i = 0; i < d.getItemNames().size(); i++) {
			if (!d.itemName(i).isEmpty()) {
				items.add(d.getItemGroups().get(i % 4) + " | " + d.itemName(i) + " | " + d.itemQuantity(i));
			}
		}
		m.put("items", items);
		return m;
	}

	private void save(String id, Map<String, Object> rec, String raw, EstimateDoc doc) {
		try {
			if (raw != null) {
				rec.put("rawJson", maskRaw(raw, doc == null ? "" : doc.getCustomer()));
			}
			Path dir = logRoot.resolve(LocalDate.now().toString().replace("-", ""));
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(id + ".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(rec));
		} catch (IOException | RuntimeException e) {
			log.warn("견적서 읽기 기록 저장 실패 {}: {}", id, e.getMessage());
		}
	}

	private static final Pattern CUSTOMER = Pattern.compile("\"customer\"\\s*:\\s*\"([^\"]*)\"");

	/**
	 * 원문(여러 회차가 이어 붙은 글) 가리기: 회차마다 이름을 조금씩 다르게 읽으므로(이선희 / 이선화)
	 * 원문 속 customer 값을 모두 찾아 가리고, 전화번호도 가림.
	 */
	static String maskRaw(String raw, String customer) {
		Set<String> names = new LinkedHashSet<>();
		if (customer != null && !customer.isBlank()) {
			names.add(customer.strip());
		}
		var m = CUSTOMER.matcher(raw);
		while (m.find()) {
			if (!m.group(1).isBlank()) {
				names.add(m.group(1).strip());
			}
		}
		String t = maskText(raw, null);
		for (String n : names) {
			t = t.replace("\"" + n + "\"", "\"" + maskName(n) + "\"");
			if (n.length() >= 2) {
				t = t.replace(n, maskName(n));
			}
		}
		return t;
	}

	/* ---------- 프롬프트 · 스키마 ---------- */

	private static String strip(String text) {
		String t = text == null ? "" : text.strip();
		return t.startsWith("```") ? t.replaceAll("^```(json)?\\s*", "").replaceAll("```\\s*$", "") : t;
	}

	private double cost(Usage u) {
		return (u.inputTokens() * priceIn + (u.outputTokens() + u.thoughtTokens()) * priceOut) / 1_000_000.0;
	}

	/** 사진이 작으면 안내 문구 (PDF · 읽을 수 없는 형식은 건너뜀) */
	static String smallPhotoNotice(Path file, String mime) {
		if (mime == null || !mime.startsWith("image/")) {
			return null;
		}
		try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
			var readers = in == null ? null : ImageIO.getImageReaders(in);
			if (readers == null || !readers.hasNext()) {
				return null;
			}
			ImageReader r = readers.next();
			try {
				r.setInput(in);
				int w = r.getWidth(0), h = r.getHeight(0);
				return Math.min(w, h) < MIN_SHORT_SIDE ? "사진이 작아서(" + w + "×" + h + ") 잘못 읽은 글자가 있을 수 있어요." : null;
			} finally {
				r.dispose();
			}
		} catch (IOException e) {
			return null;
		}
	}

	static String prompt() {
		try (InputStream in = SheetReader.class.getResourceAsStream("/estimate/" + VERSION + ".txt")) {
			if (in == null) {
				throw new IllegalStateException("estimate/" + VERSION + ".txt 가 없습니다");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static Map<String, Object> schema() {
		Map<String, Object> item = obj(map("group", Map.of("type", "STRING", "enum", GROUPS),
				"room", str("방 묶음일 때 안방 · 방Ⅰ · 방Ⅱ · 방Ⅲ, 그 밖은 빈 글자"),
				"name", str("품목 이름 (손으로 쓴 이름이 있으면 그것)"),
				"quantity", str("수량과 표기 (예: 1, 5 이상, 1 (버림), 제외, 3×7 (21), 1 (퀸))"),
				"unsure", Map.of("type", "BOOLEAN")), List.of("group", "name", "quantity"));
		Map<String, Object> option = obj(map("label", str("옵션 이름 (피아노 · 돌침대 · 조립장 · 벽걸이TV · 수작업 · 안마의자 · 삼성그랑데 · LG워시타워 등)"),
				"choice", str("체크한 쪽 (일반 · 그랜드 · 분해 · 조립 · 탈착 · 설치 · 계단 · 승강기 · 분리), 없으면 빈 글자"),
				"cost", str("금액, 없으면 빈 글자")), List.of("label"));
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("readable", Map.of("type", "BOOLEAN"));
		props.put("reject_reason", Map.of("type", "STRING", "nullable", true));
		for (String k : List.of("customer", "phone", "estimate_date", "pack_date", "transport_date", "origin",
				"destination", "volume", "moving_cost", "total", "deposit", "balance", "departure_floor",
				"destination_floor", "departure_ladder", "destination_ladder", "ladder_subtotal", "note")) {
			props.put(k, Map.of("type", "STRING"));
		}
		props.put("work_method", Map.of("type", "STRING", "enum", List.of("없음", "고급", "포장", "반포장", "일반", "보관")));
		for (String k : List.of("ladder_truck", "elevator", "stairs")) {
			props.put(k, Map.of("type", "STRING", "enum", List.of("없음", "전", "후")));
		}
		props.put("options", Map.of("type", "ARRAY", "items", option));
		props.put("items", Map.of("type", "ARRAY", "items", item));
		props.put("unsure_fields", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
				"description", "읽기 어려웠던 칸의 키 이름 (customer, phone, total 등)"));
		return obj(props, List.of("readable", "items"));
	}

	private static Map<String, Object> map(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	private static Map<String, Object> obj(Map<String, Object> props, List<String> required) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "OBJECT");
		m.put("properties", props);
		m.put("required", required);
		return m;
	}

	private static Map<String, Object> str(String description) {
		return Map.of("type", "STRING", "description", description);
	}
}
