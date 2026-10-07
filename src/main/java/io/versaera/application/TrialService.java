package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;

/**
 * 초급 수련관 시련 (TRN-02, CANON: 100명의 철인과 싸워 이겨야 하는 수련관). 진행(몇 명째인지)은 플랫폼 메모리,
 * 통과 기록과 보상은 여기서 한 번만: 명성 +300 · 인내 기록 +300 · 힘 기록 +300 (수치 ORIGINAL).
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

    /** 시작해도 되는가 (미성년은 불가). 이미 통과했어도 다시 도전은 된다 (보상은 처음 한 번) */
    public void checkStart(String uuid) {
        DomainException.require(!s.access.minor(uuid), "trial.minor", "미성년 보호 대상은 시련에 도전할 수 없습니다");
    }

    /** @return 처음 통과면 true (보상 지급) */
    public boolean complete(String uuid) {
        boolean first = tx.inTx(() -> {
            if (progress.counter(uuid, "trial.ironmen") > 0) return false;
            progress.addCounter(uuid, "trial.ironmen", 1);
            progress.addCounter(uuid, "fame", 300);
            return true;
        });
        if (first) {
            s.growth.record(uuid, "hit_taken", 300);
            s.growth.record(uuid, "hit.training", 300);
        }
        return first;
    }
}
