package com.codemove.auth;

import org.springframework.web.servlet.HandlerInterceptor;

import com.codemove.admin.web.AdminSession;
import com.codemove.customer.web.CustomerSession;
import com.codemove.member.Member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 로그인 유지 쿠키가 있으면 고객으로 다시 로그인 (관리자로 로그인해 있으면 건드리지 않음) */
public class KeepLoginInterceptor implements HandlerInterceptor {

	private final LoginDevices devices;

	public KeepLoginInterceptor(LoginDevices devices) {
		this.devices = devices;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!devices.hasCookie(request) || AdminSession.isLoggedIn(request.getSession())
				|| CustomerSession.of(request.getSession()).isLoggedIn()) {
			return true;
		}
		Member m = devices.find(request);
		if (m == null) {
			devices.forget(request, response);
		} else {
			request.changeSessionId();
			CustomerSession.of(request.getSession()).login(m.getMemberId(), m.getMemberName());
		}
		return true;
	}
}
