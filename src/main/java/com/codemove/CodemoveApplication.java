package com.codemove;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class CodemoveApplication {

	public static void main(String[] args) {
		// 방금(5초 안) 쓴 DB 연결은 살아 있는지 다시 묻지 않음 (기본 0.5초, 느린 네트워크에서 왕복을 아낌)
		if (System.getProperty("com.zaxxer.hikari.aliveBypassWindowMs") == null) {
			System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "5000");
		}
		SpringApplication.run(CodemoveApplication.class, args);
	}

}
