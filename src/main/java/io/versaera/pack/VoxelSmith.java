package io.versaera.pack;

import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 손에 드는 무기의 입체 모델 — Armourer's Workshop 스킨처럼 조각(큐브)을 쌓아 만든다.
 * <ul>
 *   <li>AW 의 장비 큐브처럼 1/16 블록 칸을 기본으로, 부위마다 단면을 따로 쌓는다:
 *       칼날 = 가운데 능선(두껍게) · 양쪽 평면 · 날 끝(얇고 밝게), 코등이 = 앞뒤로 깊이가 있는 막대 + 위로 말린 끝,
 *       손잡이 = 감은 가죽 줄무늬, 폼멜 · 보석 = 앞뒤로 튀어나온 큐브</li>
 *   <li>칸마다 색을 칠한다 (AW 의 붓 · 음영 도구처럼): 날 끝으로 갈수록 밝게, 피 홈은 어둡게, 속성 룬은 보석 색</li>
 *   <li>모양은 똑바로 세워 만들고, 모든 큐브를 -45° 돌려 바닐라 검처럼 비스듬히 눕힌다 (손 변환을 바닐라 그대로 쓰려고)</li>
 *   <li>색은 16 × 16 팔레트 텍스처 한 장 (큐브 면 하나 = 팔레트 한 칸). 면 음영은 Minecraft 가 넣는다 (위 밝게 · 옆 · 아래 어둡게)</li>
 * </ul>
 * 인벤토리 카드 판은 {@link Model3D} 와 같은 방식 (앞면만, 손에서는 뒤집혀 안 보임).
 */
public final class VoxelSmith {
    private VoxelSmith() {
    }

    record Box(double x0, double y0, double z0, double x1, double y1, double z1, Color c) {
    }

    static final class Build {
        final List<Box> boxes = new ArrayList<>();
        ItemType item;

