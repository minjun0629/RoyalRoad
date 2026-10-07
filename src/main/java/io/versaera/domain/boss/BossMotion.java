package io.versaera.domain.boss;

/**
 * 거대 보스 이동 (순수 계산). 가장 가까운 대상 쪽으로 돌고 다가가되,
 * 판정 반지름(hit_radius × scale)만큼은 떨어져 서고, 전투 공간 밖으로는 나가지 않는다 (끌고 나가기 방지).
 * 몸이 큰 만큼 천천히 돈다 (초당 회전 각도 = 120 / scale).
 */
public final class BossMotion {
    public record Pose(double x, double z, double yaw) {}

    private BossMotion() {
    }

    public static double turnRate(double scale) {
        return 120.0 / Math.max(1, scale);
    }

    /** 목표 방향 yaw (Minecraft: 남쪽 0, 서쪽 90) */
    public static double yawTo(double fx, double fz, double tx, double tz) {
        return Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
    }

    private static double wrap(double a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    public static Pose step(Pose cur, double tx, double tz, double homeX, double homeZ, double arenaRadius, double keepDistance, double speed,
                            double scale, double seconds) {
        double want = yawTo(cur.x(), cur.z(), tx, tz);
        double diff = wrap(want - cur.yaw()), maxTurn = turnRate(scale) * seconds;
        double yaw = wrap(cur.yaw() + Math.max(-maxTurn, Math.min(maxTurn, diff)));
        double dist = Math.hypot(tx - cur.x(), tz - cur.z());
        double move = Math.min(speed * seconds, Math.max(0, dist - keepDistance));
        // 바라보는 방향이 많이 틀어져 있으면 먼저 돈다
        if (Math.abs(diff) > 60) move = 0;
        double nx = cur.x(), nz = cur.z();
        if (move > 0 && dist > 1e-6) {
            nx += (tx - cur.x()) / dist * move;
            nz += (tz - cur.z()) / dist * move;
        }
        double fromHome = Math.hypot(nx - homeX, nz - homeZ), limit = Math.max(0, arenaRadius - keepDistance);
        if (fromHome > limit && fromHome > 0) {
            nx = homeX + (nx - homeX) / fromHome * limit;
            nz = homeZ + (nz - homeZ) / fromHome * limit;
        }
        return new Pose(nx, nz, yaw);
    }

    /** 대상이 없으면 제자리로 돌아간다 */
    public static Pose returnHome(Pose cur, double homeX, double homeZ, double speed, double scale, double seconds) {
        return step(cur, homeX, homeZ, homeX, homeZ, Double.MAX_VALUE, 0, speed, scale, seconds);
    }
}
