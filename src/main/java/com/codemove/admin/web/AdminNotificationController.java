package com.codemove.admin.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.codemove.customer.web.CustomerViews;
import com.codemove.notification.NotificationService;
import com.codemove.notification.NotificationSetting;

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

	private final NotificationService notifications;

	public AdminNotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	public String home() {
		return "admin/notification-home";
	}

	/** 사용자(고객)에게 보낸 알림 (최근 200건) */
	@GetMapping("/history")
	public String history(Model model) {
		java.time.LocalDate today = java.time.LocalDate.now();
		List<com.codemove.notification.Notification> sent = notifications.history(400).stream()
				.filter(n -> "01".equals(n.getReceiverType())).limit(200).toList();
		List<HistoryRecord> all = sent.stream().map(n -> new HistoryRecord(
				n.getMemberName() != null ? n.getMemberName() : "고객", n.getNtfContent(), CustomerViews.ago(n.getCreatedAt()),
				"사용자", n.getNtfTitle())).toList();
		model.addAttribute("records", all);
		model.addAttribute("totalCount", all.size());
		model.addAttribute("todayCount", sent.stream()
				.filter(n -> n.getCreatedAt() != null && n.getCreatedAt().toLocalDate().equals(today)).count());
		return "admin/notification-history";
	}

	@GetMapping("/messaging")
	public String messaging(Model model) {
		NotificationSetting s = notifications.setting();
		model.addAttribute("s", s);
		model.addAttribute("quietStartLabel", displayTime(s.getQuietStart()));
		model.addAttribute("quietEndLabel", displayTime(s.getQuietEnd()));
		return "admin/notification-messaging";
	}

	/** 바꾼 항목만 전달 (토글·에티켓 시간) */
	@PostMapping("/messaging")
	public String saveMessaging(@RequestParam(required = false) Boolean enabled,
			@RequestParam(required = false) Boolean quietHoursEnabled, @RequestParam(required = false) String quietStart,
			@RequestParam(required = false) String quietEnd, @RequestParam(required = false) Boolean smsFallbackEnabled) {
		notifications.updateSetting(enabled, quietHoursEnabled, quietStart, quietEnd, smsFallbackEnabled);
		return "redirect:/admin/notifications/messaging";
	}

	/** HH:mm → 오전/오후 hh:mm (프로토타입 displayTime) */
	public static String displayTime(String time) {
		if (time == null || !time.matches("\\d{2}:\\d{2}")) {
			return "";
		}
		int h = Integer.parseInt(time.substring(0, 2));
		return (h >= 12 ? "오후 " : "오전 ") + String.format("%02d", h % 12 == 0 ? 12 : h % 12) + time.substring(2);
	}
}
