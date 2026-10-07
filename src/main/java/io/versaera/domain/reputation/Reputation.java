package io.versaera.domain.reputation;

/**
 * 명성 · 악명 · 살인자 규칙 (개념 CANON, 수치 ORIGINAL).
 * <ul>
 *   <li>명성: 의뢰 · 보스 · 최초 발견으로 쌓인다. 높을수록 의뢰 보상이 늘고, 명사(3단계) 이상이면 일상 · 숙련 의뢰의 조건을 채우지 못해도 맡을 수 있다</li>
 *   <li>악명: 살인자가 아닌 사람을 죽이면 쌓인다. 100 이상이면 보통 NPC 가 의뢰를 맡기지 않고 보상이 줄며, 악한 NPC 와만 거래 · 의뢰가 된다</li>
 *   <li>살인자: 사람을 죽이면 붉은 이름. 살인자를 죽인 사람은 아무 페널티가 없다. NPC 가 상대하지 않고 사망 페널티가 커진다</li>
 *   <li>해소: 몬스터 사냥(1 마리 = 악명 1, 살인자 시간 1분) · 신전 기부(악명이 많을수록 1 포인트가 비싸다)</li>
 * </ul>
 */
public final class Reputation {
    public static final int NOTORIOUS = 100;
    public static final long MURDER_BASE_MS = 2 * 3_600_000L, MURDER_PER_NOTORIETY_MS = 60_000L, CLEANSE_MS_PER_KILL = 60_000L;
    public static final int NOTORIETY_PER_KILL = 100;
    private static final long[] FAME_STEPS = {0, 100, 1_000, 5_000, 20_000};
    private static final String[] FAME_NAMES = {"무명", "알려짐", "유명", "명사", "전설"};

    private Reputation() {
    }

    public static int fameTier(long fame) {
        int t = 0;
        for (int i = 0; i < FAME_STEPS.length; i++) if (fame >= FAME_STEPS[i]) t = i;
        return t;
    }

    public static String fameName(long fame) {
        return FAME_NAMES[fameTier(fame)];
    }

    public static boolean notorious(long notoriety) {
        return notoriety >= NOTORIOUS;
    }

    /** 의뢰 돈 보상 배율: 명성 단계마다 +5%, 악명 100 이상 -25% */
    public static double questRewardMult(long fame, long notoriety) {
        return Math.max(0.5, 1 + 0.05 * fameTier(fame) - (notorious(notoriety) ? 0.25 : 0));
    }

    /** 명사 이상이면 일상 · 숙련 의뢰의 조건을 낮춰 받는다 */
    public static boolean relaxesRequirements(long fame) {
        return fameTier(fame) >= 3;
    }

    /** 보통 NPC 가 의뢰 · 거래를 거절하는가 (악한 NPC 는 반대) */
    public static boolean npcRefuses(boolean evilNpc, long notoriety, boolean murderer) {
        return evilNpc ? !notorious(notoriety) : notorious(notoriety) || murderer;
    }

    /** 사람을 죽였을 때: 상대가 살인자면 아무 일도 없다. 아니면 악명 +100, 살인자 기간 = 2시간 + 악명 1당 1분 */
    public record Kill(long notorietyGain, long murderMs) {}

    public static Kill onKill(boolean victimWasMurderer, long killerNotorietyBefore) {
        if (victimWasMurderer) return new Kill(0, 0);
        long after = killerNotorietyBefore + NOTORIETY_PER_KILL;
        return new Kill(NOTORIETY_PER_KILL, MURDER_BASE_MS + after * MURDER_PER_NOTORIETY_MS);
    }

    /** 신전 기부로 악명 1을 지우는 값: 10 + 악명/10 (쌓인 만큼 비싸다) */
    public static long donationPerPoint(long notoriety) {
        return 10 + notoriety / 10;
    }

    /** 기부금으로 지울 수 있는 악명 (남는 돈은 축복으로 간다) */
    public static long cleansedBy(long money, long notoriety) {
        long n = notoriety, left = money, out = 0;
        while (n > 0 && left >= donationPerPoint(n)) {
            left -= donationPerPoint(n);
            n--;
            out++;
        }
        return out;
    }

    /** 사망 페널티 배율: 악명 1000 마다 +100% (최대 2배), 살인자면 그 위에 2배 */
    public static double deathMult(long notoriety, boolean murderer) {
        return (1 + Math.min(1.0, notoriety / 1000.0)) * (murderer ? 2 : 1);
    }
}
