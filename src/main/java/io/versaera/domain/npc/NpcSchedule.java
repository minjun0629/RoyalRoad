package io.versaera.domain.npc;

import java.util.Collection;
import java.util.Map;

/**
 * NPC 일과 이동 (NPC-02, 순수 계산). 장소 좌표는 content/places.yml (x, z — 높이는 플랫폼이 지형에서 찾는다).
 * 성능: 근처(ACTIVE_RADIUS)에 플레이어가 없으면 걷지 않고, 플레이어가 다가왔을 때 이미 그 시각의 장소에 있게 한다.
 */
public final class NpcSchedule {
    public record Point(double x, double z) {
        public double dist(Point o) {
            return Math.hypot(x - o.x, z - o.z);
        }
    }

    public static final double ACTIVE_RADIUS = 64, WALK_PER_SECOND = 3.5;

    private NpcSchedule() {
    }

    /** 그 시각에 있어야 할 곳 (일과 · 좌표가 없으면 null) */
    public static Point target(NpcDefinition n, Map<String, Point> places, int hour) {
        String place = n.placeAt(hour);
        return place == null ? null : places.get(place);
    }

    /** from → to 로 seconds 만큼 걷는다 (도착하면 to) */
    public static Point step(Point from, Point to, double seconds) {
        double d = from.dist(to), max = WALK_PER_SECOND * seconds;
        if (d <= max || d == 0) return to;
        double k = max / d;
        return new Point(from.x() + (to.x() - from.x()) * k, from.z() + (to.z() - from.z()) * k);
    }

    public static boolean active(Point npc, Collection<Point> players) {
        for (Point p : players) if (p.dist(npc) <= ACTIVE_RADIUS) return true;
        return false;
    }
}
