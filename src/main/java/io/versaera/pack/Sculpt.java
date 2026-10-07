package io.versaera.pack;

import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 입체 아이템 모델 (RP-01). 납작한 그림을 두께 1로 밀어낸 바닐라 방식이 아니라, 상자를 깎아 쌓은 3D 조각.
 * <ul>
 *   <li>무기 · 도구: 세로로 깎은 뒤 통째로 -45° 기울여 손에 든 모습이 바닐라 검과 같은 자리에 오게 한다.
 *       날은 가운데 등줄기가 두껍고 날 끝이 얇은 '빗각', 코등이 · 손잡이 감개 · 폼멜 · 피 홈 · 속성 보석</li>
 *   <li>투구: 머리에 쓰면 그대로 보이는 3D (큰 투구 · 뿔 투구 · 가시 투구 · 왕관). 머리 중심 = 모델 (8,8,8), 머리 크기 = 12.8 단위</li>
 *   <li>재료 · 소모품: 물약병 · 주괴 · 보석 · 원석 · 통나무 · 빵 · 고기 · 뼈 · 조각상 · 붕대 · 숫돌 · 냄비 · 막자사발 · 피</li>
 * </ul>
 * 바닐라 렌더러가 따로 그리는 활 · 낚싯대 · 방패 · 삼지창 · 갑옷 부위 아이콘과, 납작한 게 자연스러운 천 · 가죽 · 약초 등은 그림(PixelArt) 그대로.
 */
public final class Sculpt {
    public record Made(List<ModelKit.Cube> cubes, ModelKit.Style style, String display) {}

    private Sculpt() {
    }

