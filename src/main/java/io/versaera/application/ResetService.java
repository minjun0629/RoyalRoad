package io.versaera.application;

import io.versaera.application.port.ResetRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;

/**
 * 관리자 초기화 (플레이어 한 명). 한 트랜잭션에서 그 사람의 진행을 모두 지운다 — 다시 접속하면 종족 고르기부터.
 * 길드장이면 길드가 주인을 잃으므로 먼저 넘기거나 해산해야 한다. 스레드: DB 스레드.
 */
public final class ResetService {
    private final TxRunner tx;
    private final ResetRepository repo;
    private final GameServices s;

    ResetService(TxRunner tx, ResetRepository repo, GameServices s) {
        this.tx = tx;
        this.repo = repo;
        this.s = s;
    }

    /** 초기화할 수 있나 (길드장이면 안 된다) — 인벤토리를 비우기 전에 먼저 살핀다 */
    public void check(String uuid) {
        DomainException.require(repo.guildLedBy(uuid) == null, "reset.guild_leader", "길드장입니다 — 길드를 넘기거나 해산한 뒤 초기화하세요");
    }

    /** @return 지운 행 수 */
    public int player(String uuid, String actor) {
        check(uuid);
        int n = tx.inTx(() -> {
            int rows = repo.wipe(uuid);
            s.audit.record("PLAYER_RESET", actor, uuid, rows + " rows", null);
            return rows;
        });
        s.artworks.load();   // 지운 작품을 목록에서도 뺀다
        return n;
    }
}
