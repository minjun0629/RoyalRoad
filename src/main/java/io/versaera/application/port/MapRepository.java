package io.versaera.application.port;

import java.util.Set;

public interface MapRepository {
    /** 가 본 칸 (FogMap.pack 값) */
    Set<Long> explored(String uuid);

    /** @return 새로 밝힌 칸 수 */
    int explore(String uuid, long[] cells);
}
