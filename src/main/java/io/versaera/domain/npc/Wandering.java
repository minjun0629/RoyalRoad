package io.versaera.domain.npc;

import io.versaera.domain.npc.NpcSchedule.Point;

import java.util.List;

/**
 * 떠돌이 NPC 의 자리 (순수 계산, NPC-04). 경로의 도시를 차례로 돌고 마지막에서 처음으로 돌아온다.
 * 도시마다 stayMs 동안 머물고, 그 사이는 분당 speed 블록으로 곧게 걷는다. 시각만으로 정해지므로 저장할 것이 없고
 * 서버가 꺼져 있던 동안에도 "그만큼 걸어간" 자리에 있다.
 */
public final class Wandering {
    /** where = 지금 자리, stop = 머무는 도시 순번 (길 위면 -1), next = 다음 도시 순번, arriveInMs = 다음 도시까지 (머무는 중이면 떠날 때까지) */
    public record State(Point where, int stop, int next, long arriveInMs) {}

    private Wandering() {
    }

    public static State at(List<Point> stops, double blocksPerMinute, long stayMs, long nowMs, long offsetMs) {
        if (stops.isEmpty()) throw new IllegalArgumentException("stops");
        if (stops.size() == 1) return new State(stops.get(0), 0, 0, stayMs);
        int n = stops.size();
        long[] walk = new long[n];
        long loop = 0;
        for (int i = 0; i < n; i++) {
            walk[i] = Math.max(1, Math.round(stops.get(i).dist(stops.get((i + 1) % n)) / Math.max(1, blocksPerMinute) * 60_000));
            loop += stayMs + walk[i];
        }
        long t = Math.floorMod(nowMs + offsetMs, loop);
        for (int i = 0; i < n; i++) {
            if (t < stayMs) return new State(stops.get(i), i, (i + 1) % n, stayMs - t);
            t -= stayMs;
            if (t < walk[i]) {
                Point a = stops.get(i), b = stops.get((i + 1) % n);
                double k = (double) t / walk[i];
                return new State(new Point(a.x() + (b.x() - a.x()) * k, a.z() + (b.z() - a.z()) * k), -1, (i + 1) % n, walk[i] - t);
            }
            t -= walk[i];
        }
        return new State(stops.get(0), 0, 1 % n, stayMs);
    }
}
