package io.versaera.domain.boss;

import java.util.*;

/**
 * 보스 보상 자격 (순수 계산). 마지막 한 대가 아니라 <b>전투 내내 한 일</b>로 정한다:
 * 기여 = 준 피해 + 받아 낸(막은) 피해 × 0.5 + 지원(회복 · 버프) × 0.8.
 * 전체 기여의 3% 미만은 보상 없음 (구경 · 업혀 가기 방지), 상위 기여자(20% 이상)는 MVP.
 */
public final class BossRewards {
    public enum Tier { NONE, PARTICIPANT, MVP }

    public record Contribution(long damage, long mitigated, long support) {
        public double score() {
            return damage + mitigated * 0.5 + support * 0.8;
        }

        public Contribution plus(Contribution o) {
            return new Contribution(damage + o.damage, mitigated + o.mitigated, support + o.support);
        }
    }

    public static final double MIN_SHARE = 0.03, MVP_SHARE = 0.20;

    private BossRewards() {
    }

    public static Map<String, Tier> tiers(Map<String, Contribution> all) {
        double total = all.values().stream().mapToDouble(Contribution::score).sum();
        Map<String, Tier> out = new LinkedHashMap<>();
        for (Map.Entry<String, Contribution> e : all.entrySet()) {
            double share = total <= 0 ? 0 : e.getValue().score() / total;
            out.put(e.getKey(), share >= MVP_SHARE ? Tier.MVP : share >= MIN_SHARE ? Tier.PARTICIPANT : Tier.NONE);
        }
        return out;
    }
}
