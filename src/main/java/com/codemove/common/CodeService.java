package com.codemove.common;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Service;

/** 공통코드(TBL_CODE) 이름. 처음 쓸 때 한 번 읽어 둡니다 (코드 수정 시 서버 재시작). */
@Service
public class CodeService {

	@Mapper
	public interface CodeMapper {
		@Select("SELECT GROUP_CODE, CODE, CODE_NAME, USER_NAME FROM TBL_CODE WHERE IS_ACTIVE = 'Y'")
		List<Map<String, Object>> findAll();
	}

	private final CodeMapper mapper;
	private final Map<String, String[]> names = new ConcurrentHashMap<>();

	public CodeService(CodeMapper mapper) {
		this.mapper = mapper;
	}

	private String[] entry(String group, String code) {
		if (names.isEmpty()) {
			for (Map<String, Object> row : mapper.findAll()) {
				names.put(row.get("GROUP_CODE") + ":" + String.valueOf(row.get("CODE")).trim(), new String[] {
						(String) row.get("CODE_NAME"), (String) row.get("USER_NAME") });
			}
		}
		return code == null ? null : names.get(group + ":" + code.trim());
	}

	/** 관리자 화면 이름 (예: HOUSE_TYPE 01 → 원룸이사) */
	public String name(String group, String code) {
		String[] e = entry(group, code);
		return e == null ? "" : e[0];
	}

	/** 고객 화면 이름 (없으면 관리자 이름, 예: HOUSE_TYPE 01 → 원룸 / 오피스텔) */
	public String userName(String group, String code) {
		String[] e = entry(group, code);
		return e == null ? "" : e[1] != null ? e[1] : e[0];
	}
}
