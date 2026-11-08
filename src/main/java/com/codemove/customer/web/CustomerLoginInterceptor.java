package com.codemove.customer.web;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 필요 화면 접근 시 로그인 시트로 이동 (프로토타입 protectedScreens).
 * Spring Security 도입 시 대체합니다.
 */
public class CustomerLoginInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (CustomerSession.of(request.getSession()).isLoggedIn()) {
			return true;
		}
		// 로그인 후 원래 가려던 화면으로 돌아가도록 저장 (예: 하단 [이사견적] → 로그인 → 영상 올리기)
		if ("GET".equals(request.getMethod())) {
			String query = request.getQueryString();
			String target = request.getRequestURI().substring(request.getContextPath().length())
					+ (query != null ? "?" + query : "");
			request.getSession().setAttribute(CustomerSession.LOGIN_REDIRECT_KEY, target);
		}
		response.sendRedirect(request.getContextPath() + "/login?backdrop=home");
		return false;
	}
}
