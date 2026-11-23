package com.codemove.estimate;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TEAM03 에 V4(db/migration/V4__item_spec_and_fee.*.sql)가 적용됐는지.
 * 적용 전에는 분석 기록을 ai-logs/ 파일로, 품목은 기존 컬럼만으로 저장하고, 적용 후에는 DB 를 씁니다.
 * 공용 DB 라 서버를 다시 켜지 않아도 알 수 있게 1분마다 다시 확인합니다.
 */
@Component
public class V4Schema {

	private static final Logger log = LoggerFactory.getLogger(V4Schema.class);
	private static final long RECHECK_MS = 60_000;

	private final JdbcTemplate jdbc;
	private volatile Boolean ready;
	private volatile long checkedAt;

	public V4Schema(DataSource dataSource) {
		this.jdbc = new JdbcTemplate(dataSource);
	}

	public boolean ready() {
		long now = System.currentTimeMillis();
		if (ready == null || (!ready && now - checkedAt > RECHECK_MS)) {
			checkedAt = now;
			try {
				Integer n = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = 'TBL_AI_ANALYSIS')"
						+ " + (SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TBL_REQUEST_ITEM'"
						+ " AND COLUMN_NAME = 'MAPPING_TYPE') FROM DUAL", Integer.class);
				boolean was = Boolean.TRUE.equals(ready);
				ready = n != null && n == 2;
				if (ready && !was) {
					log.info("V4 테이블 확인: 분석 기록을 TBL_AI_ANALYSIS 에 저장합니다");
				}
			} catch (RuntimeException e) {
				ready = false;
			}
		}
		return ready;
	}
}
