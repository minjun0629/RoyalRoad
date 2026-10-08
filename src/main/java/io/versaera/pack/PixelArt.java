package io.versaera.pack;

import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * 아이템 아이콘 (32×32 픽셀 아트, {@link Canvas} 의 자동 음영)을 코드로 그린다 (RP-01). 외부 그림 없이 — 같은 아이템 = 같은 그림.
 * 모양은 아이템의 태그 · 분류 · 재질로 정하고 (검 · 단검 · 도끼 · 창 · 활 · 지팡이 · 투구 · 갑옷 · 각반 · 장화 · 원석 · 주괴 · 보석 ·
 * 목재 · 천 · 가죽 · 약초 · 생선 · 물약 · 조각상 …), 색은 재질 · 능력(화염 · 냉기 · 번개 · 신성 · 암흑 · 독) · 원작 여부로 바꾼다.
 */
public final class PixelArt {
    private PixelArt() {
    }

    // ------------------------------------------------------------------ 모양 고르기
    public static String kind(ItemType t) {
        String m = t.material(), id = t.id();
        for (String k : ACCESSORY_KINDS) if (t.hasTag(k)) return k;
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
        if (t.hasTag("helmet")) return "helmet";
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

    static final Color PALE_WOOD = new Color(204, 160, 102), ROPE = new Color(196, 178, 128);

    /** 수련용 무기: 쇠 · 놋쇠 없이 밝은 나무와 끈으로 — 진짜 무기와 한눈에 구별된다 */
    private static BufferedImage practice(Canvas c, String k) {
        if (k.equals("bow")) {
            Canvas.Layer limb = c.layer();
            for (int i = 0; i <= 40; i++) { double a = Math.PI * i / 40; limb.line(7 + Math.sin(a) * 12, 4 + i * 0.6, 7 + Math.sin(a) * 12, 4 + i * 0.6, 2.2); }
            limb.commit(R(PALE_WOOD), 0.2);
            c.layer().rect(17, 14, 3, 5).commit(R(ROPE), 0.3);                                            // 끈 감은 손잡이
            for (int y = 15; y <= 18; y += 2) c.set(17, y, Canvas.ramp(ROPE)[0]);
            for (int y = 4; y <= 28; y++) c.set(7, y, new Color(214, 206, 186));                          // 삼베 시위
            c.layer().ellipse(7.5, 4, 1.2, 1.2).ellipse(7.5, 28, 1.2, 1.2).commit(R(DARKWOOD), 0);
            return c.image(true);
        }
        // 목검: 둥근 끝 · 나뭇결 · 막대 코등이 · 끈 감은 손잡이
        c.layer().line(6.5, 25.5, 4, 28, 3.2).commit(R(ROPE), 0.3);
        for (int i = 0; i < 3; i++) c.set(5 + i, 27 - i, Canvas.ramp(ROPE)[0]);
        c.layer().ellipse(3.4, 28.6, 1.5, 1.5).commit(R(DARKWOOD), 0);
        c.layer().line(4.5, 21.5, 10.5, 27.5, 2.4).commit(R(DARKWOOD), 0.1);
        c.layer().line(9, 23, 26, 6, 4.2).ellipse(26, 6, 2.1, 2.1).commit(R(PALE_WOOD), 0.15);
        Color grain = Canvas.ramp(PALE_WOOD)[0];
        for (int i = 0; i < 14; i += 3) { c.set(11 + i, 21 - i, grain); c.set(12 + i, 20 - i, grain); }
        return c.image(true);
    }

    /** 장신구 · 원작 물건의 모양 (태그 이름 = 모양 이름) */
    static final java.util.List<String> ACCESSORY_KINDS = java.util.List.of("ring", "necklace", "bracelet", "gloves", "cloak", "belt", "pauldron", "orb",
            "harp", "fan", "cup", "book", "mirror", "map", "rake", "plow", "watering_can", "pickaxe_weapon", "whip", "scythe", "mace", "torch", "arrow",
            "hammer_chisel", "key", "flag", "seal", "crest", "skull", "plate", "compass", "furnace", "feather");

    static void cutEllipse(Canvas.Layer l, double cx, double cy, double rx, double ry) {
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                if (dx * dx + dy * dy <= 1) l.cut(x, y);
            }
    }

