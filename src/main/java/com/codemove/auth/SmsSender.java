package com.codemove.auth;

/**
 * 인증번호 문자를 보내는 곳. 지금은 구현이 없어 문자가 나가지 않습니다 (시연 모드).
 * 실제 발송을 붙일 때 이 인터페이스의 빈을 하나 만들면 시연 모드는 저절로 꺼집니다.
 */
public interface SmsSender {

	/** @throws RuntimeException 보내지 못했을 때 */
	void send(String phoneDigits, String text);
}
