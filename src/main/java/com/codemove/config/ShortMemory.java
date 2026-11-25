package com.codemove.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 거의 안 바뀌는 조회 결과를 잠깐 기억 (DB 왕복을 줄임). 다른 서버에서 바꾼 것은 그 시간만큼 늦게 보일 수 있음 */
public class ShortMemory<K, V> {

	private record Kept<V>(V value, long until) {
	}

	private final long millis;
	private final Map<K, Kept<V>> kept = new ConcurrentHashMap<>();

	public ShortMemory(Duration keep) {
		this.millis = keep.toMillis();
	}

	public V get(K key, Function<K, V> load) {
		long now = System.currentTimeMillis();
		Kept<V> k = kept.get(key);
		if (k != null && now < k.until()) {
			return k.value();
		}
		V v = load.apply(key);
		if (kept.size() > 1000) {
			kept.clear();
		}
		kept.put(key, new Kept<>(v, now + millis));
		return v;
	}

	public void forget(K key) {
		kept.remove(key);
	}
}
