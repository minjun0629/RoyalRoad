package io.versaera.pack;

import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.SplittableRandom;

/**
 * 아이템 아이콘 (16×16 픽셀 아트)을 코드로 그린다 (RP-01). 외부 그림 없이 — 같은 아이템 = 같은 그림.
 * 모양은 아이템의 태그 · 분류 · 재질로 정하고 (검 · 단검 · 도끼 · 창 · 활 · 지팡이 · 투구 · 갑옷 · 각반 · 장화 · 원석 · 주괴 · 보석 ·
 * 목재 · 천 · 가죽 · 약초 · 생선 · 물약 · 조각상 …), 색은 재질 · 능력(화염 · 냉기 · 번개 · 신성 · 암흑 · 독) · 원작 여부로 바꾼다.
 */
public final class PixelArt {
    private final int[] px = new int[256];
    private final SplittableRandom rng;

    private PixelArt(String seed) {
        rng = new SplittableRandom(seed.hashCode());
    }

    // ------------------------------------------------------------------ 모양 고르기
    public static String kind(ItemType t) {
        String m = t.material(), id = t.id();
        if (t.hasTag("axe") || t.hasTag("tool_axe") || (m.endsWith("_AXE") && t.category() != ItemCategory.TOOL)) return "axe";
        if (t.hasTag("tool_carving")) return "knife";
        if (t.hasTag("tool_sewing")) return "needle";
        if (t.hasTag("tool_hammer")) return "hammer";
        if (t.hasTag("tool_pick")) return "pickaxe";
        if (t.hasTag("tool_rod")) return "rod";
        if (t.hasTag("tool_alchemy")) return "mortar";
        if (t.hasTag("tool_cook")) return "pot";
        if (t.hasTag("bow")) return "bow";
        if (t.hasTag("staff")) return "staff";
        if (t.hasTag("spear")) return "spear";
        if (t.hasTag("shield")) return "shield";
        if (t.hasTag("dagger")) return "dagger";
        if (t.hasTag("sword") || m.endsWith("_SWORD")) return "sword";
        if (t.hasTag("crown")) return "crown";
        if (m.endsWith("_HELMET")) return "helmet";
        if (m.endsWith("_CHESTPLATE")) return "chest";
        if (m.endsWith("_LEGGINGS")) return "legs";
        if (m.endsWith("_BOOTS")) return "boots";
        if (t.hasTag("relic")) return "relic";
        if (t.hasTag("sculpture")) return "statue";
        if (t.hasTag("relief")) return "plaque";
        if (t.category() == ItemCategory.ARTWORK) return "chime";
        if (t.hasTag("potion")) return "potion";
        if (t.hasTag("bandage")) return "bandage";
        if (t.hasTag("whetstone")) return "whetstone";
        if (t.hasTag("meat")) return "meat";
        if (t.hasTag("bone")) return "bone";
        if (t.hasTag("scale")) return "scale";
        if (t.hasTag("blood")) return "drop";
        if (t.hasTag("horn")) return "horn";
        if (t.hasTag("ore")) return "ore";
        if (t.hasTag("sky")) return "scrap";
        if (t.hasTag("metal")) return "ingot";
        if (t.hasTag("gem") || t.hasTag("undead")) return "gem";
        if (t.hasTag("wood")) return "log";
        if (t.hasTag("stone")) return "block";
        if (t.hasTag("sand")) return "sand";
        if (t.hasTag("fiber")) return "fiber";
        if (t.hasTag("cloth")) return "cloth";
        if (t.hasTag("hide") || t.hasTag("leather")) return "hide";
        if (t.hasTag("dye")) return "dye";
        if (t.hasTag("herb")) return "herb";
        if (t.hasTag("fish")) return id.startsWith("grilled") ? "cooked_fish" : "fish";
        if (t.hasTag("seasoning")) return "powder";
        if (id.contains("stew")) return "stew";
        if (t.hasTag("grain")) return t.category() == ItemCategory.FOOD ? "bread" : "grain";
        if (t.category() == ItemCategory.FOOD) return "stew";
        return "blob";
    }

