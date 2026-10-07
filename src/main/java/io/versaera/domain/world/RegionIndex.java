package io.versaera.domain.world;

import java.util.*;

/**
 * 256×256 블록 격자 공간 인덱스. 위치 → 지역 조회를 칸 하나만 보고 끝낸다 (플레이어가 블록을 옮길 때만 부름).
 * 칸을 청크(16)보다 크게 잡아 50000 × 50000 세계 전체를 덮는 지역(먼 바다 · 차원의 틈)도 칸 4만여 개로 끝난다.
 */
public final class RegionIndex {
    private final Map<String, Map<Long, List<Region>>> byWorld = new HashMap<>();
    private final Map<String, Region> byId = new LinkedHashMap<>();

    private static final int CELL = 8;   // 2^8 = 256 블록

    public RegionIndex(Collection<Region> regions) {
        for (Region r : regions) {
            if (byId.putIfAbsent(r.id(), r) != null) throw new IllegalArgumentException("지역 id 중복: " + r.id());
            Map<Long, List<Region>> grid = byWorld.computeIfAbsent(r.world(), k -> new HashMap<>());
            for (int cx = r.minX() >> CELL; cx <= r.maxX() >> CELL; cx++)
                for (int cz = r.minZ() >> CELL; cz <= r.maxZ() >> CELL; cz++)
                    grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(r);
        }
        Comparator<Region> order = Comparator.comparingInt(Region::priority).reversed().thenComparing(Region::id);
        for (Map<Long, List<Region>> g : byWorld.values()) for (List<Region> l : g.values()) l.sort(order);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** 가장 구체적인 지역 (없으면 null) */
    public Region at(String world, int x, int y, int z) {
        Map<Long, List<Region>> g = byWorld.get(world);
        if (g == null) return null;
        List<Region> l = g.get(key(x >> CELL, z >> CELL));
        if (l == null) return null;
        for (Region r : l) if (r.contains(world, x, y, z)) return r;
        return null;
    }

    public Region byId(String id) {
        return byId.get(id);
    }

    public Collection<Region> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
