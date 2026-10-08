package io.versaera.domain.balance;

import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.item.ItemType;

import java.util.Map;

/**
 * 밸런스 잣대 (ORIGINAL) — 오리지널 콘텐츠를 원작(CANON) 곡선에 맞추는 데 쓰는 환산표. tools/gen_originals/balance.py 와 같은 값.
 *
 * <p>전투 공식: 주는 피해 = 공격력 × 품질 × (1 + 숙련 × 1.5%) × 치명 + 속성 고정 피해 (신성은 언데드 · 악마에 2 배),
 * 받는 피해 = 공격 × 100 / (100 + 방어 × 4) × (1 − 저항%). 그래서 옵션을 '공격력 몇 점' · '방어력 몇 점'으로 바꿔 한 줄로 비교한다.
 */
public final class Balance {
    private Balance() {
    }

    // ---------------------------------------------------------------- 무기 위력 (공격력 1 = 1)
    private static final Map<String, Double> WEAPON_FLAT = Map.ofEntries(
            Map.entry("fire", 0.8), Map.entry("ice", 0.8), Map.entry("lightning", 0.8), Map.entry("poison", 0.8), Map.entry("dark", 0.8), Map.entry("holy", 1.0),
            Map.entry("pierce", 0.12), Map.entry("lifesteal", 1.2), Map.entry("slow", 0.12), Map.entry("stun", 0.4), Map.entry("health", 1.0),
            Map.entry("resist", 0.6), Map.entry("speed", 0.2), Map.entry("regen", 2.0), Map.entry("thorns", 0.4), Map.entry("drain", -3.0), Map.entry("craft", 0.5));
    /** 공격력에 비례하는 옵션 (치명 1% = 피해 +0.5% · 상대별 % 는 그 상대를 만날 확률만큼) */
    private static final Map<String, Double> WEAPON_PCT = Map.of("crit", 0.005, "vs_undead", 0.0025, "vs_demon", 0.0025, "vs_large", 0.003,
            "vs_dragon", 0.0015, "vs_human", 0.002);

    public static double weaponPower(Map<String, Integer> s) {
        double a = s.getOrDefault("attack", 0), p = a;
        for (var e : s.entrySet()) {
            if (e.getKey().equals("attack")) continue;
            Double pct = WEAPON_PCT.get(e.getKey());
            p += pct != null ? a * pct * e.getValue() : WEAPON_FLAT.getOrDefault(e.getKey(), 0.0) * e.getValue();
        }
        return p;
    }

    // ---------------------------------------------------------------- 방어구 위력 (방어력 1 = 1)
    private static final Map<String, Double> ARMOR = Map.ofEntries(
            Map.entry("health", 1.2), Map.entry("resist", 0.7), Map.entry("regen", 2.5), Map.entry("speed", 0.3), Map.entry("crit", 0.4), Map.entry("thorns", 0.5),
            Map.entry("drain", -3.0), Map.entry("stun", 0.3), Map.entry("fire", 0.6), Map.entry("ice", 0.6), Map.entry("lightning", 0.6), Map.entry("poison", 0.6),
            Map.entry("dark", 0.6), Map.entry("holy", 0.7), Map.entry("attack", 1.0), Map.entry("lifesteal", 1.5), Map.entry("pierce", 0.15), Map.entry("vs_human", 0.1),
            Map.entry("vs_large", 0.1), Map.entry("vs_undead", 0.1), Map.entry("vs_demon", 0.1), Map.entry("vs_dragon", 0.05), Map.entry("craft", 0.5), Map.entry("slow", 0.1));
    /** 부위 비율 (몸통 = 1) */
    public static final Map<String, Double> SLOT = Map.of("chest", 1.0, "robe", 1.0, "legs", 0.75, "helmet", 0.6, "boots", 0.45, "shield", 0.65);

    public static double armorPower(Map<String, Integer> s) {
        double p = s.getOrDefault("defense", 0);
        for (var e : s.entrySet()) if (!e.getKey().equals("defense")) p += ARMOR.getOrDefault(e.getKey(), 0.0) * e.getValue();
        return p;
    }

    /** 한 장비의 옵션 상한 (원작 장비에서 본 가장 큰 값 근처) */
    public static final Map<String, Integer> CAP = Map.ofEntries(
            Map.entry("crit", 15), Map.entry("speed", 15), Map.entry("craft", 4), Map.entry("resist", 12), Map.entry("health", 8), Map.entry("regen", 2),
            Map.entry("lifesteal", 6), Map.entry("pierce", 25), Map.entry("stun", 10), Map.entry("slow", 25), Map.entry("fire", 18), Map.entry("ice", 18),
            Map.entry("lightning", 18), Map.entry("poison", 12), Map.entry("dark", 14), Map.entry("holy", 14), Map.entry("thorns", 8),
            Map.entry("vs_undead", 40), Map.entry("vs_demon", 40), Map.entry("vs_large", 25), Map.entry("vs_dragon", 60), Map.entry("vs_human", 10));
    /** 장신구 · 세트 보너스 상한 (다른 장비 위에 더해진다) */
    public static final Map<String, Integer> CAP_ACC = Map.ofEntries(
            Map.entry("crit", 8), Map.entry("speed", 10), Map.entry("craft", 3), Map.entry("resist", 10), Map.entry("health", 6), Map.entry("regen", 2),
            Map.entry("lifesteal", 6), Map.entry("attack", 6), Map.entry("defense", 8), Map.entry("fire", 10), Map.entry("ice", 10), Map.entry("lightning", 10),
            Map.entry("poison", 8), Map.entry("dark", 8), Map.entry("holy", 8), Map.entry("thorns", 8), Map.entry("vs_large", 12), Map.entry("vs_dragon", 25));