    /** 손에 들었을 때 막대처럼 기울여 드는 모양 (item/handheld) */
    public static boolean handheld(String kind) {
        return switch (kind) {
            case "sword", "dagger", "axe", "spear", "staff", "knife", "hammer", "pickaxe", "rod", "bone" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ 색
    private static Color metal(String material) {
        if (material.startsWith("GOLD")) return new Color(236, 196, 64);
        if (material.startsWith("DIAMOND")) return new Color(96, 222, 214);
        if (material.startsWith("NETHERITE")) return new Color(84, 74, 82);
        if (material.startsWith("CHAINMAIL")) return new Color(150, 152, 162);
        if (material.startsWith("LEATHER")) return new Color(146, 96, 54);
        return new Color(206, 210, 218);
    }

    private static Color accent(ItemType t) {
        var s = t.stats();
        if (s.getOrDefault("fire", 0) > 0) return new Color(244, 104, 34);
        if (s.getOrDefault("ice", 0) > 0) return new Color(150, 222, 255);
        if (s.getOrDefault("lightning", 0) > 0) return new Color(255, 236, 90);
        if (s.getOrDefault("holy", 0) > 0) return new Color(255, 246, 200);
        if (s.getOrDefault("dark", 0) > 0 || t.hasTag("cursed")) return new Color(132, 58, 168);
        if (s.getOrDefault("poison", 0) > 0) return new Color(96, 204, 76);
        return null;
    }

    /** id 에서 정한 색 (재료 · 음식처럼 재질 색이 없는 것) */
    private static Color hashed(String id, float sat, float bri) {
        float h = (id.hashCode() & 0xffff) / 65535f;
        return Color.getHSBColor(h, sat, bri);
    }

    private static Color shade(Color c, int d) {
        return new Color(cl(c.getRed() + d), cl(c.getGreen() + d), cl(c.getBlue() + d));
    }

    private static int cl(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ 그리기 기본
    private void set(int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < 16 && y < 16) px[y * 16 + x] = c.getRGB();
    }

    private void rect(int x, int y, int w, int h, Color c) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) set(i, j, c);
    }

