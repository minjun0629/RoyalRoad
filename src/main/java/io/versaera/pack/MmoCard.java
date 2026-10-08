package io.versaera.pack;

import io.versaera.domain.item.ItemType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 인벤토리 칸에 보이는 아이템 카드 (64 × 64) — 한국 MMORPG 아이콘처럼 어두운 칸 · 등급 색 테두리 · 그림 ·
 * 오른쪽 아래 레벨 조건 (Lv.18). 종류 · 스탯 조건은 쓰지 않는다.
 * 글자는 미리 그려 둔 pack/labels.png 에서 붙인다 (서버에 한글 글꼴이 없어도 같은 그림).
 */
public final class MmoCard {
    private MmoCard() {
    }

    static final int S = 64;
    private static BufferedImage sheet;
    private static final Map<String, int[]> GLYPHS = new HashMap<>();

    private static synchronized void load() {
        if (sheet != null) return;
        try (InputStream png = MmoCard.class.getResourceAsStream("/pack/labels.png"); InputStream idx = MmoCard.class.getResourceAsStream("/pack/labels.txt")) {
            if (png == null || idx == null) throw new IllegalStateException("pack/labels.png 가 없습니다");
            sheet = ImageIO.read(png);
            for (String line : new String(idx.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) continue;
                int eq = line.lastIndexOf('=');
                String[] n = line.substring(eq + 1).split(",");
                GLYPHS.put(line.substring(0, eq), new int[]{Integer.parseInt(n[0]), Integer.parseInt(n[1]), Integer.parseInt(n[2]), Integer.parseInt(n[3])});
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 레벨 조건 (숙련 레벨 조건 가운데 가장 높은 것) — 스탯 · 명성 조건은 카드에 쓰지 않는다. 없으면 0 */
    static int level(ItemType t) {
        int best = 0;
        for (var e : t.requires().entrySet()) if (e.getKey().startsWith("mastery.")) best = Math.max(best, e.getValue());
        return best;
    }

    static Color frameColor(Color grade) {
        return grade == null ? new Color(0x6a6a78) : grade;
    }

    public static BufferedImage card(ItemType t, String kind, MmoIcon.Drawn d) {
        load();
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        Color frame = frameColor(d.grade());
        // 바탕: 짙은 남색 칸 + 등급 색이 은은하게 번진 안쪽
        g.setPaint(new GradientPaint(0, 0, new Color(0x252c3e), 0, S, new Color(0x0e1119)));
        g.fill(new Rectangle(0, 0, S, S));
        g.setPaint(new RadialGradientPaint(32, 30, 34, new float[]{0f, 1f},
                new Color[]{new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 70), new Color(frame.getRed(), frame.getGreen(), frame.getBlue(), 0)}));
        g.fill(new Rectangle(0, 0, S, S));
        // 그림 (조금 줄여 가운데)
        g.drawImage(d.art(), 6, 5, 52, 52, null);
        // 테두리: 바깥 검정 · 등급 색 · 안쪽 밝은 선 · 모서리 금장
        g.setColor(new Color(0x07080c));
        g.drawRect(0, 0, S - 1, S - 1);
        g.setStroke(new BasicStroke(2f));
        g.setColor(frame);
        g.draw(new RoundRectangle2D.Double(2, 2, S - 4, S - 4, 6, 6));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 60));
        g.draw(new RoundRectangle2D.Double(3.5, 3.5, S - 7, S - 7, 5, 5));
        Color gold = new Color(0xd4a83e);
        for (int[] c : new int[][]{{1, 1, 1, 1}, {S - 2, 1, -1, 1}, {1, S - 2, 1, -1}, {S - 2, S - 2, -1, -1}}) {
            g.setColor(gold);
            g.fillRect(Math.min(c[0], c[0] + c[2] * 4), Math.min(c[1], c[1]), 5, 1);
            g.fillRect(Math.min(c[0], c[0]), Math.min(c[1], c[1] + c[3] * 4), 1, 5);
        }
        // 글자
        int lv = level(t);
        if (lv > 0) {
            String n = String.valueOf(lv);
            int w = width("Lv.") - 1 + width(n);
            int x = S - 4 - w, y = S - 2 - height();
            label(img, "Lv.", x, y, new Color(0xf6e296));
            label(img, n, x + width("Lv.") - 1, y, new Color(0xf6e296));
        }
        g.dispose();
        return img;
    }

    static int height() {
        load();
        return GLYPHS.get("0")[3];
    }

    /** 낱말 너비 (숫자는 글자마다 이어 붙임) */
    static int width(String word) {
        load();
        int[] g = GLYPHS.get(word);
        if (g != null) return g[2];
        int w = 0;
        for (char c : word.toCharArray()) w += GLYPHS.get(String.valueOf(c))[2] - 1;
        return w + 1;
    }

    /** 흰 글자를 색으로 바꿔 붙이고, 1 칸 짙은 그림자 · 외곽을 둘러 읽히게 */
    static void label(BufferedImage img, String word, int x, int y, Color color) {
        load();
        int[] g = GLYPHS.get(word);
        if (g == null) {   // 숫자 이어 붙이기
            for (char c : word.toCharArray()) {
                label(img, String.valueOf(c), x, y, color);
                x += GLYPHS.get(String.valueOf(c))[2] - 1;
            }
            return;
        }
        // 글자는 부드러운 가장자리 (알파) 를 그대로 섞는다 — 먼저 짙은 그림자, 다음 글자
        Color shadow = new Color(0x07080c);
        for (int pass = 0; pass < 2; pass++)
            for (int yy = 0; yy < g[3]; yy++)
                for (int xx = 0; xx < g[2]; xx++) {
                    int a = sheet.getRGB(g[0] + xx, g[1] + yy) >>> 24;
                    if (a == 0) continue;
                    if (pass == 0) {
                        for (int[] o : new int[][]{{1, 1}, {1, 0}, {0, 1}}) blend(img, x + xx + o[0], y + yy + o[1], shadow, Math.min(255, a * 2));
                    } else blend(img, x + xx, y + yy, color, a);
                }
    }

    static void blend(BufferedImage img, int px, int py, Color c, int a) {
        if (px < 0 || py < 0 || px >= S || py >= S) return;
        int d = img.getRGB(px, py);
        int r = ((d >> 16) & 255) * (255 - a) / 255 + c.getRed() * a / 255, gg = ((d >> 8) & 255) * (255 - a) / 255 + c.getGreen() * a / 255,
                b = (d & 255) * (255 - a) / 255 + c.getBlue() * a / 255;
        img.setRGB(px, py, 0xff000000 | (r << 16) | (gg << 8) | b);
    }
}
