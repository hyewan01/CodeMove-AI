package com.codemove.admin.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/**
 * 일정 캘린더 (SCR-A-020) · 일정 등록 (SCR-A-021) · 일정 관리 (SCR-A-022)
 * 날짜 키는 프로토타입과 같은 yy.MM.dd 형식입니다.
 */
@Controller
@RequestMapping("/admin/schedule")
public class AdminScheduleController {

	/** 달력에 표시하는 예약 상태 (프로토타입 activeRecords) */
	private static final Set<String> ACTIVE = Set.of("승인 대기", "예약 확정", "이사 완료");
	/** 손 없는 날 (프로토타입: 매월 같은 날짜) */
	private static final Set<Integer> NO_MOVE_DAYS = Set.of(7, 8, 17, 18, 27, 28);

	public record CalendarCell(Integer day, String key, int weekday, AdminMockData.Reservation event,
			AdminState.ManualSchedule manual, String managed, boolean holiday, boolean noMove) {
	}

	static String dateKey(int y, int m, int d) {
		return String.format("%02d.%02d.%02d", y % 100, m, d);
	}

	/** 프로토타입 공휴일: 9월 24~26일 (추석) */
	private static boolean isHoliday(int month, int day) {
		return month == 9 && day >= 24 && day <= 26;
	}

