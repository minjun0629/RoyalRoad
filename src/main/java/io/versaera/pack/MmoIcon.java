package io.versaera.pack;

import io.versaera.domain.item.ItemType;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * 장비 · 장신구 · 성물 아이콘을 한국 MMORPG 아이템 아이콘처럼 그린다 (RP-01) — 64 × 64.
 * <ul>
 *   <li>금속은 원통 · 날 단면을 따라 어두움 → 바탕 → 빛 → 바탕 → 어두움 띠 (광택), 칼날은 가운데 능선을 경계로 한쪽을 어둡게</li>
 *   <li>코등이 · 테두리 장식은 둘째 금속(금 · 놋쇠), 보석은 둥근 빛 + 면 + 하이라이트, 손잡이는 가죽 감기</li>
 *   <li>짙은 1 칸 외곽선, 바깥에 등급 빛 (일반 없음 · 마법 초록 · 희귀 파랑 · 고유 보라 · 전설 주황 · 저주 붉음)</li>
 *   <li>틀(칸 배경)은 그리지 않는다 — Minecraft 1.20.1 은 손에 든 모습과 인벤토리 그림이 같아서, 틀이 손에도 보이게 된다</li>
 * </ul>
 * 색은 원작 아이템 설명에서 고른 것 ({@link IconSmith#LOOKS}).
 */
public final class MmoIcon {
    private MmoIcon() {
    }

    static final int S = 64;

    public static boolean handles(String kind) {
        return IconSmith.handles(kind);
    }

    // ------------------------------------------------------------------ 등급
    static Color grade(ItemType t) {
        var s = t.stats();
        if (t.hasTag("practice")) return null;
        if (t.hasTag("cursed") || s.getOrDefault("drain", 0) > 0) return new Color(0xd02828);
        int atk = s.getOrDefault("attack", 0), def = s.getOrDefault("defense", 0);
        int fame = t.requires().getOrDefault("fame", 0);
        if (fame >= 1000 || atk >= 44 || def >= 24) return new Color(0xff9a2e);
        if (atk >= 30 || def >= 18 || fame >= 500) return new Color(0xb766ff);
        if (atk >= 18 || def >= 12 || s.size() >= 4) return new Color(0x4aa8ff);
        if (s.size() >= 2 || "CANON".equals(t.source()) && t.category().unique()) return new Color(0x5ccf5c);
        return null;
    }

    // ------------------------------------------------------------------ 칠하기 도구
    static Color[] T(Color c) {
        return IconSmith.tones(c);
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** 가로로 가로지르는 금속 광택 (x0 → x1) */
    static Paint metalX(double x0, double x1, Color base) {
        Color[] t = T(base);
        return new LinearGradientPaint((float) x0, 0, (float) x1 + 0.01f, 0, new float[]{0f, 0.2f, 0.38f, 0.48f, 0.62f, 0.85f, 1f},
                new Color[]{t[1], t[2], t[3], t[4], t[2], t[1], t[0]});
    }

    /** 세로로 가로지르는 금속 광택 (y0 → y1) */
    static Paint metalY(double y0, double y1, Color base) {
        Color[] t = T(base);
        return new LinearGradientPaint(0, (float) y0, 0, (float) y1 + 0.01f, new float[]{0f, 0.18f, 0.35f, 0.55f, 0.85f, 1f},
                new Color[]{t[3], t[4], t[3], t[2], t[1], t[0]});
    }

    /** 천 · 가죽: 위에서 빛, 아래로 어둡게 */
    static Paint soft(double y0, double y1, Color base) {
        Color[] t = T(base);
        return new LinearGradientPaint(0, (float) y0, 0, (float) y1 + 0.01f, new float[]{0f, 0.4f, 1f}, new Color[]{t[3], t[2], t[1]});
    }

    static void fill(Graphics2D g, Shape s, Paint p) {
        g.setPaint(p);
        g.fill(s);
    }

    static void line(Graphics2D g, Shape s, Color c, double w) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    static void seg(Graphics2D g, double x0, double y0, double x1, double y1, Color c, double w) {
        line(g, new Line2D.Double(x0, y0, x1, y1), c, w);
    }

    /** 테두리 안쪽에 얇은 빛 (모서리 광) */
    static void rim(Graphics2D g, Shape s, Color c) {
        Shape old = g.getClip();
        g.clip(s);
        line(g, s, alpha(c, 150), 1.6);
        g.setClip(old);
    }

    static void gem(Graphics2D g, double cx, double cy, double r, Color c) {
        Color[] t = T(c);
        Ellipse2D e = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        fill(g, e, new RadialGradientPaint(new Point2D.Double(cx - r * 0.3, cy - r * 0.35), (float) (r * 1.25f), new float[]{0f, 0.35f, 0.75f, 1f},
                new Color[]{t[4], t[3], t[2], t[1]}));
        line(g, e, t[0], 0.9);
        if (r >= 2.5) {   // 면
            seg(g, cx - r * 0.6, cy, cx + r * 0.6, cy, alpha(t[1], 120), 0.6);
            seg(g, cx, cy - r * 0.6, cx, cy + r * 0.6, alpha(t[1], 90), 0.6);
        }
        fill(g, new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.6, r * 0.5, r * 0.4), new Color(255, 255, 255, 220));
    }

    /** 대각선으로 눕히고 64 칸 안에 들어오게 줄인다 (날 끝 · 머리가 잘리지 않게) */
    static void rot45(Graphics2D g) {
        g.translate(32, 32);
        g.rotate(Math.PI / 4);
        g.scale(0.84, 0.84);
    }

    /** 그린 결과: 외곽선까지 넣은 그림(빛 없음) · 색 · 등급 빛 */
    public record Drawn(BufferedImage art, IconSmith.Look look, Color grade) {
    }

    // ------------------------------------------------------------------ 진입
    public static BufferedImage draw(ItemType t, String kind) {
        Drawn d = render(t, kind);
        return d == null ? null : finish(d.art(), d.grade());
    }

    /** 외곽선만 넣고 등급 빛은 넣지 않은 그림 (입체 모델 · 카드용) */
    public static Drawn render(ItemType t, String kind) {
        IconSmith.Look l = IconSmith.lookOf(t, kind);
        BufferedImage layer = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = layer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        boolean broad = t.hasTag("broad") || t.stats().getOrDefault("attack", 0) >= 44;
        boolean gemmed = IconSmith.LOOKS.containsKey(t.id()) && IconSmith.LOOKS.get(t.id())[3] != 0 || IconSmith.accent(t) != null;
        AffineTransform base = g.getTransform();
        switch (kind) {
            case "sword" -> sword(g, l, broad ? 6.2 : 4.6, -44, t.hasTag("jagged"), t.hasTag("curved"), gemmed, t.hasTag("dragonbone"), 15, t.hasTag("long"));
            case "dagger" -> sword(g, l, 4.6, -22, false, false, gemmed, false, 11, false);
            case "knife" -> knife(g, l);
            case "axe" -> axe(g, l, broad, t.id().equals("dragon_slaying_axe"), t.hasTag("rust"));
            case "spear" -> spear(g, l, t.hasTag("trident"), gemmed);
            case "staff" -> staff(g, l, t.hasTag("crook"));
            case "torch" -> torch(g, l);
            case "hammer", "mace" -> mace(g, l, t.hasTag("stone_head"));
            case "pickaxe", "pickaxe_weapon" -> pick(g, l);
            case "scythe" -> scythe(g, l);
            case "rake" -> rake(g, l);
            case "plow" -> plow(g, l);
            case "bow" -> bow(g, l, t.hasTag("elven"));
            case "whip" -> whip(g, l);
            case "harp" -> harp(g);
            case "fan" -> fan(g);
            case "watering_can" -> can(g);
            case "hammer_chisel" -> chisel(g);
            case "arrow" -> arrow(g);
            case "chest" -> { if (t.hasTag("robe")) robe(g, l); else chest(g, l, t.material().startsWith("LEATHER") || t.hasTag("fur")); }
            case "legs" -> legs(g, l);
            case "boots" -> boots(g, l, t.hasTag("winged"), t.material().startsWith("LEATHER"));
            case "helmet" -> { if (t.hasTag("hat")) hat(g, l); else helm(g, l, t.hasTag("horned")); }
            case "crown" -> crown(g, l);
            case "shield" -> shield(g, l);
            case "ring" -> ring(g, l);
            case "necklace" -> necklace(g, l);
            case "bracelet" -> bracelet(g, l, t.hasTag("flower"));
            case "gloves" -> gloves(g, l);
            case "cloak" -> cloak(g, l);
            case "belt" -> belt(g, l);
            case "pauldron" -> pauldron(g, l);
            case "orb" -> orb(g, l);
            case "book" -> book(g, t.id());
            case "cup" -> cup(g, t.material().equals("PAPER"));
            case "mirror" -> mirror(g, t.id().startsWith("yuskelanta"));
            case "map" -> map(g);
            case "horn" -> horn(g, t.id().contains("black"));
            case "key" -> key(g, t.id().startsWith("star"));
            case "flag" -> flag(g);
            case "seal" -> seal(g, t.id().startsWith("ruler"));
            case "crest" -> crest(g);
            case "skull" -> skull(g);
            case "plate" -> plate(g);
            case "compass" -> compass(g);
            case "furnace" -> furnace(g);
            case "feather" -> feather(g);
            default -> { g.dispose(); return null; }
        }
        g.setTransform(base);
        g.dispose();
        return new Drawn(finish(layer, null), l, grade(t));
    }

    /** 짙은 외곽선 1 칸 + 등급 빛 3 칸 */
    static BufferedImage finish(BufferedImage layer, Color glow) {
        boolean[] in = new boolean[S * S];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) in[y * S + x] = (layer.getRGB(x, y) >>> 24) > 60;
        int[] dist = new int[S * S];
        java.util.Arrays.fill(dist, 99);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                if (in[y * S + x]) { dist[y * S + x] = 0; continue; }
                for (int dy = -4; dy <= 4; dy++)
                    for (int dx = -4; dx <= 4; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= S || ny >= S || !in[ny * S + nx]) continue;
                        int d = Math.max(Math.abs(dx), Math.abs(dy));
                        if (d < dist[y * S + x]) dist[y * S + x] = d;
                    }
            }
        BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int d = dist[y * S + x];
                int under = 0;
                if (d == 1) under = 0xff120e0c;
                else if (glow != null && d >= 2 && d <= 4) {
                    int a = d == 2 ? 95 : d == 3 ? 45 : 16;   // 은은한 등급 빛 (스티커 테두리처럼 보이지 않게)
                    under = (a << 24) | (glow.getRGB() & 0xffffff);
                }
                int top = layer.getRGB(x, y);
                out.setRGB(x, y, over(top, under));
            }
        return out;
    }

    private static int over(int top, int under) {
        int ta = top >>> 24, ua = under >>> 24;
        if (ta == 255 || ua == 0) return ta == 0 ? under : top;
        if (ta == 0) return under;
        double a = ta / 255.0, b = ua / 255.0 * (1 - a), oa = a + b;
        int r = (int) Math.round((((top >> 16) & 255) * a + ((under >> 16) & 255) * b) / oa);
        int gg = (int) Math.round((((top >> 8) & 255) * a + ((under >> 8) & 255) * b) / oa);
        int bb = (int) Math.round(((top & 255) * a + (under & 255) * b) / oa);
        return ((int) Math.round(oa * 255) << 24) | (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------------ 무기 (손잡이 왼쪽 아래 → 끝 오른쪽 위)
    static void sword(Graphics2D g, IconSmith.Look l, double hw, double tip, boolean jag, boolean curved, boolean gem, boolean bone, double gripLen, boolean longBlade) {
        rot45(g);
        double gy = 10 - (gripLen - 11) * 0.5;   // 코등이 위치
        if (longBlade) tip -= 2;
        // 날
        Path2D b = new Path2D.Double();
        b.moveTo(-hw, gy);
        if (curved) b.curveTo(-hw - 1, gy - 18, -hw + 2, tip + 14, hw * 0.6, tip);
        else { b.lineTo(-hw, tip + hw * 1.7); b.lineTo(0, tip); }
        if (jag) {
            double y = tip + hw * 1.7;
            b.lineTo(hw, y);
            for (double yy = y + 4; yy < gy - 2; yy += 5) { b.lineTo(hw + 2.4, yy - 2); b.lineTo(hw, yy); }
            b.lineTo(hw, gy);
        } else if (curved) b.curveTo(hw + 3, tip + 16, hw + 1, gy - 16, hw, gy);
        else { b.lineTo(hw, tip + hw * 1.7); b.lineTo(hw, gy); }
        b.closePath();
        Color[] p = T(l.p());
        fill(g, b, metalX(-hw - 1, hw + 1, l.p()));
        Shape clip = g.getClip();
        g.clip(b);
        fill(g, new Rectangle2D.Double(0.3, tip - 2, hw + 4, gy - tip + 4), alpha(p[0], 70));      // 그늘진 반쪽
        seg(g, 0, gy - 1, 0, tip + 3, alpha(p[4], 230), 0.9);                                       // 능선
        seg(g, -hw + 0.9, gy - 1, -hw + 0.9, tip + hw * 1.7, alpha(p[4], 180), 0.9);                 // 날 빛
        if (!bone) seg(g, 0, gy - 3, 0, gy - (gy - tip) * 0.45, alpha(p[0], 200), 1.6);            // 피 홈
        else for (double y = gy - 4; y > tip + 8; y -= 6) fill(g, new Ellipse2D.Double(-hw * 0.6, y, 2.2, 1.6), alpha(p[1], 200));   // 뼈 결
        if (gem && !l.a().equals(IconSmith.RUBY)) {                                                   // 속성 룬
            Color[] a = T(l.a());
            for (double y = gy - 6; y > tip + 10; y -= 7) fill(g, new Ellipse2D.Double(-0.9, y, 1.8, 2.6), alpha(a[4], 220));
        }
        g.setClip(clip);
        line(g, b, alpha(p[0], 200), 0.8);
        // 코등이
        Color[] gd = T(l.g());
        RoundRectangle2D guard = new RoundRectangle2D.Double(-hw - 7, gy, hw * 2 + 14, 5, 3, 3);
        fill(g, guard, metalY(gy, gy + 5, l.g()));
        rim(g, guard, gd[4]);
        line(g, guard, gd[0], 0.8);
        for (int sgn : new int[]{-1, 1}) {
            Ellipse2D end = new Ellipse2D.Double(sgn * (hw + 7) - 2.6, gy - 0.1, 5.2, 5.2);
            fill(g, end, metalY(gy, gy + 5, l.g()));
            line(g, end, gd[0], 0.8);
        }
        if (gem) gem(g, 0, gy + 2.5, 2.6, l.a());
        // 손잡이 (가죽 감기)
        Rectangle2D grip = new Rectangle2D.Double(-2.6, gy + 5, 5.2, gripLen);
        fill(g, grip, metalX(-2.6, 2.6, l.s()));
        Color[] s = T(l.s());
        for (double y = gy + 6.5; y < gy + 5 + gripLen; y += 2.6) seg(g, -2.6, y, 2.6, y + 1.2, alpha(s[0], 200), 0.8);
        line(g, grip, s[0], 0.7);
        // 폼멜
        Ellipse2D pom = new Ellipse2D.Double(-4, gy + 5 + gripLen - 0.5, 8, 8);
        fill(g, pom, metalY(gy + 5 + gripLen, gy + 13 + gripLen, l.g()));
        line(g, pom, gd[0], 0.8);
        if (gem) gem(g, 0, gy + 9 + gripLen - 0.5, 1.8, l.a());
    }

    static void knife(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        Path2D b = new Path2D.Double();
        b.moveTo(-3.2, 2);
        b.lineTo(-3.2, -16);
        b.quadTo(-3, -24, 1.5, -27);
        b.lineTo(3.2, -18);
        b.lineTo(3.2, 2);
        b.closePath();
        fill(g, b, metalX(-3.6, 3.6, l.p()));
        Color[] p = T(l.p());
        seg(g, -2.3, 0, -2.3, -18, alpha(p[4], 200), 0.8);
        line(g, b, alpha(p[0], 220), 0.8);
        fill(g, new RoundRectangle2D.Double(-4.5, 2, 9, 3, 2, 2), metalY(2, 5, l.g()));
        RoundRectangle2D h = new RoundRectangle2D.Double(-3, 5, 6, 24, 4, 4);
        fill(g, h, metalX(-3, 3, l.s()));
        Color[] s = T(l.s());
        for (double y = 9; y < 28; y += 4) seg(g, -2.5, y, 2.5, y, alpha(s[1], 160), 0.6);
        line(g, h, s[0], 0.8);
        fill(g, new Ellipse2D.Double(-3.2, 27, 6.4, 4), metalY(27, 31, l.g()));
    }

    /** 나무 자루 (나뭇결 · 금속 띠) */
    static void haft(Graphics2D g, IconSmith.Look l, double y0, double y1, double w) {
        Rectangle2D h = new Rectangle2D.Double(-w / 2, y0, w, y1 - y0);
        fill(g, h, metalX(-w / 2, w / 2, l.s()));
        Color[] s = T(l.s());
        for (double y = y0 + 3; y < y1 - 2; y += 7) seg(g, -w / 2 + 0.6, y, -w / 2 + 0.6, y + 3, alpha(s[1], 170), 0.6);
        line(g, h, s[0], 0.7);
    }

    static void band(Graphics2D g, double y, double w, Color c) {
        RoundRectangle2D r = new RoundRectangle2D.Double(-w / 2, y, w, 3, 1.5, 1.5);
        fill(g, r, metalY(y, y + 3, c));
        line(g, r, T(c)[0], 0.6);
    }

    static void axe(Graphics2D g, IconSmith.Look l, boolean broad, boolean twin, boolean rust) {
        rot45(g);
        haft(g, l, -38, 36, 4.6);
        band(g, 26, 6, IconSmith.GOLD);
        double reach = twin ? 17 : broad ? 21 : 18;
        Color[] p = T(l.p());
        for (int side : twin ? new int[]{-1, 1} : new int[]{-1}) {
            Path2D h = new Path2D.Double();
            h.moveTo(side * 2.4, -33);
            h.lineTo(side * 9, -37);
            h.quadTo(side * (reach + 2), -40, side * reach, -31);
            h.quadTo(side * (reach + 4), -18, side * reach, -6);
            h.quadTo(side * (reach - 4), -1, side * 9, -12);
            h.lineTo(side * 2.4, -16);
            h.closePath();
            fill(g, h, side < 0 ? metalX(-reach - 2, -2, l.p()) : metalX(2, reach + 2, l.p()));
            // 날 끝 빛 (벼린 자리)
            Path2D edge = new Path2D.Double();
            edge.moveTo(side * (reach - 2.2), -34);
            edge.quadTo(side * (reach + 1.5), -18, side * (reach - 2.2), -7);
            line(g, edge, alpha(p[4], 230), 1.6);
            if (rust) for (int i = 0; i < 6; i++) fill(g, new Ellipse2D.Double(side * (6 + i * 2.2), -30 + (i * 7) % 15, 2.6, 2.2), alpha(new Color(0x7a4426), 200));
            line(g, h, alpha(p[0], 230), 0.9);
        }
        RoundRectangle2D collar = new RoundRectangle2D.Double(-3.6, -36, 7.2, 22, 2, 2);
        fill(g, collar, metalX(-3.6, 3.6, l.g()));
        line(g, collar, T(l.g())[0], 0.7);
        if (twin) gem(g, 0, -25, 2.4, l.a());
        else if (!l.a().equals(IconSmith.RUBY)) gem(g, 0, -25, 2, l.a());
    }

    static void spear(Graphics2D g, IconSmith.Look l, boolean trident, boolean gem) {
        rot45(g);
        haft(g, l, -22, 40, 3.8);
        band(g, -22, 6.4, l.g().equals(IconSmith.BRASS) ? IconSmith.GOLD : l.g());
        band(g, 30, 5.4, IconSmith.BRASS);
        Color[] p = T(l.p());
        if (trident) {
            Path2D h = new Path2D.Double();
            h.moveTo(-10, -22);
            h.lineTo(10, -22);
            h.lineTo(10, -28);
            h.lineTo(12, -37);
            h.lineTo(7.5, -30);
            h.lineTo(2.4, -30);
            h.lineTo(0, -44);
            h.lineTo(-2.4, -30);
            h.lineTo(-7.5, -30);
            h.lineTo(-12, -37);
            h.lineTo(-10, -28);
            h.closePath();
            fill(g, h, metalX(-12, 12, l.p()));
            seg(g, 0, -31, 0, -41, alpha(p[4], 220), 0.9);
            line(g, h, alpha(p[0], 230), 0.9);
        } else {
            Path2D h = new Path2D.Double();
            h.moveTo(0, -46);
            h.quadTo(6.5, -36, 5, -28);
            h.lineTo(2.4, -24);
            h.lineTo(-2.4, -24);
            h.lineTo(-5, -28);
            h.quadTo(-6.5, -36, 0, -46);
            fill(g, h, metalX(-6, 6, l.p()));
            Shape c = g.getClip();
            g.clip(h);
            fill(g, new Rectangle2D.Double(0.2, -48, 8, 26), alpha(p[0], 70));
            g.setClip(c);
            seg(g, 0, -27, 0, -43, alpha(p[4], 230), 0.9);
            line(g, h, alpha(p[0], 230), 0.9);
        }
        if (gem) {   // 술
            Color[] a = T(l.a());
            for (int i = -2; i <= 2; i++) seg(g, i * 0.9, -20, i * 1.6, -12, a[i == 0 ? 3 : 2], 1.2);
            gem(g, 0, -20.5, 1.8, l.a());
        }
    }

    static void staff(Graphics2D g, IconSmith.Look l, boolean crook) {
        rot45(g);
        haft(g, l, -26, 40, 4.2);
        band(g, -24, 6.4, IconSmith.GOLD);
        band(g, 8, 5.6, IconSmith.GOLD);
        if (crook) {
            Arc2D hook = new Arc2D.Double(-12, -46, 14, 16, -10, 230, Arc2D.OPEN);
            line(g, hook, T(l.s())[0], 5.6);
            line(g, hook, T(l.s())[2], 3.8);
            line(g, hook, alpha(T(l.s())[4], 160), 1);
            gem(g, -5, -24.5, 2.6, l.a());
            return;
        }
        // 발톱 받침 + 큰 보주
        Color[] gd = T(IconSmith.GOLD);
        for (int sgn : new int[]{-1, 1}) {
            Path2D claw = new Path2D.Double();
            claw.moveTo(sgn * 2, -25);
            claw.quadTo(sgn * 9, -30, sgn * 6, -40);
            line(g, claw, gd[0], 3.2);
            line(g, claw, gd[3], 1.8);
        }
        gem(g, 0, -34, 7.2, l.a());
        // 빛 고리
        line(g, new Ellipse2D.Double(-9.5, -43.5, 19, 19), alpha(T(l.a())[4], 90), 1.2);
    }

    static void torch(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        haft(g, l, -20, 38, 4.4);
        Path2D cupS = new Path2D.Double();
        cupS.moveTo(-6, -24);
        cupS.lineTo(6, -24);
        cupS.lineTo(3.2, -16);
        cupS.lineTo(-3.2, -16);
        cupS.closePath();
        fill(g, cupS, metalX(-6, 6, IconSmith.GOLD));
        line(g, cupS, T(IconSmith.GOLD)[0], 0.8);
        Color[] a = T(l.a());
        Path2D flame = new Path2D.Double();
        flame.moveTo(-6, -24);
        flame.curveTo(-9, -34, -2, -36, 0, -46);
        flame.curveTo(3, -38, 9, -34, 6, -24);
        flame.closePath();
        fill(g, flame, new RadialGradientPaint(new Point2D.Double(0, -27), 14f, new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(255, 250, 220), a[4], a[2], a[1]}));
        Path2D inner = new Path2D.Double();
        inner.moveTo(-3, -24);
        inner.curveTo(-4, -30, 0, -32, 0, -38);
        inner.curveTo(1.5, -32, 4, -30, 3, -24);
        fill(g, inner, new Color(255, 248, 210, 230));
    }

    static void mace(Graphics2D g, IconSmith.Look l, boolean stone) {
        rot45(g);
        haft(g, l, -26, 38, 4.6);
        band(g, 30, 6, IconSmith.BRASS);
        if (stone) {
            Path2D r = new Path2D.Double();
            r.moveTo(-9, -24);
            r.lineTo(-11, -34);
            r.lineTo(-4, -40);
            r.lineTo(7, -38);
            r.lineTo(11, -30);
            r.lineTo(8, -22);
            r.closePath();
            Color st = new Color(0x8a8682);
            fill(g, r, metalX(-11, 11, st));
            seg(g, -3, -36, 2, -28, alpha(T(st)[0], 200), 0.8);
            seg(g, 2, -28, 7, -31, alpha(T(st)[0], 200), 0.8);
            line(g, r, T(st)[0], 0.9);
            return;
        }
        Color[] p = T(l.p());
        for (int i = 0; i < 6; i++) {   // 날개 (flange)
            double ang = Math.PI * i / 3;
            Path2D f = new Path2D.Double();
            f.moveTo(0, -31);
            f.lineTo(Math.cos(ang) * 12, -31 + Math.sin(ang) * 6 - 4);
            f.lineTo(Math.cos(ang) * 11, -31 + Math.sin(ang) * 6 + 4);
            f.closePath();
            fill(g, f, metalX(-12, 12, l.p()));
            line(g, f, alpha(p[0], 220), 0.7);
        }
        Ellipse2D core = new Ellipse2D.Double(-7.5, -39, 15, 16);
        fill(g, core, metalX(-7.5, 7.5, l.p()));
        line(g, core, p[0], 0.9);
        for (int[] sp : new int[][]{{0, -46}, {-10, -40}, {10, -40}, {-10, -22}, {10, -22}}) {
            Path2D s = new Path2D.Double();
            s.moveTo(sp[0] * 0.55, (sp[1] + 31) * 0.55 - 31);
            s.lineTo(sp[0] - 1.6, sp[1] + 1.6);
            s.lineTo(sp[0] + 0.6, sp[1]);
            s.closePath();
            fill(g, s, p[3]);
            line(g, s, p[0], 0.6);
        }
        band(g, -24, 7, IconSmith.GOLD);
    }

    static void pick(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        haft(g, l, -30, 38, 4.4);
        Path2D h = new Path2D.Double();
        h.moveTo(-22, -24);
        h.quadTo(-12, -36, 0, -36);
        h.quadTo(12, -36, 22, -24);
        h.quadTo(12, -31, 0, -30);
        h.quadTo(-12, -31, -22, -24);
        h.closePath();
        fill(g, h, metalY(-37, -24, l.p()));
        line(g, h, T(l.p())[0], 0.9);
        RoundRectangle2D c = new RoundRectangle2D.Double(-3.8, -37, 7.6, 10, 2, 2);
        fill(g, c, metalX(-3.8, 3.8, l.g()));
        line(g, c, T(l.g())[0], 0.7);
    }

    static void scythe(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        haft(g, l, -40, 38, 4);
        band(g, -38, 6, IconSmith.GOLD);
        Path2D b = new Path2D.Double();
        b.moveTo(1, -38);
        b.curveTo(-10, -46, -28, -40, -32, -22);
        b.curveTo(-24, -32, -12, -35, 1, -32);
        b.closePath();
        fill(g, b, metalY(-46, -22, l.p()));
        Path2D edge = new Path2D.Double();
        edge.moveTo(-30, -23);
        edge.curveTo(-24, -31, -12, -34, 0, -33);
        line(g, edge, alpha(T(l.p())[4], 220), 1.2);
        line(g, b, T(l.p())[0], 0.9);
    }

    static void rake(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        haft(g, l, -26, 38, 4);
        RoundRectangle2D bar = new RoundRectangle2D.Double(-14, -30, 28, 4.2, 2, 2);
        fill(g, bar, metalY(-30, -26, l.p()));
        line(g, bar, T(l.p())[0], 0.7);
        for (int i = -2; i <= 2; i++) {
            Path2D tine = new Path2D.Double();
            tine.moveTo(i * 6.5 - 1.2, -30);
            tine.lineTo(i * 6.5 + 1.2, -30);
            tine.lineTo(i * 6.5 + 0.2, -42);
            tine.closePath();
            fill(g, tine, metalX(i * 6.5 - 1.2, i * 6.5 + 1.2, l.p()));
            line(g, tine, T(l.p())[0], 0.6);
        }
    }

    static void plow(Graphics2D g, IconSmith.Look l) {
        rot45(g);
        haft(g, l, -40, 20, 4);
        band(g, -38, 6, IconSmith.GOLD);
        Path2D blade = new Path2D.Double();
        blade.moveTo(-3, 16);
        blade.lineTo(10, 18);
        blade.quadTo(16, 30, 4, 40);
        blade.lineTo(-6, 26);
        blade.closePath();
        fill(g, blade, metalX(-6, 16, l.p()));
        line(g, blade, T(l.p())[0], 0.9);
        gem(g, 2, 25, 2.2, new Color(0x6ebe50));
    }

    static void bow(Graphics2D g, IconSmith.Look l, boolean elven) {
        rot45(g);
        Path2D limb = new Path2D.Double();
        limb.moveTo(3, 40);
        limb.curveTo(-22, 20, -22, -20, 3, -40);
        Color[] w = T(l.s());
        line(g, limb, w[0], 6.6);
        line(g, limb, w[2], 4.6);
        line(g, limb, alpha(w[4], 170), 1.4);
        if (elven) {   // 덩굴 무늬
            Path2D vine = new Path2D.Double();
            vine.moveTo(-6, 26);
            vine.curveTo(-12, 16, -14, 4, -14, -2);
            line(g, vine, alpha(T(l.a())[3], 220), 1.1);
        }
        seg(g, 3, 40, 3, -40, alpha(T(l.a())[4], 240), 0.9);   // 시위
        RoundRectangle2D grip = new RoundRectangle2D.Double(-17.5, -6, 6, 12, 2, 2);
        fill(g, grip, metalX(-17.5, -11.5, IconSmith.GRIP));
        for (double y = -4.5; y < 6; y += 2.4) seg(g, -17.5, y, -11.5, y + 1, alpha(T(IconSmith.GRIP)[0], 200), 0.6);
        line(g, grip, T(IconSmith.GRIP)[0], 0.7);
        for (int sgn : new int[]{-1, 1}) gem(g, 2.6, sgn * 39.5, 2.1, elven ? l.a() : IconSmith.BONE);
    }

    static void whip(Graphics2D g, IconSmith.Look l) {
        Color[] p = T(new Color(0x7a2a2a));
        Path2D coil = new Path2D.Double();
        coil.moveTo(14, 50);
        coil.curveTo(26, 40, 46, 42, 48, 28);
        coil.curveTo(50, 14, 30, 8, 20, 16);
        coil.curveTo(10, 24, 18, 38, 32, 34);
        coil.curveTo(42, 31, 42, 20, 34, 18);
        line(g, coil, p[0], 4.2);
        line(g, coil, p[2], 2.6);
        line(g, coil, alpha(p[4], 140), 0.8);
        RoundRectangle2D h = new RoundRectangle2D.Double(6, 46, 12, 6, 3, 3);
        AffineTransform o = g.getTransform();
        g.rotate(-0.6, 12, 49);
        fill(g, h, metalY(46, 52, IconSmith.GRIP));
        line(g, h, T(IconSmith.GRIP)[0], 0.7);
        gem(g, 5, 49, 2.4, IconSmith.GOLD);
        g.setTransform(o);
    }

    static void harp(Graphics2D g) {
        Color gold = IconSmith.GOLD;
        Path2D f = new Path2D.Double();
        f.moveTo(14, 54);
        f.lineTo(14, 14);
        f.curveTo(22, 4, 38, 18, 50, 12);
        f.lineTo(50, 54);
        line(g, f, T(gold)[0], 5.6);
        line(g, f, T(gold)[2], 3.6);
        line(g, f, alpha(T(gold)[4], 170), 1.1);
        for (int i = 0; i < 6; i++) {
            double x = 19 + i * 5.4;
            double top = 14 + Math.sin((x - 14) / 36 * Math.PI) * -2 + (x > 30 ? 0 : 0);
            seg(g, x, top + 2 - (x - 14) * 0.05, x, 52, new Color(240, 236, 224, 230), 0.7);
        }
        gem(g, 14, 13, 3, new Color(0x3cb4dc));
    }

    static void fan(Graphics2D g) {
        Color silk = new Color(0xd25a8c);
        for (int i = 0; i < 9; i++) {
            double a0 = Math.toRadians(200 + i * 15.5), a1 = Math.toRadians(200 + (i + 1) * 15.5);
            Path2D p = new Path2D.Double();
            p.moveTo(32, 50);
            p.lineTo(32 + Math.cos(a0) * 28, 50 + Math.sin(a0) * 28);
            p.lineTo(32 + Math.cos(a1) * 28, 50 + Math.sin(a1) * 28);
            p.closePath();
            Color c = i % 2 == 0 ? silk : new Color(0x3cb4a8);
            fill(g, p, soft(22, 50, c));
            line(g, p, T(c)[0], 0.5);
        }
        for (int i = 0; i <= 9; i++) {
            double a = Math.toRadians(200 + i * 15.5);
            seg(g, 32, 50, 32 + Math.cos(a) * 27, 50 + Math.sin(a) * 27, T(IconSmith.WOOD)[1], 0.9);
        }
        gem(g, 32, 50, 3, IconSmith.GOLD);
        seg(g, 32, 53, 32, 60, new Color(0xc02838), 1.6);
    }

    static void can(Graphics2D g) {
        Color c = new Color(0x8cb0cc);
        RoundRectangle2D body = new RoundRectangle2D.Double(14, 24, 28, 28, 6, 6);
        fill(g, body, metalX(14, 42, c));
        line(g, body, T(c)[0], 0.9);
        Path2D spout = new Path2D.Double();
        spout.moveTo(40, 40);
        spout.lineTo(56, 22);
        spout.lineTo(58, 25);
        spout.lineTo(42, 46);
        spout.closePath();
        fill(g, spout, metalX(40, 58, c));
        line(g, spout, T(c)[0], 0.8);
        Arc2D handle = new Arc2D.Double(16, 12, 22, 22, 0, 180, Arc2D.OPEN);
        line(g, handle, T(c)[0], 3.6);
        line(g, handle, T(c)[3], 2);
        for (int i = 0; i < 4; i++) fill(g, new Ellipse2D.Double(56 + i % 2 * 2, 28 + i * 5, 2.4, 3.4), new Color(130, 200, 255, 220));
    }

    static void chisel(Graphics2D g) {
        AffineTransform o = g.getTransform();
        g.rotate(-0.6, 32, 32);
        RoundRectangle2D handle = new RoundRectangle2D.Double(10, 26, 26, 6, 3, 3);
        fill(g, handle, metalY(26, 32, IconSmith.WOOD));
        RoundRectangle2D head = new RoundRectangle2D.Double(32, 18, 9, 22, 2, 2);
        fill(g, head, metalX(32, 41, IconSmith.STEEL));
        line(g, head, T(IconSmith.STEEL)[0], 0.8);
        line(g, handle, T(IconSmith.WOOD)[0], 0.8);
        g.setTransform(o);
        g.rotate(0.7, 32, 32);
        Path2D ch = new Path2D.Double();
        ch.moveTo(28, 10);
        ch.lineTo(34, 10);
        ch.lineTo(34, 44);
        ch.lineTo(31, 50);
        ch.lineTo(28, 44);
        ch.closePath();
        fill(g, ch, metalX(28, 34, IconSmith.STEEL));
        line(g, ch, T(IconSmith.STEEL)[0], 0.8);
        g.setTransform(o);
    }

    static void arrow(Graphics2D g) {
        rot45(g);
        haft(g, new IconSmith.Look(IconSmith.WOOD, IconSmith.WOOD, IconSmith.BRASS, IconSmith.BRASS), -30, 38, 2.4);
        Path2D head = new Path2D.Double();
        head.moveTo(0, -44);
        head.lineTo(5, -30);
        head.lineTo(-5, -30);
        head.closePath();
        Color dark = new Color(0x2a2430);
        fill(g, head, metalX(-5, 5, dark));
        line(g, head, T(dark)[0], 0.8);
        for (int sgn : new int[]{-1, 1}) {
            Path2D f = new Path2D.Double();
            f.moveTo(0, 26);
            f.lineTo(sgn * 6, 30);
            f.lineTo(sgn * 6, 40);
            f.lineTo(0, 36);
            f.closePath();
            fill(g, f, soft(26, 40, new Color(0xf0ece0)));
            line(g, f, T(new Color(0xa0a0a0))[0], 0.6);
        }
    }

    // ------------------------------------------------------------------ 갑옷
    static void chest(Graphics2D g, IconSmith.Look l, boolean soft) {
        Path2D b = new Path2D.Double();
        b.moveTo(20, 8);
        b.lineTo(26, 13);
        b.lineTo(38, 13);
        b.lineTo(44, 8);
        b.lineTo(56, 14);
        b.lineTo(58, 27);
        b.lineTo(50, 30);
        b.lineTo(48, 54);
        b.quadTo(32, 59, 16, 54);
        b.lineTo(14, 30);
        b.lineTo(6, 27);
        b.lineTo(8, 14);
        b.closePath();
        fill(g, b, soft ? soft(8, 58, l.p()) : metalX(6, 58, l.p()));
        Color[] p = T(l.p()), s = T(l.s());
        Shape clip = g.getClip();
        g.clip(b);
        if (!soft) {   // 판금 이음매 · 가슴판 볼록
            fill(g, new Ellipse2D.Double(17, 16, 30, 22), new RadialGradientPaint(new Point2D.Double(26, 22), 16f, new float[]{0f, 1f},
                    new Color[]{alpha(p[4], 140), alpha(p[2], 0)}));
            for (int y : new int[]{38, 45}) line(g, new QuadCurve2D.Double(15, y, 32, y + 4, 49, y), alpha(p[0], 200), 1.1);
            seg(g, 32, 14, 32, 36, alpha(p[0], 150), 0.9);
        } else {
            for (int y = 18; y < 54; y += 6) seg(g, 15, y, 49, y, alpha(p[1], 120), 0.6);
            seg(g, 32, 14, 32, 56, alpha(p[0], 160), 1);
            for (int y = 18; y < 40; y += 5) { seg(g, 30, y, 34, y + 2, s[3], 0.9); }
        }
        g.setClip(clip);
        // 어깨 · 목 테두리 (둘째 재질)
        Path2D collar = new Path2D.Double();
        collar.moveTo(20, 8);
        collar.lineTo(26, 13);
        collar.lineTo(38, 13);
        collar.lineTo(44, 8);
        line(g, collar, s[0], 3.6);
        line(g, collar, s[3], 2);
        line(g, new QuadCurve2D.Double(16, 54, 32, 59, 48, 54), s[0], 3.4);
        line(g, new QuadCurve2D.Double(16, 54, 32, 59, 48, 54), s[3], 1.8);
        for (int sgn : new int[]{-1, 1}) {
            Ellipse2D pad = new Ellipse2D.Double(32 + sgn * 19 - 7, 9, 14, 12);
            fill(g, pad, soft ? soft(9, 21, l.p()) : metalY(9, 21, l.p()));
            line(g, pad, p[0], 0.9);
            fill(g, new Ellipse2D.Double(32 + sgn * 19 - 1.5, 13, 3, 3), metalY(13, 16, l.s()));
        }
        line(g, b, p[0], 1);
        if (!l.a().equals(IconSmith.RUBY)) gem(g, 32, 26, 3.2, l.a());
    }

    static void robe(Graphics2D g, IconSmith.Look l) {
        Path2D b = new Path2D.Double();
        b.moveTo(24, 6);
        b.lineTo(40, 6);
        b.lineTo(54, 16);
        b.lineTo(52, 26);
        b.lineTo(46, 24);
        b.lineTo(52, 58);
        b.lineTo(12, 58);
        b.lineTo(18, 24);
        b.lineTo(12, 26);
        b.lineTo(10, 16);
        b.closePath();
        fill(g, b, soft(6, 58, l.p()));
        Color[] p = T(l.p()), s = T(l.s());
        for (int x : new int[]{24, 32, 40}) line(g, new QuadCurve2D.Double(x, 28, x + (x - 32) * 0.3, 44, x + (x - 32) * 0.5, 57), alpha(p[1], 170), 1);
        line(g, new Line2D.Double(32, 8, 32, 57), alpha(s[0], 200), 1.6);
        line(g, new Line2D.Double(32, 8, 32, 57), s[3], 0.8);
        line(g, new Line2D.Double(12, 57, 52, 57), s[3], 2.2);
        Path2D hood = new Path2D.Double();
        hood.moveTo(22, 6);
        hood.quadTo(32, 16, 42, 6);
        line(g, hood, s[0], 3.2);
        line(g, hood, s[3], 1.6);
        line(g, b, p[0], 1);
        gem(g, 32, 14, 3, l.a());
    }

    static void legs(Graphics2D g, IconSmith.Look l) {
        Path2D b = new Path2D.Double();
        b.moveTo(16, 8);
        b.lineTo(48, 8);
        b.lineTo(50, 56);
        b.lineTo(36, 56);
        b.lineTo(33, 22);
        b.lineTo(31, 22);
        b.lineTo(28, 56);
        b.lineTo(14, 56);
        b.closePath();
        fill(g, b, metalX(14, 50, l.p()));
        Color[] p = T(l.p()), s = T(l.s());
        for (int sgn : new int[]{-1, 1}) {
            Ellipse2D knee = new Ellipse2D.Double(32 + sgn * 10 - 6, 30, 12, 9);
            fill(g, knee, metalY(30, 39, l.p()));
            line(g, knee, p[0], 0.8);
        }
        RoundRectangle2D belt = new RoundRectangle2D.Double(15, 7, 34, 6, 2, 2);
        fill(g, belt, metalY(7, 13, l.s()));
        line(g, belt, s[0], 0.8);
        line(g, b, p[0], 1);
    }

    static void boots(Graphics2D g, IconSmith.Look l, boolean winged, boolean leather) {
        Color[] p = T(l.p()), s = T(l.s());
        for (int sgn : new int[]{-1, 1}) {
            double x = 32 + sgn * 11;
            Path2D b = new Path2D.Double();
            b.moveTo(x - 7, 12);
            b.lineTo(x + 6, 12);
            b.lineTo(x + 6, 40);
            b.quadTo(x + 16, 44, x + 15, 52);
            b.lineTo(x - 8, 52);
            b.lineTo(x - 8, 40);
            b.closePath();
            fill(g, b, leather ? soft(12, 52, l.p()) : metalX(x - 8, x + 15, l.p()));
            RoundRectangle2D cuff = new RoundRectangle2D.Double(x - 8, 10, 15, 6, 2, 2);
            fill(g, cuff, metalY(10, 16, l.s()));
            line(g, cuff, s[0], 0.8);
            seg(g, x - 8, 47, x + 15, 47, alpha(p[0], 200), 1.2);
            line(g, b, p[0], 1);
            if (winged) {
                Path2D w = new Path2D.Double();
                w.moveTo(x - 7, 20);
                w.curveTo(x - 16, 14, x - 18, 8, x - 14, 4);
                w.curveTo(x - 12, 10, x - 9, 14, x - 7, 26);
                fill(g, w, soft(4, 26, new Color(0xf0f0f0)));
                line(g, w, T(new Color(0xb4b4c0))[0], 0.7);
            }
        }
    }

    static void helm(Graphics2D g, IconSmith.Look l, boolean horned) {
        Color[] p = T(l.p()), s = T(l.s());
        if (horned) for (int sgn : new int[]{-1, 1}) {
            Path2D h = new Path2D.Double();
            h.moveTo(32 + sgn * 12, 22);
            h.curveTo(32 + sgn * 24, 18, 32 + sgn * 26, 8, 32 + sgn * 22, 2);
            h.curveTo(32 + sgn * 20, 10, 32 + sgn * 18, 14, 32 + sgn * 11, 16);
            h.closePath();
            fill(g, h, soft(2, 22, IconSmith.BONE));
            line(g, h, T(IconSmith.BONE)[0], 0.9);
        }
        Path2D b = new Path2D.Double();
        b.moveTo(12, 50);
        b.lineTo(12, 30);
        b.curveTo(12, 10, 52, 10, 52, 30);
        b.lineTo(52, 50);
        b.lineTo(40, 54);
        b.lineTo(40, 36);
        b.lineTo(24, 36);
        b.lineTo(24, 54);
        b.closePath();
        fill(g, b, metalX(12, 52, l.p()));
        seg(g, 32, 13, 32, 36, s[3], 2.4);
        seg(g, 32, 13, 32, 36, alpha(s[0], 150), 0.6);
        Rectangle2D slit = new Rectangle2D.Double(16, 30, 32, 3.4);
        fill(g, slit, new Color(0x18141a));
        line(g, b, p[0], 1);
        if (!l.a().equals(IconSmith.RUBY)) gem(g, 32, 22, 2.6, l.a());
    }

    static void hat(Graphics2D g, IconSmith.Look l) {
        Color straw = new Color(0xd8b866);
        Ellipse2D brim = new Ellipse2D.Double(4, 34, 56, 16);
        fill(g, brim, soft(34, 50, straw));
        line(g, brim, T(straw)[0], 0.9);
        Path2D crown = new Path2D.Double();
        crown.moveTo(18, 40);
        crown.curveTo(18, 16, 46, 16, 46, 40);
        crown.closePath();
        fill(g, crown, soft(18, 40, straw));
        for (int x = 20; x < 46; x += 4) seg(g, x, 24, x + 1, 40, alpha(T(straw)[1], 140), 0.6);
        fill(g, new Rectangle2D.Double(18, 34, 28, 4), new Color(0x8c2828));
        line(g, crown, T(straw)[0], 0.9);
        Path2D feather = new Path2D.Double();
        feather.moveTo(42, 34);
        feather.curveTo(52, 26, 54, 16, 48, 10);
        line(g, feather, new Color(0xf0ece0), 2.4);
    }

    static void crown(Graphics2D g, IconSmith.Look l) {
        Path2D c = new Path2D.Double();
        c.moveTo(8, 48);
        c.lineTo(8, 22);
        c.lineTo(18, 34);
        c.lineTo(24, 14);
        c.lineTo(32, 30);
        c.lineTo(40, 14);
        c.lineTo(46, 34);
        c.lineTo(56, 22);
        c.lineTo(56, 48);
        c.closePath();
        fill(g, c, metalX(8, 56, l.p()));
        Color[] p = T(l.p());
        RoundRectangle2D band = new RoundRectangle2D.Double(7, 40, 50, 9, 3, 3);
        fill(g, band, metalY(40, 49, l.p()));
        line(g, band, p[0], 0.9);
        line(g, c, p[0], 1);
        for (double[] pt : new double[][]{{8, 21}, {24, 13}, {40, 13}, {56, 21}}) gem(g, pt[0], pt[1], 2.4, new Color(0xf0ece0));
        gem(g, 32, 44.5, 3.4, l.a());
        gem(g, 18, 44.5, 2.4, new Color(0x3c64c8));
        gem(g, 46, 44.5, 2.4, new Color(0x3cb46e));
    }

    static void shield(Graphics2D g, IconSmith.Look l) {
        Path2D s = new Path2D.Double();
        s.moveTo(8, 8);
        s.lineTo(56, 8);
        s.lineTo(56, 30);
        s.curveTo(56, 46, 40, 54, 32, 60);
        s.curveTo(24, 54, 8, 46, 8, 30);
        s.closePath();
        fill(g, s, metalX(8, 56, l.p()));
        Color[] tr = T(IconSmith.STEEL);
        line(g, s, tr[0], 4.4);
        line(g, s, tr[3], 2.4);
        Path2D cross = new Path2D.Double();
        cross.moveTo(28, 14);
        cross.lineTo(36, 14);
        cross.lineTo(36, 24);
        cross.lineTo(48, 24);
        cross.lineTo(48, 31);
        cross.lineTo(36, 31);
        cross.lineTo(36, 50);
        cross.lineTo(28, 50);
        cross.lineTo(28, 31);
        cross.lineTo(16, 31);
        cross.lineTo(16, 24);
        cross.lineTo(28, 24);
        cross.closePath();
        fill(g, cross, metalX(16, 48, IconSmith.GOLD));
        line(g, cross, T(IconSmith.GOLD)[0], 0.8);
        gem(g, 32, 27.5, 3.4, l.a());
    }

    // ------------------------------------------------------------------ 장신구
    static void ring(Graphics2D g, IconSmith.Look l) {
        Ellipse2D outer = new Ellipse2D.Double(14, 22, 36, 36), inner = new Ellipse2D.Double(21, 29, 22, 22);
        Area band = new Area(outer);
        band.subtract(new Area(inner));
        fill(g, band, metalX(14, 50, l.p()));
        Color[] p = T(l.p());
        line(g, new Arc2D.Double(16, 24, 32, 32, 110, 120, Arc2D.OPEN), alpha(p[4], 220), 1.4);
        line(g, band, p[0], 1);
        Path2D set = new Path2D.Double();
        set.moveTo(22, 24);
        set.lineTo(26, 14);
        set.lineTo(38, 14);
        set.lineTo(42, 24);
        set.closePath();
        fill(g, set, metalY(14, 24, l.p()));
        line(g, set, p[0], 0.9);
        gem(g, 32, 13, 7, l.a());
        for (int sgn : new int[]{-1, 1}) fill(g, new Ellipse2D.Double(32 + sgn * 7 - 1.5, 17, 3, 3), metalY(17, 20, l.p()));
    }

    static void necklace(Graphics2D g, IconSmith.Look l) {
        Color[] p = T(l.p());
        for (int i = 0; i <= 22; i++) {
            double a = Math.PI * i / 22;
            double x = 32 - Math.cos(a) * 24, y = 6 + Math.sin(a) * 30;
            Ellipse2D link = new Ellipse2D.Double(x - 2, y - 1.6, 4, 3.2);
            fill(g, link, metalY(y - 1.6, y + 1.6, l.p()));
            line(g, link, p[0], 0.5);
        }
        Path2D pend = new Path2D.Double();
        pend.moveTo(32, 32);
        pend.lineTo(43, 44);
        pend.lineTo(32, 60);
        pend.lineTo(21, 44);
        pend.closePath();
        fill(g, pend, metalX(21, 43, l.p()));
        line(g, pend, p[0], 0.9);
        gem(g, 32, 45, 6.2, l.a());
    }

    static void bracelet(Graphics2D g, IconSmith.Look l, boolean flower) {
        Area band = new Area(new Ellipse2D.Double(6, 18, 52, 32));
        band.subtract(new Area(new Ellipse2D.Double(13, 23, 38, 20)));
        fill(g, band, flower ? soft(18, 50, new Color(0x6ea050)) : metalY(18, 50, l.p()));
        Color[] p = T(flower ? new Color(0x6ea050) : l.p());
        line(g, band, p[0], 1);
        if (flower) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * (0.15 + i * 0.14);
                double x = 32 - Math.cos(a) * 24, y = 34 + Math.sin(a) * 14;
                for (int k = 0; k < 5; k++) {
                    double b = k * Math.PI * 2 / 5;
                    fill(g, new Ellipse2D.Double(x + Math.cos(b) * 2.2 - 1.8, y + Math.sin(b) * 2.2 - 1.8, 3.6, 3.6), soft(y - 2, y + 2, l.a()));
                }
                fill(g, new Ellipse2D.Double(x - 1.2, y - 1.2, 2.4, 2.4), new Color(0xf6e296));
            }
            return;
        }
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * (0.2 + i * 0.15);
            gem(g, 32 - Math.cos(a) * 23, 34 + Math.sin(a) * 13, 3, i == 2 ? l.a() : i % 2 == 0 ? new Color(0x3c64c8) : l.a());
        }
    }

    static void gloves(Graphics2D g, IconSmith.Look l) {
        Path2D h = new Path2D.Double();
        h.moveTo(18, 40);
        h.lineTo(18, 20);
        h.quadTo(18, 14, 22, 14);
        h.quadTo(25, 14, 25, 20);
        h.lineTo(25, 10);
        h.quadTo(25, 5, 29, 5);
        h.quadTo(32, 5, 32, 10);
        h.lineTo(32, 9);
        h.quadTo(32, 4, 36, 4);
        h.quadTo(39, 4, 39, 9);
        h.lineTo(39, 14);
        h.quadTo(39, 9, 43, 9);
        h.quadTo(46, 9, 46, 15);
        h.lineTo(46, 34);
        h.quadTo(52, 26, 56, 28);
        h.quadTo(58, 32, 52, 40);
        h.lineTo(44, 50);
        h.lineTo(20, 50);
        h.closePath();
        fill(g, h, soft(4, 50, l.p()));
        Color[] p = T(l.p()), s = T(l.s());
        for (int x : new int[]{25, 32, 39}) seg(g, x, 14, x, 28, alpha(p[0], 170), 0.9);
        line(g, h, p[0], 1);
        RoundRectangle2D cuff = new RoundRectangle2D.Double(16, 46, 32, 12, 3, 3);
        fill(g, cuff, metalY(46, 58, l.s()));
        line(g, cuff, s[0], 0.9);
        if (!l.a().equals(IconSmith.RUBY)) gem(g, 32, 34, 3.2, l.a());
        else for (int i = 0; i < 4; i++) fill(g, new Ellipse2D.Double(22 + i * 6, 28 + (i % 2) * 6, 3.4, 2.4), new Color(140, 20, 20, 170));
    }

    static void cloak(Graphics2D g, IconSmith.Look l) {
        Path2D c = new Path2D.Double();
        c.moveTo(24, 8);
        c.lineTo(40, 8);
        c.curveTo(50, 24, 56, 44, 58, 58);
        c.lineTo(6, 58);
        c.curveTo(8, 44, 14, 24, 24, 8);
        c.closePath();
        fill(g, c, soft(8, 58, l.p()));
        Color[] p = T(l.p()), s = T(l.s());
        for (int x : new int[]{18, 26, 38, 46}) line(g, new QuadCurve2D.Double(30 + (x - 32) * 0.2, 14, x - 1, 36, x + (x - 32) * 0.25, 57), alpha(p[1], 190), 1.2);
        line(g, new Line2D.Double(7, 57, 57, 57), s[3], 2.2);
        line(g, c, p[0], 1);
        RoundRectangle2D col = new RoundRectangle2D.Double(20, 6, 24, 6, 3, 3);
        fill(g, col, metalY(6, 12, l.s()));
        line(g, col, s[0], 0.8);
        gem(g, 32, 10, 3.4, l.a());
        if (l.p().getBlue() > l.p().getRed() + 40) for (int i = 0; i < 6; i++) fill(g, new Ellipse2D.Double(14 + i * 7, 24 + (i * 13) % 26, 1.6, 1.6), new Color(240, 240, 255, 220));
    }

    static void belt(Graphics2D g, IconSmith.Look l) {
        RoundRectangle2D strap = new RoundRectangle2D.Double(2, 24, 60, 16, 4, 4);
        fill(g, strap, soft(24, 40, l.p()));
        Color[] p = T(l.p());
        seg(g, 4, 27, 60, 27, alpha(p[3], 180), 0.8);
        seg(g, 4, 37, 60, 37, alpha(p[0], 180), 0.8);
        for (int x = 8; x < 58; x += 5) if (x < 22 || x > 42) fill(g, new Ellipse2D.Double(x, 31, 1.6, 1.6), metalY(31, 33, l.s()));
        line(g, strap, p[0], 1);
        RoundRectangle2D buckle = new RoundRectangle2D.Double(21, 20, 22, 24, 5, 5);
        fill(g, buckle, metalX(21, 43, l.s()));
        line(g, buckle, T(l.s())[0], 0.9);
        gem(g, 32, 32, 5, l.a());
    }

    static void pauldron(Graphics2D g, IconSmith.Look l) {
        Color[] p = T(l.p()), s = T(l.s());
        for (int i = 2; i >= 0; i--) {
            Arc2D a = new Arc2D.Double(6 + i * 2, 14 + i * 9, 52 - i * 4, 40 - i * 4, 0, 180, Arc2D.CHORD);
            fill(g, a, metalY(14 + i * 9, 34 + i * 9, l.p()));
            line(g, a, p[0], 0.9);
            line(g, new Arc2D.Double(6 + i * 2, 14 + i * 9, 52 - i * 4, 40 - i * 4, 0, 180, Arc2D.OPEN), s[3], 1.6);
        }
        gem(g, 32, 22, 3.4, l.a());
    }

    static void orb(Graphics2D g, IconSmith.Look l) {
        Path2D stand = new Path2D.Double();
        stand.moveTo(20, 58);
        stand.lineTo(44, 58);
        stand.lineTo(40, 48);
        stand.lineTo(24, 48);
        stand.closePath();
        fill(g, stand, metalX(20, 44, l.s()));
        line(g, stand, T(l.s())[0], 0.9);
        gem(g, 32, 30, 19, l.a());
        Color[] a = T(l.a());
        for (int i = 0; i < 4; i++) line(g, new QuadCurve2D.Double(22 + i * 5, 18 + i * 3, 30 + i * 2, 30, 26 + i * 4, 44 - i), alpha(a[0], 90), 0.8);
    }

    // ------------------------------------------------------------------ 성물 · 장식
    static void book(Graphics2D g, String id) {
        Color cover = id.contains("death") || id.contains("necromancer") || id.contains("revival") ? new Color(0x3c2a3c)
                : id.contains("gluttony") || id.contains("doom") ? new Color(0x6e2828) : new Color(0x6e4a2e);
        RoundRectangle2D pages = new RoundRectangle2D.Double(16, 8, 40, 50, 3, 3);
        fill(g, pages, soft(8, 58, new Color(0xe6dcc0)));
        for (int y = 12; y < 56; y += 3) seg(g, 52, y, 56, y, new Color(0xb4a888), 0.5);
        RoundRectangle2D c = new RoundRectangle2D.Double(8, 6, 42, 52, 4, 4);
        fill(g, c, soft(6, 58, cover));
        RoundRectangle2D spine = new RoundRectangle2D.Double(8, 6, 8, 52, 4, 4);
        fill(g, spine, metalX(8, 16, cover));
        Color[] gd = T(IconSmith.GOLD);
        line(g, new RoundRectangle2D.Double(19, 11, 26, 42, 3, 3), gd[3], 1.4);
        for (int[] cn : new int[][]{{19, 11}, {45, 11}, {19, 53}, {45, 53}}) fill(g, new Ellipse2D.Double(cn[0] - 2.5, cn[1] - 2.5, 5, 5), metalY(cn[1] - 2.5, cn[1] + 2.5, IconSmith.GOLD));
        gem(g, 32, 32, 6, id.contains("death") || id.contains("necromancer") ? new Color(0x8c5abe) : new Color(0xc02838));
        line(g, c, T(cover)[0], 1);
    }

    static void cup(Graphics2D g, boolean barrel) {
        if (barrel) {
            RoundRectangle2D b = new RoundRectangle2D.Double(14, 10, 36, 46, 12, 12);
            fill(g, b, metalX(14, 50, IconSmith.WOOD));
            for (int y : new int[]{16, 48}) { RoundRectangle2D hoop = new RoundRectangle2D.Double(13, y, 38, 4, 2, 2); fill(g, hoop, metalY(y, y + 4, IconSmith.STEEL)); }
            line(g, b, T(IconSmith.WOOD)[0], 1);
            return;
        }
        Path2D c = new Path2D.Double();
        c.moveTo(12, 8);
        c.lineTo(52, 8);
        c.curveTo(52, 26, 42, 34, 36, 36);
        c.lineTo(36, 46);
        c.lineTo(46, 52);
        c.lineTo(46, 58);
        c.lineTo(18, 58);
        c.lineTo(18, 52);
        c.lineTo(28, 46);
        c.lineTo(28, 36);
        c.curveTo(22, 34, 12, 26, 12, 8);
        c.closePath();
        fill(g, c, metalX(12, 52, IconSmith.GOLD));
        fill(g, new Ellipse2D.Double(13, 5, 38, 7), soft(5, 12, new Color(0x8c1e28)));
        line(g, c, T(IconSmith.GOLD)[0], 1);
        gem(g, 32, 22, 4.4, new Color(0xc02838));
        gem(g, 22, 18, 2.4, new Color(0x3c64c8));
        gem(g, 42, 18, 2.4, new Color(0x3cb46e));
    }

    static void mirror(Graphics2D g, boolean silver) {
        Color frame = silver ? new Color(0xc8ccd8) : IconSmith.GOLD;
        RoundRectangle2D handle = new RoundRectangle2D.Double(28, 38, 8, 22, 4, 4);
        fill(g, handle, metalX(28, 36, frame));
        line(g, handle, T(frame)[0], 0.8);
        Ellipse2D f = new Ellipse2D.Double(10, 4, 44, 40);
        fill(g, f, metalX(10, 54, frame));
        line(g, f, T(frame)[0], 1);
        Ellipse2D glass = new Ellipse2D.Double(15, 9, 34, 30);
        fill(g, glass, new LinearGradientPaint(15, 9, 49, 39, new float[]{0f, 0.4f, 1f},
                new Color[]{new Color(0xe6f4fa), new Color(0x9ccce0), new Color(0x4a7a96)}));
        line(g, new Line2D.Double(22, 16, 30, 13), new Color(255, 255, 255, 220), 2);
        line(g, new Line2D.Double(20, 22, 24, 19), new Color(255, 255, 255, 200), 1.4);
        if (silver) for (int i = 0; i < 8; i++) fill(g, new Ellipse2D.Double(12 + i * 5, 4 + (i % 3) * 2, 3, 2), metalY(4, 8, frame));
    }

    static void map(Graphics2D g) {
        Color paper = new Color(0xd8c094);
        Path2D m = new Path2D.Double();
        m.moveTo(6, 12);
        m.lineTo(22, 8);
        m.lineTo(40, 12);
        m.lineTo(58, 8);
        m.lineTo(58, 52);
        m.lineTo(40, 56);
        m.lineTo(22, 52);
        m.lineTo(6, 56);
        m.closePath();
        fill(g, m, soft(8, 56, paper));
        seg(g, 22, 8, 22, 52, alpha(T(paper)[0], 120), 1);
        seg(g, 40, 12, 40, 56, alpha(T(paper)[0], 120), 1);
        Path2D coast = new Path2D.Double();
        coast.moveTo(10, 40);
        coast.curveTo(16, 30, 26, 36, 30, 26);
        coast.curveTo(34, 18, 44, 22, 52, 16);
        line(g, coast, new Color(0x5a7a96), 1.4);
        Path2D road = new Path2D.Double();
        road.moveTo(12, 48);
        road.curveTo(20, 44, 28, 48, 36, 40);
        line(g, road, new Color(0x8c3c28), 1.1);
        line(g, new Line2D.Double(42, 34, 48, 40), new Color(0xc02828), 2);
        line(g, new Line2D.Double(48, 34, 42, 40), new Color(0xc02828), 2);
        line(g, m, T(paper)[0], 1);
    }

    static void horn(Graphics2D g, boolean black) {
        Color c = black ? new Color(0x3a3440) : IconSmith.BONE;
        Path2D h = new Path2D.Double();
        h.moveTo(8, 50);
        h.curveTo(20, 54, 40, 46, 52, 18);
        h.lineTo(58, 10);
        h.lineTo(60, 20);
        h.curveTo(48, 50, 26, 62, 6, 56);
        h.closePath();
        fill(g, h, soft(10, 60, c));
        for (double t : new double[]{0.35, 0.6}) {
            double x = 8 + t * 46, y = 54 - t * 40;
            line(g, new Line2D.Double(x - 3, y - 4, x + 4, y + 3), T(IconSmith.GOLD)[0], 4.2);
            line(g, new Line2D.Double(x - 3, y - 4, x + 4, y + 3), T(IconSmith.GOLD)[3], 2.6);
        }
        line(g, h, T(c)[0], 1);
        Ellipse2D bell = new Ellipse2D.Double(53, 6, 9, 14);
        fill(g, bell, metalX(53, 62, IconSmith.GOLD));
        line(g, bell, T(IconSmith.GOLD)[0], 0.8);
    }

    static void key(Graphics2D g, boolean gold) {
        Color c = gold ? IconSmith.GOLD : new Color(0x8c8a84);
        Area bow = new Area(new Ellipse2D.Double(4, 16, 24, 24));
        bow.subtract(new Area(new Ellipse2D.Double(10, 22, 12, 12)));
        fill(g, bow, metalX(4, 28, c));
        line(g, bow, T(c)[0], 1);
        RoundRectangle2D shaft = new RoundRectangle2D.Double(26, 25, 32, 6, 2, 2);
        fill(g, shaft, metalY(25, 31, c));
        line(g, shaft, T(c)[0], 0.9);
        for (int x : new int[]{44, 52}) {
            Rectangle2D tooth = new Rectangle2D.Double(x, 31, 5, 9);
            fill(g, tooth, metalX(x, x + 5, c));
            line(g, tooth, T(c)[0], 0.8);
        }
        gem(g, 16, 28, 3, new Color(0x3c64c8));
    }

    static void flag(Graphics2D g) {
        RoundRectangle2D pole = new RoundRectangle2D.Double(8, 4, 5, 58, 2, 2);
        fill(g, pole, metalX(8, 13, IconSmith.WOOD));
        line(g, pole, T(IconSmith.WOOD)[0], 0.8);
        gem(g, 10.5, 4, 3, IconSmith.GOLD);
        Color cloth = new Color(0x2c4a8c);
        Path2D f = new Path2D.Double();
        f.moveTo(13, 8);
        f.curveTo(28, 4, 40, 14, 58, 8);
        f.lineTo(58, 38);
        f.curveTo(40, 44, 28, 34, 13, 38);
        f.closePath();
        fill(g, f, soft(4, 44, cloth));
        for (int i = 0; i < 5; i++) {   // 봉우리
            Path2D peak = new Path2D.Double();
            peak.moveTo(18 + i * 7, 32);
            peak.lineTo(22 + i * 7, 20 + (i % 2) * 4);
            peak.lineTo(26 + i * 7, 32);
            fill(g, peak, new Color(0xe6e6e6));
        }
        line(g, f, T(cloth)[0], 1);
    }

    static void seal(Graphics2D g, boolean jade) {
        Color c = jade ? new Color(0xd8d0b8) : IconSmith.GOLD;
        RoundRectangle2D base = new RoundRectangle2D.Double(12, 34, 40, 22, 4, 4);
        fill(g, base, metalX(12, 52, c));
        line(g, base, T(c)[0], 1);
        Path2D beast = new Path2D.Double();   // 손잡이 짐승 (옥새)
        beast.moveTo(18, 36);
        beast.curveTo(16, 22, 24, 12, 32, 12);
        beast.curveTo(40, 12, 48, 22, 46, 36);
        beast.closePath();
        fill(g, beast, metalX(16, 48, c));
        line(g, beast, T(c)[0], 1);
        gem(g, 27, 22, 2, new Color(0xc02838));
        gem(g, 37, 22, 2, new Color(0xc02838));
        fill(g, new Rectangle2D.Double(16, 52, 32, 4), new Color(0xa02020));
    }

    static void crest(Graphics2D g) {
        Path2D s = new Path2D.Double();
        s.moveTo(12, 8);
        s.lineTo(52, 8);
        s.lineTo(52, 30);
        s.curveTo(52, 46, 36, 54, 32, 58);
        s.curveTo(28, 54, 12, 46, 12, 30);
        s.closePath();
        Color blue = new Color(0x2c3c78);
        fill(g, s, soft(8, 58, blue));
        line(g, s, T(IconSmith.GOLD)[0], 4.2);
        line(g, s, T(IconSmith.GOLD)[3], 2.2);
        Path2D eagle = new Path2D.Double();
        eagle.moveTo(32, 16);
        eagle.lineTo(46, 22);
        eagle.lineTo(36, 28);
        eagle.lineTo(40, 44);
        eagle.lineTo(32, 38);
        eagle.lineTo(24, 44);
        eagle.lineTo(28, 28);
        eagle.lineTo(18, 22);
        eagle.closePath();
        fill(g, eagle, metalX(18, 46, IconSmith.GOLD));
        line(g, eagle, T(IconSmith.GOLD)[0], 0.8);
        gem(g, 32, 26, 2.6, new Color(0xc02838));
    }

    static void skull(Graphics2D g) {
        Color b = IconSmith.BONE;
        Path2D s = new Path2D.Double();
        s.moveTo(14, 34);
        s.curveTo(10, 8, 54, 8, 50, 34);
        s.lineTo(46, 42);
        s.lineTo(44, 54);
        s.lineTo(20, 54);
        s.lineTo(18, 42);
        s.closePath();
        fill(g, s, soft(8, 54, b));
        Color hole = new Color(0x1a1418);
        fill(g, new Ellipse2D.Double(19, 28, 10, 10), hole);
        fill(g, new Ellipse2D.Double(35, 28, 10, 10), hole);
        fill(g, new Ellipse2D.Double(23, 31, 3, 3), new Color(0x8c5abe));
        fill(g, new Ellipse2D.Double(39, 31, 3, 3), new Color(0x8c5abe));
        Path2D nose = new Path2D.Double();
        nose.moveTo(32, 38);
        nose.lineTo(29, 44);
        nose.lineTo(35, 44);
        nose.closePath();
        fill(g, nose, hole);
        for (int x = 23; x <= 41; x += 4.5) seg(g, x, 47, x, 54, T(b)[0], 0.8);
        line(g, s, T(b)[0], 1);
    }

    static void plate(Graphics2D g) {
        Color bronze = new Color(0xa0703c);
        Path2D p = new Path2D.Double();
        p.moveTo(8, 10);
        p.lineTo(56, 10);
        p.lineTo(56, 40);
        p.lineTo(48, 54);
        p.lineTo(8, 54);
        p.closePath();
        fill(g, p, metalX(8, 56, bronze));
        line(g, new Line2D.Double(48, 40, 56, 40), T(bronze)[0], 1);
        for (int i = 0; i < 4; i++) seg(g, 14, 18 + i * 8, 46, 18 + i * 8, alpha(T(bronze)[0], 160), 0.9);
        gem(g, 30, 32, 5, new Color(0x78c8b4));
        line(g, p, T(bronze)[0], 1);
    }

    static void compass(Graphics2D g) {
        Ellipse2D c = new Ellipse2D.Double(8, 8, 48, 48);
        fill(g, c, metalX(8, 56, IconSmith.BRASS));
        Ellipse2D face = new Ellipse2D.Double(14, 14, 36, 36);
        fill(g, face, soft(14, 50, new Color(0xe6dcc0)));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            seg(g, 32 + Math.cos(a) * 14, 32 + Math.sin(a) * 14, 32 + Math.cos(a) * 17, 32 + Math.sin(a) * 17, T(IconSmith.BRASS)[0], 1);
        }
        Path2D needle = new Path2D.Double();
        needle.moveTo(32, 16);
        needle.lineTo(35, 32);
        needle.lineTo(32, 48);
        needle.lineTo(29, 32);
        needle.closePath();
        fill(g, needle, new GradientPaint(32, 16, new Color(0xc02828), 32, 48, new Color(0x2a2a30)));
        gem(g, 32, 32, 2.4, IconSmith.GOLD);
        line(g, c, T(IconSmith.BRASS)[0], 1);
    }

    static void furnace(Graphics2D g) {
        Color st = new Color(0x5a5660);
        RoundRectangle2D b = new RoundRectangle2D.Double(10, 10, 44, 44, 6, 6);
        fill(g, b, metalX(10, 54, st));
        RoundRectangle2D mouth = new RoundRectangle2D.Double(18, 22, 28, 20, 8, 8);
        fill(g, mouth, new RadialGradientPaint(new Point2D.Double(32, 36), 18f, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0xfff0b4), new Color(0xff8c32), new Color(0x8c2010)}));
        line(g, mouth, T(st)[0], 1);
        for (int y : new int[]{16, 46}) seg(g, 12, y, 52, y, alpha(T(st)[0], 160), 0.9);
        RoundRectangle2D foot = new RoundRectangle2D.Double(8, 52, 48, 6, 2, 2);
        fill(g, foot, metalY(52, 58, IconSmith.BRASS));
        line(g, b, T(st)[0], 1);
    }

    static void feather(Graphics2D g) {
        Path2D f = new Path2D.Double();
        f.moveTo(10, 56);
        f.curveTo(18, 36, 34, 10, 56, 6);
        f.curveTo(54, 24, 38, 46, 14, 54);
        f.closePath();
        fill(g, f, new LinearGradientPaint(10, 56, 56, 6, new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(0x78c8f0), new Color(0xe6b4dc), new Color(0xfae678), new Color(0xffffff)}));
        line(g, new QuadCurve2D.Double(8, 60, 30, 30, 54, 8), new Color(0xf0ece0), 1.2);
        for (int i = 1; i < 8; i++) {
            double t = i / 8.0, x = 10 + t * 44, y = 56 - t * 48;
            seg(g, x, y, x + 4, y + 3, new Color(255, 255, 255, 110), 0.6);
        }
        line(g, f, new Color(0x5a4a6a), 0.9);
    }
}
