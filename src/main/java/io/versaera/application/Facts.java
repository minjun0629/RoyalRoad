package io.versaera.application;

import io.versaera.domain.hidden.PlayerFacts;

/**
 * 조건 판정용 플레이어 사실 (히든 콘텐츠 · 직업 · 퀘스트가 함께 씀). 지역 · 게임 시각은 플랫폼이 넘겨 준다.
 * DB 스레드에서만 쓴다.
 */
final class Facts implements PlayerFacts {
    private final GameServices s;
    private final String uuid, region;
    private final int hour;

    Facts(GameServices s, String uuid, String region, int hour) {
        this.s = s;
        this.uuid = uuid;
        this.region = region;
        this.hour = hour;
    }

    public long counter(String key) { return s.growth.counter(uuid, key); }
    public int mastery(String d) { return s.growth.level(uuid, d); }
    public int affinity(String npc) { return s.relations.affinity(uuid, npc); }
    public String region() { return region; }
    public boolean discovered(String kind, String ref) { return s.exploration.discovered(uuid, kind, ref); }
    public int hour() { return hour; }
}