	/** 6주(42칸) 달력 */
	private static List<CalendarCell> cells(int y, int m, AdminState state, List<AdminMockData.Reservation> active) {
		LocalDate first = LocalDate.of(y, m, 1);
		int firstDow = first.getDayOfWeek().getValue() % 7;
		int days = first.lengthOfMonth();
		List<CalendarCell> cells = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			int day = i - firstDow + 1;
			if (day < 1 || day > days) {
				cells.add(new CalendarCell(null, null, i % 7, null, null, null, false, false));
				continue;
			}
			String key = dateKey(y, m, day);
			cells.add(new CalendarCell(day, key, i % 7,
					active.stream().filter(r -> r.movingDate().equals(key)).findFirst().orElse(null),
					state.getManualSchedules().stream().filter(s -> s.date().equals(key)).findFirst().orElse(null),
					state.getManagedDateStatuses().get(key), isHoliday(m, day), NO_MOVE_DAYS.contains(day)));
		}
		return cells;
	}

	private static List<AdminMockData.Reservation> activeReservations() {
		return AdminMockData.reservations().stream().filter(r -> ACTIVE.contains(r.status())).toList();
	}

	/* ---------- 일정 캘린더 ---------- */

	@GetMapping
	public String calendar(@RequestParam(defaultValue = "2026") int y, @RequestParam(defaultValue = "9") int m,
			@RequestParam(defaultValue = "27") int d, @RequestParam(required = false) Integer rsv,
			@RequestParam(required = false) Long manual, @RequestParam(required = false) Long del,
			@RequestParam(required = false) String modal, HttpSession session, Model model) {
		AdminState state = AdminState.of(session);
		LocalDate first = LocalDate.of(y, m, 1);
		int day = Math.min(Math.max(1, d), first.lengthOfMonth());
		List<AdminMockData.Reservation> active = activeReservations();
		String selectedKey = dateKey(y, m, day);
		model.addAttribute("y", y);
		model.addAttribute("m", m);
		model.addAttribute("d", day);
		model.addAttribute("prev", first.minusMonths(1));
		model.addAttribute("next", first.plusMonths(1));
		model.addAttribute("cells", cells(y, m, state, active));
		model.addAttribute("daySchedules", active.stream().filter(r -> r.movingDate().equals(selectedKey))
				.sorted((a, b) -> a.movingTime().compareTo(b.movingTime())).toList());
		model.addAttribute("dayManuals", state.getManualSchedules().stream().filter(s -> s.date().equals(selectedKey)).toList());
		model.addAttribute("dayManaged", state.getManagedDateStatuses().get(selectedKey));
		model.addAttribute("noMove", NO_MOVE_DAYS.contains(day));
		model.addAttribute("holiday", isHoliday(m, day));
		model.addAttribute("modal", modal);
		model.addAttribute("baseUrl", "/admin/schedule?y=" + y + "&m=" + m + "&d=" + day);
		model.addAttribute("reservationTone", Map.of(
				"승인 대기", "bg-[#f0f1f0] text-[#6f7771]", "반려", "bg-[#fbefed] text-[#b9574f]",
				"예약 확정", "bg-[#edf4ed] text-[#346538]", "견적 협의", "bg-[#fff4e8] text-[#ad6d32]",
				"견적 확정", "bg-[#eeeefa] text-[#6560a4]", "이사 완료", "bg-[#edf3f8] text-[#4b759b]"));
		List<AdminMockData.Reservation> all = AdminMockData.reservations();
		if (rsv != null) {
			all.stream().filter(r -> r.id() == rsv).findFirst().ifPresent(r -> {
				model.addAttribute("selectedSchedule", r);
				// 프로토타입: estimates[(id - 1) % estimates.length]
				model.addAttribute("selectedEstimateId", AdminMockData.estimates().get((r.id() - 1) % AdminMockData.estimates().size()).id());
			});
		}
		if (manual != null) {
			state.getManualSchedules().stream().filter(s -> s.id() == manual).findFirst().ifPresent(s -> {
				model.addAttribute("selectedManual", s);
				if (s.memberId() != null) {
					AdminMockData.reservations().stream().filter(r -> r.userId().equals(s.memberId())).findFirst()
							.ifPresent(r -> model.addAttribute("linkedMember", new String[] { r.name(), r.phone() }));
				}
				if (s.reservationId() != null) {
					all.stream().filter(r -> r.id() == s.reservationId()).findFirst()
							.ifPresent(r -> model.addAttribute("linkedReservation", r));
				}
			});
		}
		if (del != null) {
			state.getManualSchedules().stream().filter(s -> s.id() == del).findFirst()
					.ifPresent(s -> model.addAttribute("deletingManual", s));
		}
		if ("new".equals(modal)) {
			model.addAttribute("newDate", String.format("%04d-%02d-%02d", y, m, day));
			// 회원·예약 연결 검색용 (프로토타입 reservationCustomers / records)
			Map<String, String[]> customers = new LinkedHashMap<>();
			all.forEach(r -> customers.putIfAbsent(r.userId(), new String[] { r.name(), r.userId(), r.phone() }));
			model.addAttribute("linkCustomers", new ArrayList<>(customers.values()));
			model.addAttribute("linkReservations", all.stream().map(r -> Map.<String, Object>of("id", r.id(),
					"userId", r.userId(), "name", r.name(), "phone", r.phone(), "origin", r.origin(),
					"destination", r.destination())).toList());
		}
		return "admin/schedule";
	}

	@PostMapping("/manual")
	public String addManual(@RequestParam String title, @RequestParam String category, @RequestParam String date,
			@RequestParam String time, @RequestParam(defaultValue = "") String place,
			@RequestParam(defaultValue = "") String memberId, @RequestParam(required = false) Integer reservationId,
			HttpSession session) {
		LocalDate day = LocalDate.parse(date);
		AdminState.of(session).getManualSchedules().add(new AdminState.ManualSchedule(System.currentTimeMillis(),
				dateKey(day.getYear(), day.getMonthValue(), day.getDayOfMonth()), title.trim(), category.trim(), time,
				place.trim(), memberId.isBlank() ? null : memberId, reservationId));
		return "redirect:/admin/schedule?y=" + day.getYear() + "&m=" + day.getMonthValue() + "&d=" + day.getDayOfMonth();
	}

	@PostMapping("/manual/{id}/delete")
	public String deleteManual(@PathVariable long id, @RequestParam(defaultValue = "") String back, HttpSession session) {
		AdminState.of(session).getManualSchedules().removeIf(s -> s.id() == id);
		return "redirect:" + AdminPageController.safeBack(back, "/admin/schedule");
	}

	/* ---------- 일정 관리 (수정 모드는 화면 스크립트에서 초안 관리 후 한 번에 저장) ---------- */

	@GetMapping("/manage")
	public String manage(HttpSession session, Model model) {
		Map<String, String> events = new LinkedHashMap<>();
		activeReservations().forEach(r -> events.putIfAbsent(r.movingDate(), r.name()));
		model.addAttribute("statuses", AdminState.of(session).getManagedDateStatuses());
		model.addAttribute("events", events);
		return "admin/schedule-manage";
	}

	/** entries: "yy.MM.dd:closed|unavailable" 목록 = 저장할 전체 상태 */
	@PostMapping("/manage")
	public String saveManage(@RequestParam(required = false) List<String> entries, HttpSession session) {
		AdminState state = AdminState.of(session);
		Map<String, String> next = new LinkedHashMap<>();
		if (entries != null) {
			for (String entry : entries) {
				String[] kv = entry.split(":");
				if (kv.length == 2 && (kv[1].equals("closed") || kv[1].equals("unavailable"))) {
					next.put(kv[0], kv[1]);
				}
			}
		}
		next.forEach((key, value) -> {
			if (!value.equals(state.getManagedDateStatuses().get(key))) {
				state.fire("closed".equals(value) ? "일정 관리 · 업체 휴무일을 등록했을 시" : "일정 관리 · 이사 예약이 불가능 할 시");
			}
		});
		state.setManagedDateStatuses(next);
		return "redirect:/admin/schedule/manage";
	}
}