    /** 착용 조건의 레벨 (숙련 · 스탯 가운데 가장 높은 것, 없으면 0) */
    public static int requiredLevel(ItemType t) {
        int best = 0;
        for (var e : t.requires().entrySet()) if (e.getKey().startsWith("mastery.") || e.getKey().startsWith("stat.")) best = Math.max(best, e.getValue());
        return best;
    }

    public static String slot(ItemType t) {
        for (String s : new String[]{"shield", "helmet", "robe", "chest"}) if (t.hasTag(s)) return s;
        String m = t.material();
        if (m.endsWith("_CHESTPLATE")) return "chest";
        if (m.endsWith("_LEGGINGS")) return "legs";
        if (m.endsWith("_BOOTS")) return "boots";
        if (m.endsWith("_HELMET")) return "helmet";
        return null;
    }

    // ---------------------------------------------------------------- 스킬: 초당 위력 + 상태 이상 값
    /** 상태 이상 1 초의 값어치 */
    private static final Map<String, Double> EFFECT = Map.of("BLEED", 0.15, "BURN", 0.15, "POISON", 0.12, "SLOW", 0.05, "FREEZE", 0.3, "STUN", 0.4,
            "WEAKEN", 0.06, "GUARD", 0.08, "HASTE", 0.06);

    /** 맞히는 넓이 배율 (부채꼴 90° · 반지름 3 = 1, 최대 3) */
    public static double area(SkillDefinition s) {
        if (s.kind() == SkillDefinition.Kind.PROJECTILE) return 1;
        if (s.kind() == SkillDefinition.Kind.SELF || s.shape() == null) return 0;
        double r = s.radius(), a = switch (s.shape()) {
            case CONE -> Math.PI * r * r * s.widthOrAngle() / 360;
            case LINE -> r * s.widthOrAngle();
            case RING -> Math.PI * r * r * 0.6;
            case CIRCLE -> Math.PI * r * r;
        };
        return Math.min(3, Math.sqrt(a / 7.07));
    }

    public static double skillScore(SkillDefinition s) {
        double fx = s.effect() == null ? 0 : EFFECT.getOrDefault(s.effect().name(), 0.0) * s.effectSeconds();
        return (s.damageMult() * area(s) + fx) / (s.cooldownMs() / 1000.0);
    }

    // ---------------------------------------------------------------- 직업 보너스 점수
    public static double combatPerks(Map<String, Double> p) {
        double s = 0;
        for (var e : p.entrySet()) {
            double v = e.getValue();
            switch (e.getKey()) {
                case "attack_pct", "defense_pct", "max_health_pct" -> s += v * 100;
                case "crit" -> s += v * 50;
                case "stamina" -> s += v * 0.15;
                case "mana" -> s += v * 0.1;
                default -> { }
            }
        }
        return s;
    }

    public static double lifePerks(Map<String, Double> p) {
        double s = 0;
        for (var e : p.entrySet()) {
            String k = e.getKey();
            double v = e.getValue();
            if (k.startsWith("craft_quality")) s += v;
            else if (k.startsWith("gather_bonus")) s += v * 40;
            else if (k.equals("price_discount")) s += v * 600;
            else if (k.equals("auction_fee_cut")) s += v * 40;
            else if (k.equals("stamina")) s += v * 0.5;
            else if (k.equals("mana")) s += v * 0.2;
            else if (k.equals("attack_pct") || k.equals("crit") || k.equals("defense_pct") || k.equals("max_health_pct")) s += v * 100;
        }
        return s;
    }

    // ---------------------------------------------------------------- 직선 · 로그 곡선
    /** 절편을 a 로 고정한 기울기 */
    public static double slopeThrough(double a, double[] xs, double[] ys) {
        double num = 0, den = 0;
        for (int i = 0; i < xs.length; i++) { num += xs[i] * (ys[i] - a); den += xs[i] * xs[i]; }
        return den == 0 ? 0 : num / den;
    }

    /** log y = a + b log x 의 {a, b} */
    public static double[] logFit(double[] xs, double[] ys) {
        int n = xs.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += Math.log(xs[i]); my += Math.log(ys[i]); }
        mx /= n; my /= n;
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) { double dx = Math.log(xs[i]) - mx; num += dx * (Math.log(ys[i]) - my); den += dx * dx; }
        double b = den == 0 ? 0 : num / den;
        return new double[]{my - b * mx, b};
    }
}
