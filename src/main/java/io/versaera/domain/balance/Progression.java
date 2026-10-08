package io.versaera.domain.balance;

import io.versaera.domain.skill.Discipline;

/**
 * 오래 하는 게임의 성장 · 난이도 눈금 (ORIGINAL). docs/BALANCE.md 「성장 시간표」, ProgressionTest 가 아래 목표를 지킨다.
 *
 * <h3>목표 (한 숙련을 사냥만으로 올릴 때)</h3>
 * 중급 1 (레벨 11) 약 7 시간 · 고급 1 (21) 약 60 시간 · 마스터 (31) 약 300 시간.
 * 레벨이 맞는 보통 몬스터는 7 번쯤 쳐야 쓰러지고, 7 ~ 9 번쯤 맞으면 내가 쓰러진다 (곡선 장비 기준).
 *
 * <h3>숙련 ↔ 몬스터 레벨</h3>
 * 원작 몬스터 레벨(1 ~ 560)은 숙련 레벨(1 ~ 31)보다 훨씬 넓다. 위험도 표(0: 1~6 … 6: 320~560)에 맞춘 기준점을 로그로 잇는다:
 * 숙련 1 = 몬스터 1 · 3 = 6 · 6 = 16 · 10 = 40 · 15 = 120 · 20 = 240 · 25 = 420 · 31 = 560.
 */
public final class Progression {
    private Progression() {
    }

    private static final int[][] POINTS = {{1, 1}, {3, 6}, {6, 16}, {10, 40}, {15, 120}, {20, 240}, {25, 420}, {31, 560}};

    /** 숙련 레벨에 맞는 몬스터 레벨 */
    public static double monsterLevelFor(double mastery) {
        double m = Math.max(1, Math.min(31, mastery));
        for (int i = 0; i + 1 < POINTS.length; i++) {
            int[] a = POINTS[i], b = POINTS[i + 1];
            if (m <= b[0]) {
                double t = (m - a[0]) / (double) (b[0] - a[0]);
                return Math.exp(Math.log(a[1]) + t * (Math.log(b[1]) - Math.log(a[1])));
            }
        }
        return 560;
    }

    /** 몬스터 레벨 → 그 몬스터를 상대할 숙련 레벨 (경험치의 '권장 레벨') */
    public static int actionLevel(int monsterLevel) {
        double lv = Math.max(1, Math.min(560, monsterLevel));
        for (int i = 0; i + 1 < POINTS.length; i++) {
            int[] a = POINTS[i], b = POINTS[i + 1];
            if (lv <= b[1]) {
                double t = (Math.log(lv) - Math.log(a[1])) / (Math.log(b[1]) - Math.log(a[1]));
                return (int) Math.round(a[0] + t * (b[0] - a[0]));
            }
        }
        return 31;
    }

    // ---------------------------------------------------------------- 숙련 곡선
    /** level → level+1 에 필요한 경험치 = 100 × 레벨² × 단계 배율 (초급 1 · 중급 2.2 · 고급 5.5) — {@link io.versaera.domain.skill.Mastery#need} */
    public static long need(int level) {
        return io.versaera.domain.skill.Mastery.need(level);
    }

    // ---------------------------------------------------------------- 들판 몬스터 난이도
    /**
     * 몬스터 체력 배율. 원작 몬스터 체력(monsters.yml)은 곡선 장비로 한두 번에 쓰러지는 눈금이라, 레벨이 맞으면 7 번쯤 치게 키운다.
     * 같은 종류 안에서도 높은 레벨로 나온 것이 조금 더 단단하다 (가운데 레벨 기준 ^0.35).
     */
    public static final double MONSTER_HP = 9.5, MONSTER_DAMAGE = 1.7;

    public static double monsterHp(double baseHp, int level, int minLevel, int maxLevel) {
        double mid = Math.max(1, (minLevel + maxLevel) / 2.0);
        return Math.min(2048, Math.max(4, baseHp * MONSTER_HP * Math.pow(Math.max(1, level) / mid, 0.35)));
    }

    public static double monsterDamage(double baseDamage, int level, int minLevel, int maxLevel) {
        double mid = Math.max(1, (minLevel + maxLevel) / 2.0);
        return Math.max(0.5, baseDamage * MONSTER_DAMAGE * Math.pow(Math.max(1, level) / mid, 0.3));
    }

    /** 필드 보스: 사람의 공격은 35% 만 들어간다 (체력 상한 2048 이라 대신 단단하게) · 보스의 공격은 1.5 배 */
    public static final double BOSS_TAKEN = 0.35, BOSS_DAMAGE = 1.5;
    /** 필드 보스의 숙련 경험 보상 배율 — 레벨이 맞으면 한 마리가 사냥 30 ~ 60 분어치 */
    public static final double BOSS_XP = 8;

    // ---------------------------------------------------------------- 경험치
    /** 한 번 맞힐 때 (연습) */
    public static final long HIT_XP = 1;

    /** 쓰러뜨렸을 때 (권장 레벨 A) */
    public static long killXp(int actionLevel) {
        return 12 + 5L * Math.max(1, actionLevel);
    }

    /** 분야 종류별 경험치 배율 — 생산 · 채집 · 보조는 한 번 하는 데 드는 시간이 길어 사냥과 같은 속도가 되게 3 배 */
    public static double categoryXp(Discipline.Category c) {
        return c == Discipline.Category.COMBAT ? 1.0 : 3.0;
    }

    // ---------------------------------------------------------------- 시간 모형 (문서 · 테스트용)
    /** 레벨이 맞는 몬스터를 7 번에 잡고, 찾고 걷는 데 25 초 → 한 시간에 약 100 마리 */
    public static final double KILLS_PER_HOUR = 100, HITS_PER_KILL = 7;

    /** 사냥만으로 한 시간에 얻는 경험치 (레벨이 맞는 상대) */
    public static double huntingXpPerHour(int level) {
        return KILLS_PER_HOUR * (HITS_PER_KILL * HIT_XP + killXp(level));
    }

    /** 사냥만으로 레벨 1 → target 까지 걸리는 시간 */
    public static double huntingHoursTo(int target) {
        double h = 0;
        for (int lv = 1; lv < Math.min(31, target); lv++) h += need(lv) / huntingXpPerHour(lv);
        return h;
    }
}
