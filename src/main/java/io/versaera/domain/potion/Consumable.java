package io.versaera.domain.potion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 물약 · 음료를 마셨을 때의 효과 (아이템 태그로 정한다, 수치는 ORIGINAL).
 * <ul>
 *   <li>potion_t1 ~ t5 (특별 태그 없음): 회복력 상승 — {@link PotionRule}</li>
 *   <li>cure: 독 · 시듦 · 허기 · 나약을 씻는다 · resist_fire: 화염 저항 180초 · resist_frost: 둔화를 풀고 받는 피해 20% 감소 20초</li>
 *   <li>mana_potion: 마나 회복 (t2 40 · t3 80) · stamina_potion: 기력 60</li>
 *   <li>음료(drink): 차 기력 20 · 사과주 · 에일 30 · 흑맥주 45 · 꿀술 기력 40 · 포도주 마나 30 · 불꽃 브랜디 화염 저항 120초 · 드워프 화주 피해 20% 감소 30초 (독한 술은 어지럼)</li>
 * </ul>
 * 특별 물약은 회복력을 올리지 않는다 — 회복 물약을 더 싸게 대신하지 못하게. 마신 뒤 lockSeconds 동안은 같은 종류를 다시 마실 수 없다.
 */
public final class Consumable {
    /** 바닐라 효과 (PotionEffectType 이름) */
    public record Buff(String type, int seconds, int amplifier) {}

    /**
     * @param regenTier 회복력 물약 등급 (0 = 회복력 없음)
     * @param drink     음료 (물약과 따로 잠근다)
     */
    public record Use(int regenTier, int stamina, int mana, List<Buff> buffs, boolean cure, boolean thaw, int nauseaSeconds, int lockSeconds, boolean drink,
                      String message) {
        public boolean nothing() {
            return regenTier == 0 && stamina == 0 && mana == 0 && buffs.isEmpty() && !cure && !thaw;
        }
    }

    private Consumable() {
    }

    public static int tier(Set<String> tags) {
        for (int i = 5; i >= 1; i--) if (tags.contains("potion_t" + i)) return i;
        return 1;
    }

    /** @return null = 마시는 아이템이 아님 */
    public static Use of(Set<String> tags) {
        if (tags.contains("potion")) {
            int t = tier(tags);
            if (tags.contains("cure")) return new Use(0, 0, 0, List.of(), true, false, 0, 10, false, "&a몸속의 독이 씻겨 나간다");
            if (tags.contains("resist_fire")) return new Use(0, 0, 0, List.of(new Buff("FIRE_RESISTANCE", 180, 0)), false, false, 0, 30, false, "&6불길이 살갗을 비켜 간다 &7(180초)");
            if (tags.contains("resist_frost")) return new Use(0, 0, 0, List.of(new Buff("DAMAGE_RESISTANCE", 20, 0)), false, true, 0, 60, false, "&b냉기가 풀리고 몸이 단단해진다 &7(20초)");
            if (tags.contains("mana_potion")) return new Use(0, 0, t >= 3 ? 80 : 40, List.of(), false, false, 0, 15, false, "&b마나가 차오른다");
            if (tags.contains("stamina_potion")) return new Use(0, 60, 0, List.of(), false, false, 0, 15, false, "&e기력이 돌아온다");
            return new Use(t, 0, 0, List.of(), false, false, 0, 0, false, null);
        }
        if (!tags.contains("drink")) return null;
        List<Buff> buffs = new ArrayList<>();
        int st = 0, mana = 0, nausea = 0;
        String msg;
        if (tags.contains("fireproof")) { buffs.add(new Buff("FIRE_RESISTANCE", 120, 0)); nausea = 6; msg = "&6속이 타오른다 — 불이 무섭지 않다 &7(120초)"; }
        else if (tags.contains("stoneskin")) { buffs.add(new Buff("DAMAGE_RESISTANCE", 30, 0)); nausea = 8; msg = "&7드워프 화주가 온몸을 돌처럼 굳힌다 &7(30초)"; }
        else if (tags.contains("wine")) { mana = 30; msg = "&d머리가 맑아진다 &7(마나 +30)"; }
        else if (tags.contains("mead")) { st = 40; msg = "&e달큰한 꿀술에 힘이 난다 &7(기력 +40)"; }
        else if (tags.contains("ale")) { st = tags.contains("stout") ? 45 : 30; msg = "&e시원하게 들이켰다 &7(기력 +" + st + ")"; }
        else if (tags.contains("cider")) { st = 30; msg = "&e새콤한 사과주 &7(기력 +30)"; }
        else { st = 20; msg = "&a따뜻한 차 한 잔 &7(기력 +20)"; }
        return new Use(0, st, mana, List.copyOf(buffs), false, false, nausea, 20, true, msg);
    }
}
