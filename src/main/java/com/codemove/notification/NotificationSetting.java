package com.codemove.notification;

import lombok.Data;

/** TBL_NOTIFICATION_SETTING (메시징 운영 설정, 한 행) */
@Data
public class NotificationSetting {
	private Long settingId;
	/** 전체 자동 메시징 가동 */
	private String isActive;
	private String quietEnabled;
	private String quietStart;
	private String quietEnd;
	private String smsFallback;

	public boolean isMessagingEnabled() {
		return "Y".equals(isActive);
	}

	public boolean isQuietHoursEnabled() {
		return "Y".equals(quietEnabled);
	}

	public boolean isSmsFallbackEnabled() {
		return "Y".equals(smsFallback);
	}
}
