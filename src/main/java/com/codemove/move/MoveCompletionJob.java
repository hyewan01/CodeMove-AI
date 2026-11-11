package com.codemove.move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 이사 날짜가 지난 '예약 완료'를 '이사 완료'로 바꿔 고객이 후기를 쓸 수 있게 함 (매일 00:05, 서버 시작 시) */
@Component
public class MoveCompletionJob {

	private static final Logger log = LoggerFactory.getLogger(MoveCompletionJob.class);

	private final MoveService moves;

	public MoveCompletionJob(MoveService moves) {
		this.moves = moves;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Scheduled(cron = "0 5 0 * * *")
	public void run() {
		try {
			int n = moves.completePast();
			if (n > 0) {
				log.info("이사 완료 처리 {}건", n);
			}
		} catch (RuntimeException e) {
			log.warn("이사 완료 처리 실패: {}", e.getMessage());
		}
	}
}
