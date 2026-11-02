package com.codemove;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Oracle DB 연결 확인용 테스트.
 * application-local.yml 또는 DB_URL/DB_USERNAME/DB_PASSWORD 환경변수가 필요합니다.
 */
@SpringBootTest
class DbConnectionTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void connect() throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			assertThat(conn.isValid(5)).isTrue();
			System.out.println("[DB] product = " + conn.getMetaData().getDatabaseProductVersion().replace('\n', ' '));
			System.out.println("[DB] driver  = " + conn.getMetaData().getDriverName() + " " + conn.getMetaData().getDriverVersion());
			System.out.println("[DB] user    = " + conn.getMetaData().getUserName());
		}

		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("SELECT 1 FROM DUAL", Integer.class)).isEqualTo(1);

		List<Map<String, Object>> nls = jdbc.queryForList(
				"SELECT PARAMETER, VALUE FROM NLS_DATABASE_PARAMETERS"
						+ " WHERE PARAMETER IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET', 'NLS_LENGTH_SEMANTICS')");
		nls.forEach(row -> System.out.println("[DB] " + row.get("PARAMETER") + " = " + row.get("VALUE")));

		List<String> tables = jdbc.queryForList("SELECT TABLE_NAME FROM USER_TABLES ORDER BY TABLE_NAME", String.class);
		List<String> sequences = jdbc.queryForList("SELECT SEQUENCE_NAME FROM USER_SEQUENCES ORDER BY SEQUENCE_NAME", String.class);
		System.out.println("[DB] tables(" + tables.size() + ")    = " + tables);
		System.out.println("[DB] sequences(" + sequences.size() + ") = " + sequences);
	}
}
