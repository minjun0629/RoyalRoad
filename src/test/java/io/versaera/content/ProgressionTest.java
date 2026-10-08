package io.versaera.content;

import io.versaera.domain.balance.Balance;
import io.versaera.domain.balance.Progression;
import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemOptions;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 오래 하는 게임의 성장 시간표 · 난이도 눈금 (Progression). 원작 곡선(장비 · 몬스터)과 실제 피해 공식으로 계산한다.
 * 곡선 장비 = 착용 레벨이 숙련과 같은 무기 (위력의 85% 가 공격력) · 같은 레벨 방어구 네 부위 (방어 비중 80%), 품질 500.
 */
class ProgressionTest {
    static ContentBundle c;
    static double weaponSlope, armorSlope;
    static double[] hpFit, dmgFit;

    @BeforeAll
    static void load() {
        c = ContentBundle.fromClasspath(ProgressionTest.class.getClassLoader());
        List<ItemType> w = c.items().stream().filter(t -> "CANON".equals(t.source()) && t.category() == ItemCategory.WEAPON
                && Balance.requiredLevel(t) > 0 && t.stats().getOrDefault("attack", 0) > 0 && !t.hasTag("cursed")).toList();
        weaponSlope = Balance.slopeThrough(8, w.stream().mapToDouble(Balance::requiredLevel).toArray(), w.stream().mapToDouble(t -> Balance.weaponPower(t.stats())).toArray());
        List<ItemType> a = c.items().stream().filter(t -> "CANON".equals(t.source()) && t.category() == ItemCategory.ARMOR
                && Balance.requiredLevel(t) > 0 && Balance.slot(t) != null).toList();
        armorSlope = Balance.slopeThrough(8, a.stream().mapToDouble(Balance::requiredLevel).toArray(),
                a.stream().mapToDouble(t -> Balance.armorPower(t.stats()) / Balance.SLOT.get(Balance.slot(t))).toArray());
        var canon = c.expansion().monsters().stream().filter(m -> !m.kinds().contains(ItemOptions.Kind.LARGE) && !"ORIGINAL".equals(m.source())).toList();
        double[] lv = canon.stream().mapToDouble(m -> (m.minLevel() + m.maxLevel()) / 2.0).toArray();
        hpFit = Balance.logFit(lv, canon.stream().mapToDouble(m -> m.hp()).toArray());
        dmgFit = Balance.logFit(lv, canon.stream().mapToDouble(m -> m.damage()).toArray());
    }

    /** 곡선 장비로 한 번 친 평균 피해 (숙련 L · 품질 500 · 치명 5%) */
    static double playerHit(int L) {
        double p = 8 + weaponSlope * L, atk = 0.85 * p, elem = 0.15 * p / 0.8, q = 0.7 + 0.7 * 500 / 1000.0;
        return atk * q * (1 + Math.min(31, L) * 0.015) * (1 + 0.05 * 0.5) + elem * q;
    }

    static double armor(int L) {
        return (8 + armorSlope * L) * (0.6 + 1.0 + 0.75 + 0.45) * 0.8;
    }

    static double monsterHp(double level) {
        return Math.exp(hpFit[0] + hpFit[1] * Math.log(level)) * Progression.MONSTER_HP;
    }

    static double monsterHit(double level, int L) {
        double raw = Math.exp(dmgFit[0] + dmgFit[1] * Math.log(level)) * Progression.MONSTER_DAMAGE;
        return raw * 100 / (100 + armor(L) * 4);
    }

    static double playerHp(int L) {
        return 20 + L / 31.0 * 10;   // 장비 체력 조금
    }

    @Test
    void masteryTakesLongButIsReachable() {
        double toIntermediate = Progression.huntingHoursTo(11), toAdvanced = Progression.huntingHoursTo(21), toMaster = Progression.huntingHoursTo(31);
        assertTrue(toIntermediate >= 5 && toIntermediate <= 10, "중급 1 까지 " + toIntermediate + " 시간");
        assertTrue(toAdvanced >= 45 && toAdvanced <= 80, "고급 1 까지 " + toAdvanced + " 시간");
        assertTrue(toMaster >= 250 && toMaster <= 380, "마스터까지 " + toMaster + " 시간");
        for (int lv = 2; lv < 31; lv++) assertTrue(Mastery.need(lv) > Mastery.need(lv - 1), "레벨마다 더 오래");
    }

    @Test
    void matchedFightsTakeSeveralHitsBothWays() {
        for (int L : new int[]{3, 5, 10, 15, 20, 25, 30}) {
            double lv = Progression.monsterLevelFor(L);
            double toKill = monsterHp(lv) / playerHit(L), toDie = playerHp(L) / monsterHit(lv, L);
            assertTrue(toKill >= 5 && toKill <= 11, "숙련 " + L + ": 몬스터(Lv." + Math.round(lv) + ") 를 " + toKill + " 번에 잡는다");
            assertTrue(toDie >= 5 && toDie <= 14, "숙련 " + L + ": " + toDie + " 번 맞으면 쓰러진다");
        }
    }

    @Test
    void harderTargetsArePunishingAndEasyTargetsGiveLittle() {
        int L = 15;
        double lvHard = Progression.monsterLevelFor(L + 5), lvEasy = Progression.monsterLevelFor(L - 8);
        assertTrue(playerHp(L) / monsterHit(lvHard, L) < playerHp(L) / monsterHit(Progression.monsterLevelFor(L), L), "센 상대는 더 아프다");
        long easy = Mastery.gain(Progression.killXp(Progression.actionLevel((int) lvEasy)), Progression.actionLevel((int) lvEasy), L, 1);
        long matched = Mastery.gain(Progression.killXp(L), L, L, 1);
        assertTrue(easy * 4 < matched, "8 레벨 아래 상대는 경험치가 1/4 도 안 된다: " + easy + " vs " + matched);
    }

    @Test
    void levelMappingRoundTrips() {
        for (int L = 1; L <= 31; L++) assertEquals(L, Progression.actionLevel((int) Math.round(Progression.monsterLevelFor(L))), 1, "숙련 " + L);
        assertEquals(1, Progression.actionLevel(1));
        assertEquals(31, Progression.actionLevel(560));
    }

    @Test
    void fieldBossesAreGroupContent() {
        for (var b : c.fieldBosses()) {
            double mastery = 8 + (Math.min(2048, b.maxHp()) - 600) / 1448.0 * 20;
            double hits = b.maxHp() / (playerHit((int) Math.round(mastery)) * Progression.BOSS_TAKEN);
            assertTrue(hits >= 30, b.id() + ": 혼자 " + hits + " 번이면 쓰러진다 — 여럿이 잡는 상대여야");
        }
    }

    @Test
    void monsterStatsStayInsideVanillaLimits() {
        for (var m : c.expansion().monsters()) {
            double hp = Progression.monsterHp(m.hp(), m.maxLevel(), m.minLevel(), m.maxLevel());
            assertTrue(hp <= 2048, m.id() + " 체력 " + hp);
        }
    }
}
