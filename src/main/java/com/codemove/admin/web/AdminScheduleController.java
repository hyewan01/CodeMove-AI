package com.codemove.admin.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.common.Fmt;
import com.codemove.notification.NotificationService;
import com.codemove.schedule.ClosedDay;
import com.codemove.schedule.Schedule;
import com.codemove.schedule.ScheduleMapper;

import jakarta.servlet.http.HttpSession;

/**
 * 일정 캘린더 (SCR-A-020) · 일정 등록 (SCR-A-021) · 일정 관리 (SCR-A-022)
 * 날짜 키는 프로토타입과 같은 yy.MM.dd 형식입니다. 이사 일정 = 예약(취소 제외), 일정 등록 = TBL_SCHEDULE,
 * 업체 휴무일·이사 예약 불가능 = TBL_CLOSED_DAY
 */
@Controller
@RequestMapping("/admin/schedule")
public class AdminScheduleController {

	/** 손 없는 날 (프로토타입: 매월 같은 날짜) */
	private static final Set<Integer> NO_MOVE_DAYS = Set.of(7, 8, 17, 18, 27, 28);

	/** date: yy.MM.dd, memberId: 회원 번호 */
	public record ManualSchedule(long id, String date, String title, String category, String time, String place,
			String memberId, Long reservationId) {
	}

	public record CalendarCell(Integer day, String key, int weekday, AdminViews.Reservation event,
			ManualSchedule manual, String managed, boolean holiday, boolean noMove) {
	}

	private final AdminViews views;
	private final ScheduleMapper schedules;
	private final NotificationService notifications;

	public AdminScheduleController(AdminViews views, ScheduleMapper schedules, NotificationService notifications) {
		this.views = views;
		this.schedules = schedules;
		this.notifications = notifications;
	}

	static String dateKey(LocalDate d) {
		return Fmt.yy(d);
	}

	private static ManualSchedule manual(Schedule s) {
		return new ManualSchedule(s.getScheduleId(), dateKey(s.getScheduleDate()), s.getTitle(), s.getCategory(),
				s.getScheduleTime(), s.getPlace() == null ? "" : s.getPlace(),
				s.getMemberId() == null ? null : String.valueOf(s.getMemberId()), s.getReservationId());
	}

	/** 날짜 → closed | unavailable */
	private Map<String, String> managedStatuses() {
		Map<String, String> map = new LinkedHashMap<>();
		for (ClosedDay c : schedules.listClosedDays()) {
			map.put(dateKey(c.getClosedDate()), "02".equals(c.getClosedType()) ? "unavailable" : "closed");
		}
		return map;
	}

	/** 달력에 표시하는 예약 (취소 제외) */
	private List<AdminViews.Reservation> active() {
		return views.reservations().stream().filter(r -> !"예약 취소".equals(r.status())).toList();
	}

	/* ---------- 일정 캘린더 ---------- */

	@GetMapping
	public String calendar(@RequestParam(required = false) Integer y, @RequestParam(required = false) Integer m,
			@RequestParam(required = false) Integer d, @RequestParam(required = false) Long rsv,
			@RequestParam(required = false) Long manual, @RequestParam(required = false) Long del,
			@RequestParam(required = false) String modal, Model model) {
		LocalDate today = LocalDate.now();
		int year = y != null ? y : today.getYear();
		int month = m != null ? Math.min(12, Math.max(1, m)) : today.getMonthValue();
		LocalDate first = LocalDate.of(year, month, 1);
		int day = Math.min(Math.max(1, d != null ? d : (y == null && m == null ? today.getDayOfMonth() : 1)), first.lengthOfMonth());
		List<AdminViews.Reservation> all = views.reservations();
		List<AdminViews.Reservation> active = active();
		List<ManualSchedule> manuals = schedules.listSchedules(first, first.plusMonths(1)).stream()
				.map(AdminScheduleController::manual).toList();
		Map<String, String> managed = managedStatuses();
		LocalDate selected = LocalDate.of(year, month, day);
		String selectedKey = dateKey(selected);

		List<CalendarCell> cells = new ArrayList<>();
		int firstDow = first.getDayOfWeek().getValue() % 7;
		for (int i = 0; i < 42; i++) {
			int dd = i - firstDow + 1;
			if (dd < 1 || dd > first.lengthOfMonth()) {
				cells.add(new CalendarCell(null, null, i % 7, null, null, null, false, false));
				continue;
			}
			LocalDate date = first.withDayOfMonth(dd);
			String key = dateKey(date);
			cells.add(new CalendarCell(dd, key, i % 7,
					active.stream().filter(r -> key.equals(r.movingDate())).findFirst().orElse(null),
					manuals.stream().filter(s -> s.date().equals(key)).findFirst().orElse(null),
					managed.get(key), Fmt.isHoliday(date), NO_MOVE_DAYS.contains(dd)));
		}
		model.addAttribute("y", year);
		model.addAttribute("m", month);
		model.addAttribute("d", day);
		model.addAttribute("prev", first.minusMonths(1));
		model.addAttribute("next", first.plusMonths(1));
		model.addAttribute("cells", cells);
		model.addAttribute("daySchedules", active.stream().filter(r -> selectedKey.equals(r.movingDate()))
				.sorted((a, b) -> String.valueOf(a.movingTime()).compareTo(String.valueOf(b.movingTime()))).toList());
		model.addAttribute("dayManuals", manuals.stream().filter(s -> s.date().equals(selectedKey)).toList());
		model.addAttribute("dayManaged", managed.get(selectedKey));
		model.addAttribute("noMove", NO_MOVE_DAYS.contains(day));
		model.addAttribute("holiday", Fmt.isHoliday(selected));
		model.addAttribute("modal", modal);
		model.addAttribute("baseUrl", "/admin/schedule?y=" + year + "&m=" + month + "&d=" + day);
		model.addAttribute("reservationTone", Map.of(
				"예약 완료", "bg-[#edf4ed] text-[#346538]", "이사 완료", "bg-[#edf3f8] text-[#4b759b]",
				"예약 취소", "bg-[#fbefed] text-[#b9574f]"));
		if (rsv != null) {
			all.stream().filter(r -> r.id() == rsv).findFirst().ifPresent(r -> model.addAttribute("selectedSchedule", r));
		}
		if (manual != null) {
			Schedule s = schedules.findSchedule(manual);
			if (s != null) {
				model.addAttribute("selectedManual", manual(s));
				if (s.getMemberId() != null) {
					model.addAttribute("linkedMember", new String[] { s.getMemberName(), s.getMemberPhone() });
				}
				if (s.getReservationId() != null) {
					all.stream().filter(r -> r.id() == s.getReservationId()).findFirst()
							.ifPresent(r -> model.addAttribute("linkedReservation", r));
				}
			}
		}
		if (del != null) {
			Schedule s = schedules.findSchedule(del);
			if (s != null) {
				model.addAttribute("deletingManual", manual(s));
			}
		}
		if ("new".equals(modal)) {
			model.addAttribute("newDate", Fmt.dash(selected));
			// 회원·예약 연결 검색용 [이름, 회원 번호, 연락처]
			model.addAttribute("linkCustomers", views.members().stream().filter(x -> x.id() > 0)
					.map(x -> new String[] { x.name(), String.valueOf(x.id()), x.phone() }).toList());
			model.addAttribute("linkReservations", all.stream().map(r -> Map.<String, Object>of("id", r.id(),
					"userId", r.userId(), "name", r.name(), "phone", r.phone(), "origin", r.origin(),
					"destination", r.destination())).toList());
		}
		return "admin/schedule";
	}