        void box(double x0, double y0, double z0, double x1, double y1, double z1, Color c) {
            if (Math.abs(x1 - x0) < 0.01 || Math.abs(y1 - y0) < 0.01 || Math.abs(z1 - z0) < 0.01) return;
            boxes.add(new Box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), c));
        }

        /** 가운데(8, 8)를 축으로 한 x 너비 · z 두께 */
        void slab(double cx, double y0, double y1, double w, double d, Color c) {
            box(cx - w / 2, y0, 8 - d / 2, cx + w / 2, y1, 8 + d / 2, c);
        }
    }

    static Color[] T(Color c) {
        return IconSmith.tones(c);
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** 이 모양을 큐브로 깎는가 */
    public static boolean handles(String kind) {
        return switch (kind) {
            case "sword", "dagger", "knife", "axe", "spear", "staff", "torch", "hammer", "mace", "pickaxe", "pickaxe_weapon", "scythe", "rake", "plow", "bow", "arrow" -> true;
            default -> false;
        };
    }

    static final double BOT = -3, TOP = 19;   // 세운 무기의 아래 · 위 (길이 22 → 눕히면 16 칸 대각선 안에 들어온다)

    public static Build build(ItemType t, String kind, IconSmith.Look l) {
        Build b = new Build();
        b.item = t;
        boolean broad = t.hasTag("broad") || t.stats().getOrDefault("attack", 0) >= 44;
        boolean gem = IconSmith.LOOKS.containsKey(t.id()) && IconSmith.LOOKS.get(t.id())[3] != 0 || IconSmith.accent(t) != null;
        switch (kind) {
            case "sword" -> sword(b, l, broad ? 1.75 : 1.3, TOP + (t.hasTag("long") ? 9 : 7), gem, t.hasTag("jagged"), t.hasTag("curved"), t.hasTag("dragonbone"), 4.2, broad ? 4.6 : 4.0);   // 검은 옆(코등이)보다 앞(칼날)으로 길게
            case "dagger" -> sword(b, l, 1.1, 13, gem, false, false, false, 2.6, 2.8);
            case "knife" -> knife(b, l);
            case "axe" -> axe(b, l, broad, t.id().equals("dragon_slaying_axe"), t.hasTag("rust"));
            case "spear" -> spear(b, l, t.hasTag("trident"), gem);
            case "staff" -> staff(b, l, t.hasTag("crook"));
            case "torch" -> torch(b, l);
            case "hammer" -> hammer(b, l);
            case "mace" -> mace(b, l, t.hasTag("stone_head"));
            case "pickaxe", "pickaxe_weapon" -> pick(b, l);
            case "scythe" -> scythe(b, l);
            case "rake" -> rake(b, l);
            case "plow" -> plow(b, l);
            case "bow" -> bow(b, l, t.hasTag("elven"));
            case "arrow" -> arrow(b, l);
            default -> { return null; }
        }
        return b;
    }

    // ------------------------------------------------------------------ 부위
    static void grip(Build b, IconSmith.Look l, double y0, double y1, double w) {
        Color[] s = T(l.s());
        for (double y = y0; y < y1 - 0.01; y += 1) b.slab(8, y, Math.min(y1, y + 1), w, w, ((int) Math.floor(y)) % 2 == 0 ? s[2] : s[1]);   // 감은 가죽
    }

    static void haft(Build b, IconSmith.Look l, double y0, double y1, double w) {
        Color[] s = T(l.s());
        for (double y = y0; y < y1 - 0.01; y += 1) {
            int k = (int) Math.floor(y - y0);
            b.slab(8, y, Math.min(y1, y + 1), w, w, k % 5 == 4 ? s[1] : k % 2 == 0 ? s[2] : mix(s[2], s[3], 0.4));   // 나뭇결
        }
    }

    static void band(Build b, Color c, double y, double w) {
        Color[] g = T(c);
        b.slab(8, y, y + 0.75, w, w, g[3]);
        b.slab(8, y + 0.75, y + 1, w - 0.25, w - 0.25, g[1]);
    }

    static void gemCube(Build b, IconSmith.Look l, double cx, double cy, double size, double depth) {
        Color[] a = T(l.a());
        b.box(cx - size / 2, cy - size / 2, 8 - depth / 2, cx + size / 2, cy + size / 2, 8 + depth / 2, a[2]);
        b.box(cx - size / 4, cy, 8 + depth / 2, cx + size / 4, cy + size / 2 - 0.1, 8 + depth / 2 + 0.2, a[4]);   // 빛 반사 (앞)
        b.box(cx - size / 4, cy, 8 - depth / 2 - 0.2, cx + size / 4, cy + size / 2 - 0.1, 8 - depth / 2, a[4]);   // (뒤)
    }

    // ------------------------------------------------------------------ 검
    static void sword(Build b, IconSmith.Look l, double hw, double top, boolean gem, boolean jag, boolean curved, boolean bone, double gripLen, double guardHalf) {
        Color[] p = T(l.p()), g = T(l.g()), a = T(l.a());
        double y = BOT;
        // 폼멜: 팔각 느낌 (가로 · 세로 판을 겹친다) + 끝 꼭지 + 앞뒤 보석
        b.slab(8, y, y + 0.4, 0.9, 0.9, g[1]);
        b.box(7.0, y + 0.4, 7.35, 9.0, y + 1.9, 8.65, g[2]);
        b.box(7.35, y + 0.4, 7.0, 8.65, y + 1.9, 9.0, g[2]);
        b.box(7.25, y + 0.25, 7.25, 8.75, y + 2.05, 8.75, g[3]);
        if (gem) {
            b.box(7.55, y + 0.75, 9.0, 8.45, y + 1.65, 9.25, a[3]);
            b.box(7.55, y + 0.75, 6.75, 8.45, y + 1.65, 7.0, a[3]);
            b.box(7.75, y + 1.3, 9.25, 8.05, y + 1.55, 9.3, a[4]);
        }
        b.slab(8, y + 2.05, y + 2.4, 1.1, 1.1, g[1]);   // 목
        y += 2.4;
        // 손잡이: 비스듬히 감은 가죽 끈 (반 칸마다 굵게 · 가늘게)
        Color[] s = T(l.s());
        for (double yy = y; yy < y + gripLen - 0.01; yy += 0.5) {
            boolean ridge = ((int) Math.round((yy - y) * 2)) % 2 == 0;
            b.slab(8, yy, Math.min(y + gripLen, yy + 0.5), ridge ? 1.45 : 1.25, ridge ? 1.45 : 1.25, ridge ? s[2] : s[1]);
        }
        b.slab(8, y + gripLen - 0.4, y + gripLen, 1.6, 1.6, g[2]);   // 손잡이 쇠고리
        y += gripLen;
        // 코등이: 가운데 두툼한 블록 + 검마다 다른 양식 (십자 · 성검 날개 · 마검 가시 · 빙결 결정 · 엘프 잎)
        b.box(6.6, y, 6.8, 9.4, y + 1.6, 9.2, g[2]);
        b.box(6.9, y + 1.6, 7.1, 9.1, y + 1.9, 8.9, g[3]);
        b.box(6.8, y - 0.3, 7.0, 9.2, y, 9.0, g[1]);
        switch (guardStyle(b.item)) {
            case "wings" -> wings(b, g, y, guardHalf);
            case "spikes" -> spikes(b, g, a, y, guardHalf);
            case "crystal" -> crystal(b, g, y, guardHalf);
            case "leaf" -> leaves(b, l, y, guardHalf);
            default -> cross(b, g, y, guardHalf);
        }
        if (gem) {   // 가운데 보석 (앞뒤로 튀어나옴)
            b.box(7.4, y + 0.3, 9.2, 8.6, y + 1.4, 9.55, a[2]);
            b.box(7.4, y + 0.3, 6.45, 8.6, y + 1.4, 6.8, a[2]);
            b.box(7.6, y + 0.9, 9.55, 7.95, y + 1.25, 9.6, a[4]);
            b.box(7.6, y + 0.9, 6.4, 7.95, y + 1.25, 6.45, a[4]);
        }
        y += 1.9;
        // 랑게트: 코등이 쇠가 칼날 위로 혀처럼 올라온다
        b.box(8 - hw * 0.55, y, 7.3, 8 + hw * 0.55, y + 0.9, 8.7, g[2]);
        b.box(8 - hw * 0.3, y + 0.9, 7.35, 8 + hw * 0.3, y + 1.4, 8.65, g[3]);
        // 칼날: 반 칸씩 단면 — 능선 · 비탈(베벨) · 날 세 겹, 홈(풀러)은 앞뒤로 파인 어두운 줄
        double len = top - y;
        boolean wavy = b.item != null && (b.item.id().equals("hellfire_sword") || b.item.hasTag("wavy")), rust = b.item != null && b.item.hasTag("rust");
        Color rune = b.item != null && (b.item.hasTag("cursed") || "spikes".equals(guardStyle(b.item))) ? new Color(0xff4030) : a[4];
        for (double yy = y; yy < top - 0.01; yy += 0.5) {
            double y1 = Math.min(top, yy + 0.5), tt = (yy - y) / len;
            double taper = Math.min(1, (top - yy) / (len * 0.28));
            double w = Math.max(0.35, hw * (0.82 + 0.18 * taper) * Math.sqrt(taper));
            double cx = 8 + (curved ? Math.pow(Math.max(0, tt - 0.3), 2) * 4.5 : 0) + (wavy ? Math.sin(tt * Math.PI * 7) * 0.35 * (1 - tt) : 0);
            Color base = mix(p[2], p[3], tt * 0.5), lite = mix(p[3], p[4], 0.3 + tt * 0.4), dark = mix(p[1], p[2], 0.4);
            int step0 = (int) Math.floor((yy - y) * 2);
            if (rust && ((step0 * 37) % 11 < 4)) { base = mix(base, RUST_C, 0.6); lite = mix(lite, RUST_C, 0.45); }
            double core = Math.min(0.55, w * 0.45);
            boolean fuller = !bone && tt < 0.62 && w > 1.0;
            // 능선 (가장 두꺼움)
            if (fuller) {   // 홈: 가운데가 파여 어둡고, 양옆 턱이 솟는다
                b.box(cx - core + 0.2, yy, 7.45, cx + core - 0.2, y1, 8.55, dark);
                b.box(cx - core, yy, 7.3, cx - core + 0.2, y1, 8.7, lite);
                b.box(cx + core - 0.2, yy, 7.3, cx + core, y1, 8.7, mix(lite, p[2], 0.4));
            } else b.box(cx - core, yy, 7.3, cx + core, y1, 8.7, lite);
            // 비탈: 왼쪽 밝게 · 오른쪽 어둡게 (빛은 왼쪽 위)
            if (w - core > 0.35) {
                b.box(cx - w + 0.35, yy, 7.55, cx - core, y1, 8.45, base);
                b.box(cx + core, yy, 7.55, cx + w - 0.35, y1, 8.45, mix(base, p[1], 0.3));
            }
            // 날: 얇고 가장 밝은 선
            b.box(cx - w, yy, 7.82, cx - w + 0.35, y1, 8.18, p[4]);
            b.box(cx + w - 0.35, yy, 7.82, cx + w, y1, 8.18, mix(p[4], p[3], 0.5));
            int step = (int) Math.floor((yy - y) * 2);
            if (bone && step % 5 == 0) b.box(cx - w + 0.4, yy, 8.45, cx + w - 0.4, y1, 8.55, p[1]);   // 뼈 마디
            if (gem && !l.a().equals(IconSmith.RUBY) && step % 6 == 2 && tt < 0.7) {   // 속성 룬 (앞뒤에 빛나는 조각)
                b.box(cx - 0.3, yy, 8.7, cx + 0.3, y1, 8.85, rune);
                b.box(cx - 0.3, yy, 7.15, cx + 0.3, y1, 7.3, rune);
            }
            if (jag && step % 3 == 0 && tt > 0.1 && tt < 0.8) {   // 톱니 (등 쪽)
                b.box(cx + w, yy, 7.85, cx + w + 0.7, yy + 0.35, 8.15, p[3]);
                b.box(cx + w, yy - 0.15, 7.88, cx + w + 0.35, yy, 8.12, p[3]);
            }
        }
        double tipX = 8 + (curved ? Math.pow(0.7, 2) * 4.5 : 0);
        b.box(tipX - 0.2, top, 7.88, tipX + 0.2, top + 0.4, 8.12, p[4]);   // 칼끝
    }

    static final Color RUST_C = new Color(0x8a4a26);

    static String guardStyle(ItemType t) {
        if (t == null) return "cross";
        return switch (t.id()) {
            case "agatha_holy_sword", "lu_divine_sword", "royal_treasure_sword", "thor_war_god_sword", "patia_sword", "sunset_soul_sword" -> "wings";
            case "coldrim_demon_sword", "darkness_sword", "black_knight_sword", "hellfire_sword", "annihilation_sword" -> "spikes";
            case "glacier_sword", "cold_lot_sword", "isren_magic_arms" -> "crystal";
            default -> t.hasTag("elven") ? "leaf" : t.hasTag("cursed") || t.hasTag("demonic") ? "spikes" : t.hasTag("winged") ? "wings"
                    : t.hasTag("frost") ? "crystal" : "cross";
        };
    }

    /** 기본 십자 코등이: 세 단으로 가늘어지는 팔, 끝은 위로 말려 구슬 */
    static void cross(Build b, Color[] g, double y, double guardHalf) {
        double[][] arm = {{1.4, 1.3, 1.2}, {guardHalf * 0.55, 1.0, 1.0}, {guardHalf * 0.85, 0.8, 0.85}};
        for (int sgn : new int[]{-1, 1}) {
            double from = arm[0][0];
            for (int i = 1; i < arm.length; i++) {
                double to = arm[i][0], hh = arm[i][1], dd = arm[i][2];
                b.box(8 + sgn * from, y + 0.8 - hh / 2, 8 - dd, 8 + sgn * to, y + 0.8 + hh / 2, 8 + dd, i == 1 ? g[2] : g[3]);
                b.box(8 + sgn * from, y + 0.8 + hh / 2, 8 - dd * 0.6, 8 + sgn * to, y + 0.8 + hh / 2 + 0.15, 8 + dd * 0.6, g[4]);   // 위 모서리 빛
                from = to;
            }
            double tip = guardHalf * 0.85;
            b.box(8 + sgn * tip, y + 0.4, 7.3, 8 + sgn * (tip + 0.7), y + 1.7, 8.7, g[3]);
            b.box(8 + sgn * (tip + 0.1), y + 1.7, 7.4, 8 + sgn * (tip + 0.8), y + 2.5, 8.6, g[3]);
            b.box(8 + sgn * (tip + 0.15), y + 2.5, 7.45, 8 + sgn * (tip + 0.95), y + 3.25, 8.55, g[4]);   // 끝 구슬
        }
    }

    /** 성검: 깃털 네 장이 칼날 쪽으로 펼쳐진 날개 */
    static void wings(Build b, Color[] g, double y, double guardHalf) {
        Color[] w = T(new Color(0xeeeae0));
        for (int sgn : new int[]{-1, 1}) {
            b.box(8 + sgn * 1.4, y + 0.2, 7.2, 8 + sgn * 2.4, y + 1.4, 8.8, g[3]);   // 날개 뿌리
            for (int f = 0; f < 4; f++) {
                double len = guardHalf * (1.05 - f * 0.18), rise = 0.5 + f * 0.9;
                int segs = (int) Math.ceil(len / 0.5);
                for (int i = 0; i < segs; i++) {
                    double x0 = 2.2 + i * 0.5, x1 = Math.min(2.2 + len, x0 + 0.5), yy = y + 0.2 + f * 0.55 + Math.pow(i / (double) segs, 1.6) * rise;
                    Color c = i == segs - 1 ? w[4] : f % 2 == 0 ? w[2] : w[3];
                    b.box(8 + sgn * x0, yy, 7.55 - f * 0.05, 8 + sgn * x1, yy + 0.7, 8.45 + f * 0.05, c);
                }
            }
            b.box(8 + sgn * 2.2, y + 0.15, 8.8, 8 + sgn * (2.2 + guardHalf * 0.5), y + 0.45, 8.95, g[4]);   // 금 테
        }
    }

    /** 마검: 아래로 꺾인 가시 팔 + 가운데 위로 솟은 뿔 */
    static void spikes(Build b, Color[] g, Color[] a, double y, double guardHalf) {
        for (int sgn : new int[]{-1, 1}) {
            for (int i = 0; i < 8; i++) {
                double x0 = 1.4 + i * 0.5, x1 = x0 + 0.5, drop = i * i * 0.04, h = 1.2 - i * 0.1;
                b.box(8 + sgn * x0, y + 0.8 - h / 2 - drop, 8 - h * 0.6, 8 + sgn * x1, y + 0.8 + h / 2 - drop, 8 + h * 0.6, i % 3 == 2 ? g[1] : g[2]);
            }
            double tx = 1.4 + 4, ty = y + 0.8 - 49 * 0.04;
            for (int i = 0; i < 4; i++) b.box(8 + sgn * (tx + i * 0.3), ty - 0.3 - i * 0.45, 7.75, 8 + sgn * (tx + i * 0.3 + 0.4), ty + 0.2 - i * 0.45, 8.25, g[3]);   // 아래로 굽은 가시
            for (double sx : new double[]{2.2, 3.4}) b.box(8 + sgn * sx, y + 1.4, 7.8, 8 + sgn * (sx + 0.35), y + 2.4, 8.2, g[3]);   // 위로 난 작은 가시
            b.box(8 + sgn * 1.1, y + 1.9, 7.6, 8 + sgn * 1.6, y + 3.0, 8.4, g[2]);   // 칼날을 무는 이빨
        }
        b.box(7.5, y + 0.4, 9.2, 8.5, y + 1.2, 9.45, new Color(0xff3020));   // 붉은 눈
        b.box(7.5, y + 0.4, 6.55, 8.5, y + 1.2, 6.8, new Color(0xff3020));
    }

    /** 빙결: 서로 다른 각도의 얼음 결정이 코등이에서 돋는다 */
    static void crystal(Build b, Color[] g, double y, double guardHalf) {
        Color[] ice = T(new Color(0x9ad8f0));
        cross(b, g, y, guardHalf * 0.7);
        double[][] shards = {{1.8, 0.6, 2.4}, {2.8, 0.2, 3.2}, {3.6, -0.2, 2.0}, {2.4, -0.6, 1.6}};
        for (int sgn : new int[]{-1, 1})
            for (int k = 0; k < shards.length; k++) {
                double sx = shards[k][0], dz = shards[k][1] * sgn, h = shards[k][2];
                for (int i = 0; i < (int) (h / 0.5); i++) {
                    double w = 0.55 * (1 - i / (h / 0.5)) + 0.15;
                    b.box(8 + sgn * (sx + i * 0.18) - w / 2, y + 1.0 + i * 0.5, 8 + dz - w / 2, 8 + sgn * (sx + i * 0.18) + w / 2, y + 1.5 + i * 0.5, 8 + dz + w / 2, i % 2 == 0 ? ice[3] : ice[4]);
                }
            }
    }

    /** 엘프: 칼날 쪽으로 감싸 오르는 잎 두 장 + 덩굴 */
    static void leaves(Build b, IconSmith.Look l, double y, double guardHalf) {
        Color[] lf = T(new Color(0x5aa050)), g = T(l.g());
        for (int sgn : new int[]{-1, 1}) {
            int n = 9;
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1), x = 1.2 + Math.sin(t * Math.PI * 0.8) * guardHalf * 0.75, yy = y + t * 3.2, w = 0.4 + Math.sin(t * Math.PI) * 0.6;
                b.box(8 + sgn * x - w / 2, yy, 7.65, 8 + sgn * x + w / 2, yy + 0.5, 8.35, i == n - 1 ? lf[4] : i % 2 == 0 ? lf[2] : lf[3]);
                if (i > 0 && i < n - 1) b.box(8 + sgn * x - 0.1, yy, 8.35, 8 + sgn * x + 0.1, yy + 0.5, 8.45, g[3]);   // 잎맥 (금)
            }
        }
    }

    static void knife(Build b, IconSmith.Look l) {
        Color[] p = T(l.p()), g = T(l.g());
        grip(b, l, -1, 6, 1.4);
        b.slab(8, -1.6, -1, 1.6, 1.6, g[2]);
        b.box(6.8, 6, 7.3, 9.2, 6.8, 8.7, g[2]);
        for (double y = 6.8; y < 13; y += 1) {
            double w = y > 11 ? 0.9 - (y - 11) * 0.3 : 0.9;
            b.box(8 - w, y, 7.7, 8 + 0.2, Math.min(13, y + 1), 8.3, p[3]);
            b.box(8 + 0.2, y, 7.8, 8 + w, Math.min(13, y + 1), 8.2, p[2]);
        }
        b.box(7.8, 13, 7.85, 8.3, 13.6, 8.15, p[4]);
    }

    // ------------------------------------------------------------------ 자루 무기
    static void axe(Build b, IconSmith.Look l, boolean broad, boolean twin, boolean rust) {
        Color[] p = T(l.p()), g = T(l.g());
        haft(b, l, BOT, TOP - 0.5, 1.5);
        b.slab(8, BOT - 0.4, BOT, 1.7, 1.7, g[2]);   // 자루 끝 쇠
        wrap(b, l, BOT + 0.5, BOT + 5, 1.75);
        double y0 = TOP - 8.5, k = broad ? 1.2 : 1;
        // 머리: 자루를 감싼 쇠 · 앞뒤 볼트
        b.box(6.5, y0 - 0.5, 6.8, 9.5, TOP - 0.3, 9.2, g[2]);
        b.box(6.7, TOP - 0.3, 7.0, 9.3, TOP + 0.2, 9.0, g[3]);
        for (double yy : new double[]{y0 + 1, TOP - 2}) {
            b.box(7.6, yy, 9.2, 8.4, yy + 0.8, 9.45, g[4]);
            b.box(7.6, yy, 6.55, 8.4, yy + 0.8, 6.8, g[4]);
        }
        for (int sgn : twin ? new int[]{-1, 1} : new int[]{-1}) {
            double xin = sgn < 0 ? 6.5 : 9.5;
            int rows = 16;
            for (int i = 0; i < rows; i++) {
                double y = y0 - 1.5 + i * 0.5 * (broad ? 1.15 : 1), t = (i + 0.5) / rows;
                double neck = 1.6 + 0.4 * Math.abs(t - 0.5);
                double r = (neck + 3.6 * Math.pow(Math.abs(t - 0.5) * 2, 2.2) + 0.9) * k;   // 초승달: 위아래가 길게 뻗는다
                if (t > 0.3 && t < 0.7) r = Math.max(r, neck * k + 0.6);
                double xout = xin + sgn * r;
                Color body = mix(p[2], p[3], t);
                if (rust && (i * 7) % 5 < 2) body = mix(body, new Color(0x7a4426), 0.55);
                double hh = 0.5 * (broad ? 1.15 : 1);
                b.box(xin, y, 7.35, xin + sgn * neck, y + hh, 8.65, body);                       // 목 (두꺼움)
                b.box(xin + sgn * neck, y, 7.6, xout - sgn * 0.7, y + hh, 8.4, mix(body, p[3], 0.3)); // 비탈
                b.box(xout - sgn * 0.7, y, 7.82, xout, y + hh, 8.18, p[4]);                       // 날
            }
            b.box(xin + sgn * 0.3, y0 + 2, 8.65, xin + sgn * 1.3, y0 + 4, 8.8, g[3]);   // 문양 판
            b.box(xin + sgn * 0.3, y0 + 2, 7.2, xin + sgn * 1.3, y0 + 4, 7.35, g[3]);
        }
        if (!twin) {   // 뒤쪽 뿔
            double[] w = {1.0, 0.85, 0.7, 0.5, 0.35};
            for (int i = 0; i < w.length; i++) b.box(9.5 + i * 0.6, y0 + 2.6 - i * 0.15, 8 - w[i] / 2, 10.1 + i * 0.6, y0 + 2.6 - i * 0.15 + w[i] * 1.6, 8 + w[i] / 2, i == 4 ? p[4] : p[2]);
        } else gemCube(b, l, 8, y0 + 3, 1.2, 2.8);
    }

    /** 가죽 끈 손잡이 (자루 위) */
    static void wrap(Build b, IconSmith.Look l, double y0, double y1, double w) {
        Color[] s = T(IconSmith.GRIP);
        for (double yy = y0; yy < y1 - 0.01; yy += 0.5) {
            boolean ridge = ((int) Math.round((yy - y0) * 2)) % 2 == 0;
            b.slab(8, yy, Math.min(y1, yy + 0.5), ridge ? w : w - 0.15, ridge ? w : w - 0.15, ridge ? s[2] : s[1]);
        }
    }

    static void spear(Build b, IconSmith.Look l, boolean trident, boolean gem) {
        Color[] p = T(l.p()), g = T(l.g());
        haft(b, l, BOT, TOP - 6.5, 1.25);
        b.slab(8, BOT - 0.5, BOT, 1.2, 1.2, g[3]);   // 물미
        wrap(b, l, 3, 7, 1.5);
        // 소켓: 아래가 넓고 위로 좁아지는 쇠통 + 고리 둘
        for (int i = 0; i < 4; i++) b.slab(8, TOP - 7.5 + i * 0.5, TOP - 7 + i * 0.5, 1.8 - i * 0.12, 1.8 - i * 0.12, i % 3 == 0 ? g[3] : g[2]);
        double y = TOP - 5.5;
        if (trident) {
            b.box(4.6, y, 7.4, 11.4, y + 0.8, 8.6, p[2]);
            b.box(5.2, y - 0.4, 7.5, 10.8, y, 8.5, p[1]);
            for (double px : new double[]{5.1, 8, 10.9}) {
                double h = px == 8 ? 6 : 4.4;
                for (double yy = y + 0.8; yy < y + h - 0.01; yy += 0.5)
                    b.box(px - 0.45, yy, 7.55, px + 0.45, yy + 0.5, 8.45, px == 8 ? p[3] : p[2]);
                b.box(px - 0.3, y + h, 7.7, px + 0.3, y + h + 0.6, 8.3, p[4]);
                b.box(px - 0.15, y + h + 0.6, 7.85, px + 0.15, y + h + 0.9, 8.15, p[4]);
                if (px != 8) b.box(px + (px < 8 ? -0.75 : 0.45), y + h - 1.2, 7.75, px + (px < 8 ? -0.45 : 0.75), y + h - 0.4, 8.25, p[4]);   // 미늘
            }
        } else {   // 나뭇잎 날: 반 칸 단면, 마름모 (능선 두껍고 날 얇게)
            int n = 13;
            for (int i = 0; i < n; i++) {
                double t = (i + 0.5) / n, yy = y + i * 0.5;
                double w = 0.5 + 1.6 * Math.sin(Math.PI * Math.pow(t, 0.8)) * (1 - t * 0.35);
                b.box(8 - 0.4, yy, 7.35, 8 + 0.4, yy + 0.5, 8.65, mix(p[3], p[4], t));
                if (w > 0.75) {
                    b.box(8 - w + 0.3, yy, 7.65, 8 - 0.4, yy + 0.5, 8.35, p[2]);
                    b.box(8 + 0.4, yy, 7.65, 8 + w - 0.3, yy + 0.5, 8.35, mix(p[2], p[1], 0.4));
                }
                b.box(8 - w, yy, 7.85, 8 - w + 0.3, yy + 0.5, 8.15, p[4]);
                b.box(8 + w - 0.3, yy, 7.85, 8 + w, yy + 0.5, 8.15, p[3]);
            }
            b.box(7.85, y + n * 0.5, 7.85, 8.15, y + n * 0.5 + 0.4, 8.15, p[4]);
        }
        if (gem) {   // 술: 늘어진 실 다발
            Color[] a = T(l.a());
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3, rx = 8 + Math.cos(ang) * 0.7, rz = 8 + Math.sin(ang) * 0.7, len = 2.6 + (i % 2) * 0.6;
                b.box(rx - 0.22, TOP - 7.5 - len, rz - 0.22, rx + 0.22, TOP - 7.5, rz + 0.22, i % 2 == 0 ? a[3] : a[2]);
            }
            b.slab(8, TOP - 7.9, TOP - 7.4, 2.0, 2.0, a[1]);
        }
    }

    static void staff(Build b, IconSmith.Look l, boolean crook) {
        Color[] a = T(l.a()), s = T(l.s());
        haft(b, l, BOT, TOP - 5, 1.5);
        band(b, IconSmith.GOLD, BOT + 2, 1.9);
        band(b, IconSmith.GOLD, 6, 1.9);
        band(b, IconSmith.GOLD, TOP - 6, 2.1);
        if (crook) {   // 갈고리
            double[][] arc = {{8, TOP - 5, 1}, {8, TOP - 4, 1}, {7.6, TOP - 3, 1}, {6.8, TOP - 2.2, 1}, {5.6, TOP - 2, 1}, {4.6, TOP - 2.6, 1}, {4.2, TOP - 3.6, 1}, {4.6, TOP - 4.5, 1}};
            for (double[] c : arc) b.slab(c[0], c[1], c[1] + 1, 1.5, 1.5, s[2]);
            gemCube(b, l, 4.9, TOP - 5.2, 1, 2);
            return;
        }
        // 발톱 넷이 보주를 쥔다
        for (int sgn : new int[]{-1, 1}) {
            b.box(8 + sgn * 0.9, TOP - 5, 7.4, 8 + sgn * 1.7, TOP - 2, 8.6, T(IconSmith.GOLD)[3]);
            b.box(7.4, TOP - 5, 8 + sgn * 0.9, 8.6, TOP - 2, 8 + sgn * 1.7, T(IconSmith.GOLD)[2]);
        }
        double cy = TOP - 1.6, r = 2.2;
        for (double x = -r; x < r; x += 0.55)
            for (double y = -r; y < r; y += 0.55)
                for (double z = -r; z < r; z += 0.55) {
                    double d = Math.sqrt((x + 0.275) * (x + 0.275) + (y + 0.275) * (y + 0.275) + (z + 0.275) * (z + 0.275));
                    if (d > r || d < r - 0.8) continue;   // 껍질만
                    Color c = y > r * 0.3 ? a[4] : y > -r * 0.3 ? a[3] : a[2];
                    b.box(8 + x, cy + y, 8 + z, 8 + x + 0.55, cy + y + 0.55, 8 + z + 0.55, c);
                }
    }

    static void torch(Build b, IconSmith.Look l) {
        Color[] a = T(l.a()), g = T(IconSmith.GOLD);
        haft(b, l, BOT, TOP - 7, 1.5);
        b.slab(8, TOP - 7, TOP - 5.5, 3, 3, g[2]);
        b.slab(8, TOP - 5.5, TOP - 5, 3.4, 3.4, g[3]);
        double[] w = {2.6, 2.4, 2.0, 1.5, 1.0, 0.5};
        for (int i = 0; i < w.length; i++) {
            Color c = i < 2 ? a[2] : i < 4 ? a[3] : new Color(255, 246, 210);
            b.slab(8 + (i % 2 == 0 ? 0.15 : -0.15), TOP - 5 + i, TOP - 4 + i, w[i], w[i], c);
        }
    }

    static void mace(Build b, IconSmith.Look l, boolean stone) {
        Color[] p = T(l.p()), g = T(l.g());
        haft(b, l, BOT, TOP - 5, 1.5);
        b.slab(8, BOT - 0.4, BOT, 1.8, 1.8, g[2]);
        wrap(b, l, BOT + 0.3, BOT + 5, 1.75);
        if (stone) {   // 깨진 돌: 울퉁불퉁한 덩어리 몇 개를 엇갈려 쌓는다
            Color[] st = T(new Color(0x8a8682));
            b.box(5.8, TOP - 5.5, 6.5, 10.2, TOP - 1.2, 9.5, st[2]);
            b.box(6.3, TOP - 6, 6.9, 9.8, TOP - 5.5, 9.1, st[1]);
            b.box(6.2, TOP - 1.2, 6.8, 9.5, TOP - 0.2, 9.0, st[3]);
            b.box(10.2, TOP - 4.6, 7.1, 10.9, TOP - 2.4, 8.9, st[1]);
            b.box(5.2, TOP - 4.0, 7.2, 5.8, TOP - 2.0, 8.6, st[3]);
            b.box(7.0, TOP - 3.5, 9.5, 8.6, TOP - 2.2, 9.8, st[4]);
            b.box(8.4, TOP - 5.0, 9.5, 9.6, TOP - 4.2, 9.7, st[1]);   // 금
            b.slab(8, TOP - 6.2, TOP - 5.5, 1.9, 1.9, T(IconSmith.LEATHER)[2]);   // 묶은 끈
            return;
        }
        // 날개 달린 철퇴: 가운데 팔각 기둥 + 날개 여섯 장 (위아래가 비스듬히 깎인 계단)
        b.slab(8, TOP - 6.5, TOP - 0.5, 2.2, 2.2, p[2]);
        b.slab(8, TOP - 6, TOP - 1, 2.6, 1.6, p[2]);
        b.slab(8, TOP - 6, TOP - 1, 1.6, 2.6, p[2]);
        double[][] dirs = {{1, 0}, {-1, 0}, {0.5, 0.87}, {-0.5, 0.87}, {0.5, -0.87}, {-0.5, -0.87}};
        for (double[] d : dirs) {
            double[] prof = {0.6, 1.2, 1.6, 1.7, 1.7, 1.5, 1.0, 0.5};
            for (int i = 0; i < prof.length; i++) {
                double y = TOP - 6 + i * 0.6, r0 = 1.1, r1 = 1.1 + prof[i];
                for (double r = r0; r < r1 - 0.01; r += 0.4) {
                    double cx = 8 + d[0] * (r + 0.2), cz = 8 + d[1] * (r + 0.2);
                    b.box(cx - 0.22, y, cz - 0.22, cx + 0.22, y + 0.6, cz + 0.22, r + 0.4 >= r1 ? p[4] : i < 3 ? p[2] : p[3]);
                }
            }
        }
        b.slab(8, TOP - 0.5, TOP + 0.3, 1.0, 1.0, p[3]);
        b.slab(8, TOP + 0.3, TOP + 0.8, 0.5, 0.5, p[4]);   // 꼭지 가시
        band(b, IconSmith.GOLD, TOP - 7.2, 2.0);
    }

    static void hammer(Build b, IconSmith.Look l) {
        Color[] p = T(l.p()), g = T(l.g());
        haft(b, l, BOT, TOP - 2, 1.4);
        wrap(b, l, BOT, BOT + 4.5, 1.65);
        b.slab(8, BOT - 0.4, BOT, 1.7, 1.7, g[2]);
        // 머리: 가운데 쇠틀 + 양쪽 타격면 (모서리를 깎은 두 겹)
        double y0 = TOP - 4, y1 = TOP;
        b.box(6.8, y0 - 0.3, 6.6, 9.2, y1 + 0.3, 9.4, g[2]);
        for (int sgn : new int[]{-1, 1}) {
            double xin = sgn < 0 ? 6.8 : 9.2;
            b.box(xin, y0, 6.4, xin + sgn * 2.6, y1, 9.6, p[2]);
            b.box(xin + sgn * 2.6, y0 + 0.3, 6.7, xin + sgn * 3.1, y1 - 0.3, 9.3, p[3]);
            b.box(xin + sgn * 3.1, y0 + 0.6, 7.0, xin + sgn * 3.35, y1 - 0.6, 9.0, p[4]);   // 반질반질한 타격면
            b.box(xin + sgn * 0.6, y0 - 0.2, 6.4, xin + sgn * 1.0, y1 + 0.2, 9.6, g[3]);  // 띠
        }
        b.box(7.4, y0 + 1.2, 9.4, 8.6, y1 - 1.2, 9.6, g[4]);   // 쇠틀 징
        b.box(7.4, y0 + 1.2, 6.4, 8.6, y1 - 1.2, 6.6, g[4]);
    }

    static void pick(Build b, IconSmith.Look l) {
        Color[] p = T(l.p());
        haft(b, l, BOT, TOP - 2, 1.5);
        b.box(6.8, TOP - 3, 6.9, 9.2, TOP - 0.5, 9.1, T(l.g())[2]);
        for (int i = 0; i < 7; i++) {
            double dy = i * i * 0.08;
            b.box(9.2 + i, TOP - 2.6 - dy, 7.5, 10.2 + i, TOP - 1.2 - dy, 8.5, i == 6 ? p[4] : p[2]);
            b.box(6.8 - i - 1, TOP - 2.6 - dy, 7.5, 6.8 - i, TOP - 1.2 - dy, 8.5, i == 6 ? p[4] : p[3]);
        }
    }

    static void scythe(Build b, IconSmith.Look l) {
        Color[] p = T(l.p()), g = T(l.g());
        haft(b, l, BOT, TOP, 1.4);
        wrap(b, l, 3, 6, 1.6);
        b.slab(8, 6.5, 7.3, 0.9, 3.0, T(IconSmith.WOOD)[2]);   // 손잡이 가지
        band(b, IconSmith.GOLD, TOP - 1.8, 1.8);
        b.box(6.9, TOP - 1.6, 7.2, 9.1, TOP - 0.2, 8.8, g[2]);
        // 날: 반 칸 열마다 등(두꺼움) → 날(얇음), 끝으로 갈수록 아래로 휜다
        int n = 22;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n, x = 6.9 - i * 0.5, top = TOP - 0.4 - t * t * 5.5, h = 2.4 * (1 - t * 0.75);
            b.box(x - 0.5, top - 0.6, 7.6, x, top, 8.4, p[1]);                 // 등
            b.box(x - 0.5, top - h + 0.4, 7.75, x, top - 0.6, 8.25, mix(p[2], p[3], t));
            b.box(x - 0.5, top - h, 7.88, x, top - h + 0.4, 8.12, p[4]);         // 날
        }
    }

    static void rake(Build b, IconSmith.Look l) {
        Color[] p = T(l.p());
        haft(b, l, BOT, TOP - 5, 1.4);
        b.box(3.2, TOP - 5, 7.4, 12.8, TOP - 4, 8.6, p[2]);
        for (double x = 3.5; x <= 12.6; x += 2.25) b.box(x - 0.3, TOP - 4, 7.7, x + 0.3, TOP, 8.3, p[3]);
    }

    static void plow(Build b, IconSmith.Look l) {
        Color[] p = T(l.p());
        haft(b, l, BOT, TOP - 4, 1.5);
        band(b, IconSmith.GOLD, TOP - 5, 1.9);
        b.box(6, TOP - 4, 6.6, 12.5, TOP - 2.6, 9.4, p[2]);
        b.box(10.5, TOP - 2.6, 7, 12.5, TOP, 9, p[3]);
        b.box(12.5, TOP - 4, 7.4, 13.2, TOP, 8.6, p[4]);
    }

    static void bow(Build b, IconSmith.Look l, boolean elven) {
        int from = b.boxes.size();
        bowShape(b, l, elven);
        // 좌우를 뒤집는다: 그대로면 손에 들었을 때 시위가 바깥(과녁 쪽), 활대가 사람 쪽으로 휘어 반대로 보였다
        for (int i = from; i < b.boxes.size(); i++) {
            Box x = b.boxes.get(i);
            b.boxes.set(i, new Box(16 - x.x1(), x.y0(), x.z0(), 16 - x.x0(), x.y1(), x.z1(), x.c()));
        }
    }

    private static void bowShape(Build b, IconSmith.Look l, boolean elven) {
        Color[] s = T(l.s()), a = T(l.a());
        double len = TOP - BOT;
        double prevX = 8;
        for (double y = BOT; y < TOP - 0.01; y += 0.5) {
            double t = (y + 0.25 - BOT) / len, e = Math.abs(t - 0.5) * 2;
            double x = 8 - 4.8 * Math.sin(Math.PI * t) + (e > 0.82 ? Math.pow((e - 0.82) / 0.18, 2) * 1.3 : 0);   // 끝이 앞으로 휘는 리커브
            boolean grip = e < 0.14;
            Color c = grip ? T(IconSmith.GRIP)[((int) (y * 2)) % 2 == 0 ? 2 : 1] : mix(s[2], s[3], 0.3 + 0.4 * (1 - e));
            double w = grip ? 1.8 : 1.35 - e * 0.65, d = grip ? 1.6 : 1.1 - e * 0.35;
            b.box(Math.min(x, prevX) - w / 2, y, 8 - d / 2, Math.max(x, prevX) + w / 2, y + 0.5, 8 + d / 2, c);
            b.box(x - w / 2, y, 8 + d / 2, x - w / 2 + 0.3, y + 0.5, 8 + d / 2 + 0.08, mix(c, Color.WHITE, 0.25));   // 앞 모서리 빛
            if (elven && !grip && ((int) (y * 2)) % 5 == 0) b.box(x - w / 2 - 0.2, y, 7.75, x - w / 2, y + 0.5, 8.25, a[3]);   // 덩굴 잎
            if (!grip && Math.abs(e - 0.3) < 0.02) b.box(x - w / 2 - 0.05, y, 8 - d / 2 - 0.05, x + w / 2 + 0.05, y + 0.5, 8 + d / 2 + 0.05, T(IconSmith.GOLD)[2]);
            prevX = x;
        }
        b.box(9.12, BOT + 0.3, 7.92, 9.28, TOP - 0.3, 8.08, new Color(236, 232, 220));   // 시위
        b.box(3.2 - 0.5, 7.6, 7.6, 3.2 + 0.3, 8.6, 8.4, T(IconSmith.GOLD)[3]);   // 화살 받침
        for (double y : new double[]{BOT, TOP - 0.7}) b.slab(9.2, y, y + 0.7, 0.9, 0.9, elven ? a[3] : T(IconSmith.BONE)[3]);
    }

    static void arrow(Build b, IconSmith.Look l) {
        haft(b, l, BOT + 2, TOP - 3, 0.6);
        Color dark = new Color(0x2a2430);
        Color[] d = T(dark);
        for (int i = 0; i < 4; i++) b.box(8 - 1.4 + i * 0.35, TOP - 3 + i * 0.8, 7.8, 8 + 1.4 - i * 0.35, TOP - 2.2 + i * 0.8, 8.2, i == 3 ? d[4] : d[2]);
        for (int sgn : new int[]{-1, 1}) b.box(8, BOT + 2, 7.95, 8 + sgn * 1.3, BOT + 5, 8.05, new Color(0xf0ece0));
        b.box(7.95, BOT + 2, 8, 8.05, BOT + 5, 8 + 1.3, new Color(0xd8d0c0));
    }

    // ------------------------------------------------------------------ 모델 JSON
    /** 팔레트 텍스처 (16 × 16, 칸 하나 = 색 하나) */
    public static BufferedImage palette(Build b) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int i = 0;
        for (Color c : colors(b).keySet()) { img.setRGB(i % 16, i / 16, c.getRGB() | 0xff000000); i++; }
        return img;
    }

    static LinkedHashMap<Color, Integer> colors(Build b) {
        LinkedHashMap<Color, Integer> m = new LinkedHashMap<>();
        for (Box x : b.boxes) if (!m.containsKey(x.c()) && m.size() < 256) m.put(x.c(), m.size());
        return m;
    }

    private static String n(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** 45° 눕혔을 때 16 × 16 칸 안에 들도록 (8, 8, 8) 을 가운데로 줄이는 배율 */
    static double fit(Build b) {
        double m = 0;
        for (Box x : b.boxes)
            for (double px : new double[]{x.x0(), x.x1()})
                for (double py : new double[]{x.y0(), x.y1()}) {
                    double dx = px - 8, dy = py - 8, c = Math.sqrt(0.5);
                    m = Math.max(m, Math.max(Math.abs(dx * c + dy * c), Math.abs(-dx * c + dy * c)));
                }
        return m > 7.9 ? 7.9 / m : 1;
    }

    /** 배율을 적용한 상자들 */
    static List<Box> fitted(Build b) {
        double k = fit(b);
        List<Box> out = new ArrayList<>();
        for (Box x : b.boxes)
            out.add(new Box(8 + (x.x0() - 8) * k, 8 + (x.y0() - 8) * k, 8 + (x.z0() - 8) * k, 8 + (x.x1() - 8) * k, 8 + (x.y1() - 8) * k, 8 + (x.z1() - 8) * k, x.c()));
        return out;
    }

    public static String json(Build b, String art, String card) {
        return json(b, VoxelPaint.paint(b.boxes), art, card);
    }

    /** 상자마다 여섯 면이 칠한 텍스처(아틀라스)의 제자리를 가리킨다 */
    public static String json(Build b, VoxelPaint.Painted paint, String art, String card) {
        return json(b, paint, art, card, true);
    }

    /** @param withCard 앞면 카드 판을 넣을지 (손 모델은 넣지 않는다 — 다른 사람 눈에 카드가 보이므로) */
    public static String json(Build b, VoxelPaint.Painted paint, String art, String card, boolean withCard) {
        StringBuilder e = new StringBuilder();
        double maxZ = 9, k = 16.0 / paint.size();
        List<Box> boxes = fitted(b);
        for (int i = 0; i < boxes.size(); i++) {
            Box x = boxes.get(i);
            maxZ = Math.max(maxZ, x.z1());
            if (e.length() > 0) e.append(',');
            e.append("{\"from\":[").append(n(x.x0())).append(',').append(n(x.y0())).append(',').append(n(x.z0())).append("],\"to\":[")
                    .append(n(x.x1())).append(',').append(n(x.y1())).append(',').append(n(x.z1()))
                    .append("],\"rotation\":{\"angle\":-45,\"axis\":\"z\",\"origin\":[8,8,8]},\"faces\":{");
            for (int f = 0; f < 6; f++) {
                int[] r = paint.rects()[i][f];
                if (f > 0) e.append(',');
                e.append('"').append(VoxelPaint.FACES[f]).append("\":{\"uv\":[").append(n(r[0] * k)).append(',').append(n(r[1] * k)).append(',')
                        .append(n((r[0] + r[2]) * k)).append(',').append(n((r[1] + r[3]) * k)).append("],\"texture\":\"#art\"}");
            }
            e.append("}}");
        }
        double cz = maxZ + 0.1;
        if (withCard) e.append(",{\"from\":[0,0,").append(n(cz)).append("],\"to\":[16,16,").append(n(cz)).append("],\"faces\":{\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#card\"}}}");
        return "{\"textures\":{\"art\":\"" + art + "\",\"card\":\"" + card + "\",\"particle\":\"" + card + "\"},\"gui_light\":\"front\",\"elements\":[" + e
                + "],\"display\":" + Model3D.display(true, Math.min(1.6, 1 / fit(b))) + "}";
    }
}
