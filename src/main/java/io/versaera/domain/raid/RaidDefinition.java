package io.versaera.domain.raid;

import io.versaera.domain.common.DomainException;

import java.util.List;

/**
 * 레이드 (RAID-01): 여러 파티가 묶인 공격대가 거대 보스(bosses.yml)에 도전한다. 일주일에 한 번 귀속.
 *
 * @param region     공격대장이 서 있어야 하는 지역 (보스가 그 지역 가운데에 나타난다)
 * @param boss       bosses.yml 의 보스 id
 * @param mastery    추천(필수) 전투 숙련 — 공격대원 모두 가장 높은 전투 숙련이 이 이상
 * @param timeLimitMs 이 안에 쓰러뜨려야 한다
 */
public record RaidDefinition(String id, String name, String region, String boss, int minPlayers, int maxPlayers, int mastery,
                             long timeLimitMs, long money, List<String> items, String title, int fame, String desc) {
    public RaidDefinition {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "raid.bad_id", "레이드 id 형식: " + id);
        DomainException.require(minPlayers >= 2 && maxPlayers >= minPlayers && maxPlayers <= 40, "raid.bad_size", "공격대 인원: " + id);
        DomainException.require(timeLimitMs >= 60_000 && money >= 0 && fame >= 0 && mastery >= 0 && mastery <= io.versaera.domain.skill.Mastery.MAX_LEVEL, "raid.bad_reward", "레이드 수치: " + id);
        items = List.copyOf(items);
    }
}
