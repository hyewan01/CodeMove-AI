package com.codemove.config;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

/** 화면(HTML)을 그리는 요청인지. 알림 확인 같은 JSON 요청과 저장 요청(POST)에서는 화면 공통 값을 조회하지 않음 */
public final class PageRequest {

	private PageRequest() {
	}

	public static boolean is(HttpServletRequest request) {
		return "GET".equals(request.getMethod())
				&& request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod h
				&& !AnnotatedElementUtils.hasAnnotation(h.getMethod(), ResponseBody.class);
	}
}
