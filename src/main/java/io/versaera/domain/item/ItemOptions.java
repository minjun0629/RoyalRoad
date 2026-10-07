package io.versaera.domain.item;

import java.util.*;
import java.util.function.ToLongFunction;

/**
 * 아이템 능력 (ITM-02). 원작의 무기 · 방어구에는 공격력 · 방어력 말고도 속성 피해 · 특정 몬스터 추가 피해 · 생명력 흡수 ·
 * 체력 · 이동 속도 같은 옵션이 붙고, 저주받은 물건은 불리한 옵션도 붙는다 (CANON 개념). 수치 · 이름 키는 ORIGINAL.
 * <ul>
 *   <li>속성 피해 (fire · ice · lightning · poison · dark · holy): 타격마다 더하는 고정 피해 (품질 배율). 신성(holy)은 언데드 · 악마에게 2배</li>
 *   <li>상대별 추가 피해 (vs_undead · vs_demon · vs_large · vs_dragon · vs_human): %</li>
 *   <li>crit 치명 확률 % · pierce 방어 무시 % · lifesteal 피해의 % 회복 · slow · stun 확률 %</li>
 *   <li>health 최대 체력 · resist 받는 피해 감소 % (최대 40) · speed 이동 속도 % · regen 10초마다 회복 · thorns 반사 %</li>
 *   <li>불리한 옵션: drain 10초마다 체력을 잃는다 · speed 음수</li>
 *   <li>craft: 도구로 쓸 때 제작 품질 +10 × 값</li>
 * </ul>
 */
public final class ItemOptions {
    public static final Set<String> KEYS = Set.of("attack", "defense", "fire", "ice", "lightning", "poison", "dark", "holy",
            "vs_undead", "vs_demon", "vs_large", "vs_dragon", "vs_human", "crit", "pierce", "lifesteal", "slow", "stun",
            "health", "resist", "speed", "regen", "thorns", "drain", "craft");
    public static final List<String> ELEMENTS = List.of("fire", "ice", "lightning", "poison", "dark", "holy");

    private ItemOptions() {
    }

    /** 감정해야 보이는 능력 (공격력 · 방어력은 늘 보인다) */
    public static boolean hidden(String key) {
        return !key.equals("attack") && !key.equals("defense");
    }

    public static String name(String key) {
        return switch (key) {
            case "attack" -> "공격력";
            case "defense" -> "방어력";
            case "fire" -> "화염 피해";
            case "ice" -> "냉기 피해";
            case "lightning" -> "번개 피해";
            case "poison" -> "독 피해";
            case "dark" -> "암흑 피해";
            case "holy" -> "신성 피해";
            case "vs_undead" -> "언데드 추가 피해 %";
            case "vs_demon" -> "악마 추가 피해 %";
            case "vs_large" -> "거대 몬스터 추가 피해 %";
            case "vs_dragon" -> "용 추가 피해 %";
            case "vs_human" -> "사람 추가 피해 %";
            case "crit" -> "치명타 확률 %";
            case "pierce" -> "방어 무시 %";
            case "lifesteal" -> "생명력 흡수 %";
            case "slow" -> "둔화 확률 %";
            case "stun" -> "기절 확률 %";
            case "health" -> "최대 체력";
            case "resist" -> "받는 피해 감소 %";
            case "speed" -> "이동 속도 %";
            case "regen" -> "체력 회복 (10초)";
            case "thorns" -> "피해 반사 %";
            case "drain" -> "저주: 체력 감소 (10초)";
            case "craft" -> "제작 품질";
            default -> key;
        };
    }

    /** 상대의 종류 (플랫폼이 엔티티를 보고 정한다) */
    public enum Kind { UNDEAD, DEMON, LARGE, DRAGON, HUMAN }

    /**
     * @param bonus     더하는 고정 피해 (속성)
     * @param mult      곱하는 배율 (상대별 추가 피해)
     * @param elements  이번 타격에 실린 속성 (효과 연출 · 상태 이상용)
     */
    public record Hit(double bonus, double mult, double critBonus, double pierce, double lifesteal, double slowChance, double stunChance,
                      List<String> elements) {
        public static final Hit NONE = new Hit(0, 1, 0, 0, 0, 0, 0, List.of());
    }

