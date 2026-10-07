package io.versaera.domain.dungeon;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.quest.QuestDefinition;

import java.util.List;

/**
 * 던전 정의 (content/dungeons.yml).
 * @param palette 방을 지을 블록 (바닥 · 벽 · 장식) — 플랫폼이 Material 로 바꾼다
 * @param monsters 전투방에 나오는 몬스터 (엔티티 종류)
 * @param boss bosses.yml 의 보스 id (거대 보스) 또는 null 이면 bossMob 을 강화해서 쓴다
 */
public record DungeonDefinition(String id, String name, String region, int danger, int rooms, int minParty, int maxParty, long timeLimitMs,
                                int levers, List<String> palette, List<String> monsters, int monstersPerRoom, double monsterHealth,
                                String boss, String bossMob, double bossHealth, QuestDefinition.Reward reward,
                                QuestDefinition.Reward hiddenReward, long cooldownMs, String source) {
    public DungeonDefinition {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "dungeon.bad_id", "던전 id 형식: " + id);
        DomainException.require(rooms >= 5 && rooms <= 40, "dungeon.bad_rooms", "방 5 ~ 40: " + id);
        DomainException.require(minParty >= 1 && maxParty >= minParty && maxParty <= 8, "dungeon.bad_party", "인원 1 ~ 8: " + id);
        DomainException.require(palette != null && palette.size() >= 3, "dungeon.bad_palette", "palette 는 바닥 · 벽 · 장식 3개 이상: " + id);
        DomainException.require(monsters != null && !monsters.isEmpty(), "dungeon.no_monsters", "몬스터가 없습니다: " + id);
        palette = List.copyOf(palette);
        monsters = List.copyOf(monsters);
    }
}
