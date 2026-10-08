package io.versaera.content;

import io.versaera.domain.balance.Balance;
import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.crafting.MaterialSlot;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 오리지널 콘텐츠의 밸런스 — 원작(CANON) 데이터에서 곡선을 뽑아, 오리지널 수치가 그 곡선 ± 허용 오차 안에 있는지 본다.
 * 기준 · 환산표: {@link Balance}, 생성: tools/gen_originals/balance.py. 원작 곡선은 원작 파일이 바뀌면 같이 움직인다.
 */
class BalanceTest {
    static ContentBundle c;
    static Map<String, ItemType> items;
    static Set<String> originalItems, originalMonsters, originalBosses, originalSkills, originalJobs, originalRecipes, originalSets;

    @SuppressWarnings("unchecked")
    static Set<String> ids(String file, String key) throws Exception {
        try (InputStream in = BalanceTest.class.getClassLoader().getResourceAsStream("content/original/" + file)) {
            Map<String, Object> m = ContentLoader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), file);
            return ((Map<String, Object>) m.get(key)).keySet();
        }
    }

    @BeforeAll
    static void load() throws Exception {
        c = ContentBundle.fromClasspath(BalanceTest.class.getClassLoader());
        items = c.items().stream().collect(Collectors.toMap(ItemType::id, t -> t));
        originalItems = ids("items.yml", "items");
        originalSets = ids("items.yml", "sets");
        originalMonsters = ids("monsters.yml", "monsters");
        originalBosses = ids("field_bosses.yml", "field_bosses");
        originalSkills = new HashSet<>(ids("skills.yml", "skills"));
        originalSkills.addAll(ids("skills.yml", "combo_skills"));
        originalJobs = ids("jobs.yml", "jobs");
        originalRecipes = ids("recipes.yml", "recipes");
    }

    static List<ItemType> canon(ItemCategory cat) {
        return c.items().stream().filter(t -> "CANON".equals(t.source()) && t.category() == cat).toList();
    }

    // ---------------------------------------------------------------- 장비
    @Test
    void originalWeaponsFollowTheCanonPowerCurve() {
        List<ItemType> w = canon(ItemCategory.WEAPON).stream()
                .filter(t -> Balance.requiredLevel(t) > 0 && t.stats().getOrDefault("attack", 0) > 0 && !t.hasTag("cursed")).toList();
        double b = Balance.slopeThrough(8, w.stream().mapToDouble(Balance::requiredLevel).toArray(), w.stream().mapToDouble(t -> Balance.weaponPower(t.stats())).toArray());
        assertTrue(b > 1.3 && b < 2.4, "원작 무기 곡선 기울기: " + b);
        for (String id : originalItems) {
            ItemType t = items.get(id);
            if (t.category() != ItemCategory.WEAPON) continue;
            double target = 8 + b * Balance.requiredLevel(t), p = Balance.weaponPower(t.stats());
            assertTrue(p >= target * 0.82 && p <= target * 1.15, id + " 위력 " + p + " / 원작 곡선 " + target + " (L" + Balance.requiredLevel(t) + ")");
            caps(id, t.stats(), Balance.CAP);
        }
    }

    @Test
    void originalArmorFollowsTheCanonDefenseCurve() {
        List<ItemType> a = canon(ItemCategory.ARMOR).stream().filter(t -> Balance.requiredLevel(t) > 0 && Balance.slot(t) != null).toList();
        double b = Balance.slopeThrough(8, a.stream().mapToDouble(Balance::requiredLevel).toArray(),
                a.stream().mapToDouble(t -> Balance.armorPower(t.stats()) / Balance.SLOT.get(Balance.slot(t))).toArray());
        for (String id : originalItems) {
            ItemType t = items.get(id);
            if (t.category() != ItemCategory.ARMOR) continue;
            if (t.hasTag("accessory")) {
                double p = Balance.armorPower(t.stats());
                assertTrue(p <= 3 + 0.5 * 30 + 0.5, id + " 장신구 위력 " + p);
                caps(id, t.stats(), Balance.CAP_ACC);
                continue;
            }
            String slot = Balance.slot(t);
            assertNotNull(slot, id + " 부위");
            double target = (8 + b * Balance.requiredLevel(t)) * Balance.SLOT.get(slot), p = Balance.armorPower(t.stats());
            assertTrue(p >= target * 0.8 && p <= target * 1.18, id + " 위력 " + p + " / 원작 곡선 " + target);
            caps(id, t.stats(), Balance.CAP);
        }
        for (var set : c.sets()) {
            if (!originalSets.contains(set.id())) continue;
            set.bonuses().values().forEach(bonus -> caps(set.id() + " 세트", bonus, Balance.CAP_ACC));
        }
    }

    static void caps(String id, Map<String, Integer> s, Map<String, Integer> cap) {
        for (var e : s.entrySet()) {
            Integer max = cap.get(e.getKey());
            if (max != null) assertTrue(e.getValue() <= max, id + " " + e.getKey() + " " + e.getValue() + " > 상한 " + max);
        }
    }

    @Test
    void craftedGearCannotBeWornBelowItsCraftingLevel() {
        for (Recipe r : c.recipes()) {
            if (!originalRecipes.contains(r.id())) continue;
            ItemType t = items.get(r.output());
            if ((t.category() != ItemCategory.WEAPON && t.category() != ItemCategory.ARMOR) || t.hasTag("accessory")) continue;
            assertTrue(Balance.requiredLevel(t) >= r.minLevel(), r.id() + ": 착용 조건 " + Balance.requiredLevel(t) + " < 제작 숙련 " + r.minLevel());
        }
    }

    // ---------------------------------------------------------------- 몬스터 · 보스
    @Test
    void originalMonstersFollowTheCanonLevelCurve() {
        var all = c.expansion().monsters();
        for (boolean large : new boolean[]{false, true}) {
            var canon = all.stream().filter(m -> "CANON".equals(m.source()) || "SOURCE-BASED".equals(m.source()))
                    .filter(m -> m.kinds().contains(io.versaera.domain.item.ItemOptions.Kind.LARGE) == large).toList();
            double[] lv = canon.stream().mapToDouble(m -> (m.minLevel() + m.maxLevel()) / 2.0).toArray();
            double[] hp = Balance.logFit(lv, canon.stream().mapToDouble(m -> m.hp()).toArray());
            double[] dmg = Balance.logFit(lv, canon.stream().mapToDouble(m -> m.damage()).toArray());
            for (var m : all) {
                if (!originalMonsters.contains(m.id()) || m.kinds().contains(io.versaera.domain.item.ItemOptions.Kind.LARGE) != large) continue;
                double mid = (m.minLevel() + m.maxLevel()) / 2.0;
                double h = Math.exp(hp[0] + hp[1] * Math.log(mid)), d = Math.exp(dmg[0] + dmg[1] * Math.log(mid));
                assertTrue(m.hp() >= h * 0.6 && m.hp() <= h * 1.3, m.id() + " 체력 " + m.hp() + " / 원작 곡선 " + h);
                assertTrue(m.damage() >= d * 0.5 && m.damage() <= d * 1.35, m.id() + " 공격 " + m.damage() + " / 원작 곡선 " + d);
            }
        }
    }

    @Test
    void originalBossesFollowTheCanonRewardCurve() {
        var canon = c.fieldBosses().stream().filter(b -> !originalBosses.contains(b.id())).toList();
        double[] hp = canon.stream().mapToDouble(b -> b.maxHp()).toArray();
        double[] dmg = Balance.logFit(hp, canon.stream().mapToDouble(b -> b.damage()).toArray());
        double[] money = Balance.logFit(hp, canon.stream().mapToDouble(b -> b.reward().money()).toArray());
        double[] fame = Balance.logFit(hp, canon.stream().mapToDouble(b -> Math.max(1, b.reward().fame())).toArray());
        for (var b : c.fieldBosses()) {
            if (!originalBosses.contains(b.id())) continue;
            double h = b.maxHp();
            assertTrue(within(b.damage(), dmg, h, 0.15), b.id() + " 공격 " + b.damage());
            assertTrue(within(b.reward().money(), money, h, 0.15), b.id() + " 돈 " + b.reward().money());
            assertTrue(within(b.reward().fame(), fame, h, 0.25), b.id() + " 명성 " + b.reward().fame());
        }
    }

    static boolean within(double v, double[] f, double x, double tol) {
        double want = Math.exp(f[0] + f[1] * Math.log(x));
        return v >= want * (1 - tol) && v <= want * (1 + tol);
    }

    // ---------------------------------------------------------------- 스킬 · 직업
    @Test
    void originalSkillsStayInsideTheCanonBand() {
        List<SkillDefinition> canon = c.skills().stream().filter(s -> !originalSkills.contains(s.id())).toList();
        Set<String> finishers = c.combos().stream().map(x -> x.finisher()).collect(Collectors.toSet());
        double maxSkill = canon.stream().filter(s -> !s.basic() && !finishers.contains(s.id())).mapToDouble(Balance::skillScore).max().orElseThrow();
        double maxCombo = canon.stream().filter(s -> finishers.contains(s.id())).mapToDouble(Balance::skillScore).max().orElseThrow();
        // 기본기: 마력탄(마나 8)은 싼 마나 기본기라 따로 — 기력 기본기의 가장 큰 값
        double maxBasic = canon.stream().filter(s -> s.basic() && !finishers.contains(s.id()) && s.resource() == SkillDefinition.Resource.STAMINA)
                .mapToDouble(Balance::skillScore).max().orElseThrow();
        for (SkillDefinition s : c.skills()) {
            if (!originalSkills.contains(s.id())) continue;
            double sc = Balance.skillScore(s);
            double max = finishers.contains(s.id()) ? maxCombo : s.basic() ? maxBasic : maxSkill;
            assertTrue(sc <= max * 1.03, s.id() + " 점수 " + sc + " > 원작 최대 " + max);
            if (s.basic()) assertFalse(s.effect() == io.versaera.domain.combat.StatusEffect.STUN || s.effect() == io.versaera.domain.combat.StatusEffect.FREEZE,
                    s.id() + ": 기본기에 기절 · 빙결을 달면 계속 묶인다");
        }
    }

    @Test
    void originalJobsStayInsideTheCanonPerkBand() {
        var canon = c.jobs().stream().filter(j -> !originalJobs.contains(j.id()) && !j.id().equals("moonlight_sculptor")).toList();
        for (var j : c.jobs()) {
            if (!originalJobs.contains(j.id())) continue;
            boolean combat = j.slot().equals("COMBAT");
            var same = canon.stream().filter(x -> x.slot().equals(j.slot())).toList();
            double max = same.stream().mapToDouble(x -> combat ? Balance.combatPerks(x.perks()) : Balance.lifePerks(x.perks())).max().orElseThrow();
            double min = same.stream().filter(x -> x.tier() == j.tier()).mapToDouble(x -> combat ? Balance.combatPerks(x.perks()) : Balance.lifePerks(x.perks())).min().orElse(0);
            double sc = combat ? Balance.combatPerks(j.perks()) : Balance.lifePerks(j.perks());
            double top = combat ? (j.tier() == 1 ? 10.5 : max) : (j.tier() == 1 ? 62 : 75);
            assertTrue(sc <= top, j.id() + " 직업 점수 " + sc + " > " + top);
            assertTrue(sc >= min * 0.9, j.id() + " 직업 점수 " + sc + " < 원작 같은 단계 최소 " + min);
        }
    }

    // ---------------------------------------------------------------- 경제
    @Test
    void craftingPaysButIsNotFreeMoney() {
        Map<String, Long> price = c.market().prices();
        Map<String, List<ItemType>> byTag = new HashMap<>();
        for (ItemType t : c.items()) for (String tag : t.tags()) byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(t);
        for (Recipe r : c.recipes()) {
            if (!originalRecipes.contains(r.id())) continue;
            double cost = 0;
            for (MaterialSlot s : r.slots()) {
                if (s.optional()) continue;
                double each = s.accepts().startsWith("type:") ? price.getOrDefault(s.accepts().substring(5), 0L)
                        : byTag.getOrDefault(s.accepts().substring(4), List.of()).stream().mapToLong(t -> price.getOrDefault(t.id(), Long.MAX_VALUE)).min().orElse(0);
                cost += each * s.count();
            }
            double unit = cost / r.outputCount(), sell = price.getOrDefault(r.output(), 0L);
            if (!originalItems.contains(r.output())) continue;   // 원작 재료를 만드는 법 (미스릴 · 흑철 제련)은 원작 시세 그대로
            assertTrue(sell >= unit * 0.95, r.id() + ": 팔면 손해 (재료 " + unit + " > 시세 " + sell + ")");
            assertTrue(sell <= unit * 3 + 4 * r.minLevel() * r.minLevel() + 60, r.id() + ": 너무 남는다 (재료 " + unit + " · 시세 " + sell + ")");
        }
    }

    @Test
    void higherPotionTiersCostMore() {
        Map<String, Long> price = c.market().prices();
        long[] cheapest = new long[6];
        Arrays.fill(cheapest, Long.MAX_VALUE);
        long[] dearest = new long[6];
        for (ItemType t : c.items()) {
            if (!t.hasTag("potion") || t.hasTag("cure") || t.hasTag("mana_potion") || t.hasTag("resist_fire") || t.hasTag("resist_frost") || t.hasTag("stamina_potion"))
                continue;
            int tier = io.versaera.domain.potion.Consumable.tier(t.tags());
            long p = price.getOrDefault(t.id(), 0L);
            cheapest[tier] = Math.min(cheapest[tier], p);
            dearest[tier] = Math.max(dearest[tier], p);
        }
        for (int t = 2; t <= 5; t++)
            if (cheapest[t] != Long.MAX_VALUE && dearest[t - 1] > 0)
                assertTrue(cheapest[t] > dearest[t - 1], "회복 물약 " + t + "등급이 " + (t - 1) + "등급보다 싸다");
    }

    @Test
    void specialPotionsDoNotStackAsCheapHealing() {
        for (String id : originalItems) {
            ItemType t = items.get(id);
            var use = io.versaera.domain.potion.Consumable.of(t.tags());
            if (use == null) continue;
            assertFalse(use.nothing(), id + ": 마셔도 아무 효과가 없다");
            if (t.hasTag("cure") || t.hasTag("mana_potion") || t.hasTag("resist_fire") || t.hasTag("resist_frost") || t.hasTag("stamina_potion"))
                assertEquals(0, use.regenTier(), id + ": 특별 물약이 회복 물약 노릇까지 한다");
        }
    }

    @Test
    void foodIsActuallyEdible() {
        for (String id : originalItems) {
            ItemType t = items.get(id);
            if (t.category() == ItemCategory.FOOD && !t.hasTag("drink"))
                assertFalse(t.material().equals("CAKE"), id + ": 케이크는 손에 들고 먹을 수 없다 (설치되는 블록)");
        }
    }
}