    /** 장신구 · 원작 물건 그림. 없는 모양이면 false */
    static boolean accessory(Canvas c, String k, Color metal, Color acc, Color own) {
        Color gem = acc != null ? acc : own;
        switch (k) {
            case "ring" -> {
                Canvas.Layer band = c.layer().ellipse(16, 19, 9, 9);
                cutEllipse(band, 16, 19, 6.2, 6.2);
                band.commit(R(GOLD), 0);
                c.layer().ellipse(16, 9.5, 4.2, 4.2).commit(R(gem), 0);                                       // 보석
                c.layer().rect(13, 12, 7, 2).commit(R(GOLD), 0);                                              // 물림쇠
                c.set(15, 8, Color.WHITE);
            }
            case "necklace" -> {
                for (int i = 0; i <= 20; i++) {                                                               // 늘어진 사슬
                    double a = Math.PI * i / 20;
                    int x = (int) Math.round(16 - Math.cos(a) * 11), y = (int) Math.round(5 + Math.sin(a) * 15);
                    if (i % 2 == 0) c.layer().ellipse(x + 0.5, y + 0.5, 1.1, 1.1).commit(R(GOLD), 0);
                }
                c.layer().poly(new double[]{16, 21, 16, 11}, new double[]{18, 23, 30, 23}).commit(R(gem), 0.05);   // 펜던트
                c.set(15, 21, Color.WHITE);
            }
            case "bracelet" -> {
                Canvas.Layer band = c.layer().ellipse(16, 17, 12, 8);
                cutEllipse(band, 16, 17, 8.5, 4.6);
                band.commit(R(k.equals("bracelet") && acc == null ? GOLD : metal), 0.05);
                for (int x = 8; x <= 24; x += 4) c.layer().ellipse(x + 0.5, x == 16 ? 25.5 : 24.2 - Math.abs(x - 16) * 0.35, 1.5, 1.5).commit(R(gem), 0);
            }
            case "gloves" -> {
                c.layer().rect(9, 16, 13, 12).ellipse(15.5, 16, 6.5, 4).commit(R(LEATHER), 0.25);               // 손등
                for (int i = 0; i < 4; i++) c.layer().rect(9 + i * 3, 6 + Math.abs(i - 1), 3, 11).commit(R(LEATHER), 0.25);   // 손가락
                c.layer().line(21, 18, 26, 13, 3.2).commit(R(LEATHER), 0.25);                                  // 엄지
                c.layer().rect(8, 26, 15, 3).commit(R(acc != null ? acc : WRAP), 0.1);                        // 손목
            }
            case "cloak" -> {
                c.layer().poly(new double[]{11, 21, 28, 4}, new double[]{4, 4, 29, 29}).commit(R(new Color(118, 118, 126)), 0.3);
                c.layer().rect(10, 3, 12, 3).commit(R(DARKWOOD), 0.1);                                        // 깃
                c.layer().ellipse(16, 5, 1.8, 1.8).commit(R(GOLD), 0);                                       // 여밈
                for (int y = 9; y < 28; y += 3) c.set(16 + (y % 2), y, Canvas.ramp(new Color(118, 118, 126))[0]);
            }
            case "orb" -> {
                Color glass = acc != null ? acc : new Color(150, 90, 200);
                c.layer().ellipse(16, 14, 10, 10).commit(R(glass), 0.05);
                c.layer().rect(10, 24, 12, 3).rect(12, 27, 8, 2).commit(R(DARKWOOD), 0.2);                   // 받침
                for (int i = 0; i < 8; i++) c.set(12 + i, 8 + (i * 7) % 11, Canvas.ramp(glass)[0]);          // 금
                c.set(12, 9, Color.WHITE);
                c.set(13, 9, Color.WHITE);
            }
            case "harp" -> {
                c.layer().line(7, 27, 9, 5, 3).line(9, 5, 25, 11, 3).commit(R(GOLD), 0.05);                  // 기둥 · 목
                c.layer().line(7, 27, 25, 27, 3).line(25, 11, 25, 27, 2.6).commit(R(WOOD), 0.15);
                for (int x = 11; x <= 23; x += 3) for (int y = 7 + (x - 9) * 6 / 16; y <= 25; y++) c.set(x, y, new Color(236, 232, 220));   // 줄
            }
            case "fan" -> {
                Canvas.Layer leaf = c.layer();
                for (int i = 0; i <= 8; i++) {
                    double a = Math.PI * (0.15 + 0.7 * i / 8);
                    leaf.line(16, 26, 16 - Math.cos(a) * 14, 26 - Math.sin(a) * 14, 3.4);
                }
                leaf.commit(R(gem), 0.1);
                for (int i = 0; i <= 8; i += 2) {
                    double a = Math.PI * (0.15 + 0.7 * i / 8);
                    c.layer().line(16, 26, 16 - Math.cos(a) * 13, 26 - Math.sin(a) * 13, 1).commit(R(DARKWOOD), 0);
                }
                c.layer().ellipse(16, 26, 2, 2).commit(R(GOLD), 0);
            }
            case "cup" -> {
                c.layer().poly(new double[]{7, 25, 21, 11}, new double[]{5, 5, 17, 17}).commit(R(GOLD), 0.05);   // 잔
                c.layer().rect(14, 17, 4, 7).commit(R(GOLD), 0.05);                                          // 대
                c.layer().ellipse(16, 26, 7, 2.6).commit(R(GOLD), 0.05);                                     // 받침
                c.layer().ellipse(16, 6, 8, 1.6).commit(R(new Color(150, 20, 30)), 0);                       // 붉은 술
                c.layer().ellipse(16, 11, 2, 2).commit(R(gem), 0);
            }
            case "book" -> {
                c.layer().rect(6, 5, 20, 23).commit(R(new Color(110, 40, 40)), 0.15);                        // 표지
                c.layer().rect(24, 6, 3, 21).commit(R(new Color(236, 226, 196)), 0.2);                       // 책장
                c.layer().rect(6, 5, 3, 23).commit(R(DARKWOOD), 0.1);                                        // 책등
                c.layer().ellipse(16, 16, 4.5, 4.5).commit(R(GOLD), 0);                                     // 문양
                c.layer().ellipse(16, 16, 2, 2).commit(R(gem), 0);
            }
            case "mirror" -> {
                c.layer().ellipse(16, 13, 10, 11).commit(R(GOLD), 0.05);
                c.layer().ellipse(16, 13, 7.5, 8.5).commit(R(new Color(170, 210, 230)), 0);
                c.layer().line(16, 23, 16, 30, 3.2).commit(R(GOLD), 0.05);
                for (int i = 0; i < 4; i++) c.set(12 + i, 8 + i, Color.WHITE);
            }
            case "map" -> {
                c.layer().poly(new double[]{4, 13, 20, 28, 28, 20, 13, 4}, new double[]{6, 4, 6, 4, 26, 28, 26, 28}).commit(R(new Color(222, 200, 150)), 0.25);
                for (int i = 0; i < 12; i++) c.set(8 + i, 12 + (int) Math.round(Math.sin(i * 0.8) * 3), new Color(150, 60, 40));   // 길
                c.layer().line(20, 18, 24, 22, 1.2).line(24, 18, 20, 22, 1.2).commit(R(new Color(200, 30, 30)), 0);                // X
            }
            case "rake" -> {
                c.layer().line(5, 28, 22, 11, 2.6).commit(R(WOOD), 0.2);
                c.layer().line(17, 6, 28, 17, 2.4).commit(R(metal.equals(IRON) ? new Color(214, 218, 224) : metal), 0.05);
                for (int i = 0; i < 4; i++) c.layer().line(18 + i * 3, 7 + i * 3, 21 + i * 3, 4 + i * 3, 1.6).commit(R(new Color(214, 218, 224)), 0);
            }
            case "plow" -> {
                c.layer().line(4, 8, 22, 22, 2.8).commit(R(WOOD), 0.2);                                      // 손잡이 자루
                c.layer().poly(new double[]{18, 29, 25, 14}, new double[]{18, 22, 29, 26}).commit(R(metal), 0.05);   // 보습
                c.layer().line(4, 8, 8, 4, 2.4).commit(R(DARKWOOD), 0.1);
            }
            case "watering_can" -> {
                c.layer().rect(8, 12, 14, 14).ellipse(15, 12, 7, 2.5).commit(R(new Color(140, 170, 200)), 0.05);
                c.layer().line(22, 20, 29, 10, 2.4).commit(R(new Color(140, 170, 200)), 0.05);               // 주둥이
                c.layer().ellipse(29, 9, 2.2, 1.6).commit(R(new Color(200, 220, 240)), 0);
                Canvas.Layer handle = c.layer().ellipse(15, 9, 7, 5);
                cutEllipse(handle, 15, 9.5, 5, 3.6);
                handle.cutRect(0, 10, 32, 22);
                handle.commit(R(new Color(110, 130, 160)), 0);
                for (int i = 0; i < 3; i++) c.set(25 + i * 2, 4 + i, new Color(120, 190, 255));            // 물방울
            }
            case "pickaxe_weapon" -> {
                c.layer().line(6, 27, 21, 12, 3).commit(R(WRAP), 0.25);
                c.layer().poly(new double[]{14, 21, 30, 23}, new double[]{9, 4, 3, 12}).commit(R(metal), 0.05);   // 강철 부리
                c.set(29, 3, Color.WHITE);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** 손에 들었을 때 막대처럼 기울여 드는 모양 (item/handheld) */
    public static boolean handheld(String kind) {
        return switch (kind) {
            case "sword", "dagger", "axe", "spear", "staff", "knife", "hammer", "pickaxe", "rod", "bone", "rake", "plow", "pickaxe_weapon", "mace", "scythe",
                 "torch", "hammer_chisel" -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ 색
    static final Color IRON = new Color(176, 184, 196), GOLD = new Color(232, 186, 58), DIAMOND = new Color(84, 214, 214),
            NETHERITE = new Color(78, 68, 80), CHAIN = new Color(140, 144, 156), LEATHER = new Color(150, 96, 52),
            WOOD = new Color(132, 88, 48), DARKWOOD = new Color(86, 56, 34), BRASS = new Color(206, 160, 62), BONE = new Color(226, 218, 196),
            CLOTH = new Color(232, 226, 210), WRAP = new Color(96, 52, 40);

    private static Color metal(String material) {
        if (material.startsWith("GOLD")) return GOLD;
        if (material.startsWith("DIAMOND")) return DIAMOND;
        if (material.startsWith("NETHERITE")) return NETHERITE;
        if (material.startsWith("CHAINMAIL")) return CHAIN;
        if (material.startsWith("LEATHER")) return LEATHER;
        return IRON;
    }

    /** 능력 속성색 (없으면 null) */
    static Color accent(ItemType t) {
        var s = t.stats();
        if (s.getOrDefault("fire", 0) > 0) return new Color(240, 96, 30);
        if (s.getOrDefault("ice", 0) > 0) return new Color(130, 210, 255);
        if (s.getOrDefault("lightning", 0) > 0) return new Color(250, 226, 70);
        if (s.getOrDefault("holy", 0) > 0) return new Color(255, 236, 160);
        if (s.getOrDefault("dark", 0) > 0 || t.hasTag("cursed")) return new Color(140, 60, 190);
        if (s.getOrDefault("poison", 0) > 0) return new Color(96, 200, 70);
        return null;
    }

    private static Color hashed(String id, float sat, float bri) {
        return Color.getHSBColor((id.hashCode() & 0xffff) / 65535f, sat, bri);
    }

    private static Color[] R(Color c) {
        return Canvas.ramp(c);
    }

    // ------------------------------------------------------------------ 아이템 (32×32)
    public static BufferedImage item(ItemType t) {
        Canvas c = new Canvas(32, 32, t.id());
        String k = kind(t);
        Color metal = metal(t.material()), acc = accent(t), own = hashed(t.id(), 0.55f, 0.8f);
        Color guard = acc != null ? acc : BRASS;
        boolean canon = "CANON".equals(t.source()) && t.category().unique();
        // 장비 · 장신구 · 성물: 16 칸 손 픽셀 (IconSmith). 재료는 아래 그림 그대로
        if (IconSmith.handles(k) && (t.category().unique() || ACCESSORY_KINDS.contains(k))) {
            BufferedImage img = IconSmith.draw(t, k);
            if (img != null) return img;
        }
        if (accessory(c, k, metal, acc, own)) {
            if (canon) c.sparkle(28, 3, new Color(255, 226, 110));
            return c.image(true);
        }
        switch (k) {
            case "sword" -> {
                boolean big = t.stats().getOrDefault("attack", 0) >= 30;
                c.layer().line(6.5, 25.5, 4, 28, 3.2).commit(R(WRAP), 0.3);                                   // 손잡이
                c.layer().ellipse(3.2, 28.8, 1.8, 1.8).commit(R(guard), 0);                                   // 폼멜
                c.layer().line(4, 21, 11, 28, 3).commit(R(guard), 0);                                         // 코등이
                c.layer().poly(new double[]{8, 10.5 + (big ? 1 : 0), 29, 27}, new double[]{21.5 + (big ? 1 : 0), 24, 3, 3.5}).line(9, 23, 28, 3.5, big ? 5 : 3.6).commit(R(metal), 0.05);
                for (int i = 0; i < 15; i++) c.set(11 + i, 20 - i, Canvas.ramp(metal)[0]);                       // 피 홈
                if (acc != null) for (int i = 0; i < 12; i += 2) c.set(13 + i, 17 - i, acc);                    // 속성 빛
                c.set(27, 4, Color.WHITE);
            }
            case "dagger", "knife" -> {
                c.layer().line(9, 23, 5, 27, 3.4).commit(R(k.equals("knife") ? WOOD : WRAP), 0.3);
                c.layer().line(7, 19, 13, 25, 2.6).commit(R(guard), 0);
                c.layer().poly(new double[]{10, 13, 24, 22}, new double[]{20.5, 23, 8, 7}).commit(R(metal), 0.05);
                if (acc != null) for (int i = 0; i < 8; i += 2) c.set(13 + i, 19 - i, acc);
                c.set(23, 8, Color.WHITE);
            }
            case "axe" -> {
                c.layer().line(5, 28, 22, 6, 2.8).commit(R(WOOD), 0.3);
                c.layer().poly(new double[]{17, 27, 30, 28, 18}, new double[]{6, 2, 10, 18, 13}).commit(R(metal), 0.08);
                c.layer().poly(new double[]{16, 12, 14, 18}, new double[]{8, 6, 13, 12}).commit(R(metal), 0.08);   // 뒷날
                c.layer().rect(18, 7, 3, 6).commit(R(DARKWOOD), 0);
                if (acc != null) for (int y = 5; y < 16; y += 2) c.set(28, y, acc);
            }
            case "spear" -> {
                c.layer().line(3, 29, 23, 9, 2.4).commit(R(WOOD), 0.3);
                c.layer().line(21, 11, 24, 8, 3.4).commit(R(BRASS), 0);
                c.layer().poly(new double[]{22, 29, 25}, new double[]{10, 3, 7}).poly(new double[]{22, 29, 26}, new double[]{10, 3, 6}).line(23, 8, 28.5, 3.5, 3.2).commit(R(metal), 0.05);
            }
            case "staff" -> {
                c.layer().line(4, 29, 21, 12, 2.6).commit(R(DARKWOOD), 0.35);
                c.layer().line(18, 9, 25, 16, 2).line(20, 6, 28, 13, 1.6).commit(R(BRASS), 0);                // 갈래 받침
                Color orb = acc != null ? acc : new Color(120, 160, 255);
                c.layer().ellipse(24, 8, 4.2, 4.2).commit(R(orb), 0);
                c.set(22, 6, Color.WHITE); c.set(23, 6, Color.WHITE); c.set(22, 7, Canvas.ramp(orb)[4]);
                c.sparkle(29, 3, Canvas.ramp(orb)[4]);
            }
            case "bow" -> {
                Canvas.Layer limb = c.layer();
                for (int i = 0; i <= 40; i++) { double a = Math.PI * i / 40; limb.line(6 + Math.sin(a) * 15, 3 + i * 0.65, 6 + Math.sin(a) * 15, 3 + i * 0.65, 2.6); }
                limb.commit(R(acc != null ? Canvas.mix(WOOD, acc, 0.35) : WOOD), 0.2);
                c.layer().rect(19, 14, 3, 5).commit(R(WRAP), 0.2);                                            // 손잡이
                for (int y = 3; y <= 29; y++) c.set(6, y, new Color(236, 232, 220));                          // 시위
                c.layer().ellipse(6.5, 3, 1.5, 1.5).ellipse(6.5, 29, 1.5, 1.5).commit(R(BRASS), 0);
                if (acc != null) { c.sparkle(25, 8, acc); }
            }
            case "shield" -> {
                c.layer().poly(new double[]{5, 27, 27, 16, 5}, new double[]{4, 4, 18, 29, 18}).commit(R(own), 0.15);
                c.layer().rect(14, 5, 4, 22).rect(6, 11, 20, 4).commit(R(metal), 0.05);
            }
            case "hammer" -> {
                c.layer().line(5, 28, 20, 13, 2.8).commit(R(WOOD), 0.3);
                c.layer().poly(new double[]{13, 22, 29, 20}, new double[]{9, 2, 11, 18}).commit(R(metal), 0.12);
            }
            case "pickaxe" -> {
                c.layer().line(5, 28, 19, 14, 2.8).commit(R(WOOD), 0.3);
                Canvas.Layer head = c.layer();
                for (int i = 0; i <= 30; i++) { double a = Math.PI * (0.15 + 0.7 * i / 30.0); head.line(17 - Math.cos(a) * 13, 14 - Math.sin(a) * 11, 17 - Math.cos(a) * 13, 14 - Math.sin(a) * 11, 3); }
                head.commit(R(metal), 0.05);
            }
            case "rod" -> {
                c.layer().line(3, 30, 27, 4, 2).commit(R(WOOD), 0.25);
                c.layer().rect(4, 24, 4, 3).commit(R(WRAP), 0.2);
                for (int y = 5; y < 22; y++) c.set(28, y, new Color(226, 226, 226));
                c.layer().ellipse(28, 23, 1.6, 1.6).commit(R(new Color(200, 50, 40)), 0);
            }
            case "needle" -> {
                c.layer().line(6, 26, 25, 7, 1.8).commit(R(metal), 0);
                Canvas.Layer th = c.layer();
                for (int i = 0; i < 24; i++) th.px(5 + i, 12 + (int) Math.round(Math.sin(i / 3.0) * 3));
                th.commit(R(own), 0);
                c.set(24, 8, Color.WHITE);
            }
            case "mortar" -> {
                c.layer().poly(new double[]{5, 27, 24, 8}, new double[]{15, 15, 27, 27}).commit(R(new Color(176, 168, 158)), 0.35);
                c.layer().ellipse(16, 15, 11, 3).commit(R(new Color(120, 112, 104)), 0.2);
                c.layer().line(15, 15, 26, 4, 3.4).commit(R(WOOD), 0.3);
            }
            case "pot" -> {
                c.layer().ellipse(16, 19, 12, 9).commit(R(new Color(72, 72, 80)), 0.15);
                c.layer().ellipse(16, 12, 12, 3).commit(R(new Color(96, 96, 104)), 0);
                c.layer().ellipse(16, 12, 9, 2).commit(R(new Color(204, 120, 60)), 0.2);
                c.layer().rect(2, 13, 3, 2).rect(27, 13, 3, 2).commit(R(new Color(72, 72, 80)), 0);
            }
            case "helmet" -> {
                c.layer().ellipse(16, 14, 11.5, 11).rect(5, 14, 22, 12).poly(new double[]{4.5, 27.5, 24, 8}, new double[]{25, 25, 29, 29}).commit(R(metal), 0.05);
                Color hole = new Color(22, 18, 26);
                c.layer().rect(7, 13, 18, 3).rect(13, 13, 6, 12).commit(new Color[]{hole, hole, hole, Canvas.mix(hole, metal, 0.15), Canvas.mix(hole, metal, 0.3)}, 0);
                c.layer().line(16, 3.5, 16, 11, 1.6).commit(R(Canvas.ramp(metal)[3]), 0);                    // 이마 능선
                for (int y = 18; y < 25; y += 2) { c.set(9, y, Canvas.ramp(metal)[0]); c.set(22, y, Canvas.ramp(metal)[0]); }   // 숨구멍
                if (acc != null) c.layer().ellipse(16, 9, 2, 2).commit(R(acc), 0);
                if (t.stats().containsKey("thorns")) for (int x = 6; x < 27; x += 5) c.layer().poly(new double[]{x, x + 2, x + 4}, new double[]{6, 0, 6}).commit(R(Canvas.ramp(metal)[1]), 0);
            }
            case "crown" -> {
                c.layer().rect(4, 15, 24, 9).poly(new double[]{4, 7, 10}, new double[]{15, 6, 15}).poly(new double[]{13, 16, 19}, new double[]{15, 3, 15})
                        .poly(new double[]{22, 25, 28}, new double[]{15, 6, 15}).commit(R(GOLD), 0.03);
                c.layer().ellipse(10, 19.5, 2, 2).commit(R(new Color(220, 40, 50)), 0);
                c.layer().ellipse(16, 19.5, 2.2, 2.2).commit(R(new Color(60, 110, 230)), 0);
                c.layer().ellipse(22, 19.5, 2, 2).commit(R(new Color(50, 190, 90)), 0);
                c.sparkle(16, 3, new Color(255, 240, 160));
            }
            case "chest" -> {
                c.layer().poly(new double[]{3, 10, 13, 19, 22, 29, 27, 24, 24, 8, 8, 5}, new double[]{8, 4, 7, 7, 4, 8, 15, 14, 28, 28, 14, 15}).commit(R(metal), 0.06);
                c.layer().rect(15, 9, 2, 18).commit(R(Canvas.ramp(metal)[1]), 0);                             // 가운데 골
                c.layer().rect(8, 22, 16, 2).commit(R(LEATHER), 0.2);                                         // 허리띠
                c.layer().rect(15, 21, 3, 4).commit(R(BRASS), 0);
                if (acc != null) c.layer().ellipse(16, 14, 3, 3).commit(R(acc), 0);
            }
            case "legs" -> {
                c.layer().rect(7, 4, 18, 6).rect(7, 10, 7, 19).rect(18, 10, 7, 19).commit(R(metal), 0.06);
                c.layer().rect(7, 4, 18, 2).commit(R(LEATHER), 0.2);
                c.layer().ellipse(10.5, 17, 3, 2.5).ellipse(21.5, 17, 3, 2.5).commit(R(Canvas.ramp(metal)[3]), 0);   // 무릎
            }
            case "boots" -> {
                c.layer().rect(4, 10, 8, 14).rect(4, 22, 12, 6).commit(R(metal), 0.08);
                c.layer().rect(18, 10, 8, 14).rect(18, 22, 12, 6).commit(R(metal), 0.08);
                c.layer().rect(4, 10, 8, 2).rect(18, 10, 8, 2).commit(R(LEATHER), 0.2);
                if (t.stats().containsKey("speed")) { c.layer().poly(new double[]{1, 4, 4}, new double[]{12, 9, 17}).poly(new double[]{15, 18, 18}, new double[]{12, 9, 17}).commit(R(new Color(240, 240, 255)), 0); }
            }
            case "statue", "relic" -> {
                Color st = k.equals("relic") ? new Color(236, 214, 140) : new Color(226, 222, 212);
                c.layer().rect(8, 25, 16, 5).commit(R(Canvas.mix(st, Color.GRAY, 0.4)), 0.2);
                c.layer().poly(new double[]{12, 20, 21, 11}, new double[]{12, 12, 25, 25}).commit(R(st), 0.15);
                c.layer().ellipse(16, 8, 3.6, 4).commit(R(st), 0.1);
                c.layer().line(12, 13, 8, 19, 2.2).line(20, 13, 24, 17, 2.2).commit(R(st), 0.1);
                if (k.equals("relic")) { c.sparkle(5, 6, new Color(255, 236, 140)); c.sparkle(27, 10, new Color(255, 236, 140)); c.sparkle(25, 3, new Color(255, 236, 140)); }
            }
            case "plaque" -> {
                c.layer().rect(3, 5, 26, 22).commit(R(new Color(146, 104, 62)), 0.25);
                c.layer().rect(6, 8, 20, 16).commit(R(new Color(214, 208, 196)), 0.2);
                c.layer().ellipse(16, 16, 5, 5).commit(R(new Color(180, 174, 164)), 0.1);
            }
            case "chime" -> {
                c.layer().rect(6, 3, 20, 3).commit(R(WOOD), 0.2);
                for (int i = 0; i < 5; i++) c.layer().rect(7 + i * 4, 7, 2, 10 + (i % 3) * 4).commit(R(new Color(170, 214, 232)), 0);
                c.layer().ellipse(16, 26, 2.5, 2.5).commit(R(BRASS), 0);
            }
            case "potion" -> {
                Color liq = t.hasTag("potion_t3") ? new Color(196, 30, 80) : t.hasTag("potion_t2") ? new Color(228, 60, 60) : new Color(240, 130, 110);
                c.layer().rect(13, 2, 6, 4).commit(R(new Color(140, 96, 56)), 0.2);
                c.layer().rect(12, 6, 8, 5).ellipse(16, 20, 10, 9).commit(R(new Color(196, 220, 236)), 0);
                c.layer().ellipse(16, 21, 8, 7).commit(R(liq), 0);
                c.set(11, 16, Color.WHITE); c.set(11, 17, Color.WHITE); c.set(12, 15, Color.WHITE);
            }
            case "bandage" -> {
                c.layer().ellipse(16, 16, 12, 9).commit(R(CLOTH), 0.1);
                c.layer().ellipse(16, 16, 5, 4).commit(R(new Color(196, 188, 170)), 0);
                c.layer().rect(22, 20, 8, 5).commit(R(CLOTH), 0.1);
                c.layer().rect(8, 7, 3, 4).rect(7, 8, 5, 2).commit(R(new Color(210, 40, 40)), 0);
            }
            case "whetstone" -> {
                c.layer().poly(new double[]{3, 26, 29, 6}, new double[]{14, 9, 18, 23}).commit(R(new Color(118, 120, 130)), 0.45);
                c.layer().line(6, 15, 25, 11, 1).commit(R(new Color(170, 174, 184)), 0);
            }
            case "meat" -> {
                c.layer().ellipse(14, 15, 11, 9).commit(R(new Color(196, 64, 64)), 0.2);
                c.layer().ellipse(11, 13, 4, 3).commit(R(new Color(240, 196, 190)), 0);
                c.layer().line(23, 21, 29, 27, 3).commit(R(BONE), 0);
                c.layer().ellipse(29, 27, 2.2, 2.2).commit(R(BONE), 0);
            }
            case "bone" -> {
                c.layer().line(7, 25, 25, 7, 3.6).ellipse(5, 25, 2.6, 2.6).ellipse(7, 27, 2.6, 2.6).ellipse(25, 5, 2.6, 2.6).ellipse(27, 7, 2.6, 2.6).commit(R(BONE), 0.15);
            }
            case "scale" -> {
                for (int r = 0; r < 3; r++) for (int q = 0; q < 3; q++) c.layer().ellipse(8 + q * 8 + (r % 2) * 4, 8 + r * 7, 5, 5).commit(R(Canvas.mix(own, Color.WHITE, r * 0.08)), 0.05);
            }
            case "drop" -> {
                c.layer().ellipse(16, 20, 8, 8).poly(new double[]{16, 9, 23}, new double[]{3, 17, 17}).commit(R(new Color(150, 12, 24)), 0);
                c.set(12, 17, new Color(255, 160, 160)); c.set(12, 18, new Color(255, 160, 160));
            }
            case "horn" -> {
                Canvas.Layer hn = c.layer();
                for (int i = 0; i <= 30; i++) { double a = i / 30.0; hn.line(5 + a * 22, 27 - a * 18 - Math.sin(a * Math.PI) * 6, 5 + a * 22, 27 - a * 18 - Math.sin(a * Math.PI) * 6, 7 - a * 6); }
                hn.commit(R(new Color(96, 36, 36)), 0.15);
                for (int i = 0; i < 4; i++) c.set(9 + i * 4, 22 - i * 4 - i, new Color(150, 90, 80));
            }
            case "ore" -> {
                c.layer().poly(new double[]{4, 12, 26, 29, 22, 7}, new double[]{17, 6, 7, 19, 27, 26}).commit(R(new Color(112, 108, 104)), 0.5);
                Color ore = t.id().contains("copper") ? new Color(222, 120, 70) : t.id().contains("silver") ? new Color(220, 226, 240) : new Color(214, 170, 140);
                for (int i = 0; i < 7; i++) { int x = 8 + c.rng.nextInt(15), y = 10 + c.rng.nextInt(12); c.layer().ellipse(x, y, 1.8, 1.4).commit(R(ore), 0); }
            }
            case "ingot" -> {
                Color m2 = t.id().contains("silver") ? new Color(222, 228, 240) : t.id().contains("gold") ? GOLD : IRON;
                c.layer().poly(new double[]{3, 23, 29, 9}, new double[]{17, 11, 19, 25}).commit(R(Canvas.ramp(m2)[1]), 0);
                c.layer().poly(new double[]{6, 22, 26, 10}, new double[]{15, 10, 15, 20}).commit(R(m2), 0);
                c.sparkle(20, 12, Color.WHITE);
            }
            case "scrap" -> {
                c.layer().poly(new double[]{6, 15, 27, 25, 13, 4}, new double[]{12, 4, 9, 22, 28, 21}).commit(R(new Color(62, 74, 120)), 0.3);
                c.sparkle(12, 12, new Color(180, 210, 255)); c.sparkle(21, 19, new Color(180, 210, 255));
            }
            case "gem" -> {
                Color g2 = t.hasTag("frost") ? new Color(150, 222, 255) : t.hasTag("undead") ? new Color(40, 170, 150) : t.material().equals("ECHO_SHARD") ? new Color(30, 90, 110) : own;
                c.layer().poly(new double[]{16, 26, 22, 10, 6}, new double[]{3, 12, 28, 28, 12}).commit(R(g2), 0);
                c.layer().poly(new double[]{16, 20, 16, 12}, new double[]{6, 13, 24, 13}).commit(R(Canvas.mix(g2, Color.WHITE, 0.35)), 0);
                c.sparkle(13, 9, Color.WHITE);
            }
            case "log" -> {
                Color bark = t.id().contains("highland") ? new Color(84, 58, 36) : new Color(118, 82, 48);
                c.layer().rect(3, 9, 22, 15).commit(R(bark), 0.5);
                c.layer().ellipse(25, 16.5, 5, 7.5).commit(R(new Color(204, 168, 112)), 0.1);
                c.layer().ellipse(25, 16.5, 2.5, 4).commit(R(new Color(170, 128, 80)), 0);
                c.set(25, 16, new Color(130, 90, 50));
            }
            case "block" -> {
                Color st = t.hasTag("marble") ? new Color(234, 232, 228) : new Color(218, 200, 148);
                c.layer().poly(new double[]{16, 29, 16, 3}, new double[]{3, 9, 15, 9}).commit(R(Canvas.mix(st, Color.WHITE, 0.15)), 0.2);
                c.layer().poly(new double[]{3, 16, 16, 3}, new double[]{9, 15, 29, 23}).commit(R(st), 0.25);
                c.layer().poly(new double[]{16, 29, 29, 16}, new double[]{15, 9, 23, 29}).commit(R(Canvas.mix(st, Color.DARK_GRAY, 0.3)), 0.25);
                if (t.hasTag("marble")) for (int i = 0; i < 6; i++) c.set(6 + i * 2, 13 + i, new Color(170, 170, 186));
            }
            case "sand" -> {
                c.layer().ellipse(16, 22, 13, 7).ellipse(16, 18, 8, 6).commit(R(new Color(226, 210, 156)), 0.5);
            }
            case "fiber" -> {
                Canvas.Layer f = c.layer();
                for (int i = 0; i < 6; i++) f.line(7 + i * 2, 28, 15 + i * 2.5, 4, 1.2);
                f.commit(R(new Color(222, 210, 170)), 0.2);
                c.layer().rect(9, 17, 12, 3).commit(R(new Color(140, 110, 70)), 0);
            }
            case "cloth" -> {
                c.layer().poly(new double[]{4, 26, 28, 6}, new double[]{6, 4, 26, 28}).commit(R(CLOTH), 0.12);
                for (int y = 8; y < 26; y += 4) c.layer().line(6, y, 26, y - 2, 1).commit(R(new Color(206, 198, 180)), 0);
                c.layer().poly(new double[]{20, 28, 26}, new double[]{26, 26, 18}).commit(R(new Color(206, 198, 180)), 0);
            }
            case "hide" -> {
                Color hc = t.hasTag("leather") ? new Color(150, 96, 52) : new Color(190, 146, 104);
                c.layer().poly(new double[]{6, 12, 20, 26, 29, 26, 27, 20, 12, 5, 3, 6}, new double[]{4, 7, 7, 4, 12, 16, 27, 25, 25, 28, 16, 12}).commit(R(hc), 0.35);
                if (t.hasTag("leather")) for (int x = 8; x < 26; x += 3) c.set(x, 9, Canvas.ramp(hc)[0]);
            }
            case "dye" -> {
                Color d = t.id().contains("indigo") ? new Color(40, 66, 178) : new Color(184, 34, 44);
                c.layer().ellipse(16, 22, 10, 7).commit(R(new Color(176, 166, 150)), 0.2);
                c.layer().ellipse(16, 19, 8, 3.5).commit(R(d), 0.1);
                c.layer().ellipse(21, 10, 4, 5).commit(R(Canvas.mix(d, Color.WHITE, 0.15)), 0);
            }
            case "herb" -> {
                Color lf = t.id().contains("salt") ? new Color(184, 158, 110) : t.id().contains("moon") ? new Color(110, 190, 170) : new Color(90, 170, 80);
                c.layer().line(16, 29, 16, 8, 1.6).commit(R(new Color(70, 120, 50)), 0);
                c.layer().ellipse(10, 13, 5, 3).ellipse(22, 12, 5, 3).ellipse(16, 6, 3, 5).ellipse(11, 21, 4.5, 2.6).ellipse(21, 20, 4.5, 2.6).commit(R(lf), 0.15);
                if (t.id().contains("moon")) c.sparkle(25, 5, new Color(220, 240, 255));
            }
            case "grain" -> {
                Canvas.Layer st = c.layer();
                for (int i = 0; i < 5; i++) st.line(10 + i * 2, 29, 12 + i * 3, 10, 1.2);
                st.commit(R(new Color(176, 152, 64)), 0);
                Canvas.Layer ear = c.layer();
                for (int i = 0; i < 5; i++) ear.ellipse(12 + i * 3, 7, 1.8, 4.5);
                ear.commit(R(new Color(230, 196, 92)), 0.2);
                c.layer().rect(10, 20, 13, 3).commit(R(new Color(150, 110, 60)), 0);
            }
            case "fish", "cooked_fish" -> {
                Color fc = k.equals("cooked_fish") ? new Color(200, 120, 56) : t.hasTag("deep") ? new Color(60, 74, 120) : new Color(226, 120, 100);
                c.layer().ellipse(14, 16, 11, 6).poly(new double[]{23, 30, 30}, new double[]{16, 9, 23}).commit(R(fc), 0.15);
                c.layer().ellipse(7, 15, 1.4, 1.4).commit(R(new Color(30, 30, 40)), 0);
                for (int x = 11; x < 22; x += 3) c.set(x, 16, Canvas.ramp(fc)[1]);
                if (k.equals("cooked_fish")) { c.layer().ellipse(16, 25, 14, 4).commit(R(new Color(226, 222, 214)), 0); }
            }
            case "powder" -> {
                c.layer().ellipse(16, 23, 12, 6).ellipse(16, 18, 7, 6).commit(R(new Color(238, 238, 240)), 0.25);
                c.set(12, 14, new Color(200, 210, 230)); c.set(20, 19, new Color(200, 210, 230));
            }
            case "stew" -> {
                c.layer().ellipse(16, 18, 13, 9).cutRect(0, 0, 32, 16).commit(R(new Color(132, 88, 50)), 0.2);
                c.layer().ellipse(16, 16, 13, 4).commit(R(t.id().contains("fish") ? new Color(220, 140, 80) : new Color(176, 92, 52)), 0.3);
                c.layer().ellipse(11, 15, 2, 1.4).commit(R(new Color(240, 200, 120)), 0);
                c.layer().ellipse(20, 16, 2, 1.4).commit(R(new Color(110, 176, 80)), 0);
                for (int i = 0; i < 3; i++) c.set(12 + i * 4, 9 - (i % 2) * 2, new Color(230, 230, 230, 160));
            }
            case "bread" -> {
                c.layer().ellipse(16, 18, 13, 8).commit(R(new Color(204, 150, 76)), 0.15);
                for (int i = 0; i < 3; i++) c.layer().line(9 + i * 6, 14, 12 + i * 6, 20, 1.2).commit(R(new Color(240, 200, 130)), 0);
            }
            default -> { c.layer().ellipse(16, 16, 10, 10).commit(R(own), 0.1); c.sparkle(12, 12, Color.WHITE); }
        }
        if (canon) c.sparkle(28, 3, new Color(255, 226, 110));   // 원작에 이름이 나온 물건: 금빛 반짝임
        return c.image(true);
    }
}