	@PostMapping("/manual")
	public String addManual(@RequestParam String title, @RequestParam String category, @RequestParam String date,
			@RequestParam String time, @RequestParam(defaultValue = "") String place,
			@RequestParam(defaultValue = "") String memberId, @RequestParam(required = false) Long reservationId,
			HttpSession session) {
		LocalDate day = LocalDate.parse(date);
		Schedule s = new Schedule();
		s.setTitle(cut(title.trim(), 50));
		s.setCategory(cut(category.trim(), 20));
		s.setScheduleDate(day);
		s.setScheduleTime(time.matches("\\d{2}:\\d{2}") ? time : null);
		s.setPlace(place.isBlank() ? null : cut(place.trim(), 200));
		s.setMemberId(memberId.matches("\\d+") ? Long.valueOf(memberId) : null);
		s.setReservationId(reservationId);
		s.setAdminId(AdminSession.adminId(session));
		schedules.insertSchedule(s);
		return "redirect:/admin/schedule?y=" + day.getYear() + "&m=" + day.getMonthValue() + "&d=" + day.getDayOfMonth();
	}

	@PostMapping("/manual/{id}/delete")
	public String deleteManual(@PathVariable long id, @RequestParam(defaultValue = "") String back) {
		schedules.deleteSchedule(id);
		return "redirect:" + AdminPageController.safeBack(back, "/admin/schedule");
	}

	/* ---------- 일정 관리 (수정 모드는 화면 스크립트에서 초안 관리 후 한 번에 저장) ---------- */

	@GetMapping("/manage")
	public String manage(Model model) {
		Map<String, String> events = new LinkedHashMap<>();
		active().forEach(r -> events.putIfAbsent(r.movingDate(), r.name()));
		LocalDate today = LocalDate.now();
		model.addAttribute("statuses", managedStatuses());
		model.addAttribute("events", events);
		model.addAttribute("startYear", today.getYear());
		model.addAttribute("startMonth", today.getMonthValue());
		return "admin/schedule-manage";
	}

	/** entries: "yy.MM.dd:closed|unavailable" 목록 = 저장할 전체 상태 */
	@PostMapping("/manage")
	@Transactional
	public String saveManage(@RequestParam(required = false) List<String> entries, HttpSession session) {
		Map<String, String> before = managedStatuses();
		Map<String, String> next = new LinkedHashMap<>();
		if (entries != null) {
			for (String entry : entries) {
				String[] kv = entry.split(":");
				if (kv.length == 2 && (kv[1].equals("closed") || kv[1].equals("unavailable")) && Fmt.parseDate(kv[0]) != null) {
					next.put(kv[0], kv[1]);
				}
			}
		}
		Long adminId = AdminSession.adminId(session);
		for (String key : before.keySet()) {
			if (!next.containsKey(key)) {
				schedules.deleteClosedDay(Fmt.parseDate(key));
			}
		}
		next.forEach((key, value) -> {
			if (!value.equals(before.get(key))) {
				schedules.deleteClosedDay(Fmt.parseDate(key));
				schedules.insertClosedDay(Fmt.parseDate(key), "closed".equals(value) ? "01" : "02", adminId);
				AdminState.of(session).toast(notifications.fire(
						"closed".equals(value) ? "일정 관리 · 업체 휴무일을 등록했을 시" : "일정 관리 · 이사 예약이 불가능 할 시", null, null));
			}
		});
		return "redirect:/admin/schedule/manage";
	}

	private static String cut(String s, int max) {
		return s.length() > max ? s.substring(0, max) : s;
	}
}
