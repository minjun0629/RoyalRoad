package io.versaera.domain.worldevent;

/**
 * 월드 이벤트 시간표 (EVT-01, 순수 계산). 확률로 "터지는" 이벤트가 아니라, 서버 시드로 정해진 시간표를 따른다:
 * n 번째 주기의 시작 = 기준 + n × 주기 + 흔들림(시드 · 이벤트 · n 의 해시).
 * 그래서 관찰하는 사람(어부의 날씨 예보, 대상단장의 소식)은 다음 이벤트를 미리 알 수 있다 — 예보 시간(forecast) 안에서만.
 */
public final class WorldEventClock {
    public record Window(long start, long end) {
        public boolean contains(long t) {
            return t >= start && t < end;
        }
    }

    private final long seed, epoch;

    public WorldEventClock(long seed, long epoch) {
        this.seed = seed;
        this.epoch = epoch;
    }

    private long jitter(WorldEventDefinition d, long n) {
        if (d.jitterMs() <= 0) return 0;
        long h = seed ^ (d.id().hashCode() * 0x9E3779B97F4A7C15L) ^ (n * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return Math.floorMod(h, d.jitterMs());
    }

    public Window window(WorldEventDefinition d, long n) {
        long s = epoch + n * d.periodMs() + jitter(d, n);
        return new Window(s, s + d.durationMs());
    }

    private long cycle(WorldEventDefinition d, long now) {
        return Math.floorDiv(now - epoch, d.periodMs());
    }

    /** 지금 진행 중이면 그 시간, 아니면 null */
    public Window activeAt(WorldEventDefinition d, long now) {
        long n = cycle(d, now);
        for (long k = n - 1; k <= n; k++) {
            Window w = window(d, k);
            if (w.contains(now)) return w;
        }
        return null;
    }

    /** 다음 시작 (진행 중이면 그다음) */
    public Window next(WorldEventDefinition d, long now) {
        long n = cycle(d, now);
        for (long k = n; ; k++) {
            Window w = window(d, k);
            if (w.start() > now) return w;
        }
    }

    /** 예보: 다음 시작이 예보 시간 안이면 그 시각, 아니면 -1 */
    public long forecast(WorldEventDefinition d, long now) {
        Window w = next(d, now);
        return w.start() - now <= d.forecastMs() ? w.start() : -1;
    }
}