    /** 결이 있는 사각형 (조금씩 다른 밝기) */
    private void tex(int x, int y, int w, int h, Color c, int noise) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) set(i, j, shade(c, rng.nextInt(-noise, noise + 1)));
    }

    /** 왼쪽 아래 → 오른쪽 위 대각선 (두께 w) */
    private void diag(int x0, int y0, int len, int w, Color c) {
        for (int k = 0; k < len; k++) for (int d = 0; d < w; d++) set(x0 + k + d, y0 - k, c);
    }

    private void disc(int cx, int cy, int r, Color c) {
        for (int y = -r; y <= r; y++) for (int x = -r; x <= r; x++) if (x * x + y * y <= r * r + r) set(cx + x, cy + y, c);
    }

    private BufferedImage image() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int a = px[y * 16 + x];
                if (a != 0) { img.setRGB(x, y, a); continue; }
                boolean near = false;   // 어두운 바탕에서도 보이게 1픽셀 외곽선
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && px[ny * 16 + nx] != 0) near = true;
                }
                if (near) img.setRGB(x, y, 0xFF1A1714);
            }
        return img;
    }

    // ------------------------------------------------------------------ 아이템
    public static BufferedImage item(ItemType t) {
        PixelArt a = new PixelArt(t.id());
        String k = kind(t);
        Color metal = metal(t.material()), acc = accent(t), wood = new Color(122, 82, 44), grip = new Color(70, 44, 30);
        Color edge = acc != null ? acc : shade(metal, 30);
        Color own = hashed(t.id(), 0.55f, 0.85f);
        switch (k) {
            case "sword" -> {
                a.diag(5, 10, 9, 2, metal);                       // 날
                a.diag(6, 9, 8, 1, edge);                         // 날 끝 빛
                a.diag(2, 14, 2, 2, grip);                        // 손잡이
                a.rect(3, 10, 4, 2, acc != null ? acc : new Color(196, 160, 60)); a.rect(4, 9, 2, 4, acc != null ? acc : new Color(196, 160, 60));   // 코등이
                a.set(1, 15, new Color(196, 160, 60));
            }
            case "dagger", "knife" -> {
                a.diag(6, 9, 6, 2, metal);
                a.diag(7, 8, 5, 1, edge);
                a.rect(4, 9, 3, 2, new Color(196, 160, 60));
                a.diag(2, 13, 3, 2, k.equals("knife") ? wood : grip);
            }
            case "axe" -> {
                a.diag(2, 14, 10, 1, wood); a.diag(3, 14, 9, 1, shade(wood, -20));
                a.tex(8, 1, 6, 7, metal, 10); a.rect(13, 0, 2, 9, edge);
                if (acc != null) a.rect(9, 3, 3, 3, acc);
            }
            case "spear" -> {
                a.diag(1, 15, 12, 1, wood);
                a.diag(11, 5, 4, 2, metal); a.set(15, 0, edge); a.set(14, 1, edge);
                a.rect(10, 5, 2, 2, new Color(196, 160, 60));
            }
            case "staff" -> {
                a.diag(2, 15, 11, 1, wood);
                Color orb = acc != null ? acc : new Color(130, 170, 255);
                a.disc(12, 3, 2, orb); a.set(11, 2, Color.WHITE);
                a.set(10, 5, new Color(196, 160, 60)); a.set(14, 5, new Color(196, 160, 60));
            }
            case "bow" -> {
                for (int i = 0; i < 12; i++) { int x = 3 + (int) Math.round(Math.sin(i / 11.0 * Math.PI) * 6); a.set(x, 2 + i, wood); a.set(x + 1, 2 + i, shade(wood, -15)); }
                for (int y = 2; y < 14; y++) a.set(3, y, new Color(230, 230, 220));   // 시위
                if (acc != null) { a.set(9, 7, acc); a.set(9, 8, acc); }
            }
            case "shield" -> {
                a.tex(3, 2, 10, 9, own, 8); a.tex(4, 11, 8, 2, own, 8); a.rect(6, 13, 4, 1, own);
                a.rect(3, 2, 10, 1, metal); a.rect(7, 4, 2, 6, metal); a.rect(5, 6, 6, 2, metal);
            }
            case "hammer" -> { a.diag(2, 14, 9, 1, wood); a.tex(7, 1, 8, 5, metal, 8); }
            case "pickaxe" -> {
                a.diag(2, 14, 10, 1, wood);
                for (int i = 0; i < 11; i++) { int y = 2 + Math.abs(i - 5) / 2; a.set(4 + i, y, metal); a.set(4 + i, y + 1, shade(metal, -25)); }
            }
            case "rod" -> { a.diag(2, 15, 13, 1, wood); for (int y = 2; y < 12; y++) a.set(15, y, new Color(220, 220, 220)); a.set(15, 12, metal); }
            case "needle" -> { a.diag(4, 13, 9, 1, metal); a.set(13, 4, Color.WHITE); for (int i = 0; i < 6; i++) a.set(3 + i, 6 + (i % 2), own); }
            case "mortar" -> { a.tex(3, 8, 10, 6, new Color(170, 160, 150), 10); a.rect(2, 7, 12, 1, new Color(190, 180, 170)); a.diag(8, 9, 6, 1, wood); }
            case "pot" -> { a.tex(2, 6, 12, 8, new Color(70, 70, 76), 6); a.rect(1, 5, 14, 1, new Color(110, 110, 116)); a.rect(4, 6, 8, 2, new Color(200, 120, 60)); }
            case "helmet", "crown" -> {
                if (k.equals("crown")) {
                    a.tex(2, 8, 12, 5, metal, 10);
                    for (int x = 2; x < 14; x += 3) a.rect(x, 4, 2, 4, metal);
                    a.set(4, 10, new Color(220, 40, 40)); a.set(8, 10, new Color(60, 120, 230)); a.set(11, 10, new Color(60, 200, 90));
                } else {
                    a.tex(3, 3, 10, 9, metal, 8); a.rect(2, 7, 1, 6, metal); a.rect(13, 7, 1, 6, metal);
                    a.rect(5, 8, 6, 2, new Color(30, 26, 30));   // 눈구멍
                    if (acc != null) a.rect(7, 3, 2, 5, acc);
                }
            }
            case "chest" -> {
                a.tex(3, 3, 10, 11, metal, 8); a.rect(1, 3, 2, 5, metal); a.rect(13, 3, 2, 5, metal);
                for (int x = 6; x < 10; x++) a.px[3 * 16 + x] = 0;   // 목 트임
                a.rect(7, 6, 2, 7, shade(metal, -30));
                if (acc != null) a.rect(5, 8, 6, 2, acc);
            }
            case "legs" -> {
                a.tex(4, 2, 8, 4, metal, 8); a.tex(4, 6, 3, 9, metal, 8); a.tex(9, 6, 3, 9, metal, 8);
                a.rect(4, 2, 8, 1, shade(metal, -35));
            }
            case "boots" -> {
                a.tex(2, 5, 4, 7, metal, 8); a.tex(2, 12, 6, 2, metal, 8);
                a.tex(9, 5, 4, 7, metal, 8); a.tex(9, 12, 6, 2, metal, 8);
                if (acc != null || t.stats().containsKey("speed")) { a.set(1, 7, Color.WHITE); a.set(8, 7, Color.WHITE); a.set(0, 8, Color.WHITE); }
            }
            case "statue", "relic" -> {
                Color st = k.equals("relic") ? new Color(232, 214, 150) : new Color(226, 222, 214);
                a.tex(4, 12, 8, 3, shade(st, -40), 6);           // 받침
                a.tex(6, 6, 4, 6, st, 8); a.disc(8, 4, 2, st);   // 몸 · 머리
                a.rect(5, 7, 1, 3, st); a.rect(10, 7, 1, 3, st);
                if (k.equals("relic")) { a.set(3, 2, new Color(255, 240, 140)); a.set(12, 3, new Color(255, 240, 140)); a.set(13, 9, new Color(255, 240, 140)); }
            }
            case "plaque" -> { a.tex(2, 3, 12, 10, new Color(150, 110, 70), 8); a.tex(4, 5, 8, 6, new Color(214, 208, 196), 10); a.disc(8, 8, 1, new Color(170, 165, 155)); }
            case "chime" -> { a.rect(7, 1, 2, 3, new Color(150, 150, 150)); for (int i = 0; i < 4; i++) a.rect(3 + i * 3, 4, 2, 6 + (i % 2) * 3, new Color(170, 210, 230)); }
            case "potion" -> {
                Color liq = t.hasTag("potion_t3") ? new Color(200, 40, 80) : t.hasTag("potion_t2") ? new Color(230, 70, 70) : new Color(240, 140, 120);
                a.rect(7, 1, 2, 2, new Color(130, 90, 50)); a.rect(6, 3, 4, 2, new Color(200, 220, 230));
                a.disc(8, 10, 4, new Color(200, 220, 230)); a.disc(8, 11, 3, liq); a.set(6, 9, Color.WHITE);
            }
            case "bandage" -> { a.tex(3, 4, 10, 8, new Color(240, 236, 226), 6); a.rect(3, 7, 10, 2, new Color(214, 206, 190)); a.rect(7, 5, 2, 6, new Color(200, 40, 40)); a.rect(5, 7, 6, 2, new Color(200, 40, 40)); }
            case "whetstone" -> { a.tex(2, 6, 12, 5, new Color(120, 120, 128), 12); a.rect(2, 6, 12, 1, new Color(170, 170, 178)); }
            case "meat" -> { a.disc(8, 8, 5, new Color(200, 70, 70)); a.disc(7, 7, 2, new Color(240, 200, 200)); a.rect(12, 11, 3, 2, new Color(236, 230, 214)); }
            case "bone" -> { a.diag(3, 12, 9, 2, new Color(236, 230, 214)); a.disc(3, 12, 1, new Color(236, 230, 214)); a.disc(12, 3, 1, new Color(236, 230, 214)); }
            case "scale" -> { for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) a.disc(4 + c * 4, 4 + r * 4, 2, shade(own, (r + c) * 8 - 10)); }
            case "drop" -> { a.disc(8, 10, 4, new Color(150, 10, 20)); for (int y = 2; y < 7; y++) a.rect(8 - (y - 2) / 2, y, 1 + (y - 2), 1, new Color(150, 10, 20)); a.set(6, 9, new Color(230, 90, 90)); }
            case "horn" -> { for (int i = 0; i < 11; i++) a.rect(3 + i, 13 - i - (i * i) / 20, Math.max(1, 4 - i / 3), 2, shade(new Color(90, 30, 30), i * 6)); }
            case "ore" -> { a.tex(3, 4, 10, 9, new Color(110, 108, 104), 14); for (int i = 0; i < 6; i++) a.rect(4 + a.rng.nextInt(8), 5 + a.rng.nextInt(7), 2, 1, own); }
            case "ingot" -> {
                Color c = t.id().contains("silver") ? new Color(220, 226, 236) : t.id().contains("gold") ? new Color(240, 200, 60) : new Color(206, 210, 218);
                a.tex(2, 7, 12, 5, c, 6); a.rect(4, 5, 8, 2, shade(c, 25)); a.rect(2, 11, 12, 1, shade(c, -40));
            }
            case "scrap" -> { a.tex(4, 4, 8, 8, new Color(70, 80, 120), 18); a.set(6, 6, new Color(200, 220, 255)); a.set(9, 9, new Color(200, 220, 255)); }
            case "gem" -> {
                Color c = t.hasTag("frost") ? new Color(160, 230, 255) : t.hasTag("undead") ? new Color(40, 160, 140) : own;
                for (int y = 0; y < 10; y++) { int w = y < 4 ? 2 + y * 2 : 10 - (y - 4) * 2 + 2; a.rect(8 - w / 2, 3 + y, Math.max(1, w), 1, shade(c, (y < 4 ? 30 : -10))); }
                a.set(7, 5, Color.WHITE);
            }
            case "log" -> { a.tex(2, 4, 12, 9, t.id().contains("highland") ? new Color(90, 62, 36) : new Color(120, 86, 50), 10); a.disc(13, 8, 3, new Color(196, 160, 110)); a.disc(13, 8, 1, new Color(160, 120, 80)); }
            case "block" -> { Color c = t.hasTag("marble") ? new Color(232, 230, 226) : new Color(216, 200, 150); a.tex(2, 2, 12, 12, c, 10); a.rect(2, 7, 12, 1, shade(c, -30)); a.rect(8, 2, 1, 5, shade(c, -30)); a.rect(5, 8, 1, 6, shade(c, -30)); }
            case "sand" -> { for (int i = 0; i < 70; i++) a.set(3 + a.rng.nextInt(10), 6 + a.rng.nextInt(8), shade(new Color(224, 210, 160), a.rng.nextInt(-20, 20))); }
            case "fiber" -> { for (int i = 0; i < 5; i++) a.diag(3 + i, 13, 9, 1, shade(new Color(220, 210, 170), i * 6)); }
            case "cloth" -> { a.tex(2, 3, 12, 10, new Color(236, 232, 220), 6); for (int x = 3; x < 14; x += 3) a.rect(x, 3, 1, 10, new Color(214, 206, 190)); }
            case "hide" -> { Color c = t.hasTag("leather") ? new Color(150, 98, 54) : new Color(186, 140, 100); a.tex(3, 3, 10, 10, c, 10); a.rect(1, 2, 3, 3, c); a.rect(12, 2, 3, 3, c); a.rect(1, 11, 3, 3, c); a.rect(12, 11, 3, 3, c); }
            case "dye" -> { Color c = t.id().contains("indigo") ? new Color(40, 60, 170) : new Color(180, 30, 40); a.disc(8, 9, 4, c); a.rect(6, 3, 4, 3, new Color(200, 200, 200)); }
            case "herb" -> { Color c = t.id().contains("salt") ? new Color(170, 150, 110) : new Color(80, 160, 90); a.rect(8, 6, 1, 9, new Color(60, 110, 50)); a.disc(5, 6, 2, c); a.disc(11, 5, 2, c); a.disc(8, 3, 2, shade(c, 20)); }
            case "grain" -> { for (int i = 0; i < 3; i++) { a.diag(4 + i * 2, 15, 8, 1, new Color(170, 150, 60)); a.disc(11 + i, 4 - i / 2, 1, new Color(226, 196, 90)); } }
            case "fish", "cooked_fish" -> {
                Color c = k.equals("cooked_fish") ? new Color(196, 120, 60) : t.hasTag("deep") ? new Color(60, 70, 110) : new Color(220, 120, 100);
                a.tex(3, 6, 9, 5, c, 8); a.rect(12, 5, 3, 7, shade(c, -20)); a.set(5, 7, Color.BLACK);
            }
            case "powder" -> { a.disc(8, 10, 4, new Color(236, 236, 236)); a.set(6, 8, new Color(200, 200, 210)); a.set(10, 11, new Color(200, 200, 210)); }
            case "stew" -> { a.tex(2, 8, 12, 5, new Color(120, 80, 46), 6); a.rect(3, 7, 10, 2, t.id().contains("fish") ? new Color(220, 140, 80) : new Color(170, 90, 50)); a.set(6, 7, new Color(240, 200, 120)); a.set(9, 7, new Color(110, 170, 80)); }
            case "bread" -> { a.tex(2, 6, 12, 6, new Color(200, 150, 80), 8); a.rect(3, 5, 10, 1, new Color(220, 170, 100)); for (int x = 4; x < 13; x += 3) a.set(x, 7, new Color(160, 110, 50)); }
            default -> { a.disc(8, 8, 5, own); a.set(6, 6, Color.WHITE); }
        }
        // 원작에 이름이 나온 물건: 오른쪽 위에 금빛 반짝임
        if ("CANON".equals(t.source()) && t.category().unique()) { a.set(14, 1, new Color(255, 230, 120)); a.set(13, 2, new Color(255, 250, 200)); a.set(15, 2, new Color(255, 230, 120)); }
        return a.image();
    }
}
