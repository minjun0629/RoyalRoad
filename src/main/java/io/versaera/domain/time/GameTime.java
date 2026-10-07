package io.versaera.domain.time;

import io.versaera.domain.common.DomainException;

/**
 * 게임 속 시간 (CANON: 로열 로드의 시간은 현실보다 약 4배 빠르다 — 현실 하루 = 게임 4일).
 * 게임 시각은 현실 시각 × 비율로 정해져 서버를 껐다 켜도 이어진다 (저장할 것 없음).
 * 비율 0 이면 마인크래프트 기본 낮밤(20분 하루)을 그대로 쓴다.
 */
public record GameTime(int ratio) {
    public static final long DAY_MS = 86_400_000L;

    public GameTime {
        DomainException.require(ratio >= 0 && ratio <= 72, "time.bad_ratio", "시간 비율은 0 ~ 72");
    }

    public boolean enabled() {
        return ratio > 0;
    }

    public long gameMillis(long realMillis) {
        return realMillis * ratio;
    }

    /** 게임 날짜 (1970 년부터 센 게임 일) */
    public long day(long realMillis) {
        return Math.floorDiv(gameMillis(realMillis), DAY_MS);
    }

    /** 게임 시각 0 ~ 23 */
    public int hour(long realMillis) {
        return (int) (Math.floorMod(gameMillis(realMillis), DAY_MS) / 3_600_000L);
    }

    /** 마인크래프트 세계 시간 (0 = 아침 6시, 24000 틱 = 하루) */
    public long minecraftTime(long realMillis) {
        long msOfDay = Math.floorMod(gameMillis(realMillis), DAY_MS);
        return Math.floorMod(msOfDay * 24_000L / DAY_MS - 6_000L, 24_000L);
    }

    /** 게임 시간 n 일이 흐르는 데 드는 현실 시간 (비율 0 = 마인크래프트 하루 20분) */
    public long realMillisFor(double gameDays) {
        return ratio == 0 ? (long) (gameDays * 1_200_000L) : (long) (gameDays * DAY_MS / ratio);
    }
}
