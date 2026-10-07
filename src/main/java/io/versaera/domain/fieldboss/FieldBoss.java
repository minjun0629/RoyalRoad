package io.versaera.domain.fieldboss;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.ItemOptions;
import io.versaera.domain.quest.QuestDefinition;

import java.util.List;
import java.util.Set;

/**
 * 필드 보스 (content/field_bosses.yml, BOS-02). 이름 · 사는 곳 · 성격은 원작 (나무위키 「로열 로드/몬스터」 사용자 제공 본문, CANON),
 * 체력 · 패턴 수치 · 드롭 확률 · 둥지 자리는 이 게임의 것 (ORIGINAL). 거대 보스(bosses.yml)와 달리 바닐라 몸에 이름 · 능력을 붙인 몬스터로,
 * 둥지(region) 가까이에 사람이 오면 나타나고 쓰러지면 respawn 분 뒤에 다시 나타난다.
 *
 * @param entity    바닐라 엔티티 종류 (Bukkit EntityType 이름)
 * @param region    둥지 지역 id (regions.yml)
 * @param mechanics REGEN · MINIONS · FEAR · VESSEL(생명의 그릇: 그릇이 남아 있으면 한 번 되살아남) · ENRAGE · BREATH · FLIGHT
 * @param kinds     속성 추가 피해 판정용 (UNDEAD · DEMON · LARGE · DRAGON)
 * @param drops     쓰러뜨린 사람(가장 많이 때린 사람)에게 확률로
 * @param reward    기여 10% 이상인 사람마다
 * @param look      리소스팩 모델 모양 (KNIGHT · CASTER · DEMON · DRAGON · BEAST · GOLEM · HYDRA · SALAMANDER · FLYER · VAMPIRE)
 * @param size      모델 크기 배율 (1 ~ 4) — 판정은 바닐라 몸 그대로, 보이는 모습만 커진다
 */
public record FieldBoss(String id, String name, String entity, String region, double maxHp, double damage, int respawnMinutes,
                        Set<String> mechanics, String minion, Set<ItemOptions.Kind> kinds, List<Drop> drops, QuestDefinition.Reward reward,
                        String description, String source, String look, double size) {
    /** 리소스팩 모델 모양 (ModelKit 템플릿) */
    public static final Set<String> LOOKS = Set.of("KNIGHT", "CASTER", "DEMON", "DRAGON", "BEAST", "GOLEM", "HYDRA", "SALAMANDER", "FLYER", "VAMPIRE");

    public static final Set<String> MECHANICS = Set.of("REGEN", "MINIONS", "FEAR", "VESSEL", "ENRAGE", "BREATH", "FLIGHT");

    /** @param chance 0 ~ 1 */
    public record Drop(String item, int quality, double chance) {
        public Drop {
            DomainException.require(chance > 0 && chance <= 1 && quality >= 0 && quality <= 1000, "fboss.bad_drop", "드롭 값이 잘못되었습니다: " + item);
        }
    }

    public FieldBoss {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "fboss.bad_id", "필드 보스 id 형식: " + id);
        DomainException.require(maxHp > 0 && maxHp <= 2048 && damage >= 0 && respawnMinutes >= 1, "fboss.bad_numbers",
                "필드 보스 수치가 잘못되었습니다 (체력 ≤ 2048): " + id);
        mechanics = Set.copyOf(mechanics == null ? Set.of() : mechanics);
        for (String m : mechanics) DomainException.require(MECHANICS.contains(m), "fboss.bad_mechanic", "없는 패턴: " + m + " (" + id + ")");
        DomainException.require(!mechanics.contains("MINIONS") || minion != null, "fboss.no_minion", "MINIONS 에는 minion 이 필요합니다: " + id);
        kinds = Set.copyOf(kinds == null ? Set.of() : kinds);
        drops = List.copyOf(drops == null ? List.of() : drops);
        reward = reward == null ? QuestDefinition.Reward.NONE : reward;
        DomainException.require(look != null && LOOKS.contains(look), "fboss.bad_look", "없는 모델 모양: " + look + " (" + id + ")");
        DomainException.require(size >= 1 && size <= 4, "fboss.bad_size", "모델 크기는 1 ~ 4: " + id);
    }

    /** 기여 몫이 이 비율 이상이면 보상 */
    public static final double SHARE = 0.10;
}
