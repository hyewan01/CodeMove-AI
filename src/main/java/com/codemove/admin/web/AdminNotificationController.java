package com.codemove.admin.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

/** 알림 관리 (SCR-A-013) · 알림 내역 (014) · 메시징 운영 설정 (015) */
@Controller
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

	/** recipient: 관리자 | 사용자 */
	public record HistoryRecord(String name, String message, String time, String recipient, String category) {

		public String initial() {
			return name.substring(0, 1);
		}
	}

	/** 프로토타입 notificationHistory (사용자에게 보낸 팝업 알림) */
	private static final List<HistoryRecord> USER_HISTORY = List.of(
			new HistoryRecord("홍길동", "내일 오전 9시에 방문 견적이 예정되어 있습니다.", "방금", "사용자", null),
			new HistoryRecord("김명품", "요청하신 이사 견적서가 등록되었습니다. 확인해 주세요.", "10분 전", "사용자", null),
			new HistoryRecord("박철수", "계약금 입금이 확인되어 이사 예약이 확정되었습니다.", "35분 전", "사용자", null),
			new HistoryRecord("최지우", "이사 예정일이 3일 남았습니다. 준비 사항을 확인해 주세요.", "1시간 전", "사용자", null),
			new HistoryRecord("정현우", "예약 상태가 승인 대기에서 견적 협의로 변경되었습니다.", "2시간 전", "사용자", null),
			new HistoryRecord("강민주", "포장 박스 배송이 오늘 오후에 시작될 예정입니다.", "오전 9:20", "사용자", null),
			new HistoryRecord("이서준", "오늘은 이사 예정일입니다. 담당 기사 정보를 확인해 주세요.", "오전 8:00", "사용자", null),
			new HistoryRecord("윤아름", "이사 완료 처리가 되었습니다. 서비스 이용은 어떠셨나요?", "어제", "사용자", null),
			new HistoryRecord("송지호", "방문 견적 일정이 10월 2일 오후 2시로 변경되었습니다.", "어제", "사용자", null),
			new HistoryRecord("한유진", "견적 요청이 정상적으로 접수되었습니다.", "2일 전", "사용자", null));

	@GetMapping
	public String home() {
		return "admin/notification-home";
	}

	@GetMapping("/history")
	public String history(HttpSession session, Model model) {
		// 관리자 알림(헤더 알림 패널) → 메시지에 회원 이름이 있으면 그 회원, 없으면 '관리자' (프로토타입 adminNotificationHistory)
		List<HistoryRecord> all = new ArrayList<>();
		List<AdminMockData.Reservation> customers = AdminMockData.reservations();
		for (AdminMockData.Notification n : AdminState.of(session).getNotifications()) {
			String name = customers.stream().map(r -> r.name()).filter(nm -> n.message().contains(nm)).findFirst().orElse("관리자");
			all.add(new HistoryRecord(name, n.message(), n.relativeTime(), "관리자", n.category()));
		}
		all.addAll(USER_HISTORY);
		long adminCount = all.stream().filter(r -> "관리자".equals(r.recipient())).count();
		model.addAttribute("records", all);
		model.addAttribute("adminCount", adminCount);
		model.addAttribute("userCount", all.size() - adminCount);
		return "admin/notification-history";
	}

	@GetMapping("/messaging")
	public String messaging(HttpSession session, Model model) {
		AdminState s = AdminState.of(session);
		model.addAttribute("s", s);
		model.addAttribute("quietStartLabel", displayTime(s.getQuietStart()));
		model.addAttribute("quietEndLabel", displayTime(s.getQuietEnd()));
		return "admin/notification-messaging";
	}

	/** 바꾼 항목만 전달 (토글·에티켓 시간) */
	@PostMapping("/messaging")
	public String saveMessaging(@RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) Boolean quietHoursEnabled, @RequestParam(required = false) String quietStart,
			@RequestParam(required = false) String quietEnd, @RequestParam(required = false) Boolean smsFallbackEnabled,
			HttpSession session) {
		AdminState s = AdminState.of(session);
		if (enabled != null) s.setMessagingEnabled(enabled);
		if (quietHoursEnabled != null) s.setQuietHoursEnabled(quietHoursEnabled);
		if (quietStart != null && quietStart.matches("\\d{2}:\\d{2}")) s.setQuietStart(quietStart);
		if (quietEnd != null && quietEnd.matches("\\d{2}:\\d{2}")) s.setQuietEnd(quietEnd);
		if (smsFallbackEnabled != null) s.setSmsFallbackEnabled(smsFallbackEnabled);
		return "redirect:/admin/notifications/messaging";
	}

	/** HH:mm → 오전/오후 hh:mm (프로토타입 displayTime) */
	public static String displayTime(String time) {
		int h = Integer.parseInt(time.substring(0, 2));
		return (h >= 12 ? "오후 " : "오전 ") + String.format("%02d", h % 12 == 0 ? 12 : h % 12) + time.substring(2);
	}
}
