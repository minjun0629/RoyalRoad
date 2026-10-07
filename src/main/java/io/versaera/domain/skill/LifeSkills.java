package io.versaera.domain.skill;

/**
 * 생활 스킬 수치 (SKL-05). 이름 · 개념은 원작 (붕대 감기 · 검 갈기 · 방어구 닦기 · 다림질 · 도축 · 사자후 · 조각 파괴술 · 일점 공격),
 * 수치는 ORIGINAL. 레벨은 숙련 레벨 1 ~ 31.
 */
public final class LifeSkills {
    private LifeSkills() {
    }

    /** 붕대 감기: 회복량 (반 하트 단위). 전투 중이면 절반 */
    public static double bandageHeal(int level, boolean inCombat) {
        double h = 4 + level * 0.5;
        return inCombat ? h / 2 : h;
    }

    /** 검 갈기 · 방어구 닦기 · 다림질: 공격력 / 방어력 +% (10분) */
    public static double carePct(int level) {
        return Math.min(25, 5 + level * 0.65);
    }

    public static final int CARE_MINUTES = 10;

    /** 도축: 동물을 잡을 때 고기 · 가죽을 더 얻을 확률 */
    public static double butcherChance(int level) {
        return Math.min(0.9, 0.25 + level * 0.02);
    }

    /** 도축: 더 얻는 개수 */
    public static int butcherExtra(int level) {
        return 1 + level / 10;
    }

    /** 사자후: 반지름 · 공포 시간(초) — 검술 숙련 레벨과 인내 스탯으로 */
    public static double roarRadius(int level) {
        return 6 + level * 0.25;
    }

    public static int roarSeconds(int level, int endurance) {
        return Math.min(12, 3 + level / 5 + endurance / 10);
    }

    /** 조각 파괴술: 예술 스탯을 힘으로 — 근접 피해 +% (최대 40) 와 지속 분 (조각품 품질이 높을수록 길게) */
    public static double destructionPct(int artistry) {
        return Math.min(40, artistry * 0.5);
    }

    public static int destructionMinutes(int quality) {
        return 3 + quality / 100;
    }

    /** 일점 공격: 같은 상대의 같은 곳을 이어 치면 쌓임마다 +5% (최대 6), 2초 안에 이어 쳐야 한다. 검술 숙련 10 부터 */
    public static final int FOCUS_MIN_LEVEL = 10, FOCUS_MAX_STACK = 6;
    public static final long FOCUS_WINDOW_MS = 2000;

    public static double focusMult(int stacks) {
        return 1 + Math.min(FOCUS_MAX_STACK, Math.max(0, stacks)) * 0.05;
    }
}
