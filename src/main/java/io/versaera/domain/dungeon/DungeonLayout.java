package io.versaera.domain.dungeon;

import java.util.*;

/**
 * 던전 배치 생성기 (DUN-01, ORIGINAL). 같은 시드는 항상 같은 배치 → 서버 재시작 · 재접속에도 같은 던전.
 * 격자 위에서 방을 이어 붙여 나무 모양을 만든다 (막다른 길이 생겨 탐색할 이유가 있음).
 * <ul>
 *   <li>START: 입구 · BOSS: 입구에서 가장 먼 방 · PUZZLE: 보스로 가는 길 위 한 곳 (풀어야 보스 문이 열림)</li>
 *   <li>TREASURE: 막다른 방 · HIDDEN: 일반 문이 아니라 <b>숨은 벽</b>으로만 이어진 방 (단서는 벽의 금 · 바람 소리)</li>
 * </ul>
 */
public final class DungeonLayout {
    public enum Kind { START, COMBAT, PUZZLE, TREASURE, BOSS, HIDDEN }

    public record Room(int id, int gx, int gz, Kind kind) {}

    /** a ↔ b. secret 이면 숨은 벽 */
    public record Link(int a, int b, boolean secret) {}

    private final List<Room> rooms;
    private final List<Link> links;
    private final long seed;

    private DungeonLayout(long seed, List<Room> rooms, List<Link> links) {
        this.seed = seed;
        this.rooms = List.copyOf(rooms);
        this.links = List.copyOf(links);
    }

    public long seed() { return seed; }
    public List<Room> rooms() { return rooms; }
    public List<Link> links() { return links; }

    public Room room(Kind k) {
        return rooms.stream().filter(r -> r.kind() == k).findFirst().orElse(null);
    }

    public List<Integer> neighbours(int id, boolean includeSecret) {
        List<Integer> out = new ArrayList<>();
        for (Link l : links) {
            if (l.secret() && !includeSecret) continue;
            if (l.a() == id) out.add(l.b());
            else if (l.b() == id) out.add(l.a());
        }
        return out;
    }

    /** 일반 문만으로 갈 수 있는 거리 (못 가면 -1) */
    public int[] distances(boolean includeSecret) {
        int[] d = new int[rooms.size()];
        Arrays.fill(d, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        d[0] = 0;
        q.add(0);
        while (!q.isEmpty()) {
            int c = q.poll();
            for (int n : neighbours(c, includeSecret))
                if (d[n] < 0) {
                    d[n] = d[c] + 1;
                    q.add(n);
                }
        }
        return d;
    }

    /** 입구 → 보스 최단 경로 (방 id 목록) */
    public List<Integer> mainPath() {
        int target = room(Kind.BOSS).id();
        int[] prev = new int[rooms.size()];
        Arrays.fill(prev, -2);
        prev[0] = -1;
        ArrayDeque<Integer> q = new ArrayDeque<>(List.of(0));
        while (!q.isEmpty()) {
            int c = q.poll();
            for (int n : neighbours(c, false))
                if (prev[n] == -2) {
                    prev[n] = c;
                    q.add(n);
                }
        }
        LinkedList<Integer> path = new LinkedList<>();
        for (int c = target; c != -1; c = prev[c]) path.addFirst(c);
        return path;
    }

    public static DungeonLayout generate(long seed, int roomCount) {
        if (roomCount < 5 || roomCount > 40) throw new IllegalArgumentException("방 개수는 5 ~ 40: " + roomCount);
        SplittableRandom rng = new SplittableRandom(seed);
        List<int[]> cells = new ArrayList<>();
        Map<Long, Integer> at = new HashMap<>();
        List<int[]> edges = new ArrayList<>();
        cells.add(new int[]{0, 0});
        at.put(key(0, 0), 0);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int main = roomCount - 1;   // 마지막 하나는 숨은 방
        int guard = 0;
        while (cells.size() < main && guard++ < 10_000) {
            // 최근 방에서 뻗어 나가는 쪽을 더 자주 골라 긴 통로가 생기게
            int from = rng.nextInt(4) == 0 ? rng.nextInt(cells.size()) : Math.max(0, cells.size() - 1 - rng.nextInt(Math.min(3, cells.size())));
            int[] d = dirs[rng.nextInt(4)];
            int nx = cells.get(from)[0] + d[0], nz = cells.get(from)[1] + d[1];
            if (at.containsKey(key(nx, nz))) continue;
            at.put(key(nx, nz), cells.size());
            edges.add(new int[]{from, cells.size(), 0});
            cells.add(new int[]{nx, nz});
        }
        // 숨은 방: 빈 칸 중 아무 방에나 붙되, 입구 · 바로 옆이 아닌 곳
        List<int[]> spots = new ArrayList<>();
        for (int i = 2; i < cells.size(); i++)
            for (int[] d : dirs) {
                int nx = cells.get(i)[0] + d[0], nz = cells.get(i)[1] + d[1];
                if (!at.containsKey(key(nx, nz))) spots.add(new int[]{i, nx, nz});
            }
        int[] h = spots.get(rng.nextInt(spots.size()));
        at.put(key(h[1], h[2]), cells.size());
        edges.add(new int[]{h[0], cells.size(), 1});
        cells.add(new int[]{h[1], h[2]});

        // 종류 정하기
        Kind[] kinds = new Kind[cells.size()];
        Arrays.fill(kinds, Kind.COMBAT);
        kinds[0] = Kind.START;
        kinds[cells.size() - 1] = Kind.HIDDEN;
        List<Link> links = new ArrayList<>();
        for (int[] e : edges) links.add(new Link(e[0], e[1], e[2] == 1));
        List<Room> tmp = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) tmp.add(new Room(i, cells.get(i)[0], cells.get(i)[1], kinds[i]));
        DungeonLayout draft = new DungeonLayout(seed, tmp, links);
        int[] dist = draft.distances(false);
        int boss = 0;
        for (int i = 0; i < dist.length; i++) if (kinds[i] != Kind.HIDDEN && dist[i] > dist[boss]) boss = i;
        kinds[boss] = Kind.BOSS;
        tmp.set(boss, new Room(boss, cells.get(boss)[0], cells.get(boss)[1], Kind.BOSS));
        List<Integer> path = new DungeonLayout(seed, tmp, links).mainPath();
        int puzzle = path.get(Math.max(1, path.size() / 2));
        if (puzzle == boss) puzzle = path.get(path.size() - 2);
        kinds[puzzle] = Kind.PUZZLE;
        Set<Integer> onPath = new HashSet<>(path);
        for (int i = 1; i < cells.size(); i++) {
            if (kinds[i] != Kind.COMBAT || onPath.contains(i)) continue;
            if (draft.neighbours(i, false).size() == 1) kinds[i] = Kind.TREASURE;   // 막다른 방
        }
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) rooms.add(new Room(i, cells.get(i)[0], cells.get(i)[1], kinds[i]));
        return new DungeonLayout(seed, rooms, links);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
