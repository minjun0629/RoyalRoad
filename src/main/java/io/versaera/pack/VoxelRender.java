package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * 큐브 무기 모델을 그대로 렌더링해 인벤토리 카드 그림을 만든다 — 한국 MMORPG 아이콘처럼 입체 모델을 찍은 그림이고,
 * 손에 든 모델과 생김새가 똑같다. 4 배로 크게 그려 줄이고 (가장자리 부드럽게), 면마다 빛 방향 밝기 · 모서리 어둡게 · 짙은 외곽선.
 */
public final class VoxelRender {
    private VoxelRender() {
    }

    static final int SS = 4;
    private static final double[][] NORMALS = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    /** 모델 좌표 → 화면 (z 축 −45° 로 눕힌 뒤 살짝 돌려 본다). 반환: {x, y, 깊이} */
    private static double[] view(double x, double y, double z, double ay, double ax) {
        double c = Math.cos(Math.toRadians(-45)), s = Math.sin(Math.toRadians(-45));
        double px = x - 8, py = y - 8, pz = z - 8;
        double qx = px * c - py * s, qy = px * s + py * c;
        double rx = qx * Math.cos(ay) + pz * Math.sin(ay), rz = -qx * Math.sin(ay) + pz * Math.cos(ay);
        double ry = qy * Math.cos(ax) - rz * Math.sin(ax), rz2 = qy * Math.sin(ax) + rz * Math.cos(ax);
        return new double[]{rx, ry, rz2};
    }

    public static BufferedImage icon(VoxelSmith.Build b, int size) {
        return render(b.boxes, size, Math.toRadians(-24), Math.toRadians(16));
    }

    static BufferedImage render(List<VoxelSmith.Box> boxes, int size, double ay, double ax) {
        VoxelPaint.Painted paint = VoxelPaint.paint(boxes);
        // 화면에 꽉 차게 배율
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (VoxelSmith.Box bx : boxes)
            for (double x : new double[]{bx.x0(), bx.x1()})
                for (double y : new double[]{bx.y0(), bx.y1()})
                    for (double z : new double[]{bx.z0(), bx.z1()}) {
                        double[] v = view(x, y, z, ay, ax);
                        minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]); minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
                    }
        int big = size * SS;
        double pad = 3.0 * SS, scale = Math.min((big - 2 * pad) / (maxX - minX), (big - 2 * pad) / (maxY - minY));
        double offX = big / 2.0 - (minX + maxX) / 2 * scale, offY = big / 2.0 + (minY + maxY) / 2 * scale;
        int[] rgb = new int[big * big];
        double[] zbuf = new double[big * big];
        Arrays.fill(zbuf, -1e9);
        double[] light = norm(new double[]{-0.45, 0.7, 0.55});
        double step = 0.5 / scale;
        for (int bi = 0; bi < boxes.size(); bi++) {
            VoxelSmith.Box bx = boxes.get(bi);
            double[] f = {bx.x0(), bx.y0(), bx.z0()}, t = {bx.x1(), bx.y1(), bx.z1()};
            for (int fi = 0; fi < 6; fi++) {
                double[] n = NORMALS[fi];
                int axis = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2, a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
                double[] o = view(8 + n[0], 8 + n[1], 8 + n[2], ay, ax), c0 = view(8, 8, 8, ay, ax);
                double[] vn = {o[0] - c0[0], o[1] - c0[1], o[2] - c0[2]};
                if (vn[2] < -1e-6) continue;   // 뒤를 보는 면
                double diff = Math.max(0, vn[0] * light[0] + vn[1] * light[1] + vn[2] * light[2]);
                double lit = 0.66 + 0.55 * diff + (vn[1] > 0.5 ? 0.06 : 0);
                double spec = Math.pow(Math.max(0, vn[0] * light[0] + vn[1] * light[1] + vn[2] * light[2]), 12) * 0.35;
                double fixed = (n[axis] > 0 ? t : f)[axis];
                double l1 = t[a1] - f[a1], l2 = t[a2] - f[a2];
                double edge = Math.min(l1, l2) > 0.9 ? 0.1 : 0;   // 큰 면만 모서리를 어둡게 (잘게 쌓은 칼날이 줄무늬로 보이지 않게)
                for (double u = f[a1]; u <= t[a1] + 1e-9; u += step)
                    for (double v = f[a2]; v <= t[a2] + 1e-9; v += step) {
                        double[] p = new double[3];
                        p[axis] = fixed; p[a1] = Math.min(u, t[a1]); p[a2] = Math.min(v, t[a2]);
                        double[] q = view(p[0], p[1], p[2], ay, ax);
                        int sx = (int) Math.round(offX + q[0] * scale), sy = (int) Math.round(offY - q[1] * scale);
                        if (sx < 0 || sy < 0 || sx >= big || sy >= big) continue;
                        int k = sy * big + sx;
                        if (q[2] + 1e-4 < zbuf[k]) continue;
                        zbuf[k] = q[2];
                        boolean rim = p[a1] - f[a1] < edge || t[a1] - p[a1] < edge || p[a2] - f[a2] < edge || t[a2] - p[a2] < edge;
                        double m = lit * (rim ? 0.94 : 1);
                        int tc = VoxelPaint.sample(paint, bi, fi, bx, p[0], p[1], p[2]);
                        int r = clamp(((tc >> 16) & 255) * m + 255 * spec), g = clamp(((tc >> 8) & 255) * m + 255 * spec), bl = clamp((tc & 255) * m + 255 * spec);
                        rgb[k] = 0xff000000 | (r << 16) | (g << 8) | bl;
                    }
            }
        }
        // 4 × 4 를 한 칸으로 (알파 평균)
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int a = 0, r = 0, g = 0, bl = 0;
                for (int yy = 0; yy < SS; yy++)
                    for (int xx = 0; xx < SS; xx++) {
                        int c = rgb[(y * SS + yy) * big + x * SS + xx];
                        if ((c >>> 24) == 0) continue;
                        a++; r += (c >> 16) & 255; g += (c >> 8) & 255; bl += c & 255;
                    }
                if (a == 0) continue;
                out.setRGB(x, y, ((a * 255 / (SS * SS)) << 24) | ((r / a) << 16) | ((g / a) << 8) | (bl / a));
            }
        return outline(out);
    }

    /** 바깥 1 칸 짙은 외곽선 (반투명 가장자리 아래에 깔린다) */
    static BufferedImage outline(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int best = 0;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h) best = Math.max(best, img.getRGB(nx, ny) >>> 24);
                    }
                int base = best == 0 ? 0 : (Math.min(255, best * 3 / 2) << 24) | 0x0a0a10;
                int c = img.getRGB(x, y), a = c >>> 24;
                if (a == 0) { out.setRGB(x, y, base); continue; }
                int ba = base >>> 24;
                int r = (((c >> 16) & 255) * a + 10 * (255 - a)) / 255, g = (((c >> 8) & 255) * a + 10 * (255 - a)) / 255, bl = ((c & 255) * a + 16 * (255 - a)) / 255;
                out.setRGB(x, y, (Math.max(a, ba) << 24) | (r << 16) | (g << 8) | bl);
            }
        return out;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
