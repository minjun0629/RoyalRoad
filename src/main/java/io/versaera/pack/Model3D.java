package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * 손에 든 장비의 입체 모델 (RP-01) — Armourer's Workshop 처럼 부위마다 두께가 다른 조각 모델.
 * 64 칸 그림의 칸마다 부위를 가려 두께를 정하고 (칼날 얇게 · 손잡이 · 코등이 · 보석 두껍게), 같은 두께가 이어진 칸을 한 상자로 묶는다.
 * 상자 앞뒤에는 그림이 그대로, 옆면에는 그 칸 색이 칠해진다.
 * <p>
 * 인벤토리 카드: 모델 맨 앞에 앞면(south)만 있는 판을 하나 두고 카드 그림을 붙인다. 인벤토리는 앞면을 보므로 카드가 입체 모델을 가리고,
 * 손에 들었을 때는 모델을 무기 축으로 뒤집어(표시 변환) 카드 판의 뒷면이 보이게 한다 — 뒷면은 그리지 않으므로 입체 모델만 보인다.
 * (Minecraft 1.20.1 에는 인벤토리 · 손 모델을 따로 고르는 기능이 없다)
 */
public final class Model3D {
    private Model3D() {
    }

    static final int S = 64;
    static final double PX = 16.0 / S;

