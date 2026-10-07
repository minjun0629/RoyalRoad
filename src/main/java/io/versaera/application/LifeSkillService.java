package io.versaera.application;

import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.LifeSkills;

import java.util.random.RandomGenerator;

/**
 * 생활 스킬 (SKL-05). 판정 · 숙련 · 아이템 변화는 여기서, 버프 · 회복 같은 몸의 효과는 플랫폼 (LifeSkillRuntime) 이 메모리로.
 * 묶음 재료(붕대 · 숫돌)는 인벤토리에 있는 것을 플랫폼이 먼저 빼고 부른다.
 */
public final class LifeSkillService {
    /** @param pct 효과 % · @param level 숙련 레벨 */
    public record Care(String discipline, int level, double pct, int minutes, String itemId) {}

    public record Destruction(int artistry, int quality, double pct, int minutes) {}

    private final TxRunner tx;
    private final GameServices s;

    LifeSkillService(TxRunner tx, GameServices s) {
        this.tx = tx;
        this.s = s;
    }

    /** 붕대 감기: 회복량 */
    public double bandage(String uuid, boolean inCombat) {
        int lv = s.growth.level(uuid, "bandaging");
        s.growth.addXp(uuid, "bandaging", inCombat ? 6 : 4, 1);
        return LifeSkills.bandageHeal(lv, inCombat);
    }

    /**
     * 검 갈기(무기) · 방어구 닦기(쇠 · 판금 방어구) · 다림질(천 · 가죽 방어구). 맞지 않는 물건이면 예외.
     *
     * @param discipline whetting · polishing · ironing
     */
    public Care care(String uuid, String discipline, String itemId) {
        ItemInstance it = s.items.find(itemId).filter(x -> x.custody().ownedBy(uuid))
                .orElseThrow(() -> DomainException.of("care.no_item", "내 고유 장비를 손에 들어야 합니다"));
        ItemType t = s.items.types().get(it.typeId());
        boolean cloth = t.material().startsWith("LEATHER_") || t.id().contains("robe");
        switch (discipline) {
            case "whetting" -> DomainException.require(t.category() == io.versaera.domain.item.ItemCategory.WEAPON || t.stats().containsKey("attack"),
                    "care.not_weapon", "검 갈기는 무기에만");
            case "polishing" -> DomainException.require(t.category() == io.versaera.domain.item.ItemCategory.ARMOR && !cloth,
                    "care.not_metal", "방어구 닦기는 쇠 · 판금 방어구에만 (천 · 가죽은 다림질)");
            case "ironing" -> DomainException.require(t.category() == io.versaera.domain.item.ItemCategory.ARMOR && cloth,
                    "care.not_cloth", "다림질은 천 · 가죽 방어구에만");
            default -> throw DomainException.of("care.unknown", "없는 손질: " + discipline);
        }
        DomainException.require(!it.broken(), "care.broken", "부서진 장비는 먼저 고쳐야 합니다");
        int lv = s.growth.level(uuid, discipline);
        s.growth.addXp(uuid, discipline, 8, 1);
        return new Care(discipline, lv, LifeSkills.carePct(lv), LifeSkills.CARE_MINUTES, itemId);
    }

    /** 도축: 동물을 잡았을 때. @return 더 얻은 개수 (0 이면 없음). 고기 · 가죽은 배달함으로 */
    public int butcher(String uuid, boolean hide, RandomGenerator rng) {
        int lv = s.growth.level(uuid, "butchery");
        s.growth.addXp(uuid, "butchery", 3, 1);
        if (rng.nextDouble() >= LifeSkills.butcherChance(lv)) return 0;
        int n = LifeSkills.butcherExtra(lv);
        s.items.deliverBulk(uuid, hide ? "hide" : "raw_meat", 400 + lv * 10, n, "butchery");
        return n;
    }

    /** 사자후: 검술 숙련 5 이상. @return {반지름, 공포 초} */
    public double[] roar(String uuid) {
        int lv = s.growth.level(uuid, "swordsmanship");
        DomainException.require(lv >= 5, "roar.level", "사자후는 검술 숙련 5 부터 (지금 " + lv + ")");
        s.growth.record(uuid, "roar.used", 1);
        return new double[]{LifeSkills.roarRadius(lv), LifeSkills.roarSeconds(lv, s.growth.statPoints(uuid, "endurance"))};
    }

    /** 조각 파괴술: 조각가가 손에 든 자기 조각품을 부수고, 예술 스탯만큼 잠시 힘을 얻는다 (조각품은 사라진다) */
    public Destruction destroySculpture(String uuid, String itemId) {
        DomainException.require(s.jobs.held(uuid).values().stream().anyMatch(h -> h.jobId().equals("sculptor")),
                "destroy.job", "조각 파괴술은 조각가만 쓸 수 있습니다");
        ItemInstance it = s.items.find(itemId).filter(x -> x.custody().ownedBy(uuid))
                .orElseThrow(() -> DomainException.of("destroy.no_item", "부술 조각품을 손에 들어야 합니다"));
        ItemType t = s.items.types().get(it.typeId());
        DomainException.require(t.hasTag("sculpture") && !t.hasTag("relic"), "destroy.not_sculpture", "조각품만 부술 수 있습니다 (비기의 조각상은 안 됨)");
        int art = s.growth.statPoints(uuid, "artistry");
        s.items.destroy(itemId, uuid, "조각 파괴술", "destroy:" + itemId);
        s.growth.addXp(uuid, "sculpting", 5, 1);
        return new Destruction(art, it.quality(), LifeSkills.destructionPct(art), LifeSkills.destructionMinutes(it.quality()));
    }
}
