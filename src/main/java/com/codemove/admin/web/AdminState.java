package com.codemove.admin.web;

import java.io.Serializable;

import com.codemove.notification.NotificationService.Toast;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;

/**
 * 관리자 화면 사이에서만 이어지는 값 (세션). 업무 데이터는 모두 DB 에 있습니다.
 * · 자동 메시징 루틴이 만든 토스트 (다음 화면에서 한 번 표시)
 * · 견적서 등록 단계 진행 상태 (등록을 마치면 DB 에 저장)
 */
@Getter
@Setter
public class AdminState implements Serializable {

	private static final String KEY = "adminState";

	private Toast pendingToast;
	private AdminEstimates.Registration registration = new AdminEstimates.Registration();

	public static AdminState of(HttpSession session) {
		AdminState state = (AdminState) session.getAttribute(KEY);
		if (state == null) {
			state = new AdminState();
			session.setAttribute(KEY, state);
		}
		return state;
	}

	public void toast(Toast toast) {
		if (toast != null) {
			pendingToast = toast;
		}
	}

	public Toast takeToast() {
		Toast toast = pendingToast;
		pendingToast = null;
		return toast;
	}
}
