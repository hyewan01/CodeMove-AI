package com.codemove.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

/** 노트북이 절전에서 깨어나면(시계가 건너뛰면) 그 전에 열어 둔 DB 연결은 죽어 있으므로 모두 새로 연결 */
@Component
public class WakeUpWatch {

	private static final Logger log = LoggerFactory.getLogger(WakeUpWatch.class);
	private static final long TICK = 5_000, SLEPT = 20_000;

	private final DataSource dataSource;
	private long last = System.currentTimeMillis();

	public WakeUpWatch(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Scheduled(fixedDelay = TICK)
	void tick() {
		long now = System.currentTimeMillis(), gap = now - last;
		last = now;
		if (gap > SLEPT && dataSource instanceof HikariDataSource h && h.getHikariPoolMXBean() != null) {
			log.info("{}초 동안 멈춰 있었음 (절전). DB 연결을 새로 맺습니다", gap / 1000);
			h.getHikariPoolMXBean().softEvictConnections();
		}
	}
}