    /** 칸 부위 → 두께 (블록 1 = 16) */
    static double[][] thickness(BufferedImage art, IconSmith.Look l, boolean weapon) {
        Color[][] roles = {IconSmith.tones(l.p()), IconSmith.tones(l.s()), IconSmith.tones(l.g()), IconSmith.tones(l.a())};
        double[] depth = weapon ? new double[]{0.75, 1.5, 2.25, 2.75} : new double[]{2.0, 2.5, 2.75, 3.0};
        double[][] t = new double[S][S];
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int p = art.getRGB(x, y);
                if ((p >>> 24) < 200) continue;
                Color c = new Color(p);
                if (c.getRed() + c.getGreen() + c.getBlue() < 70) { t[y][x] = -1; continue; }   // 외곽선: 이웃을 따른다
                int best = 0;
                double bd = Double.MAX_VALUE;
                for (int r = 0; r < roles.length; r++)
                    for (Color tone : roles[r]) {
                        double d = sq(c.getRed() - tone.getRed()) + sq(c.getGreen() - tone.getGreen()) + sq(c.getBlue() - tone.getBlue());
                        if (d < bd) { bd = d; best = r; }
                    }
                t[y][x] = depth[best];
            }
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                if (t[y][x] != -1) continue;
                double m = 0;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + o[0], ny = y + o[1];
                    if (nx >= 0 && ny >= 0 && nx < S && ny < S && t[ny][nx] > m) m = t[ny][nx];
                }
                t[y][x] = m > 0 ? m : depth[0];
            }
        return t;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static String n(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * @param art    입체 모델 그림 텍스처 (versaera:item3d/id)
     * @param card   인벤토리 카드 텍스처 (versaera:item/id)
     * @param weapon 무기 (무기 축으로 뒤집기 · 손에 비스듬히 드는 변환)
     */
    public static String json(BufferedImage img, IconSmith.Look l, boolean weapon, String art, String card) {
        double[][] t = thickness(img, l, weapon);
        StringBuilder e = new StringBuilder();
        double maxT = 0;
        for (int y = 0; y < S; y++) {
            int x = 0;
            while (x < S) {
                if (t[y][x] == 0) { x++; continue; }
                int x0 = x;
                double th = t[y][x];
                while (x < S && t[y][x] == th) x++;
                maxT = Math.max(maxT, th);
                double fx = x0 * PX, tx = x * PX, fy = 16 - (y + 1) * PX, ty = 16 - y * PX, fz = 8 - th / 2, tz = 8 + th / 2;
                String uv = n(x0 * PX) + "," + n(y * PX) + "," + n(x * PX) + "," + n((y + 1) * PX);
                String uvBack = n(x * PX) + "," + n(y * PX) + "," + n(x0 * PX) + "," + n((y + 1) * PX);
                String left = n(x0 * PX) + "," + n(y * PX) + "," + n((x0 + 1) * PX) + "," + n((y + 1) * PX);
                String right = n((x - 1) * PX) + "," + n(y * PX) + "," + n(x * PX) + "," + n((y + 1) * PX);
                if (e.length() > 0) e.append(',');
                e.append("{\"from\":[").append(n(fx)).append(',').append(n(fy)).append(',').append(n(fz)).append("],\"to\":[")
                        .append(n(tx)).append(',').append(n(ty)).append(',').append(n(tz)).append("],\"faces\":{")
                        .append("\"south\":{\"uv\":[").append(uv).append("],\"texture\":\"#art\"},")
                        .append("\"north\":{\"uv\":[").append(uvBack).append("],\"texture\":\"#art\"},")
                        .append("\"up\":{\"uv\":[").append(uv).append("],\"texture\":\"#art\"},")
                        .append("\"down\":{\"uv\":[").append(uv).append("],\"texture\":\"#art\"},")
                        .append("\"west\":{\"uv\":[").append(left).append("],\"texture\":\"#art\"},")
                        .append("\"east\":{\"uv\":[").append(right).append("],\"texture\":\"#art\"}}}");
            }
        }
        double cz = 8 + maxT / 2 + 0.05;   // 카드 판: 앞면만
        e.append(",{\"from\":[0,0,").append(n(cz)).append("],\"to\":[16,16,").append(n(cz)).append("],\"faces\":{\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#card\"}}}");
        return "{\"textures\":{\"art\":\"" + art + "\",\"card\":\"" + card + "\",\"particle\":\"" + card + "\"},\"gui_light\":\"front\",\"elements\":[" + e
                + "],\"display\":" + display(weapon) + "}";
    }

    // ------------------------------------------------------------------ 표시 변환
    /** 바닐라 손 변환에 '모델 뒤집기'를 합친 회전 (도) */
    static String display(boolean weapon) {
        return display(weapon, 1);
    }

    /** @param grow 모델을 줄여 16 칸에 넣었을 때 손에서는 다시 키우는 배율 */
    static String display(boolean weapon, double grow) {
        double[][] flip = weapon
                ? new double[][]{{0, 1, 0}, {1, 0, 0}, {0, 0, -1}}     // 무기 축(대각선) 둘레 180°: 칼끝 방향은 그대로, 앞뒤만 바뀐다
                : new double[][]{{-1, 0, 0}, {0, 1, 0}, {0, 0, -1}};   // 세로축 둘레 180°
        String tpR = hand(weapon ? new double[]{0, -90, 55} : new double[]{0, 0, 0}, flip, weapon ? new double[]{0, 4, 0.5} : new double[]{0, 3, 1}, (weapon ? 0.85 : 0.55) * grow);
        String tpL = hand(weapon ? new double[]{0, 90, -55} : new double[]{0, 0, 0}, flip, weapon ? new double[]{0, 4, 0.5} : new double[]{0, 3, 1}, (weapon ? 0.85 : 0.55) * grow);
        String fpR = hand(new double[]{0, -90, 25}, flip, new double[]{1.13, 3.2, 1.13}, 0.68 * grow);
        String fpL = hand(new double[]{0, 90, -25}, flip, new double[]{1.13, 3.2, 1.13}, 0.68 * grow);
        return "{\"thirdperson_righthand\":" + tpR + ",\"thirdperson_lefthand\":" + tpL + ",\"firstperson_righthand\":" + fpR + ",\"firstperson_lefthand\":" + fpL
                + ",\"gui\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}"
                + ",\"ground\":{\"rotation\":[0,0,0],\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]}"
                + ",\"fixed\":{\"rotation\":[0,180,0],\"translation\":[0,0,0],\"scale\":[1,1,1]}"
                + ",\"head\":{\"rotation\":[0,180,0],\"translation\":[0,13,7],\"scale\":[1,1,1]}}";
    }

    static String hand(double[] deg, double[][] flip, double[] tr, double sc) {
        double[] r = euler(mul(rxyz(deg), flip));
        return "{\"rotation\":[" + n(r[0]) + "," + n(r[1]) + "," + n(r[2]) + "],\"translation\":[" + n(tr[0]) + "," + n(tr[1]) + "," + n(tr[2])
                + "],\"scale\":[" + n(sc) + "," + n(sc) + "," + n(sc) + "]}";
    }

    /** Minecraft 표시 회전: Quaternionf.rotationXYZ = Rx · Ry · Rz */
    static double[][] rxyz(double[] deg) {
        double x = Math.toRadians(deg[0]), y = Math.toRadians(deg[1]), z = Math.toRadians(deg[2]);
        double[][] rx = {{1, 0, 0}, {0, Math.cos(x), -Math.sin(x)}, {0, Math.sin(x), Math.cos(x)}};
        double[][] ry = {{Math.cos(y), 0, Math.sin(y)}, {0, 1, 0}, {-Math.sin(y), 0, Math.cos(y)}};
        double[][] rz = {{Math.cos(z), -Math.sin(z), 0}, {Math.sin(z), Math.cos(z), 0}, {0, 0, 1}};
        return mul(mul(rx, ry), rz);
    }

    static double[][] mul(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) c[i][j] += a[i][k] * b[k][j];
        return c;
    }

    /** R = Rx · Ry · Rz 를 각도로 (도) */
    static double[] euler(double[][] r) {
        double y = Math.asin(Math.max(-1, Math.min(1, r[0][2])));
        double x, z;
        if (Math.abs(r[0][2]) < 0.9999) {
            x = Math.atan2(-r[1][2], r[2][2]);
            z = Math.atan2(-r[0][1], r[0][0]);
        } else {   // 짐벌 잠금
            x = Math.atan2(r[2][1], r[1][1]);
            z = 0;
        }
        return new double[]{round(Math.toDegrees(x)), round(Math.toDegrees(y)), round(Math.toDegrees(z))};
    }

    private static double round(double d) {
        return Math.round(d * 1000) / 1000.0;
    }
}
