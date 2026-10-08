package io.versaera.pack;

import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 장비 · 장신구 · 성물 아이콘 (RP-01) — 바닐라 아이템처럼 16 × 16 칸에 한 칸씩 찍고 2 배로 키운다.
 * 자동 음영 · 잡티 · 반짝임 없이 색 네 단계(어두움 · 바탕 · 밝음 · 빛)와 짙은 외곽선만 쓴다.
 * <ul>
 *   <li>무기는 왼쪽 아래 손잡이에서 오른쪽 위 끝으로 비스듬히 (바닐라 검 · 도끼와 같은 방향). 칸 좌표를 대각선 좌표
 *       (s2 = 손잡이에서 끝까지, d2 = 칼날 옆) 로 바꿔 모양 규칙으로 찍는다 — 같은 규칙에서 날 너비 · 톱니 · 휘어짐만 바꾼다</li>
 *   <li>갑옷 · 장신구 · 성물은 손으로 그린 문자 그림 (1 2 3 4 = 바탕 재질 어두움 → 빛, d m l = 둘째 재질, a A * = 보석 · 속성)</li>
 *   <li>색은 원작 아이템 설명에서 (석양 · 레드 드래곤의 뼈 · 태양신의 금빛 · 녹 · 엘프목 …) — {@link #LOOKS}</li>
 * </ul>
 */
public final class IconSmith {
    private IconSmith() {
    }

    static final int N = 16;

    /** 이 모양을 여기서 그리는가 */
    public static boolean handles(String kind) {
        return KINDS.contains(kind);
    }

    static final Set<String> KINDS = Set.of("sword", "dagger", "knife", "axe", "spear", "staff", "hammer", "pickaxe", "pickaxe_weapon", "mace", "scythe",
            "whip", "bow", "rake", "plow", "harp", "fan", "watering_can", "hammer_chisel", "torch", "arrow",
            "chest", "legs", "boots", "shield", "helmet", "crown",
            "ring", "necklace", "bracelet", "gloves", "cloak", "belt", "pauldron", "orb",
            "book", "cup", "mirror", "map", "horn", "key", "flag", "seal", "crest", "skull", "plate", "compass", "furnace", "feather");

    // ------------------------------------------------------------------ 색
    /** 0 외곽선 · 1 어두움 · 2 바탕 · 3 밝음 · 4 빛 */
    static Color[] tones(Color c) {
        return new Color[]{scale(c, 0.32), scale(c, 0.66), c, lift(c, 0.28), lift(c, 0.6)};
    }

    private static Color scale(Color c, double f) {
        return new Color(clamp(c.getRed() * f + 6), clamp(c.getGreen() * f + 4), clamp(c.getBlue() * f + 8));
    }

    private static Color lift(Color c, double f) {
        return new Color(clamp(c.getRed() + (255 - c.getRed()) * f), clamp(c.getGreen() + (255 - c.getGreen()) * f * 0.97),
                clamp(c.getBlue() + (255 - c.getBlue()) * f * 0.9));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static Color rgb(int hex) {
        return new Color(hex);
    }

    static final Color STEEL = rgb(0xa8acb4), GOLD = rgb(0xd4a83e), BRASS = rgb(0xb88a40), LEATHER = rgb(0x8c5c38), GRIP = rgb(0x5c3a26),
            WOOD = rgb(0x8a6034), CLOTH = rgb(0x8a8a92), BONE = rgb(0xded4b8), RUST = rgb(0x96603e), RUBY = rgb(0xb02830), DARKSTEEL = rgb(0x58525c);

    /** 바탕 · 둘째 · 날밑(코등이) · 보석 */
    record Look(Color p, Color s, Color g, Color a) {
    }

    /** 원작 설명에서 고른 색 (id → 바탕 · 둘째 · 날밑 · 보석, 0 = 기본값) */
    static final Map<String, int[]> LOOKS = new HashMap<>();

    private static void look(String id, int p, int s, int g, int a) {
        LOOKS.put(id, new int[]{p, s, g, a});
    }

    static {
        // 검
        look("hard_iron_sword", 0xa8acb4, 0x5c3a26, 0x7c7c84, 0);
        look("zahab_carving_knife", 0xc4c8cc, 0x4a3022, 0x8a6034, 0);
        look("clay_sword", 0xb4c4cc, 0x3c3c56, 0x8aa0b0, 0x86c8f0);
        look("sunset_soul_sword", 0xcc9670, 0x5c3a26, 0x8a5a3a, 0xe6783c);
        look("calamor_sword", 0xb4b8c0, 0x2c3c78, 0xd4a83e, 0x3c58b4);
        look("agatha_holy_sword", 0xd6dae2, 0xf0ead6, 0xdcb446, 0xfaf0c8);
        look("cold_lot_sword", 0xbedcec, 0x3a4a5c, 0x8cb4cc, 0xa8e0ff);
        look("coldrim_demon_sword", 0x6e7084, 0x2c2430, 0x5a4a64, 0x8c5abe);
        look("bone_sword", 0xded4b8, 0x5c4a36, 0xb4a888, 0x64b446);
        look("training_sword", 0xa0a4ac, 0x6e4a2e, 0x7c7c84, 0xeed650);
        look("rusted_famed_sword", 0x96603e, 0x4a3022, 0x7a5a3a, 0);
        look("darkness_sword", 0x3c3842, 0x241e28, 0x2c2832, 0x5a4a6a);
        look("roa_masterpiece", 0xc4d6c4, 0x6e5a2e, 0xc8aa50, 0x6ebe6e);
        look("drower", 0x4e5878, 0x1e1c2a, 0x3a3a52, 0xf0dc5a);
        look("red_star", 0xb02c24, 0x3c2824, 0xe0d0b0, 0xff9640);
        look("lu_divine_sword", 0xf4de8c, 0xf0ead6, 0xdcb446, 0xfffae0);
        look("royal_treasure_sword", 0xb4b8c0, 0x6e1e28, 0xdcb446, 0xc02838);
        look("hellfire_sword", 0x8c3228, 0x2c1e1e, 0x5a2c24, 0xff7a2c);
        look("valmondga", 0x782c2c, 0x2c1a1a, 0x4a2424, 0xe65a3c);
        look("patia_sword", 0xb8bcc4, 0x3a5a5a, 0xc8aa50, 0x3cb4a8);
        look("nameless_sword", 0x969aa0, 0x4a3022, 0x6e6e74, 0);
        look("glacier_sword", 0xb0def0, 0x4a6478, 0x8cc8e0, 0xe0f8ff);
        look("ultor", 0x483c4e, 0x2a1e2a, 0x6e2c3c, 0xaa283c);
        look("annihilation_sword", 0xd66030, 0x5a2a1a, 0xe0d0b0, 0xffbe50);
        look("thor_war_god_sword", 0xece8dc, 0xf0ead6, 0xdcb446, 0xfaec78);
        look("wandering_scimitar", 0xa0a0a4, 0x8a5a34, 0x8a6a3a, 0);
        look("black_knight_sword", 0x32323a, 0x1e1e24, 0x3c3c46, 0x962828);
        look("isren_magic_arms", 0xb8c4d4, 0x3c3c5a, 0xc8aa50, 0x8c78e6);
        look("practice_sword", 0xcca066, 0xc4b280, 0x5a3a22, 0);
        // 활 · 지팡이 · 창 · 도끼
        look("sioggrade_bow", 0, 0x7a5230, 0, 0xbca07a);
        look("yerika_bow", 0, 0xdccea0, 0, 0x6ebe6e);
        look("practice_bow", 0, 0xcca066, 0, 0xc4b280);
        look("saint_staff", 0, 0xb0a082, 0, 0xe6e6c8);
        look("alliance_staff", 0, 0xece2be, 0, 0xfadc78);
        look("howler_staff", 0, 0x463c46, 0, 0x786e82);
        look("torch_of_justice", 0, 0x6e4a2e, 0, 0xff8c32);
        look("zenokis_spear", 0x3c2c34, 0x2a1e24, 0, 0xc8322a);
        look("sealed_thunder_spear", 0xbebeaa, 0x5a5a64, 0, 0xeed650);
        look("pascran_spear", 0x8c9098, 0x4a3a2c, 0, 0);
        look("garmung_spear", 0xb88c50, 0x5a3a22, 0, 0);
        look("delam_trident", 0x463240, 0x2a1e24, 0, 0xaa2828);
        look("chaos_axe", 0x5a464c, 0x3a2a24, 0, 0xdc5028);
        look("dragon_slaying_axe", 0xbabec6, 0x5a3a22, 0xdcb446, 0xc83c28);
        look("huge_hard_axe", 0x9a9ea6, 0x5a3a22, 0, 0);
        // 몸통 · 신발
        look("graham_plate", 0x84593a, 0x5a3a22, 0xb4b8c0, 0);
        look("talok_armor", 0xc6ced8, 0xdcb446, 0, 0x6e8cc8);
        look("vine_magic_robe", 0x82828a, 0x50505a, 0, 0x8c78c8);
        look("yeti_coat", 0xe4e4e8, 0xb4b4bc, 0, 0);
        look("womens_leather", 0x3c3230, 0x6e5a4a, 0, 0);
        look("bone_breast_armor", 0xded4b8, 0x8c7a5a, 0, 0);
        look("seven_color_armor", 0xc4b2d2, 0xdcb446, 0, 0x3cb4dc);
        look("mad_warrior_half_plate", 0x786e78, 0x4a3a3a, 0, 0xaa2828);
        look("shoddy_rusty_armor", 0x96603e, 0x5a3a28, 0, 0);
        look("goddess_knight_armor", 0xe2e6ee, 0xdcb446, 0, 0xfaf0c8);
        look("fabio_heavy_armor", 0x6e7078, 0x46464e, 0, 0);
        look("kallamore_commander_armor", 0xb0b4be, 0x2c3c78, 0, 0xdcb446);
        look("earth_armor", 0x6e8250, 0x785a3c, 0, 0x8cc850);
        look("conqueror_leather", 0xbe5a32, 0xdcb446, 0, 0xffbe50);
        look("sarin_armor", 0x8a7a6a, 0x5a4a3a, 0, 0);
        look("black_bear_suit", 0x322c2a, 0x5a4a40, 0, 0);
        look("sky_ruler_armor", 0xdce6f0, 0x78b4e6, 0, 0xfaec78);
        look("thor_god_armor", 0xf0ecde, 0xdcb446, 0, 0xfaec78);
        look("agony_breastplate", 0x3c343c, 0x241e24, 0, 0x96283c);
        look("hell_lord_robe", 0x3c2832, 0x6e2828, 0, 0xc83c28);
        look("kubicha_plate", 0x784032, 0x3c2420, 0, 0xdc5028);
        look("resentful_pants", 0x5a4a5a, 0x3a2e3a, 0, 0);
        look("kurdal_shoes", 0x5a8cd2, 0xf0f0f0, 0, 0);
        look("light_black_boots", 0x2e2e34, 0x6e6e78, 0, 0);
        look("cold_ones_boots", 0x786048, 0xbedcec, 0, 0);
        look("kubicha_boots", 0x6e4632, 0x3c2420, 0, 0xdc5028);
        look("darkness_ruler_boots", 0x322838, 0x6e2828, 0, 0);
        look("demon_soldier_boots", 0x503238, 0x2a1e24, 0, 0);
        // 장신구
        look("perot_ring", 0xd4a83e, 0, 0, 0x3c64c8);
        look("sloar_wedding_ring", 0xd4a83e, 0, 0, 0x3cb46e);
        look("vampiric_ring", 0x9a9aa4, 0, 0, 0xa0141e);
        look("ring_of_extinction", 0x3c3842, 0, 0, 0x8c5abe);
        look("balder_ring", 0xd4a83e, 0, 0, 0x8cdc78);
        look("hell_ring", 0x5a2c2c, 0, 0, 0xff5a28);
        look("death_knight_necklace", 0x9a9aa4, 0, 0, 0xc02838);
        look("black_life_necklace", 0x9a9aa4, 0, 0, 0x2a2430);
        look("undead_necklace", 0x828a7a, 0, 0, 0x78a064);
        look("warrior_necklace", 0xded4b8, 0, 0, 0xb48c50);
        look("purgatory_necklace", 0x5a3a32, 0, 0, 0xff8c32);
        look("irekaya_necklace", 0xd4a83e, 0, 0, 0x3cb4a8);
        look("gray_shroud", 0x82828a, 0x4a4a52, 0, 0x9a9aa4);
        look("ancient_cloak", 0x7a5a3a, 0xc8aa50, 0, 0xc8aa50);
        look("hell_cloak", 0x2c2228, 0x8c2828, 0, 0xc83c28);
        look("space_cloak", 0x3a3878, 0x1e1c3c, 0, 0xe0e0ff);
        look("baharan_bracelet", 0xd4a83e, 0, 0, 0x3c64c8);
        look("selina_flower_bracelet", 0x6ea050, 0, 0, 0xf0a0c8);
        look("sealed_soul_bracelet", 0xb4bcc8, 0, 0, 0x6ec8f0);
        look("eternal_bracer", 0x8c9098, 0x5a3a22, 0, 0xc8aa50);
        look("iron_blood_bracer", 0x4a4650, 0x8c2828, 0, 0xc02838);
        look("bloody_old_gloves", 0x7a5a42, 0x5a3a28, 0, 0x8c1e1e);
        look("grudge_gloves", 0x3c3238, 0x241e24, 0, 0x8c5abe);
        look("skilled_maker_gloves", 0x8c5c38, 0x5a3a22, 0, 0);
        look("legendary_knight_gloves", 0xb4b8c0, 0xdcb446, 0, 0x3c58b4);
        look("immortal_knight_gloves", 0xdcdce6, 0xdcb446, 0, 0xfaf0c8);
        look("piercing_gloves", 0x5a5a64, 0x3a3a42, 0, 0xc02838);
        look("dimension_gloves", 0x5a5096, 0x2c2a50, 0, 0x6ee6f0);
        look("graham_steel_belt", 0x5a3a22, 0xa8acb4, 0, 0);
        look("seven_gem_belt", 0x6e4a2e, 0xdcb446, 0, 0x3cb4dc);
        look("great_victor_belt", 0xdcdce2, 0xdcb446, 0, 0xc02838);
        look("waist_guard", 0x8c9098, 0x5a3a22, 0, 0);
        look("bardray_pauldron", 0x3a3a44, 0x8c2828, 0, 0xc8aa50);
        look("witch_broken_orb", 0, 0x4a3a2c, 0, 0x8ccce6);
        look("hildern_orb", 0, 0x6e5a3c, 0, 0xf0b4c8);
        look("soul_remnant", 0, 0x2a2430, 0, 0x8c5abe);
    }

    static Look lookOf(ItemType t, String kind) {
        Color p = metal(t.material()), s = GRIP, g = BRASS, a = accent(t);
        if (t.hasTag("rust")) p = RUST;
        if (t.hasTag("bone") || t.hasTag("dragonbone")) p = BONE;
        if (t.hasTag("elven")) { p = rgb(0xc4d6c4); g = rgb(0xc8aa50); }
        switch (kind) {
            case "bow" -> { s = WOOD; a = a == null ? rgb(0xd8d2c0) : a; }
            case "staff", "torch" -> s = WOOD;
            case "spear", "axe", "hammer", "pickaxe", "pickaxe_weapon", "mace", "scythe", "rake", "plow" -> s = WOOD;
            case "ring", "necklace", "bracelet", "crown" -> { p = t.material().startsWith("IRON") ? STEEL : GOLD; a = a == null ? gem(t.id()) : a; }
            case "gloves" -> { p = LEATHER; s = GRIP; }
            case "cloak" -> { p = CLOTH; s = rgb(0x4a4a52); }
            case "belt" -> { p = LEATHER; s = GOLD; }
            case "chest", "legs", "boots", "helmet", "shield" -> s = t.material().startsWith("LEATHER") ? GRIP : GOLD;
            default -> { }
        }
        if (a == null) a = RUBY;
        int[] o = LOOKS.get(t.id());
        if (o != null) {
            if (o[0] != 0) p = rgb(o[0]);
            if (o[1] != 0) s = rgb(o[1]);
            if (o[2] != 0) g = rgb(o[2]);
            if (o[3] != 0) a = rgb(o[3]);
        }
        return new Look(p, s, g, a);
    }

    static Color metal(String m) {
        if (m.startsWith("GOLD")) return GOLD;
        if (m.startsWith("DIAMOND")) return rgb(0x9cc4d6);   // 미스릴 빛
        if (m.startsWith("NETHERITE")) return DARKSTEEL;
        if (m.startsWith("LEATHER")) return LEATHER;
        if (m.startsWith("CHAINMAIL")) return rgb(0x96969c);
        if (m.startsWith("STONE")) return rgb(0x82807c);
        if (m.equals("SHIELD")) return WOOD;
        return STEEL;
    }

    /** 속성 색 (없으면 null) */
    static Color accent(ItemType t) {
        var s = t.stats();
        if (s.getOrDefault("fire", 0) > 0) return rgb(0xe6582c);
        if (s.getOrDefault("ice", 0) > 0) return rgb(0x86c8f0);
        if (s.getOrDefault("lightning", 0) > 0) return rgb(0xeed650);
        if (s.getOrDefault("holy", 0) > 0) return rgb(0xf6e296);
        if (s.getOrDefault("dark", 0) > 0 || t.hasTag("cursed")) return rgb(0x8042a8);
        if (s.getOrDefault("poison", 0) > 0) return rgb(0x64b446);
        return null;
    }

    private static Color gem(String id) {
        int[] g = {0xb02830, 0x3c64c8, 0x3cb46e, 0x8c5abe, 0xe6b43c};
        return rgb(g[Math.floorMod(id.hashCode(), g.length)]);
    }

    // ------------------------------------------------------------------ 캔버스
    static final class Grid {
        final int[] px = new int[N * N];

        void set(int x, int y, Color c) {
            if (x >= 0 && y >= 0 && x < N && y < N && c != null) px[y * N + x] = c.getRGB();
        }

        boolean filled(int x, int y) {
            return x >= 0 && y >= 0 && x < N && y < N && (px[y * N + x] >>> 24) != 0;
        }

        /** 대각선 좌표: s2 = 손잡이(왼쪽 아래)에서 끝(오른쪽 위)까지 · d2 = 위왼쪽(+) / 아래오른쪽(-) */
        void diag(int s2, int d2, Color c) {
            if (((s2 + d2) & 1) != 0) return;
            int x = (s2 - d2) / 2, y = 15 - (s2 + d2) / 2;
            set(x, y, c);
        }

        /** 문자 그림 */
        void art(String[] rows, Look l) {
            Color[] p = tones(l.p), s = tones(l.s), a = tones(l.a);
            for (int y = 0; y < rows.length; y++)
                for (int x = 0; x < rows[y].length(); x++) {
                    Color c = switch (rows[y].charAt(x)) {
                        case '1' -> p[1];
                        case '2' -> p[2];
                        case '3' -> p[3];
                        case '4' -> p[4];
                        case 'd' -> s[1];
                        case 'm' -> s[2];
                        case 'l' -> s[3];
                        case 'a' -> a[1];
                        case 'A' -> a[2];
                        case '*' -> a[4];
                        case 'k' -> p[0];
                        case 'w' -> new Color(0xf0ece0);
                        default -> null;
                    };
                    set(x, y, c);
                }
        }

        /** 외곽선 (이웃 색의 아주 어두운 단계) + 2 배 */
        BufferedImage image() {
            int[] out = px.clone();
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    if (filled(x, y)) continue;
                    int nb = 0;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                        if (filled(x + d[0], y + d[1])) { nb = px[(y + d[1]) * N + x + d[0]]; break; }
                    if (nb != 0) {
                        Color c = new Color(nb);
                        out[y * N + x] = new Color(clamp(c.getRed() * 0.22 + 14), clamp(c.getGreen() * 0.22 + 10), clamp(c.getBlue() * 0.22 + 14)).getRGB();
                    }
                }
            BufferedImage img = new BufferedImage(N * 2, N * 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < N * 2; y++) for (int x = 0; x < N * 2; x++) img.setRGB(x, y, out[(y / 2) * N + x / 2]);
            return img;
        }
    }

    // ------------------------------------------------------------------ 그리기
    public static BufferedImage draw(ItemType t, String kind) {
        Grid g = new Grid();
        Look l = lookOf(t, kind);
        boolean broad = t.hasTag("broad") || t.stats().getOrDefault("attack", 0) >= 44, jag = t.hasTag("jagged"), curved = t.hasTag("curved"),
                gemmed = LOOKS.containsKey(t.id()) && LOOKS.get(t.id())[3] != 0 || accent(t) != null;
        switch (kind) {
            case "sword" -> sword(g, l, broad ? 3 : 2, broad ? 25 : 27, jag, curved, gemmed, broad ? 3 : 2, 4);
            case "dagger" -> sword(g, l, 2, 20, false, false, gemmed, 1, 4);
            case "knife" -> knife(g, l);
            case "axe" -> axe(g, l, broad, t.id().equals("dragon_slaying_axe"));
            case "spear" -> spear(g, l, t.hasTag("trident"), gemmed);
            case "staff" -> staff(g, l, t.hasTag("crook"));
            case "torch" -> torch(g, l);
            case "hammer", "mace" -> mace(g, l, kind.equals("mace") && !t.hasTag("stone_head"));
            case "pickaxe", "pickaxe_weapon" -> pick(g, l);
            case "scythe" -> scythe(g, l);
            case "rake" -> rake(g, l);
            case "plow" -> g.art(PLOW, l);
            case "bow" -> bow(g, l);
            case "whip" -> g.art(WHIP, l);
            case "harp" -> g.art(HARP, new Look(GOLD, WOOD, BRASS, rgb(0xf0ece0)));
            case "fan" -> g.art(FAN, new Look(rgb(0x8a6034), rgb(0xd25a8c), BRASS, rgb(0x3cb4a8)));
            case "watering_can" -> g.art(CAN, new Look(rgb(0x8cb0cc), rgb(0x5a7a96), BRASS, rgb(0x78c8ff)));
            case "hammer_chisel" -> g.art(CHISEL, new Look(STEEL, WOOD, BRASS, STEEL));
            case "arrow" -> g.art(ARROW, new Look(rgb(0x2a2430), WOOD, BRASS, rgb(0xf0ece0)));
            case "chest" -> g.art(t.hasTag("robe") ? ROBE : CHEST, l);
            case "legs" -> g.art(LEGS, l);
            case "boots" -> g.art(t.hasTag("winged") ? WINGED_BOOTS : BOOTS, l);
            case "helmet" -> g.art(t.hasTag("horned") ? HORNED_HELM : t.hasTag("hat") ? HAT : HELM, l);
            case "crown" -> g.art(CROWN, l);
            case "shield" -> g.art(SHIELD, l);
            case "ring" -> g.art(RING, l);
            case "necklace" -> g.art(NECKLACE, l);
            case "bracelet" -> g.art(t.hasTag("flower") ? FLOWER_BAND : BRACELET, l);
            case "gloves" -> g.art(GLOVES, l);
            case "cloak" -> g.art(CLOAK, l);
            case "belt" -> g.art(BELT, l);
            case "pauldron" -> g.art(PAULDRON, l);
            case "orb" -> g.art(ORB, l);
            case "book" -> g.art(BOOK, new Look(bookColor(t.id()), rgb(0xe6dcc0), BRASS, GOLD));
            case "cup" -> g.art(CUP, new Look(GOLD, rgb(0x8c1e28), BRASS, RUBY));
            case "mirror" -> g.art(MIRROR, new Look(t.id().startsWith("yuskelanta") ? rgb(0xc8ccd8) : GOLD, rgb(0xa8d4e6), BRASS, RUBY));
            case "map" -> g.art(MAP, new Look(rgb(0xd8c094), rgb(0x8c3c28), BRASS, rgb(0xc02828)));
            case "horn" -> g.art(HORN, new Look(t.id().contains("black") ? rgb(0x3a3440) : BONE, GOLD, BRASS, GOLD));
            case "key" -> g.art(KEY, new Look(t.id().startsWith("star") ? GOLD : rgb(0x8c8a84), GOLD, BRASS, rgb(0x3c64c8)));
            case "flag" -> g.art(FLAG, new Look(rgb(0x2c4a8c), WOOD, BRASS, rgb(0xe6e6e6)));
            case "seal" -> g.art(SEAL, new Look(t.id().startsWith("ruler") ? rgb(0xd8d0b8) : GOLD, rgb(0xa02020), BRASS, RUBY));
            case "crest" -> g.art(CREST, new Look(GOLD, rgb(0x2c3c78), BRASS, RUBY));
            case "skull" -> g.art(SKULL, new Look(BONE, BONE, BRASS, rgb(0x8c5abe)));
            case "plate" -> g.art(PLATE, new Look(rgb(0xa0703c), rgb(0x5a3a22), BRASS, rgb(0x78c8b4)));
            case "compass" -> g.art(COMPASS, new Look(BRASS, rgb(0xe6dcc0), BRASS, RUBY));
            case "furnace" -> g.art(FURNACE, new Look(rgb(0x5a5660), rgb(0x3a3640), BRASS, rgb(0xff8c32)));
            case "feather" -> g.art(FEATHER, new Look(rgb(0xe6b4dc), rgb(0x78c8f0), BRASS, rgb(0xfae678)));
            default -> { return null; }
        }
        return g.image();
    }

    private static Color bookColor(String id) {
        if (id.contains("death") || id.contains("necromancer") || id.contains("revival")) return rgb(0x3c2a3c);
        if (id.contains("gluttony") || id.contains("doom")) return rgb(0x6e2828);
        return rgb(0x6e4a2e);
    }

    /** 검: 손잡이 · 코등이 · 날 (너비 2 ~ 3) · 끝 */
    static void sword(Grid g, Look l, int width, int tip, boolean jag, boolean curved, boolean gem, int guardR, int gripTo) {
        Color[] p = tones(l.p), s = tones(l.s), gd = tones(l.g), a = tones(l.a);
        g.diag(2, 0, a[2]);                                              // 폼멜
        g.diag(3, -1, a[1]);
        for (int s2 = 4; s2 <= gripTo * 2 + 1; s2++) { g.diag(s2, 0, s[2]); g.diag(s2, -1, s[1]); }   // 손잡이
        int gs = gripTo * 2 + 2;
        for (int d2 = -2 * guardR; d2 <= 2 * guardR; d2 += 2) {           // 코등이 (칼날에 수직)
            g.diag(gs, d2, d2 > 0 ? gd[3] : d2 < 0 ? gd[1] : gd[2]);
            g.diag(gs + 1, d2 - 1, gd[1]);
        }
        if (gem) g.diag(gs, 0, a[3]);
        for (int s2 = gs + 2; s2 <= tip; s2++) {
            int w = s2 >= tip - 1 ? 1 : s2 >= tip - 3 ? Math.min(width, 2) : width;
            int top = width == 3 ? 1 : 0;
            if (curved && s2 > tip - 8) top += 1;
            for (int k = 0; k < w; k++) {
                int d2 = top - k;
                Color c = k == 0 ? (s2 % 6 == 0 || s2 >= tip - 1 ? p[4] : p[3]) : k == w - 1 ? p[1] : p[2];
                g.diag(s2, d2, c);
            }
            if (jag && s2 % 4 == 0 && s2 < tip - 3) g.diag(s2, top - w, p[1]);   // 톱니
            if (curved && s2 > tip - 8) g.diag(s2, top - w, p[1]);
        }
        if (gem && width >= 2) { g.diag(gs + 6, width == 3 ? 0 : 0, a[3]); }   // 날에 깃든 속성 빛
    }

    static void knife(Grid g, Look l) {
        Color[] p = tones(l.p), s = tones(l.s), gd = tones(l.g);
        for (int s2 = 2; s2 <= 11; s2++) { g.diag(s2, 0, s[2]); g.diag(s2, -1, s[1]); }
        g.diag(12, 0, gd[2]);
        g.diag(12, 2, gd[3]);
        g.diag(12, -2, gd[1]);
        for (int s2 = 14; s2 <= 22; s2++) {
            g.diag(s2, 0, s2 >= 21 ? p[4] : p[3]);
            if (s2 < 21) g.diag(s2, -1, p[1]);
        }
    }

    static void haft(Grid g, Color[] w, int from, int to) {
        for (int s2 = from; s2 <= to; s2++) { g.diag(s2, 0, w[2]); g.diag(s2, -1, w[1]); }
    }

    static void axe(Grid g, Look l, boolean broad, boolean doubleBit) {
        Color[] p = tones(l.p), w = tones(l.s), gd = tones(l.g), a = tones(l.a);
        haft(g, w, 2, 26);
        int reach = broad ? 11 : 9;
        for (int s2 = 15; s2 <= 27; s2++) {
            int c = 21, span = Math.abs(s2 - c);
            int max = reach - (span >= 6 ? 4 : span >= 5 ? 2 : span >= 4 ? 1 : 0);
            for (int d2 = 1; d2 <= max; d2++) g.diag(s2, d2, d2 >= max ? p[4] : d2 >= max - 1 ? p[3] : d2 <= 2 ? p[1] : p[2]);
            if (doubleBit) for (int d2 = -2; d2 >= -max; d2--) g.diag(s2, d2, d2 <= -max ? p[3] : d2 >= -3 ? p[1] : p[2]);
        }
        if (doubleBit) { g.diag(21, 1, gd[3]); g.diag(20, 0, gd[2]); g.diag(22, 0, a[3]); }
        else if (l.a != null && !l.a.equals(RUBY)) g.diag(21, 3, a[3]);
    }

    static void spear(Grid g, Look l, boolean trident, boolean gem) {
        Color[] p = tones(l.p), w = tones(l.s), a = tones(l.a);
        haft(g, w, 0, 21);
        if (trident) {
            for (int d2 = -5; d2 <= 5; d2++) g.diag(22, d2, p[2]);
            for (int d2 = -4; d2 <= 4; d2++) g.diag(23, d2, p[1]);
            for (int s2 = 24; s2 <= 29; s2++) { g.diag(s2, 0, p[3]); g.diag(s2, -1, p[1]); }
            for (int s2 = 24; s2 <= 27; s2++) { g.diag(s2, 4, p[3]); g.diag(s2, -4, p[1]); }
            g.diag(30, 0, p[4]);
            g.diag(28, 4, p[4]);
            g.diag(28, -4, p[3]);
        } else {
            for (int d2 = -3; d2 <= 2; d2++) g.diag(21, d2, gem ? a[2] : p[1]);   // 창날 밑동
            for (int s2 = 22; s2 <= 30; s2++) {
                int half = s2 <= 25 ? 2 : s2 <= 27 ? 1 : 0;
                for (int d2 = -half; d2 <= half; d2++) g.diag(s2, d2, d2 > 0 ? p[3] : d2 < 0 ? p[1] : s2 >= 28 ? p[4] : p[2]);
            }
        }
        if (gem) g.diag(18, 2, a[3]);
    }

    static void staff(Grid g, Look l, boolean crook) {
        Color[] w = tones(l.s), a = tones(l.a);
        haft(g, w, 0, 22);
        if (crook) {   // 갈고리 지팡이 (성자)
            for (int[] c : new int[][]{{23, 0}, {24, 1}, {25, 2}, {26, 3}, {26, 5}, {25, 6}, {24, 7}, {23, 7}})
                g.diag(c[0], c[1], w[c[1] > 3 ? 1 : 2]);
            g.diag(22, 6, a[3]);
            return;
        }
        int cx = 12, cy = 3;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d <= 2.3) g.set(x, y, d < 1 && x <= cx ? a[4] : x + y < cx + cy ? a[3] : a[2]);
            }
        g.set(cx - 1, cy - 1, a[4]);
        for (int[] c : new int[][]{{cx - 3, cy + 1}, {cx + 1, cy + 3}}) g.set(c[0], c[1], w[1]);   // 받침 가지
    }

    static void torch(Grid g, Look l) {
        Color[] w = tones(l.s), a = tones(l.a);
        haft(g, w, 2, 20);
        for (int d2 = -2; d2 <= 1; d2++) g.diag(21, d2, tones(GOLD)[1]);
        int[][] fire = {{22, 0, 2}, {22, -2, 1}, {22, 2, 2}, {23, 1, 3}, {23, -1, 2}, {24, 0, 4}, {24, 2, 3}, {25, 1, 4}, {26, 0, 4}, {25, -1, 3}};
        for (int[] f : fire) g.diag(f[0], f[1], a[f[2]]);
    }

    static void mace(Grid g, Look l, boolean spikes) {
        Color[] p = tones(l.p), w = tones(l.s);
        haft(g, w, 2, 20);
        for (int s2 = 21; s2 <= 27; s2++)
            for (int d2 = -4; d2 <= 3; d2++) {
                boolean corner = (s2 == 21 || s2 == 27) && (d2 <= -3 || d2 >= 2);
                if (!corner) g.diag(s2, d2, d2 >= 2 ? p[3] : d2 <= -3 ? p[1] : p[2]);
            }
        g.diag(26, 2, p[4]);
        if (spikes) for (int[] c : new int[][]{{24, 5}, {24, -6}, {29, -1}, {19, -1}, {28, 3}, {28, -4}}) g.diag(c[0], c[1], p[3]);
    }

    static void pick(Grid g, Look l) {
        Color[] p = tones(l.p), w = tones(l.s);
        haft(g, w, 2, 24);
        for (int d2 = -8; d2 <= 7; d2++) {
            int bend = Math.abs(d2) >= 6 ? -1 : 0;
            g.diag(24 + bend, d2, d2 > 0 ? p[3] : p[2]);
            if (Math.abs(d2) <= 4) g.diag(25, d2 - 1, p[1]);
        }
        g.diag(23, 8, p[4]);
        g.diag(23, -8, p[3]);
    }

    static void scythe(Grid g, Look l) {
        Color[] p = tones(l.p), w = tones(l.s);
        haft(g, w, 0, 26);
        int[][] blade = {{26, 2}, {25, 3}, {25, 5}, {24, 6}, {23, 7}, {22, 8}, {21, 9}, {20, 10}, {19, 11}, {18, 12}};
        for (int[] b : blade) { g.diag(b[0], b[1], p[3]); g.diag(b[0] + 1, b[1] - 1, p[1]); }
        g.diag(17, 13, p[4]);
    }

    static void rake(Grid g, Look l) {
        Color[] p = tones(l.p), w = tones(l.s);
        haft(g, w, 2, 22);
        for (int d2 = -6; d2 <= 6; d2++) g.diag(24 - (d2 & 1), d2, p[2]);
        for (int d2 = -6; d2 <= 6; d2 += 3) { g.diag(26 - (d2 & 1), d2, p[3]); g.diag(28 - (d2 & 1), d2, p[4]); }
    }

    static void bow(Grid g, Look l) {
        Color[] w = tones(l.s), str = tones(l.a);
        for (int s2 = 3; s2 <= 27; s2++) {   // 활대: 위왼쪽으로 휜 호
            double f = Math.sin(Math.PI * (s2 - 3) / 24.0);
            int d2 = (int) Math.round(f * 9);
            if (((s2 + d2) & 1) != 0) d2 -= 1;
            boolean grip = s2 >= 14 && s2 <= 16;
            g.diag(s2, d2, grip ? tones(GRIP)[2] : w[3]);
            g.diag(s2 + 1, d2 - 1, grip ? tones(GRIP)[1] : w[1]);
        }
        for (int s2 = 4; s2 <= 26; s2++) g.diag(s2, (s2 & 1) == 0 ? 0 : -1, str[3]);   // 시위
    }

    // ------------------------------------------------------------------ 문자 그림 (16 × 16)
    static final String[] CHEST = {
            "................",
            "..122......221..",
            ".13322....22331.",
            ".134322222233321",
            ".133332222223331",
            "..1332222222331.",
            "...13222mm2231..",
            "...13222mm2221..",
            "...1322222222 1.".replace(' ', '2'),
            "...13222222221..",
            "...13222222221..",
            "...ddddddmddd...",
            "...13222222221..",
            "...13222222221..",
            "...11111111111..",
            "................"};
    static final String[] ROBE = {
            "................",
            "......dmmd......",
            "....12dmmd21....",
            "...1332dd2221...",
            "..133322222221..",
            "..133222222221..",
            "...1322mm2221...",
            "...1322222221...",
            "...1322222221...",
            "..13322222222 1.".replace(' ', '1'),
            "..13222222222 1.".replace(' ', '1'),
            ".1332222222222 1".replace(' ', '1'),
            ".13222222222222 ".replace(' ', '1'),
            ".1322222222222 1".replace(' ', '1'),
            ".mmmmmmmmmmmmmm.",
            "................"};
    static final String[] LEGS = {
            "................",
            "...dddddddddd...",
            "...1332222221...",
            "...1332112221...",
            "...13321.1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1331..1221...",
            "...1221..1221...",
            "...1111..1111...",
            "................",
            "................"};
    static final String[] BOOTS = {
            "................",
            "................",
            "................",
            "..dmmd....dmmd..",
            "..1321....1321..",
            "..1321....1321..",
            "..1321....1321..",
            "..1321....1321..",
            "..13221...13221.",
            "..132221..132221",
            "..133221..133221",
            "..122221..122221",
            "..111111..111111",
            "................",
            "................",
            "................"};
    static final String[] WINGED_BOOTS = {
            "................",
            "................",
            ".l.........l....",
            ".ll..dmmd..ll...",
            "..llm1321.llm321".substring(0, 16),
            "...l.1321...l321",
            ".....1321....321",
            ".....1321....321",
            ".....13221...322",
            ".....132221..322",
            ".....133221..332",
            ".....122221..122",
            ".....111111..111",
            "................",
            "................",
            "................"};
    static final String[] HELM = {
            "................",
            "................",
            ".....111111.....",
            "....13342221....",
            "...1343222221...",
            "...1332222221...",
            "...13322AA221...",
            "...13211111221..".substring(0, 16),
            "...1321..12221..".substring(0, 16),
            "...1321..1221...",
            "...1221..1221...",
            "...1111..1111...",
            "................",
            "................",
            "................",
            "................"};
    static final String[] HORNED_HELM = {
            "................",
            ".l............l.",
            ".ll..111111..ll.",
            "..lm13342221ml..",
            "...m343222221...",
            "...1332222221...",
            "...13322AA221...",
            "...13211111221..".substring(0, 16),
            "...1321..12221..".substring(0, 16),
            "...1321..1221...",
            "...1221..1221...",
            "...1111..1111...",
            "................",
            "................",
            "................",
            "................"};
    static final String[] HAT = {
            "................",
            "................",
            "................",
            "................",
            "......1111......",
            ".....133221.....",
            "....13332221....",
            "....1mmmmmm1....",
            "..113322222211..",
            ".13333222222221.",
            ".11111111111111.",
            "................",
            "................",
            "................",
            "................",
            "................"};
    static final String[] CROWN = {
            "................",
            "................",
            "................",
            "...*....*....*..",
            "...3...343...2..",
            "...33..323..22..",
            "...343.323.222..",
            "...3332322222 ..".replace(' ', '2'),
            "...3A22A22A22 ..".replace(' ', '2'),
            "...33222222222..",
            "...11111111111..",
            "................",
            "................",
            "................",
            "................",
            "................"};
    static final String[] SHIELD = {
            "................",
            "..111111111111..",
            "..1mmmmmmmmmm1..",
            "..1m33322222m1..",
            "..1m3432AA22m1..",
            "..1m332AAAA2m1..",
            "..1m3222AA22m1..",
            "..1m3222AA22m1..",
            "...1m322222m1...",
            "...1m322222m1...",
            "....1m3222m1....",
            ".....1m22m1.....",
            "......1mm1......",
            ".......11.......",
            "................",
            "................"};
    static final String[] RING = {
            "................",
            "................",
            ".......**.......",
            "......*AAa......",
            "......AAAa......",
            ".......aa.......",
            ".....123321.....",
            "....13....21....",
            "...13......21...",
            "...13......21...",
            "...13......21...",
            "...12......21...",
            "....12....21....",
            ".....122221.....",
            "................",
            "................"};
    static final String[] NECKLACE = {
            "................",
            ".3............2.",
            ".3............2.",
            "..3..........2..",
            "..2..........2..",
            "...3........2...",
            "...2........2...",
            "....3......2....",
            ".....32..22.....",
            ".......12.......",
            "......*AAa......",
            "......AAAa......",
            "......aAAa......",
            ".......aa.......",
            "................",
            "................"};
    static final String[] BRACELET = {
            "................",
            "................",
            "................",
            "................",
            "....12222221....",
            "..1332222222 1..".replace(' ', '2'),
            ".134........221.",
            ".13..........21.",
            ".13..........21.",
            ".132........221.",
            "..13A22A22A221..",
            "...1122222211...",
            "................",
            "................",
            "................",
            "................"};
    static final String[] FLOWER_BAND = {
            "................",
            "................",
            "................",
            "................",
            "....12222221....",
            "..A*22A*222A*...",
            ".1AA........AA1.",
            ".13..........21.",
            ".13..........21.",
            ".132........221.",
            "..13A*2A*2A*21..",
            "...1122222211...",
            "................",
            "................",
            "................",
            "................"};
    static final String[] GLOVES = {
            "................",
            "......1.1.......",
            ".....131311.....",
            ".....1313121....",
            ".....1313221....",
            "..1..1323221....",
            ".131.1322221....",
            ".1321322222 1...".replace(' ', '2'),
            "..1332222221....",
            "...132222221....",
            "....1322A221....",
            "....12222221....",
            "....dddddddd....",
            "....dmmmmmmd....",
            "....dddddddd....",
            "................"};
    static final String[] CLOAK = {
            "................",
            "......dAAd......",
            ".....1dmmd1.....",
            "....13d22d21....",
            "....13222221....",
            "...1332222221...",
            "...1332212221...",
            "..133222122221..",
            "..133222122221..",
            "..132222122221..",
            ".13322221222221.",
            ".13222221222221.",
            ".13222221222221.",
            ".12121212121211.",
            "................",
            "................"};
    static final String[] BELT = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            ".11111111111111.",
            ".133333mmm33331.",
            ".12222ml*lm2221.",
            ".12222mAAAm2221.",
            ".111111mmm11111.",
            "................",
            "................",
            "................",
            "................",
            "................"};
    static final String[] PAULDRON = {
            "................",
            "................",
            "................",
            ".....111111.....",
            "...1334432221...",
            "..133322222221..",
            ".13322222222221.",
            ".1mmmmmmmmmmmm1.",
            ".13322222222221.",
            "..1322A**A2221..",
            "..1mmmmmmmmmm1..",
            "...1322222221...",
            "....11111111....",
            "................",
            "................",
            "................"};
    static final String[] ORB = {
            "................",
            "................",
            "......aaaa......",
            "....aA**AAAa....",
            "...aA*AAAAAAa...",
            "...aA*AAAAAAa...",
            "...aAAAAAA*Aa...",
            "...aAAA*AAAaa...",
            "....aAAAAAaa....",
            "......aaaa......",
            ".....dmmmmd.....",
            "....dmllmmmd....",
            "....dddddddd....",
            "................",
            "................",
            "................"};
    static final String[] BOOK = {
            "................",
            "................",
            "..1111111111....",
            "..13332222221...",
            "..132222222l1...",
            "..13222AA22l1...",
            "..1322A**A2l1...",
            "..1322A**A2l1...",
            "..13222AA22l1...",
            "..132222222l1...",
            "..132222222l1...",
            "..13222222 l1...".replace(' ', '2'),
            "..11111111111...",
            "................",
            "................",
            "................"};
    static final String[] CUP = {
            "................",
            "................",
            "...1111111111...",
            "...1mmmmmmmm1...",
            "...1342222221...",
            "...1332AA2221...",
            "....13222221....",
            ".....132221.....",
            "......1221......",
            "......1321......",
            "......1221......",
            ".....132221.....",
            "....13322221....",
            "....11111111....",
            "................",
            "................"};
    static final String[] MIRROR = {
            "................",
            ".....122221.....",
            "....13mmmm21....",
            "...13mwllmm21...",
            "...13mwlmmm21...",
            "...13mlmmmm21...",
            "...13mmmmmm21...",
            "....13mmmm21....",
            ".....132221.....",
            ".......12.......",
            ".......12.......",
            ".......12.......",
            ".......12.......",
            "......1221......",
            "................",
            "................"};
    static final String[] MAP = {
            "................",
            "................",
            ".1111..1111..11.",
            ".13321133221132.",
            ".132222322223221",
            ".132m22322A22221",
            ".1322m2322*22221",
            ".13222m322A22221",
            ".1322223m2222221",
            ".132222322mm2221",
            ".132222322222m21",
            ".132222322222221",
            ".1111..1111..111",
            "................",
            "................",
            "................"};
    static final String[] HORN = {
            "................",
            "................",
            "................",
            "...........11...",
            "..........1331..",
            ".........13221..",
            "........133221..",
            ".......1m3221...",
            "......13m221....",
            "....1133m21.....",
            "...133322 1.....".replace(' ', '1'),
            "..1332221.......",
            "..mmmm11........",
            "..1111..........",
            "................",
            "................"};
    static final String[] KEY = {
            "................",
            "................",
            "................",
            "...1111.........",
            "..133221........",
            "..13..21........",
            "..13..21........",
            "..1322A111111111",
            "...1221333333331",
            "........1121.121",
            "........131..131",
            "........11...11.",
            "................",
            "................",
            "................",
            "................"};
    static final String[] FLAG = {
            "................",
            "..d.............",
            "..m1111111111...",
            "..m1332222221...",
            "..m132**22221...",
            "..m132**222 1...".replace(' ', '2'),
            "..m13222*2221...",
            "..m1322222221...",
            "..m111222211....",
            "..m...1221......",
            "..m.....11......",
            "..m.............",
            "..m.............",
            "..m.............",
            "..d.............",
            "................"};
    static final String[] SEAL = {
            "................",
            "................",
            "......1111......",
            ".....133221.....",
            ".....132221.....",
            "......1221......",
            "......1321......",
            "....11322211....",
            "...1333222221...",
            "...1322222221...",
            "...1322222221...",
            "...1mmmmmmmm1...",
            "...1mlm22mlm1...".replace('2', 'm'),
            "...1111111111...",
            "................",
            "................"};
    static final String[] CREST = {
            "................",
            "......1111......",
            "....11344211....",
            "...1334mm2221...",
            "...134mmmm221...",
            "...13mmAAmm21...",
            "...13mAAAAm21...",
            "...13mmAAmm21...",
            "...132mmmm221...",
            "....132mm221....",
            ".....13221......",
            "......121.......",
            ".......1........",
            "................",
            "................",
            "................"};
    static final String[] SKULL = {
            "................",
            "................",
            ".....111111.....",
            "....13344221....",
            "...1334222221...",
            "...1332222221...",
            "...13kk22kk21...",
            "...13kA22Ak21...",
            "...13222k2221...",
            "....132222 1....".replace(' ', '2'),
            "....1w1w1w1.....",
            ".....11111......",
            "................",
            "................",
            "................",
            "................"};
    static final String[] PLATE = {
            "................",
            "................",
            "..111111111111..",
            "..133333222221..",
            "..13m2m2m2m221..",
            "..1322AAAA2221..",
            "..13m2A**A2m21..",
            "..1322AAAA2221..",
            "..13m2m2m2m221..",
            "..132222222 11..".replace(' ', '2'),
            "..122222211.....",
            "..1111111.......",
            "................",
            "................",
            "................",
            "................"};
    static final String[] COMPASS = {
            "................",
            "......1111......",
            "....11344211....",
            "...13mmmmmm21...",
            "..13mmmmAmmm21..",
            "..13mmmmAmmm21..",
            "..13mmm1Ammm21..",
            "..13mmm11mmm21..",
            "..13mmmm1mmm21..",
            "...13mmm1mm21...",
            "....12222221....",
            "......1111......",
            "................",
            "................",
            "................",
            "................"};
    static final String[] FURNACE = {
            "................",
            "................",
            "...1111111111...",
            "...1333322221...",
            "...1311111121...",
            "...131AA*A121...",
            "...131*AAA121...",
            "...131aAAa121...",
            "...1311111121...",
            "...1322222221...",
            "...1mmmmmmmm1...",
            "...1m......m1...",
            "...11......11...",
            "................",
            "................",
            "................"};
    static final String[] FEATHER = {
            "................",
            "...........11...",
            ".........1331...",
            "........13m21...",
            ".......13m221...",
            "......13m221....",
            ".....13m221.....",
            "....13m221......",
            "...13m221.......",
            "...1m221........",
            "..1m221.........",
            "..1m11..........",
            ".1m.............",
            ".1..............",
            "................",
            "................"};
    static final String[] PLOW = {
            "................",
            "..m.............",
            "...m............",
            "....m...........",
            ".....m..........",
            "......m.........",
            ".......m........",
            "........m.......",
            ".........m......",
            "..........m11...",
            "........133321..",
            ".......1332221..",
            "........122221..",
            ".........1111...",
            "................",
            "................"};
    static final String[] WHIP = {
            "................",
            "................",
            ".....1111.......",
            "....13..21......",
            "...13....21.....",
            "...12.....21....",
            "...12.....21....",
            "....12...121....",
            ".....1222 1.....".replace(' ', '2'),
            "..........12....",
            "...........12...",
            "............mm..",
            "............md..",
            ".............dd.",
            "................",
            "................"};
    static final String[] HARP = {
            "................",
            "...11...........",
            "..1331111.......",
            "..13.m3332111...",
            "..13.w.w.m33321.",
            "..13.w.w.w.w.21.",
            "..13.w.w.w.w.21.",
            "..13.w.w.w.w.21.",
            "..13.w.w.w.w.21.",
            "..13.w.w.w.w.21.",
            "..13.w.w.w.w.21.",
            "..13mmmmmmmmm21.",
            "..1111111111111.",
            "................",
            "................",
            "................"};
    static final String[] FAN = {
            "................",
            "................",
            "................",
            "...m.m.m.m.m....",
            "..mlmlmlmlmlm...",
            "..mmlmmlmmlmm...",
            "...mmmmmmmmm....",
            "....mAmmmAm.....",
            ".....mmmmm......",
            "......121.......",
            "......121.......",
            ".......1........",
            ".......A........",
            "................",
            "................",
            "................"};
    static final String[] CAN = {
            "................",
            "................",
            ".....1111.......",
            "....1m..m1......",
            "....1....1......",
            "...111111111....",
            "...13332222211.*",
            "...133222222221A",
            "...1322222222211",
            "...132222222 1..".replace(' ', '2'),
            "...13222222221..",
            "...13222222221..",
            "...11111111111..",
            "................",
            "................",
            "................"};
    static final String[] CHISEL = {
            "................",
            "................",
            "..111...........",
            ".13321.....1....",
            ".13221....131...",
            "..1m1....131....",
            "..1m1...131.....",
            "..1m1..131......",
            "..1m1.1m1.......",
            "..1m1.1m1.......",
            "..1m1..1........",
            "..1m1...........",
            "...1............",
            "................",
            "................",
            "................"};
    static final String[] ARROW = {
            "................",
            "...........111..",
            "...........1A1..",
            "..........1AA1..",
            ".........1m1....",
            "........1m1.....",
            ".......1m1......",
            "......1m1.......",
            ".....1m1........",
            "....1m1.........",
            "..1*m1..........",
            ".1*1*...........",
            "..1*............",
            "................",
            "................",
            "................"};
}
