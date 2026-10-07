package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.SplittableRandom;

/**
 * 픽셀 아트 캔버스 (RP-01). 모양을 '층'으로 그리고 층마다 자동 음영을 넣는다:
 * <ul>
 *   <li>색 사다리(ramp): 바탕색에서 어두운 쪽은 푸르게 · 밝은 쪽은 노랗게 색상을 비튼 5단계 (손으로 칠한 픽셀 아트의 기본 기법)</li>
 *   <li>빛은 왼쪽 위에서 — 층 모양의 왼쪽 위 가장자리는 밝게, 오른쪽 아래는 어둡게, 안쪽은 가장자리에서 멀수록 중간</li>
 *   <li>외곽선은 검정이 아니라 그 자리 색의 가장 어두운 단계 (selective outline)</li>
 * </ul>
 */
public final class Canvas {
    public final int w, h;
    private final int[] px;
    final SplittableRandom rng;

    public Canvas(int w, int h, String seed) {
        this.w = w;
        this.h = h;
        px = new int[w * h];
        rng = new SplittableRandom(seed.hashCode());
    }

    // ------------------------------------------------------------------ 색 사다리
    /** 0 가장 어두움 · 2 바탕 · 4 가장 밝음 */
    public static Color[] ramp(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        Color[] r = new Color[5];
        float[] dv = {-0.42f, -0.2f, 0, 0.16f, 0.3f}, ds = {0.12f, 0.06f, 0, -0.08f, -0.18f}, dh = {0.045f, 0.022f, 0, -0.018f, -0.035f};
        for (int i = 0; i < 5; i++) {
            float hue = hsb[0], sat = hsb[1] < 0.06f ? hsb[1] : clamp(hsb[1] + ds[i]);
            // 어두운 쪽은 파랑(0.66)으로, 밝은 쪽은 노랑(0.15)으로 조금 끌어당긴다
            if (hsb[1] >= 0.06f) hue = towards(hue, dh[i] > 0 ? 0.66f : 0.15f, Math.abs(dh[i]));
            r[i] = Color.getHSBColor(hue, sat, clamp(hsb[2] + dv[i]));
        }
        return r;
    }

    private static float towards(float h, float target, float amt) {
        float d = target - h;
        if (d > 0.5f) d -= 1;
        if (d < -0.5f) d += 1;
        float out = h + Math.signum(d) * Math.min(Math.abs(d), amt);
        return out < 0 ? out + 1 : out % 1f;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ------------------------------------------------------------------ 층 (모양 마스크)
    /** 모양을 그리고 commit(ramp) 하면 음영을 넣어 캔버스에 얹는다 */
    public final class Layer {
        final boolean[] m = new boolean[w * h];
        /** 칠하지 않고 남길 무늬 (층 위에 다시 그릴 강조 픽셀) */
        final int[] detail = new int[w * h];

        public Layer px(int x, int y) {
            if (x >= 0 && y >= 0 && x < w && y < h) m[y * w + x] = true;
            return this;
        }

        public Layer rect(int x, int y, int rw, int rh) {
            for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) px(i, j);
            return this;
        }

        public Layer ellipse(double cx, double cy, double rx, double ry) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                    if (dx * dx + dy * dy <= 1) px(x, y);
                }
            return this;
        }

        /** 두께 있는 선 (끝은 둥글게) */
        public Layer line(double x0, double y0, double x1, double y1, double width) {
            double len = Math.hypot(x1 - x0, y1 - y0);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((x + 0.5 - x0) * (x1 - x0) + (y + 0.5 - y0) * (y1 - y0)) / (len * len)));
                    double qx = x0 + t * (x1 - x0), qy = y0 + t * (y1 - y0);
                    if (Math.hypot(x + 0.5 - qx, y + 0.5 - qy) <= width / 2) px(x, y);
                }
            return this;
        }

        public Layer poly(double[] xs, double[] ys) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    boolean in = false;
                    for (int i = 0, j = xs.length - 1; i < xs.length; j = i++)
                        if ((ys[i] > y + 0.5) != (ys[j] > y + 0.5) && x + 0.5 < (xs[j] - xs[i]) * (y + 0.5 - ys[i]) / (ys[j] - ys[i]) + xs[i]) in = !in;
                    if (in) px(x, y);
                }
            return this;
        }

        public Layer cut(int x, int y) {
            if (x >= 0 && y >= 0 && x < w && y < h) m[y * w + x] = false;
            return this;
        }

        public Layer cutRect(int x, int y, int rw, int rh) {
            for (int j = y; j < y + rh; j++) for (int i = x; i < x + rw; i++) cut(i, j);
            return this;
        }

        private boolean in(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && m[y * w + x];
        }

        /**
         * 음영을 넣어 얹는다.
         * @param noise 결 (0 = 매끈한 쇠, 1 = 거친 돌 · 가죽)
         */
        public void commit(Color[] r, double noise) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    if (!in(x, y)) continue;
                    int idx = 2;
                    boolean lt = !in(x - 1, y) || !in(x, y - 1), rb = !in(x + 1, y) || !in(x, y + 1);
                    boolean lt2 = !in(x - 2, y) || !in(x, y - 2), rb2 = !in(x + 2, y) || !in(x, y + 2);
                    if (lt && !rb) idx = 4;
                    else if (rb && !lt) idx = 1;
                    else if (lt2 && !rb2) idx = 3;
                    else if (rb2 && !lt2) idx = 2;
                    if (noise > 0 && rng.nextDouble() < noise * 0.35) idx = Math.max(1, Math.min(4, idx + (rng.nextBoolean() ? 1 : -1)));
                    px[y * w + x] = r[idx].getRGB();
                }
        }
    }

    public Layer layer() {
        return new Layer();
    }

    // ------------------------------------------------------------------ 직접 찍기
    public void set(int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c.getRGB();
    }

    public int get(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h ? px[y * w + x] : 0;
    }

    public boolean filled(int x, int y) {
        return (get(x, y) >>> 24) != 0;
    }

    /** 반짝임 (십자 빛) */
    public void sparkle(int x, int y, Color c) {
        set(x, y, Color.WHITE);
        set(x - 1, y, c); set(x + 1, y, c); set(x, y - 1, c); set(x, y + 1, c);
    }

    /** 외곽선: 바깥 빈칸 중 이웃이 칠해진 곳을 그 이웃 색의 가장 어두운 단계로 */
    public BufferedImage image(boolean outline) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int a = px[y * w + x];
                if ((a >>> 24) != 0) { img.setRGB(x, y, a); continue; }
                if (!outline) continue;
                int nb = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int v = get(x + d[0], y + d[1]);
                    if ((v >>> 24) != 0) { nb = v; break; }
                }
                if (nb != 0) {
                    Color c = new Color(nb, true);
                    img.setRGB(x, y, new Color(c.getRed() / 4 + 8, c.getGreen() / 4 + 6, c.getBlue() / 4 + 12).getRGB());
                }
            }
        return img;
    }
}