    // 표시 변환 — 손에 든 무기 (바닐라 item/handheld 와 같은 값) + 아이콘은 살짝 비스듬히(두께가 보이게)
    static final String HANDHELD = "{\"thirdperson_righthand\":{\"rotation\":[0,-90,55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[0,90,-55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
            + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]},\"gui\":{\"rotation\":[12,-28,0],\"scale\":[0.92,0.92,0.92]},"
            + "\"fixed\":{\"rotation\":[0,180,0]},\"head\":{\"rotation\":[0,180,0],\"translation\":[0,13,7]}}";
    // 덩어리 물건 — 바닐라 블록과 같은 값
    static final String BLOCKY = "{\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.75,0.75,0.75]},\"ground\":{\"translation\":[0,3,0],\"scale\":[0.35,0.35,0.35]},"
            + "\"fixed\":{\"scale\":[0.6,0.6,0.6]},\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.42,0.42,0.42]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.42,0.42,0.42]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.45,0.45,0.45]},\"firstperson_lefthand\":{\"rotation\":[0,225,0],\"scale\":[0.45,0.45,0.45]}}";
    // 머리에 쓰는 투구 — head 는 변환 없음 (머리 중심 = 모델 중심)
    static final String HELMET = "{\"head\":{\"scale\":[1,1,1]},\"gui\":{\"rotation\":[25,200,0],\"scale\":[0.62,0.62,0.62]},"
            + "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.3,0.3,0.3]},\"fixed\":{\"rotation\":[0,180,0],\"scale\":[0.6,0.6,0.6]},"
            + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
            + "\"thirdperson_lefthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.375,0.375,0.375]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"scale\":[0.4,0.4,0.4]},\"firstperson_lefthand\":{\"rotation\":[0,225,0],\"scale\":[0.4,0.4,0.4]}}";

    private static ModelKit.Cube b(double x1, double y1, double z1, double x2, double y2, double z2, int mat) {
        return new ModelKit.Cube(x1, y1, z1, x2, y2, z2, mat, null);
    }

    private static ModelKit.Cube b(double x1, double y1, double z1, double x2, double y2, double z2, int mat, String deco) {
        return new ModelKit.Cube(x1, y1, z1, x2, y2, z2, mat, deco);
    }

    /** 가운데(cx, cz)에 선 기둥 */
    private static ModelKit.Cube col(double cx, double cz, double w, double d, double y1, double y2, int mat) {
        return b(cx - w / 2, y1, cz - d / 2, cx + w / 2, y2, cz + d / 2, mat);
    }

    /** 팔각 기둥 (모서리를 깎은 상자 두 개) — 둥근 투구 · 통 */
    private static void oct(List<ModelKit.Cube> c, double x1, double y1, double z1, double x2, double y2, double z2, double cut, int mat) {
        c.add(b(x1, y1, z1 + cut, x2, y2, z2 - cut, mat));
        c.add(b(x1 + cut, y1, z1, x2 - cut, y2, z2, mat));
    }

    /** 계단식 둥근 윗머리 */
    private static void dome(List<ModelKit.Cube> c, double y, double lo, double hi, int mat) {
        oct(c, lo + 0.8, y, lo + 0.8, hi - 0.8, y + 1.4, hi - 0.8, 1.6, mat);
        oct(c, lo + 2.4, y + 1.4, lo + 2.4, hi - 2.4, y + 2.4, hi - 2.4, 1.4, mat);
        c.add(b(lo + 4.4, y + 2.4, lo + 4.4, hi - 4.4, y + 3, hi - 4.4, mat));
    }

    /** 이 아이템을 입체로 깎는가 (null = 그림 그대로) */
    public static Made of(ItemType t) {
        String k = PixelArt.kind(t);
        Color metal = metalOf(t.material()), acc = PixelArt.accent(t);
        return switch (k) {
            case "sword", "dagger", "knife", "axe", "spear", "staff", "hammer", "pickaxe" -> weapon(t, k, metal, acc);
            case "helmet", "crown" -> helmet(t, metal, acc);
            case "potion", "ingot", "gem", "ore", "log", "block", "bread", "meat", "bone", "statue", "relic", "bandage", "whetstone", "pot", "mortar", "drop", "scrap" ->
                    lump(t, k, acc);
            default -> null;
        };
    }

    static Color metalOf(String material) {
        if (material.startsWith("GOLD")) return PixelArt.GOLD;
        if (material.startsWith("DIAMOND")) return PixelArt.DIAMOND;
        if (material.startsWith("NETHERITE")) return PixelArt.NETHERITE;
        return PixelArt.IRON;
    }

    // ------------------------------------------------------------------ 무기 · 도구
    private static Made weapon(ItemType t, String k, Color metal, Color acc) {
        List<ModelKit.Cube> c = new ArrayList<>();
        boolean big = t.stats().getOrDefault("attack", 0) >= 30;
        boolean wood = k.equals("knife") || k.equals("axe") || k.equals("spear") || k.equals("staff") || k.equals("hammer") || k.equals("pickaxe");
        Color guard = acc != null ? Canvas.mix(PixelArt.BRASS, acc, 0.35) : PixelArt.BRASS;
        ModelKit.Style st = new ModelKit.Style(metal, guard, acc != null ? acc : new Color(120, 200, 255), wood ? PixelArt.WOOD : PixelArt.WRAP,
                Canvas.mix(metal, Color.WHITE, 0.25), "blade", "metal", wood ? "wood" : "wrap");
        switch (k) {
            case "sword", "dagger", "knife" -> {
                double L = k.equals("sword") ? (big ? 24 : 21) : 13, W = k.equals("sword") ? (big ? 3.0 : 2.4) : 2.0, ys = 8 - L / 2;
                double grip = k.equals("sword") ? 4.5 : 3.5;
                c.add(col(8, 8, 2.2, 2.2, ys, ys + 1.8, 1));                                       // 폼멜
                c.add(col(8, 8, 1.3, 1.3, ys + 1.8, ys + 1.8 + grip, 3));                          // 손잡이
                c.add(col(8, 8, 1.7, 1.7, ys + 2.3, ys + 2.8, 1));                                 // 감개 고리
                double gy = ys + 1.8 + grip, gw = k.equals("sword") ? (big ? 8 : 7) : 4.5;
                c.add(col(8, 8, gw, 1.6, gy, gy + 1.4, 1));                                        // 코등이
                if (k.equals("sword")) { c.add(col(8 - gw / 2 + 0.6, 8, 1.2, 1.8, gy + 1.4, gy + 2.4, 1)); c.add(col(8 + gw / 2 - 0.6, 8, 1.2, 1.8, gy + 1.4, gy + 2.4, 1)); }
                if (acc != null) c.add(col(8, 8, 1.1, 2.0, gy + 0.15, gy + 1.25, 2));             // 속성 보석
                double b0 = gy + 1.4, b1 = ys + L;
                c.add(col(8, 8, W, 0.55, b0, b1 - 3, 4));                                          // 날 (얇은 날 끝)
                c.add(col(8, 8, W * 0.5, 1.0, b0, b1 - 3, 0));                                     // 등줄기 (두꺼움) → 빗각
                c.add(col(8, 8, 0.45, 1.08, b0 + 0.8, b1 - 5, acc != null ? 2 : 3));               // 피 홈
                c.add(col(8, 8, W * 0.8, 0.5, b1 - 3, b1 - 2, 4));
                c.add(col(8, 8, W * 0.55, 0.45, b1 - 2, b1 - 1, 4));
                c.add(col(8, 8, W * 0.28, 0.4, b1 - 1, b1, 4));
            }
            case "axe" -> {
                double L = big ? 24 : 21, ys = 8 - L / 2, top = ys + L;
                c.add(col(8, 8, 1.5, 1.5, ys, top, 3));
                c.add(col(8, 8, 2.0, 2.0, ys, ys + 1.2, 1));
                c.add(col(8, 8, 2.1, 2.1, top - 7, top - 5.5, 1));                                  // 고정 테
                c.add(b(8.8, top - 9, 7.45, 13.6, top - 1.5, 8.55, 0));                            // 도끼날 판
                c.add(b(13.6, top - 10, 7.7, 15.2, top - 0.5, 8.3, 4));                            // 날 (얇게)
                if (big) { c.add(b(2.4, top - 9, 7.45, 7.2, top - 1.5, 8.55, 0)); c.add(b(0.8, top - 10, 7.7, 2.4, top - 0.5, 8.3, 4)); }   // 양날
                else { c.add(b(4.6, top - 6.6, 7.6, 7.2, top - 4.4, 8.4, 0)); c.add(b(3.4, top - 6, 7.75, 4.6, top - 5, 8.25, 4)); }       // 뒷가시
                c.add(col(8, 8, 1.8, 1.8, top - 1.5, top, 1));
                if (acc != null) c.add(b(10.2, top - 6.5, 7.3, 11.8, top - 4.5, 8.7, 2));
            }
            case "spear" -> {
                double L = 26, ys = 8 - L / 2, top = ys + L;
                c.add(col(8, 8, 1.1, 1.1, ys, top - 6, 3));
                c.add(col(8, 8, 1.5, 1.5, ys, ys + 1, 1));
                c.add(col(8, 8, 1.7, 1.7, top - 7, top - 5.5, 1));
                c.add(col(8, 8, 2.8, 0.6, top - 5.5, top - 2.8, 4));
                c.add(col(8, 8, 0.9, 1.0, top - 5.5, top - 1.2, 0));
                c.add(col(8, 8, 1.8, 0.55, top - 2.8, top - 1.4, 4));
                c.add(col(8, 8, 0.8, 0.5, top - 1.4, top, 4));
            }
            case "staff" -> {
                double L = 24, ys = 8 - L / 2, top = ys + L;
                Color orb = acc != null ? acc : new Color(120, 160, 255);
                st = new ModelKit.Style(PixelArt.DARKWOOD, PixelArt.BRASS, orb, PixelArt.DARKWOOD, PixelArt.BRASS, "wood", "metal", "wood");
                c.add(col(8, 8, 1.5, 1.5, ys, top - 5, 3));
                c.add(col(8, 8, 2.0, 2.0, ys, ys + 1.2, 1));
                c.add(col(8, 8, 2.0, 2.0, top - 9, top - 8, 1));
                c.add(col(8, 8, 2.4, 2.4, top - 6, top - 5, 1));                                    // 받침
                for (int i = -1; i <= 1; i += 2) { c.add(col(8 + i * 1.9, 8, 0.7, 0.7, top - 5.5, top - 1, 1)); c.add(col(8, 8 + i * 1.9, 0.7, 0.7, top - 5.5, top - 1, 1)); }   // 갈퀴
                c.add(col(8, 8, 3.0, 3.0, top - 4.6, top - 1.4, 2));                                // 보주 (공 모양 근사)
                c.add(col(8, 8, 2.0, 3.8, top - 4.1, top - 1.9, 2));
                c.add(col(8, 8, 3.8, 2.0, top - 4.1, top - 1.9, 2));
                c.add(col(8, 8, 2.0, 2.0, top - 5.0, top - 1.0, 2));
            }
            case "hammer" -> {
                double L = 20, ys = 8 - L / 2, top = ys + L;
                c.add(col(8, 8, 1.4, 1.4, ys, top - 1, 3));
                c.add(col(8, 8, 1.9, 1.9, ys, ys + 1, 1));
                c.add(b(4, top - 5, 6.2, 12, top, 9.8, 0));                                         // 망치 머리
                c.add(b(3.4, top - 4.6, 6.6, 4, top - 0.4, 9.4, 4)); c.add(b(12, top - 4.6, 6.6, 12.6, top - 0.4, 9.4, 4));   // 치는 면
                c.add(b(7, top - 5.3, 6, 9, top + 0.3, 10, 1));                                    // 쇠테
            }
            default -> {   // pickaxe
                double L = 21, ys = 8 - L / 2, top = ys + L;
                c.add(col(8, 8, 1.4, 1.4, ys, top - 1, 3));
                c.add(col(8, 8, 2.4, 2.0, top - 2.6, top, 1));
                c.add(b(10.2, top - 2.2, 7.4, 13.5, top - 0.6, 8.6, 0)); c.add(b(13.5, top - 3, 7.55, 15.6, top - 1.6, 8.45, 4));   // 오른쪽 날 (조금씩 휜다)
                c.add(b(2.5, top - 2.2, 7.4, 5.8, top - 0.6, 8.6, 0)); c.add(b(0.4, top - 3, 7.55, 2.5, top - 1.6, 8.45, 4));
                c.add(b(5.8, top - 2.4, 7.3, 10.2, top - 0.4, 8.7, 0));
            }
        }
        List<ModelKit.Cube> tilted = new ArrayList<>();
        for (ModelKit.Cube q : c) tilted.add(q.rotated("z", -45, 8, 8, 8));   // 손에 든 바닐라 검과 같은 기울기
        return new Made(tilted, st, HANDHELD);
    }

    // ------------------------------------------------------------------ 투구 (머리 중심 = 8,8,8 · 머리 = 1.6 ~ 14.4)
    private static Made helmet(ItemType t, Color metal, Color acc) {
        List<ModelKit.Cube> c = new ArrayList<>();
        String id = t.id();
        ModelKit.Style st;
        if (t.hasTag("crown")) {
            st = new ModelKit.Style(PixelArt.GOLD, new Color(60, 110, 230), new Color(220, 40, 50), new Color(120, 80, 20), new Color(255, 236, 150), "metal", "glow", "smooth");
            c.add(b(1.4, 11, 1.4, 14.6, 14, 2.2, 0)); c.add(b(1.4, 11, 13.8, 14.6, 14, 14.6, 0));
            c.add(b(1.4, 11, 2.2, 2.2, 14, 13.8, 0)); c.add(b(13.8, 11, 2.2, 14.6, 14, 13.8, 0));
            c.add(b(1.2, 10.6, 1.2, 14.8, 11.2, 14.8, 4));                                          // 아래 테
            double[] px = {1.4, 7.25, 13.1};
            for (double x : px) for (double z : new double[]{1.4, 13.1}) c.add(b(x, 14, z, x + 1.5, x == 7.25 ? 17.5 : 16, z + 1.5, 0));   // 뾰족 장식
            c.add(b(1.4, 14, 7.25, 2.9, 16, 8.75, 0)); c.add(b(13.1, 14, 7.25, 14.6, 16, 8.75, 0));
            c.add(b(7.3, 11.6, 0.8, 8.7, 13.2, 1.4, 2)); c.add(b(3.6, 11.9, 1.0, 4.8, 12.9, 1.4, 1)); c.add(b(11.2, 11.9, 1.0, 12.4, 12.9, 1.4, 1));   // 보석
            c.add(b(7.4, 17.5, 7.4, 8.6, 18.7, 8.6, 2));
            return new Made(c, st, HELMET);
        }
        boolean dark = id.contains("hawk") || t.material().startsWith("NETHERITE");
        Color eye = id.contains("hawk") ? new Color(255, 50, 50) : acc != null ? acc : new Color(255, 200, 80);
        Color plume = id.contains("graham") ? new Color(48, 86, 192) : id.contains("hawk") ? new Color(130, 20, 30) : new Color(200, 50, 40);
        st = new ModelKit.Style(dark ? new Color(70, 64, 80) : metal, id.contains("talok") ? new Color(222, 210, 180) : PixelArt.BRASS, plume,
                id.contains("talok") ? new Color(120, 92, 64) : new Color(36, 30, 40), eye, "metal", id.contains("talok") ? "bone" : "metal", "hide");
        if (id.contains("hawk")) st = new ModelKit.Style(new Color(70, 64, 80), new Color(104, 98, 116), new Color(200, 30, 40), new Color(30, 26, 34),
                new Color(190, 190, 206), "metal", "metal", "hide");   // 데스 나이트: 검은 쇠 · 은빛 가시 · 붉은 눈
        if (id.contains("talok")) {   // 뿔 투구: 얼굴이 트인 둥근 모자 + 털 테 + 굽은 뿔
            oct(c, 1.2, 7, 1.2, 14.8, 13.2, 14.8, 1.4, 0);
            dome(c, 13.2, 1.2, 14.8, 0);
            c.add(b(1.2, 1.5, 1.2, 3, 7, 8.5, 0)); c.add(b(13, 1.5, 1.2, 14.8, 7, 8.5, 0)); c.add(b(1.2, 1.5, 9, 14.8, 7, 14.8, 0));
            c.add(b(0.6, 6, 0.6, 15.4, 8.2, 15.4, 3));                                              // 털 테
            c.add(b(7.2, 7, 0.6, 8.8, 13.5, 1.2, 0));                                               // 코가리개
            // 뿔: 옆으로 나와 위로 휜다 (세 마디)
            c.add(b(14.4, 10, 6.8, 18, 12.4, 9.2, 1).rotated("z", 22.5, 14.4, 11.2, 8));
            c.add(b(16.6, 12.6, 7, 19.2, 14.6, 9, 1).rotated("z", 45, 16.6, 13.6, 8));
            c.add(b(17.6, 15, 7.3, 19, 18, 8.7, 1));
            c.add(b(-2, 10, 6.8, 1.6, 12.4, 9.2, 1).rotated("z", -22.5, 1.6, 11.2, 8));
            c.add(b(-3.2, 12.6, 7, -0.6, 14.6, 9, 1).rotated("z", -45, -0.6, 13.6, 8));
            c.add(b(-3, 15, 7.3, -1.6, 18, 8.7, 1));
            return new Made(c, st, HELMET);
        }
        // 큰 투구 (그라함 · 반 호크 · 그 밖)
        oct(c, 1.2, 1.4, 1.2, 14.8, 13.2, 14.8, 1.6, 0);                                           // 통 (모서리를 깎은 팔각)
        dome(c, 13.2, 1.2, 14.8, 0);                                                               // 둥근 윗머리
        c.add(b(2.6, 1.8, 0.6, 13.4, 12.6, 1.2, 0, "VISOR"));                                      // 얼굴 가리개 (앞으로 나옴)
        oct(c, 0.8, 0.8, 0.8, 15.2, 2.2, 15.2, 1.6, 1);                                            // 아래 테
        c.add(b(7.4, 13, 0.9, 8.6, 16.6, 15.2, 1));                                                // 정수리 능선
        if (id.contains("hawk")) {   // 가시 볏 · 뺨 날 · 뒤로 뻗은 뿔
            for (int i = 0; i < 3; i++) c.add(b(7.3, 15.8, 4 + i * 3, 8.7, 19.5 - i, 5.4 + i * 3, 4).rotated("x", -22.5, 8, 15.8, 4.7 + i * 3));
            c.add(b(-0.6, 2, 1.5, 1.2, 9, 6, 1).rotated("y", -22.5, 1.2, 5, 4)); c.add(b(14.8, 2, 1.5, 16.6, 9, 6, 1).rotated("y", 22.5, 14.8, 5, 4));
            c.add(b(13.8, 10.5, 8, 15.4, 12.5, 16, 4).rotated("x", 22.5, 14.6, 11.5, 8)); c.add(b(0.6, 10.5, 8, 2.2, 12.5, 16, 4).rotated("x", 22.5, 1.4, 11.5, 8));
        } else {   // 깃털 장식 (뒤로 넘어감)
            c.add(b(7.2, 16, 3.5, 8.8, 20, 6, 2));
            c.add(b(7.2, 15.5, 6, 8.8, 19.5, 12, 2).rotated("x", 22.5, 8, 16, 6));
            c.add(b(7.4, 12, 12, 8.6, 16, 15.6, 2));
        }
        return new Made(c, st, HELMET);
    }

    // ------------------------------------------------------------------ 덩어리 물건
    private static Made lump(ItemType t, String k, Color acc) {
        List<ModelKit.Cube> c = new ArrayList<>();
        Color own = Color.getHSBColor((t.id().hashCode() & 0xffff) / 65535f, 0.55f, 0.8f);
        ModelKit.Style st;
        switch (k) {
            case "potion" -> {
                Color liq = t.hasTag("potion_t3") ? new Color(196, 30, 80) : t.hasTag("potion_t2") ? new Color(228, 60, 60) : new Color(240, 130, 110);
                st = new ModelKit.Style(liq, new Color(200, 224, 236), liq, PixelArt.WOOD, Color.WHITE, "smooth", "glass", "wood");
                c.add(col(8, 8, 7, 7, 1, 7.5, 2)); c.add(col(8, 8, 8, 5, 2, 6.5, 2)); c.add(col(8, 8, 5, 8, 2, 6.5, 2));   // 둥근 몸통
                c.add(col(8, 8, 6, 6, 7.5, 9, 1)); c.add(col(8, 8, 2.6, 2.6, 9, 11.5, 1)); c.add(col(8, 8, 3.4, 3.4, 11.5, 12.3, 1));
                c.add(col(8, 8, 2.4, 2.4, 12.3, 14.2, 3));
            }
            case "ingot" -> {
                Color m = t.id().contains("silver") ? new Color(222, 228, 240) : t.id().contains("gold") ? PixelArt.GOLD : PixelArt.IRON;
                st = new ModelKit.Style(m, m, m, m, Color.WHITE, "blade", "blade");
                c.add(col(8, 8, 11, 5.5, 3, 5.4, 0)); c.add(col(8, 8, 9.8, 4.6, 5.4, 7.6, 0)); c.add(col(8, 8, 8.6, 3.6, 7.6, 8.3, 4));
            }
            case "gem", "scrap" -> {
                Color g = t.hasTag("frost") ? new Color(150, 222, 255) : t.hasTag("undead") ? new Color(40, 170, 150) : t.material().equals("ECHO_SHARD") ? new Color(30, 90, 110)
                        : k.equals("scrap") ? new Color(62, 74, 120) : own;
                st = new ModelKit.Style(g, Canvas.mix(g, Color.WHITE, 0.35), g, Canvas.mix(g, Color.BLACK, 0.3), Color.WHITE, "crystal", "crystal");
                c.add(col(8, 8, 2.4, 2.4, 2, 3.5, 3)); c.add(col(8, 8, 5, 5, 3.5, 6, 0)); c.add(col(8, 8, 7, 7, 6, 9, 0));
                c.add(col(8, 8, 5.6, 5.6, 9, 10.5, 1)); c.add(col(8, 8, 3.6, 3.6, 10.5, 11.3, 4));
            }
            case "ore" -> {
                Color ore = t.id().contains("copper") ? new Color(222, 120, 70) : t.id().contains("silver") ? new Color(220, 226, 240) : new Color(214, 170, 140);
                st = new ModelKit.Style(new Color(112, 108, 104), new Color(96, 92, 90), ore, new Color(70, 66, 64), ore, "stone", "stone");
                c.add(b(3, 2, 4, 11, 8, 12, 0)); c.add(b(8, 2, 3, 13, 6.5, 9, 1)); c.add(b(5, 7, 5, 10, 10, 10, 0));
                c.add(b(4.5, 5, 3.5, 6.5, 6.5, 4.2, 2)); c.add(b(9, 8, 4.6, 10.5, 9.2, 5.2, 2)); c.add(b(12.6, 3, 5, 13.3, 4.6, 6.5, 2)); c.add(b(6, 2.5, 11.6, 8, 4, 12.3, 2));
            }
            case "log" -> {
                Color bark = t.id().contains("highland") ? new Color(84, 58, 36) : new Color(118, 82, 48);
                st = new ModelKit.Style(bark, new Color(204, 168, 112), bark, bark, bark, "bark", "rings");
                c.add(b(2, 4, 5, 14, 10, 11, 0)); c.add(b(2, 5, 4, 14, 9, 12, 0));
                c.add(b(1.6, 5, 5, 2, 9, 11, 1)); c.add(b(14, 5, 5, 14.4, 9, 11, 1));
            }
            case "block" -> {
                Color s = t.hasTag("marble") ? new Color(234, 232, 228) : new Color(218, 200, 148);
                st = new ModelKit.Style(s, s, s, s, s, "stone", "stone");
                c.add(col(8, 8, 10, 10, 3, 13, 0));
            }
            case "bread" -> {
                st = new ModelKit.Style(new Color(204, 150, 76), new Color(232, 186, 112), new Color(240, 200, 130), new Color(170, 110, 50), Color.WHITE, "hide", "hide");
                c.add(b(3, 3, 5, 13, 7, 11, 0)); c.add(b(4, 7, 5.6, 12, 8.6, 10.4, 1));
                for (int i = 0; i < 3; i++) c.add(b(5 + i * 2.5, 8.6, 6.5, 6 + i * 2.5, 8.9, 9.5, 2));   // 칼집
            }
            case "meat" -> {
                st = new ModelKit.Style(new Color(196, 64, 64), PixelArt.BONE, new Color(240, 196, 190), new Color(150, 40, 40), Color.WHITE, "hide", "bone");
                c.add(b(3, 3, 4.5, 11, 7.5, 11.5, 0)); c.add(b(3.6, 7.5, 5.2, 10.4, 8.4, 10.8, 2));
                c.add(b(11, 4.6, 7.2, 14.6, 5.8, 8.8, 1)); c.add(b(14.2, 4, 6.6, 15.6, 6.4, 9.4, 1));
            }
            case "bone" -> {
                st = new ModelKit.Style(PixelArt.BONE, PixelArt.BONE, PixelArt.BONE, Canvas.mix(PixelArt.BONE, Color.GRAY, 0.3), Color.WHITE, "bone", "bone");
                c.add(b(3.4, 7.2, 7.2, 12.6, 8.8, 8.8, 0));
                c.add(b(1.6, 6.2, 6.4, 3.4, 8.2, 8, 0)); c.add(b(1.6, 7.8, 8, 3.4, 9.8, 9.6, 0));
                c.add(b(12.6, 6.2, 6.4, 14.4, 8.2, 8, 0)); c.add(b(12.6, 7.8, 8, 14.4, 9.8, 9.6, 0));
            }
            case "statue", "relic" -> {
                Color s = k.equals("relic") ? new Color(236, 214, 140) : new Color(226, 222, 212);
                st = new ModelKit.Style(s, Canvas.mix(s, Color.GRAY, 0.35), new Color(255, 236, 140), Canvas.mix(s, Color.GRAY, 0.5), Color.WHITE, "stone", "stone");
                c.add(b(4, 0, 4, 12, 1.6, 12, 1)); c.add(b(5, 1.6, 5, 11, 2.4, 11, 1));
                c.add(b(6.3, 2.4, 6.6, 9.7, 9, 9.4, 0)); c.add(b(6.6, 9, 6.6, 9.4, 12, 9.4, 0, "FACE"));
                c.add(b(5, 5.5, 7.2, 6.3, 9, 8.8, 0)); c.add(b(9.7, 6.5, 7.2, 11, 10, 8.8, 0));
                if (k.equals("relic")) c.add(b(9.8, 10, 7.3, 11.2, 11.4, 8.7, 2));
            }
            case "bandage" -> {
                st = new ModelKit.Style(new Color(236, 230, 214), new Color(210, 40, 40), new Color(210, 40, 40), new Color(196, 188, 170), Color.WHITE, "cloth", "smooth");
                c.add(b(4, 4, 5, 12, 10, 11, 0)); c.add(b(4, 5, 4, 12, 9, 12, 0)); c.add(b(3.6, 5, 5, 4, 9, 11, 3)); c.add(b(12, 5, 5, 12.4, 9, 11, 3));
                c.add(b(7.2, 10, 6.2, 8.8, 10.2, 9.8, 1)); c.add(b(6.2, 10, 7.2, 9.8, 10.2, 8.8, 1));
                c.add(b(12, 4, 6, 15, 4.4, 10, 0));
            }
            case "whetstone" -> {
                st = new ModelKit.Style(new Color(118, 120, 130), new Color(150, 152, 162), new Color(170, 174, 184), new Color(90, 92, 100), Color.WHITE, "stone", "stone");
                c.add(b(2, 3, 5, 14, 6, 11, 0)); c.add(b(2.4, 6, 5.4, 13.6, 6.4, 10.6, 1));
            }
            case "pot" -> {
                st = new ModelKit.Style(new Color(72, 72, 80), new Color(96, 96, 104), new Color(204, 120, 60), new Color(50, 50, 56), Color.WHITE, "metal", "metal");
                c.add(col(8, 8, 12, 12, 1, 9, 0)); c.add(col(8, 8, 13, 13, 9, 10, 1)); c.add(col(8, 8, 10.6, 10.6, 9.4, 10.1, 2));
                c.add(b(0.6, 7, 7, 2, 8, 9, 1)); c.add(b(14, 7, 7, 15.4, 8, 9, 1));
            }
            case "mortar" -> {
                st = new ModelKit.Style(new Color(176, 168, 158), new Color(196, 188, 176), new Color(120, 160, 90), PixelArt.WOOD, Color.WHITE, "stone", "stone", "wood");
                c.add(col(8, 8, 9, 9, 1, 3, 0)); c.add(col(8, 8, 11, 11, 3, 7, 0)); c.add(col(8, 8, 9, 9, 6.6, 7.2, 2));
                c.add(b(9, 5, 7.3, 10.4, 14, 8.7, 3).rotated("z", -22.5, 9.7, 7, 8));
            }
            default -> {   // drop (피)
                st = new ModelKit.Style(new Color(150, 12, 24), new Color(190, 40, 50), new Color(255, 160, 160), new Color(110, 8, 18), Color.WHITE, "smooth", "smooth");
                c.add(col(8, 8, 6, 6, 2, 6.5, 0)); c.add(col(8, 8, 7, 4.6, 2.5, 6, 0)); c.add(col(8, 8, 4, 4, 6.5, 8.5, 1)); c.add(col(8, 8, 2.2, 2.2, 8.5, 10.5, 1));
                c.add(col(8, 8, 0.8, 0.8, 10.5, 11.6, 1)); c.add(b(5.4, 4.5, 4.9, 6.2, 5.6, 5.1, 2));
            }
        }
        return new Made(c, st, BLOCKY);
    }
}
