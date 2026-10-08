package io.versaera.domain.combat;

/**
 * 허수아비 타이밍 미니게임: 표시가 막대 위를 왕복하고, 노란 칸(가운데는 초록) 안에서 쳐야 수련이 된다.
 * 연타로는 늘지 않는다 — 빗나가면 연속이 끊기고 잠깐 칠 수 없다. 연속이 쌓일수록 표시가 빨라지고 칸이 좁아진다.
 */
public final class TimingBar {
    private TimingBar() {
    }

    public enum Grade {
        PERFECT(2), GOOD(1), MISS(0);

        /** 이 판정이 세는 수련 횟수 */
        public final int hits;

        Grade(int hits) {
            this.hits = hits;
        }
    }

    public static final int CELLS = 25;
    /** 빗나간 뒤 칠 수 없는 시간 */
    public static final long MISS_LOCK_MS = 700;

    /** 표시가 한 번 왕복하는 시간: 연속 0 = 1.6초 → 연속이 늘수록 빨라져 0.9초까지 */
    public static long period(int combo) {
        return Math.max(900, 1600 - Math.min(combo, 14) * 50L);
    }

    /** 칸 너비 (막대 전체 = 1): 연속 0 = 0.24 → 0.12 까지 */
    public static double width(int combo) {
        return Math.max(0.12, 0.24 - Math.min(combo, 12) * 0.01);
    }

    /** 표시 위치 0 ~ 1 (삼각파) */
    public static double position(long elapsedMs, long periodMs) {
        double t = Math.floorMod(elapsedMs, periodMs) / (double) periodMs;
        return t < 0.5 ? t * 2 : 2 - t * 2;
    }

    public static Grade judge(double pos, double center, double width) {
        double d = Math.abs(pos - center);
        if (d <= width / 6) return Grade.PERFECT;
        if (d <= width / 2) return Grade.GOOD;
        return Grade.MISS;
    }

    /** 칸 가운데 (막대 끝에 붙지 않게 0.2 ~ 0.8) */
    public static double nextCenter(double random01) {
        return 0.2 + 0.6 * Math.max(0, Math.min(1, random01));
    }

    /** "§8▮▮§e▮§a▮§e▮§8▮… " — 표시 칸은 흰색 */
    public static String render(double pos, double center, double width) {
        StringBuilder sb = new StringBuilder();
        int marker = (int) Math.round(pos * (CELLS - 1));
        for (int i = 0; i < CELLS; i++) {
            double c = i / (double) (CELLS - 1), d = Math.abs(c - center);
            String color = i == marker ? "§f" : d <= width / 6 ? "§a" : d <= width / 2 ? "§e" : "§8";
            sb.append(color).append(i == marker ? "┃" : "▮");
        }
        return sb.toString();
    }
}
