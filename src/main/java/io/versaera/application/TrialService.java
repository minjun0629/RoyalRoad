package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;

/**
 * 초급 수련관 시련 (TRN-02, CANON: 100명의 철인과 싸워 이겨야 하는 수련관). 진행(몇 명째인지)은 플랫폼 메모리,
 * 통과 기록과 보상은 여기서 한 번만: 명성 +300 · 인내 기록 +300 · 힘 기록 +300 · 단단한 철검 (수치 ORIGINAL).
 */
public final class TrialService {
    public static final int IRON_MEN = 100;
    private final TxRunner tx;
    private final ProgressRepository progress;
    private final GameServices s;

    TrialService(TxRunner tx, ProgressRepository progress, GameServices s) {
        this.tx = tx;
        this.progress = progress;
        this.s = s;
    }

    public boolean cleared(String uuid) {
        return progress.counter(uuid, "trial.ironmen") > 0;
    }

    /** @return 처음 통과면 true (보상 지급) */
    public boolean complete(String uuid) {
        AfterCommit after = new AfterCommit();
        boolean first = tx.inTx(() -> {
            if (progress.counter(uuid, "trial.ironmen") > 0) return false;
            progress.addCounter(uuid, "trial.ironmen", 1);
            progress.addCounter(uuid, "fame", 300);
            // 통과 기념 '단단한 철검' (ITM-02, 이름 CANON) — 배달함으로
            s.items.createInTx("hard_iron_sword", 600, null, "초급 수련관", "trial", java.util.Map.of(), uuid, "trial:ironmen:" + uuid, after);
            return true;
        });
        after.publish(s.bus);
        if (first) {
            s.growth.record(uuid, "hit_taken", 300);
            s.growth.record(uuid, "hit.training", 300);
        }
        return first;
    }
}
