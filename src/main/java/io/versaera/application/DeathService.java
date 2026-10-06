package io.versaera.application;

import io.versaera.application.port.JobRepository;
import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.death.DeathPenalty;

/** 사망 처리 — 숙련 진행도 감소 · 장비 마모 · 기록 (DeathPenalty 참고) */
public final class DeathService {
    private final TxRunner tx;
    private final ProgressRepository progress;
    private final JobRepository jobs;
    private final ItemService items;
    private final GrowthService growth;
    private final GameClock clock;

    public DeathService(TxRunner tx, ProgressRepository progress, JobRepository jobs, ItemService items, GrowthService growth, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.jobs = jobs;
        this.items = items;
        this.growth = growth;
        this.clock = clock;
    }

    /** @param equipped 사망 순간 입고 · 들고 있던 고유 아이템 id */
    public DeathPenalty.Result die(String uuid, String region, int danger, java.util.List<String> equipped) {
        DeathPenalty.Result r = DeathPenalty.compute(progress.allMastery(uuid), danger);
        tx.inTx(() -> {
            for (var e : r.xpLoss().entrySet()) progress.setMasteryXp(uuid, e.getKey(), Math.max(0, progress.masteryXp(uuid, e.getKey()) - e.getValue()));
            jobs.deathLog(uuid, region, danger, r.totalXpLoss(), clock.nowMillis());
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
        return r;
    }
}
