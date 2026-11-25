package com.codemove.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 화면 하나를 여는 동안(GET 요청) 똑같은 조회를 다시 하지 않게 결과를 기억. DB 가 멀어 왕복 한 번이 비쌈.
 * 요청이 끝나면 사라지고, 그 요청에서 무엇이든 저장하면 바로 비움. 저장 요청(POST)에서는 쓰지 않음.
 */
@Component
@Intercepts({
		@Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class }),
		@Signature(type = Executor.class, method = "update", args = { MappedStatement.class, Object.class }) })
public class RequestQueryCache implements Interceptor {

	private static final String KEY = RequestQueryCache.class.getName();

	@Override
	public Object intercept(Invocation in) throws Throwable {
		Map<CacheKey, List<?>> cache = cache();
		if (cache == null) {
			return in.proceed();
		}
		Object[] a = in.getArgs();
		MappedStatement ms = (MappedStatement) a[0];
		if (a.length == 2) {
			cache.clear();
			return in.proceed();
		}
		// 번호표(SEQ.NEXTVAL) 같은 selectKey 는 매번 새로
		if (a[3] != null || ms.isFlushCacheRequired() || ms.getId().contains("!selectKey")) {
			return in.proceed();
		}
		CacheKey key = ((Executor) in.getTarget()).createCacheKey(ms, a[1], (RowBounds) a[2], ms.getBoundSql(a[1]));
		List<?> hit = cache.get(key);
		if (hit == null) {
			hit = (List<?>) in.proceed();
			cache.put(key, new ArrayList<>(hit));
			return hit;
		}
		return new ArrayList<>(hit);
	}

	@SuppressWarnings("unchecked")
	private static Map<CacheKey, List<?>> cache() {
		RequestAttributes r = RequestContextHolder.getRequestAttributes();
		if (!(r instanceof ServletRequestAttributes web) || !"GET".equals(web.getRequest().getMethod())) {
			return null;
		}
		Map<CacheKey, List<?>> m = (Map<CacheKey, List<?>>) web.getRequest().getAttribute(KEY);
		if (m == null) {
			m = new HashMap<>();
			web.getRequest().setAttribute(KEY, m);
		}
		return m;
	}
}
