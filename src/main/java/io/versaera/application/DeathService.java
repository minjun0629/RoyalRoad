package io.versaera.application;

import io.versaera.application.port.JobRepository;
import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.death.DeathPenalty;
import io.versaera.domain.reputation.Reputation;

import java.util.function.Supplier;

/**
 * 사망 처리 (DTH-01 · DTH-02). 서버 규칙(death.mode)에 따라:
 * <ul>
 *   <li>canon (원작): 숙련도가 떨어지고 (레벨은 그대로), 행동 스탯이 떨어지고, 무작위 아이템이 떨어진다 (어느 칸인지는 플랫폼이 서버 난수로 고름).
 *       악명 · 살인자면 더 크게. 초보 기간(시작 도시 밖에 못 나가는 동안)에는 페널티가 없다</li>
 *   <li>soft: 지금 단계 진행도만 감소 · 드롭 · 접속 제한 없음</li>
 * </ul>
 */
public final class DeathService {
    public record Outcome(DeathPenalty.Result penalty, boolean beginner, boolean murderer) {
        public int weakSeconds() {
            return penalty.weakSeconds();
        }

        public long totalXpLoss() {
            return penalty.totalXpLoss();
        }

        public int wear() {
            return penalty.wear();
        }

        public boolean heavyWear() {
            return penalty.heavyWear();
        }
    }

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final JobRepository jobs;
    private final ItemService items;
    private final GrowthService growth;
    private final GameClock clock;
    private final Supplier<ServerRules> rules;
    private OriginService origins;
    private ReputationService reputation;

    public DeathService(TxRunner tx, ProgressRepository progress, JobRepository jobs, ItemService items, GrowthService growth, GameClock clock,
                        Supplier<ServerRules> rules) {
        this.tx = tx;
        this.progress = progress;
        this.jobs = jobs;
        this.items = items;
        this.growth = growth;
        this.clock = clock;
        this.rules = rules;
    }

    void attach(OriginService origins, ReputationService reputation) {
        this.origins = origins;
        this.reputation = reputation;
    }

    /** @param equipped 사망 순간 입고 · 들고 있던 고유 아이템 id */
    public Outcome die(String uuid, String region, int danger, java.util.List<String> equipped) {
        if (origins != null && origins.beginner(uuid)) {
            // 원작: 성문 밖에 못 나가는 초보 상태에서는 사망 페널티가 없다
            tx.inTx(() -> {
                jobs.deathLog(uuid, region, danger, 0, clock.nowMillis());
                return null;
            });
            growth.record(uuid, "death", 1);
            return new Outcome(DeathPenalty.NONE, true, false);
        }
        ServerRules sr = rules.get();
        ReputationService.Standing st = reputation == null ? new ReputationService.Standing(0, 0, 0, false) : reputation.standing(uuid);
        DeathPenalty.Result r = sr.canonDeath()
                ? DeathPenalty.computeCanon(progress.allMastery(uuid), statCounters(uuid), danger, Reputation.deathMult(st.notoriety(), st.murderer()))
                : DeathPenalty.compute(progress.allMastery(uuid), danger);
        tx.inTx(() -> {
            for (var e : r.xpLoss().entrySet()) progress.setMasteryXp(uuid, e.getKey(), Math.max(0, progress.masteryXp(uuid, e.getKey()) - e.getValue()));
            jobs.deathLog(uuid, region, danger, r.totalXpLoss(), clock.nowMillis());
            for (var e : r.statLoss().entrySet()) progress.addCounter(uuid, e.getKey(), -Math.min(e.getValue(), progress.counter(uuid, e.getKey())));
            return null;
        });
        for (String id : equipped) {
            try {
                items.wear(id, uuid, r.wear(), r.heavyWear());
            } catch (io.versaera.domain.common.DomainException ex) {
                // 남의 아이템 · 사라진 아이템은 마모하지 않는다 (InventoryGuard 가 따로 회수)
                if (!ex.code().startsWith("item.")) throw ex;
            }
        }
        growth.record(uuid, "death", 1);
        return new Outcome(r, false, st.murderer());
    }

    /** 행동 스탯이 쓰는 기록들 (스탯마다 하나) */
    private java.util.Map<String, Long> statCounters(String uuid) {
        java.util.Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (var s : growth.stats()) m.put(s.counter(), progress.counter(uuid, s.counter()));
        return m;
    }
}