    /** 무기 한 자루의 타격 능력. qualityMult = Quality.statMultiplier(품질) */
    public static Hit onHit(Map<String, Integer> s, double qualityMult, Set<Kind> kinds) {
        if (s.isEmpty()) return Hit.NONE;
        boolean unholy = kinds.contains(Kind.UNDEAD) || kinds.contains(Kind.DEMON);
        double bonus = 0;
        List<String> el = new ArrayList<>();
        for (String e : ELEMENTS) {
            int v = s.getOrDefault(e, 0);
            if (v <= 0) continue;
            bonus += v * qualityMult * (e.equals("holy") && unholy ? 2 : 1);
            el.add(e);
        }
        double pct = 0;
        if (kinds.contains(Kind.UNDEAD)) pct += s.getOrDefault("vs_undead", 0);
        if (kinds.contains(Kind.DEMON)) pct += s.getOrDefault("vs_demon", 0);
        if (kinds.contains(Kind.LARGE)) pct += s.getOrDefault("vs_large", 0);
        if (kinds.contains(Kind.DRAGON)) pct += s.getOrDefault("vs_dragon", 0);
        if (kinds.contains(Kind.HUMAN)) pct += s.getOrDefault("vs_human", 0);
        return new Hit(bonus, 1 + pct / 100.0, s.getOrDefault("crit", 0) / 100.0, Math.min(0.8, s.getOrDefault("pierce", 0) / 100.0),
                Math.min(0.3, s.getOrDefault("lifesteal", 0) / 100.0), s.getOrDefault("slow", 0) / 100.0, Math.min(0.25, s.getOrDefault("stun", 0) / 100.0),
                List.copyOf(el));
    }

    /** 입은 장비가 늘 주는 효과 */
    public record Passive(double health, double resistPct, double speedPct, double regen, double drain, double thornsPct) {
        public static final Passive NONE = new Passive(0, 0, 0, 0, 0, 0);
    }

    /** 입은 장비 능력 합 + 세트 보너스 */
    public static Map<String, Integer> total(Collection<ItemType> worn, Map<String, ItemSet> sets) {
        Map<String, Integer> out = new TreeMap<>();
        Map<String, Integer> setCount = new HashMap<>();
        for (ItemType t : worn) {
            t.stats().forEach((k, v) -> out.merge(k, v, Integer::sum));
            if (t.set() != null) setCount.merge(t.set(), 1, Integer::sum);
        }
        setCount.forEach((id, n) -> {
            ItemSet st = sets.get(id);
            if (st != null) st.bonusFor(n).forEach((k, v) -> out.merge(k, v, Integer::sum));
        });
        return out;
    }

    public static Passive passive(Map<String, Integer> t) {
        return new Passive(Math.max(-10, t.getOrDefault("health", 0)), Math.min(40, Math.max(0, t.getOrDefault("resist", 0))),
                Math.max(-30, Math.min(30, t.getOrDefault("speed", 0))), Math.max(0, t.getOrDefault("regen", 0)),
                Math.max(0, t.getOrDefault("drain", 0)), Math.min(30, Math.max(0, t.getOrDefault("thorns", 0))));
    }

    /**
     * 착용 조건 중 못 채운 것. 원작: 대장장이 스킬이 높으면 착용 제한이 줄어든다 — 여기서는 대장 기술 숙련 레벨 1마다 1% (최대 31%).
     *
     * @param have "mastery.X" → 숙련 레벨, "stat.X" → 스탯 포인트, "fame" → 명성
     * @return 못 채운 조건 → 필요한 값 (비면 착용 가능)
     */
    public static Map<String, Long> unmet(Map<String, Integer> requires, ToLongFunction<String> have, int smithingLevel) {
        Map<String, Long> out = new TreeMap<>();
        double cut = Math.min(31, Math.max(0, smithingLevel)) / 100.0;
        for (var e : requires.entrySet()) {
            long need = (long) Math.ceil(e.getValue() * (1 - cut));
            if (have.applyAsLong(e.getKey()) < need) out.put(e.getKey(), need);
        }
        return out;
    }

    /** 감정 난이도 (1 ~ 31): 착용 조건이 높을수록, 원작 이름이 붙은 물건일수록 어렵다 */
    public static int appraiseDifficulty(ItemType t) {
        int d = 1;
        for (var e : t.requires().entrySet())
            d = Math.max(d, e.getKey().startsWith("mastery.") ? e.getValue() : e.getValue() / 20);
        if ("CANON".equals(t.source())) d += 4;
        return Math.max(1, Math.min(31, d));
    }

    /** 감정 성공 확률: 숙련 레벨이 난이도 이상이면 반드시, 아니면 (레벨/난이도) × 0.8, 최소 5% */
    public static double appraiseChance(int level, int difficulty) {
        if (level >= difficulty) return 1;
        return Math.max(0.05, level / (double) difficulty * 0.8);
    }
}
