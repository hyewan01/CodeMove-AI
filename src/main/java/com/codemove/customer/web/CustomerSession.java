package com.codemove.customer.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;

/**
 * 고객 화면 개발 단계용 세션 상태.
 * 프로토타입(App.tsx)의 화면 전환 상태를 서버 세션으로 옮긴 것으로, 회원·견적 API 연동 시 실제 데이터로 대체합니다.
 */
@Getter
@Setter
public class CustomerSession implements Serializable {

	private static final String KEY = "customerSession";
	/** 로그인이 필요한 화면에 접근했을 때 저장해 두는 원래 주소 */
	public static final String LOGIN_REDIRECT_KEY = "customerLoginRedirect";

	private boolean loggedIn;
	private String userName = "김무브";
	private String phone = "010-1234-5678";
	/** 견적 진행 상태: none / waiting-approval / approved / confirmed */
	private String estimateStatus = "approved";
	/** 내 이사 탭: booking / estimate / done */
	private String moveTab = "estimate";
	/** 화면 모드: standard / easy */
	private String mode = "standard";
	private Set<Integer> unreadNotifications = new LinkedHashSet<>(Set.of(1, 4, 6));
	private Set<String> connectedSocials = new LinkedHashSet<>();
	/** 예약 요청사항 (TBL_RESERVATION.REQUEST_NOTE) */
	private String requestNote = "냉장고는 전날 비워둘게요. 도착지 주차는 지하 2층에 가능합니다.";
	/** 이번 세션에서 작성한 후기 */
	private List<CustomerMockData.MyReview> submittedReviews = new ArrayList<>();

	/** 고객 로그인 후 이동할 주소: 저장된 원래 주소(관리자 화면 제외)가 있으면 그곳, 없으면 고객 홈 */
	public static String redirectAfterLogin(HttpSession session) {
		String target = popLoginRedirect(session);
		return "redirect:" + (target != null && !target.startsWith("/admin") ? target : "/home");
	}

	/** 관리자 로그인 후 이동할 주소: 저장된 관리자 화면 주소가 있으면 그곳, 없으면 관리자 홈 */
	public static String redirectAfterAdminLogin(HttpSession session) {
		String target = popLoginRedirect(session);
		return "redirect:" + (target != null && target.startsWith("/admin") ? target : "/admin");
	}

	private static String popLoginRedirect(HttpSession session) {
		Object target = session.getAttribute(LOGIN_REDIRECT_KEY);
		session.removeAttribute(LOGIN_REDIRECT_KEY);
		return target != null ? target.toString() : null;
	}

	public static CustomerSession of(HttpSession session) {
		CustomerSession state = (CustomerSession) session.getAttribute(KEY);
		if (state == null) {
			state = new CustomerSession();
			session.setAttribute(KEY, state);
		}
		return state;
	}

	public boolean isEasy() {
		return "easy".equals(mode);
	}

	public int getNotificationCount() {
		return loggedIn ? unreadNotifications.size() : 0;
	}

	/** 프로토타입 테스트 계정: 김코드(예약 확정) / 김무브(견적 도착) */
	public void loginAs(String account, String provider) {
		boolean kimCode = "kimCode".equals(account);
		userName = kimCode ? "김코드" : "김무브";
		estimateStatus = kimCode ? "confirmed" : "approved";
		moveTab = kimCode ? "booking" : "estimate";
		if (provider != null) {
			connectedSocials.add(provider);
		}
		loggedIn = true;
	}

	public void signupComplete(String name, String phone) {
		if (name != null && !name.isBlank()) {
			this.userName = name;
		}
		if (phone != null && !phone.isBlank()) {
			this.phone = phone;
		}
		estimateStatus = "none";
		moveTab = "estimate";
		unreadNotifications.clear();
		loggedIn = true;
	}
}
