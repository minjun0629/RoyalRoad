package io.versaera.application;

import io.versaera.application.port.ItemRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemOptions;
import io.versaera.domain.item.ItemType;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * 장비 (ITM-02): 착용 조건 · 감정. 능력 계산 자체는 도메인 {@link ItemOptions}, 타격마다의 적용은 플랫폼 (CombatListener · GearRuntime).
 * <ul>
 *   <li>착용 조건: 숙련 레벨 · 행동 스탯 · 명성. 대장 기술 숙련이 높으면 조건이 줄어든다 (원작: 대장장이 스킬)</li>
 *   <li>감정 (원작의 '감정' 스킬): 숨은 능력 · 내력은 감정해야 보인다. 성공하면 그 아이템에 기록이 남아 누구에게 넘겨도 보인다.
 *       감정 숙련이 오를수록 어려운 물건도 감정한다. 처음 감정하는 종류면 숙련을 더 많이 얻는다</li>
 * </ul>
 */
public final class GearService {
    public static final String APPRAISED = "appraised";

    public record Appraisal(boolean success, ItemType type, int difficulty, long xp, boolean firstOfKind) {}

    private final TxRunner tx;
    private final ItemRepository items;
    private final GameServices s;
    private final GameClock clock;

    GearService(TxRunner tx, ItemRepository items, GameServices s, GameClock clock) {
        this.tx = tx;
        this.items = items;
        this.s = s;
        this.clock = clock;
    }

    /** 착용 조건에 쓰는 값: "mastery.X" → 숙련 레벨 · "stat.X" → 스탯 포인트 · "fame" → 명성 · "smithing" → 대장 기술 레벨 */
    public Map<String, Long> context(String uuid) {
        Map<String, Long> out = new HashMap<>();
        for (var d : s.growth.disciplines()) out.put("mastery." + d.id(), (long) s.growth.level(uuid, d.id()));
        s.growth.statPoints(uuid).forEach((k, v) -> out.put("stat." + k, (long) v));
        out.put("fame", s.reputation.standing(uuid).fame());
        out.put("smithing", (long) s.growth.level(uuid, "smithing"));
        return out;
    }

    /** 못 채운 조건 (비면 쓸 수 있다) */
    public static Map<String, Long> unmet(ItemType t, Map<String, Long> ctx) {
        return ItemOptions.unmet(t.requires(), k -> ctx.getOrDefault(k, 0L), ctx.getOrDefault("smithing", 0L).intValue());
    }

    public static boolean appraised(ItemInstance it) {
        return "1".equals(it.props().get(APPRAISED));
    }

    /** 손에 든 고유 아이템을 감정한다 */
    public Appraisal appraise(String uuid, String itemId, RandomGenerator rng) {
        int lv = s.growth.level(uuid, "appraisal");
        Appraisal a = tx.inTx(() -> {
            ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            DomainException.require(it.custody().ownedBy(uuid), "item.not_owner", "내 아이템만 감정할 수 있습니다");
            ItemType t = s.items.types().get(it.typeId());
            DomainException.require(t.hasHidden(), "appraise.plain", "감정할 것이 없는 평범한 물건입니다");
            DomainException.require(!appraised(it), "appraise.done", "이미 감정한 물건입니다");
            int diff = ItemOptions.appraiseDifficulty(t);
            boolean ok = rng.nextDouble() < ItemOptions.appraiseChance(lv, diff);
            boolean first = false;
            if (ok) {
                it.prop(APPRAISED, "1");
                items.update(it);
                items.history(it.id(), "APPRAISED", uuid, "lv=" + lv, clock.nowMillis());
                first = s.progress.discover(uuid, "appraise", t.id(), clock.nowMillis());
            }
            long xp = ok ? 10 + diff * 3L + (first ? 20 : 0) : 3;
            return new Appraisal(ok, t, diff, xp, first);
        });
        s.growth.addXp(uuid, "appraisal", a.xp(), a.difficulty());
        s.growth.record(uuid, "appraise.tries", 1);
        return a;
    }
}
