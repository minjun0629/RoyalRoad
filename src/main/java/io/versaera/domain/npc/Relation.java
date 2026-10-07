package io.versaera.domain.npc;

import java.util.Set;

/**
 * 플레이어 ↔ NPC 관계 (플레이어별 저장). 호감 -1000 ~ 1000. 단계 이름 · 수치는 ORIGINAL.
 * 매일 대화는 첫 번째만 크게 오르고(같은 날 반복 대화는 의미 없음), 선물은 취향에 따라 오르내린다.
 */
public final class Relation {
    public static final int MIN = -1000, MAX = 1000;
    private static final int[] STEPS = {-1000, -300, -50, 100, 400, 800};
    private static final String[] NAMES = {"적대", "냉담", "중립", "호의", "신뢰", "맹우"};

    private Relation() {
    }

    /**
     * 관계 단계 (NPC-04). 처음 만남 → 인지 → 관심 → 우호 → 신뢰 → 친밀 → 동료 → 특별한 사이, 그리고 나쁜 쪽은 냉담 · 적대.
     * 단계가 오를 때마다 열리는 것: 관심 = 소문 · 우호 = 지도(숙련)와 할인 · 신뢰 = 숨은 의뢰 · 친밀 = 먼 곳의 비밀 · 동료 = 지도 두 배 · 특별 = 큰 할인
     */
    public enum Stage {
        HOSTILE("적대"), COLD("냉담"), STRANGER("처음 만남"), KNOWN("인지"), INTEREST("관심"), FRIENDLY("우호"), TRUST("신뢰"), CLOSE("친밀"),
        COMRADE("동료"), BOND("특별한 사이");
        public final String label;

        Stage(String label) { this.label = label; }

        public boolean atLeast(Stage o) { return ordinal() >= o.ordinal(); }

        /** NPC 상점 할인 */
        public double discount() {
            return switch (this) { case FRIENDLY -> 0.04; case TRUST -> 0.07; case CLOSE -> 0.10; case COMRADE -> 0.13; case BOND -> 0.18; default -> 0; };
        }

        /** 대사 묶음: 0 낯섦 · 1 보통 · 2 가까움 */
        public int lineGroup() {
            return atLeast(CLOSE) ? 2 : atLeast(INTEREST) ? 1 : 0;
        }
    }

    /** @param met 한 번이라도 대화했는가 */
    public static Stage stage(int affinity, boolean met) {
        if (affinity <= -300) return Stage.HOSTILE;
        if (affinity < -50) return Stage.COLD;
        if (!met) return Stage.STRANGER;
        if (affinity < 50) return Stage.KNOWN;
        if (affinity < 150) return Stage.INTEREST;
        if (affinity < 300) return Stage.FRIENDLY;
        if (affinity < 500) return Stage.TRUST;
        if (affinity < 700) return Stage.CLOSE;
        if (affinity < 900) return Stage.COMRADE;
        return Stage.BOND;
    }

    /** 관계가 퍼지는 비율: 가족은 30%, 일로 엮인 사이는 20%, 경쟁자는 거꾸로 -15% */
    public static double spread(String linkType) {
        return switch (linkType) {
            case "SPOUSE", "PARENT", "CHILD", "SIBLING" -> 0.30;
            case "PARTNER", "MASTER", "APPRENTICE", "LORD", "VASSAL" -> 0.20;
            case "RIVAL" -> -0.15;
            default -> 0;
        };
    }

    public static int clamp(long v) {
        return (int) Math.max(MIN, Math.min(MAX, v));
    }

    public static int tier(int affinity) {
        int t = 0;
        for (int i = 0; i < STEPS.length; i++) if (affinity >= STEPS[i]) t = i;
        return t;
    }

    public static String tierName(int affinity) {
        return NAMES[tier(affinity)];
    }

    /** 대화로 오르는 호감: 그날 첫 대화만 +5 (호감이 높을수록 오르기 어려움) */
    public static int talkGain(int affinity, boolean firstToday) {
        if (!firstToday) return 0;
        return affinity >= 400 ? 2 : affinity >= 100 ? 3 : 5;
    }

    /** 선물: 좋아하는 태그면 +(10 ~ 40, 품질 따라), 싫어하는 태그면 -15, 그 밖은 +2 */
    public static int giftGain(NpcDefinition npc, Set<String> itemTags, int quality) {
        for (String t : itemTags) if (npc.dislikes().contains(t)) return -15;
        for (String t : itemTags) if (npc.likes().contains(t)) return 10 + Math.max(0, Math.min(1000, quality)) * 30 / 1000;
        return 2;
    }
}
