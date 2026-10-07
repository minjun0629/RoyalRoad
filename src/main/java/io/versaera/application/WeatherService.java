package io.versaera.application;

import io.versaera.domain.common.GameClock;
import io.versaera.domain.weather.Climate;
import io.versaera.domain.weather.WeatherClock;
import io.versaera.domain.weather.WeatherKind;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 날씨 (WTH-01 · WTH-02): 지역마다 지금 날씨 · 예보 · 효과(채집 · 전투 · NPC 일과). 저장 없이 시드 · 시각으로 계산하므로 어느 스레드에서나 부를 수 있다.
 * 서버 시드는 secret.key 에서 (reseed) — 서버마다 날씨 시간표가 다르다.
 */
public final class WeatherService {
    public enum Attack { MELEE, RANGED, SPELL }

    private final Collection<WeatherKind> kinds;
    private final List<Climate> climates;
    private final long windowMs;
    private final int cell;
    private final RegionIndex regions;
    private final GameClock clock;
    private volatile WeatherClock weather;

    WeatherService(Collection<WeatherKind> kinds, List<Climate> climates, long windowMs, int cell, RegionIndex regions, GameClock clock) {
        this.kinds = List.copyOf(kinds);
        this.climates = List.copyOf(climates);
        this.windowMs = windowMs;
        this.cell = cell;
        this.regions = regions;
        this.clock = clock;
        reseed(0L);
    }

    public void reseed(long seed) {
        weather = new WeatherClock(kinds, climates, windowMs, cell, seed);
    }

    public WeatherClock clock() {
        return weather;
    }

    /** 그 지역의 지금 날씨 (지역이 없으면 맑음 취급 — 첫 번째 날씨) */
    public WeatherKind at(String regionId) {
        Region r = regionId == null ? null : regions.byId(regionId);
        return r == null ? weather.kinds().iterator().next() : weather.at(r, clock.nowMillis());
    }

    /** 다음 날씨와 바뀌는 시각 (예보) */
    public Map.Entry<WeatherKind, Long> next(String regionId) {
        Region r = regions.byId(regionId);
        return r == null ? Map.entry(at(null), clock.nowMillis() + windowMs) : weather.next(r, clock.nowMillis());
    }

    public double gather(String regionId, String discipline) {
        return at(regionId).gather(discipline);
    }

    public double combat(String regionId, Attack a) {
        WeatherKind k = at(regionId);
        return switch (a) {
            case MELEE -> k.melee();
            case RANGED -> k.ranged();
            case SPELL -> k.spell();
        };
    }

    /** NPC 가 집 · 주점에 들어가 있는 날씨인가 */
    public boolean indoor(String regionId) {
        return at(regionId).indoor();
    }
}
