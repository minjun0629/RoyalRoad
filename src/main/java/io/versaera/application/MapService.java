package io.versaera.application;

import io.versaera.application.port.MapRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.map.FogMap;

import java.util.*;

/**
 * 탐험 지도 (MAP-01). 가 본 칸은 DB 에 남고, 접속 중인 사람의 것은 메모리에 들고 있다.
 * 이동마다 DB 에 쓰지 않도록 새 칸일 때만 기록한다. DB 스레드에서만 쓴다.
 */
public final class MapService {
    private final TxRunner tx;
    private final MapRepository repo;
    private final Map<String, Set<Long>> cache = new HashMap<>();

    MapService(TxRunner tx, MapRepository repo) {
        this.tx = tx;
        this.repo = repo;
    }

    public Set<Long> explored(String uuid) {
        return cache.computeIfAbsent(uuid, repo::explored);
    }

    /** @return 새로 밝힌 칸 수 */
    public int visit(String uuid, int x, int z) {
        Set<Long> known = explored(uuid);
        long[] cells = FogMap.reveal(x, z);
        long[] fresh = Arrays.stream(cells).filter(c -> !known.contains(c)).toArray();
        if (fresh.length == 0) return 0;
        int n = tx.inTx(() -> repo.explore(uuid, fresh));
        for (long c : fresh) known.add(c);
        return n;
    }

    /** 플랫폼 렌더러가 메인 스레드에서 읽을 복사본 */
    public Set<Long> snapshot(String uuid) {
        return Set.copyOf(explored(uuid));
    }

    public void forget(String uuid) {
        cache.remove(uuid);
    }
}
