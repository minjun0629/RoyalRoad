package io.versaera.domain.weather;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.world.Region;

import java.util.*;

/**
 * 날씨 시간표 (WTH-02, 순수 계산). 저장하지 않는다 — 서버 시드 · 기후 · 지역의 큰 칸(cell 블록) · 시간 칸(windowMs)만으로 정해진다.
 * 이웃 지역은 같은 칸이면 같은 날씨를 겪고, 다음 칸의 날씨를 미리 알 수 있다 (예보).
 */
public final class WeatherClock {
    private final Map<String, WeatherKind> kinds;
    private final List<Climate> climates;
    private final long windowMs;
    private final int cell;
    private final long seed;

    public WeatherClock(Collection<WeatherKind> kinds, List<Climate> climates, long windowMs, int cell, long seed) {
        Map<String, WeatherKind> m = new LinkedHashMap<>();
        for (WeatherKind k : kinds) DomainException.require(m.putIfAbsent(k.id(), k) == null, "weather.dup", "날씨 중복: " + k.id());
        this.kinds = Map.copyOf(m);
        DomainException.require(climates.stream().anyMatch(c -> c.tags().isEmpty()), "weather.no_default", "기본 기후(tags 없음)가 필요합니다");
        for (Climate c : climates)
            for (String k : c.weights().keySet()) DomainException.require(m.containsKey(k), "weather.unknown", "없는 날씨 " + k + ": " + c.id());
        this.climates = List.copyOf(climates);
        DomainException.require(windowMs >= 60_000 && cell >= 256, "weather.bad_clock", "날씨 칸이 너무 작습니다");
        this.windowMs = windowMs;
        this.cell = cell;
        this.seed = seed;
    }

    public Climate climate(Region r) {
        for (Climate c : climates) if (!c.tags().isEmpty() && c.tags().stream().anyMatch(r.tags()::contains)) return c;
        return climates.stream().filter(c -> c.tags().isEmpty()).findFirst().orElseThrow();
    }

    public WeatherKind kind(String id) {
        WeatherKind k = kinds.get(id);
        DomainException.require(k != null, "weather.unknown", "없는 날씨: " + id);
        return k;
    }

    public Collection<WeatherKind> kinds() {
        return kinds.values();
    }

    public long windowMs() {
        return windowMs;
    }

    /** 그 지역의 지금 날씨 */
    public WeatherKind at(Region r, long now) {
        return pick(r, Math.floorDiv(now, windowMs));
    }

    /** 다음 날씨와 바뀌는 시각 */
    public Map.Entry<WeatherKind, Long> next(Region r, long now) {
        long w = Math.floorDiv(now, windowMs);
        WeatherKind cur = pick(r, w);
        for (long i = w + 1; i < w + 48; i++) {
            WeatherKind k = pick(r, i);
            if (!k.id().equals(cur.id())) return Map.entry(k, i * windowMs);
        }
        return Map.entry(cur, (w + 1) * windowMs);
    }

    private WeatherKind pick(Region r, long window) {
        Climate c = climate(r);
        long cx = Math.floorDiv((r.minX() + r.maxX()) / 2, cell), cz = Math.floorDiv((r.minZ() + r.maxZ()) / 2, cell);
        long h = mix(seed ^ mix(c.id().hashCode()) ^ mix(cx * 0x9E3779B97F4A7C15L + cz) ^ mix(window * 0xC2B2AE3D27D4EB4FL) ^ r.world().hashCode());
        int total = c.weights().values().stream().mapToInt(Integer::intValue).sum();
        long roll = Math.floorMod(h, total);
        for (Map.Entry<String, Integer> e : new TreeMap<>(c.weights()).entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return kinds.get(e.getKey());
        }
        return kinds.get(c.weights().keySet().iterator().next());
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
