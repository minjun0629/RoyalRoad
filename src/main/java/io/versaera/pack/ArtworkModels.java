package io.versaera.pack;

import io.versaera.domain.art.ArtMaterials;
import io.versaera.domain.art.ArtworkKind;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 대형 조각 작품의 3D 모델 (ART-02). 작품 종류마다 자리(받침 · 몸체 · 장식)를 따로 깎고, 재료 모양(돌 · 사암 · 대리석 · 쇠 · 은 · 하늘 금속 ·
 * 자수정 · 서리 수정 · 참나무 · 가문비)마다 색 · 결을 입힌다 → 받침 · 몸체 · 장식을 겹쳐 세우면 재료 조합이 그대로 보인다.
 * 모델 좌표: 바닥 y = 0, 가운데 x = z = 8 (서버가 크기 배율만큼 키운다).
 */
public final class ArtworkModels {
    static final String DISPLAY = "{\"fixed\":{\"rotation\":[0,180,0]},\"gui\":{\"rotation\":[25,200,0],\"scale\":[0.5,0.5,0.5]}}";

    private ArtworkModels() {
    }

    public static String key(String kind, String slot, String look) {
        return "artwork/" + kind + "/" + slot + "/" + look;
    }

    /** 만들 모델 키 전부 */
    public static List<String> keys(Collection<ArtworkKind> kinds) {
        List<String> out = new ArrayList<>();
        for (ArtworkKind k : kinds)
            for (ArtworkKind.Part p : k.parts())
                for (String look : ArtMaterials.LOOKS) out.add(key(k.id(), p.slot(), look));
        return out;
    }

    public static ModelKit.Built build(String kind, String slot, String look) {
        List<ModelKit.Cube> c = shape(kind, slot);
        if (c.isEmpty()) throw new IllegalArgumentException("모양 없음: " + kind + "/" + slot);
        String tex = "artwork/" + kind + "_" + slot + "_" + look;
        return ModelKit.build(tex, List.of(new ModelKit.Part("art", "NONE", 8, 0, 8, 0, c)), style(look), tex, DISPLAY);
    }

    // ------------------------------------------------------------------ 재료 모양
    static ModelKit.Style style(String look) {
        record L(int r, int g, int b, String grain) {}
        L l = switch (look) {
            case "sandstone" -> new L(214, 192, 138, "stone");
            case "marble" -> new L(234, 230, 222, "stone");
            case "iron" -> new L(168, 172, 182, "metal");
            case "silver" -> new L(214, 218, 230, "metal");
            case "skymetal" -> new L(118, 150, 214, "metal");
            case "amethyst" -> new L(160, 100, 212, "crystal");
            case "frost" -> new L(168, 220, 242, "crystal");
            case "oak" -> new L(152, 110, 64, "wood");
            case "spruce" -> new L(108, 78, 48, "wood");
            default -> new L(130, 128, 122, "stone");
        };
        Color main = new Color(l.r(), l.g(), l.b());
        return new ModelKit.Style(main, Canvas.mix(main, Color.BLACK, 0.18), Canvas.mix(main, Color.WHITE, 0.35), Canvas.mix(main, Color.BLACK, 0.45),
                Canvas.mix(main, Color.WHITE, 0.2), l.grain(), l.grain(), l.grain());
    }

    // ------------------------------------------------------------------ 모양 (단위: 16 = 한 블록)
    private static ModelKit.Cube b(double x1, double y1, double z1, double x2, double y2, double z2, int mat) {
        return new ModelKit.Cube(x1, y1, z1, x2, y2, z2, mat, null);
    }

    /** 가운데 (8, 8) 에 선 상자 */
    private static ModelKit.Cube col(double w, double d, double y1, double y2, int mat) {
        return b(8 - w / 2, y1, 8 - d / 2, 8 + w / 2, y2, 8 + d / 2, mat);
    }

    static List<ModelKit.Cube> shape(String kind, String slot) {
        List<ModelKit.Cube> c = new ArrayList<>();
        switch (kind + "/" + slot) {
            // ---- 흉상
            case "bust/pedestal" -> {
                c.add(col(10, 10, 0, 1.5, 1));
                c.add(col(7, 7, 1.5, 9, 0));
                c.add(col(9, 9, 9, 10.5, 1));
            }
            case "bust/body" -> {
                c.add(b(3, 10.5, 5, 13, 15, 11, 0));        // 어깨 · 가슴
                c.add(b(4, 15, 5.5, 12, 15.8, 10.5, 1));    // 옷깃
                c.add(col(3, 3, 15, 17, 0));                // 목
                c.add(b(5.5, 17, 5, 10.5, 23, 11, 0));      // 머리
                c.add(b(5.2, 21, 4.6, 10.8, 24, 10.2, 1));  // 머리카락
                c.add(b(7.5, 19, 11, 8.5, 21, 12, 0));      // 코
                c.add(b(6, 20.6, 10.9, 7, 21.2, 11.1, 3)); // 눈
                c.add(b(9, 20.6, 10.9, 10, 21.2, 11.1, 3));
            }
            // ---- 입상 (기사)
            case "statue/pedestal" -> {
                c.add(col(15, 15, 0, 2, 1));
                c.add(col(13, 13, 2, 5, 0));
                c.add(col(14, 14, 5, 5.6, 1));
            }
            case "statue/body" -> {
                c.add(b(5, 5.6, 7, 7.5, 14, 9.5, 0));       // 다리
                c.add(b(8.5, 5.6, 7, 11, 14, 9.5, 0));
                c.add(b(4.5, 14, 6.5, 11.5, 21, 9.5, 0));   // 몸통
                c.add(b(4, 12, 9.5, 12, 21, 10.6, 1));      // 망토
                c.add(b(3, 14.5, 7, 4.5, 21, 9, 0));        // 팔
                c.add(b(11.5, 14.5, 7, 13, 21, 9, 0));
                c.add(b(5.5, 19, 6, 10.5, 21.5, 10, 1));    // 어깨받이
                c.add(col(2, 2, 21, 22, 0));                // 목
                c.add(col(4.4, 4.4, 22, 26.4, 0));          // 머리
                c.add(b(7.3, 23.6, 5.4, 8.7, 24.4, 5.9, 3)); // 눈매
            }
            case "statue/accent" -> {
                c.add(b(5.6, 26.4, 5.6, 10.4, 27.4, 10.4, 0));   // 관
                for (double x : new double[]{5.6, 7.6, 9.4}) c.add(b(x, 27.4, 5.6, x + 1, 28.4, 6.6, 0));
                c.add(b(13, 7, 7.4, 13.8, 21, 8.6, 0));          // 검: 날
                c.add(b(12.2, 6, 7, 14.6, 7, 9, 1));             // 코등이
                c.add(b(13, 3.5, 7.6, 13.8, 6, 8.4, 3));         // 손잡이
                c.add(b(6.5, 17, 6.2, 9.5, 18.6, 6.6, 2));       // 가슴 보석
            }
            // ---- 짐승상 (웅크린 사자)
            case "beast/pedestal" -> {
                c.add(col(15, 16, 0, 3, 1));
                c.add(col(13, 14, 3, 3.6, 0));
            }
            case "beast/body" -> {
                c.add(b(4, 3.6, 2, 12, 9.5, 12, 0));       // 몸통
                c.add(b(3.5, 5, 10, 12.5, 14, 12.5, 1));   // 갈기
                c.add(b(4.5, 6, 12, 11.5, 13, 15.5, 0));   // 머리
                c.add(b(6, 6, 15.5, 10, 9, 16.5, 0));      // 주둥이
                c.add(b(4, 3.6, 12, 6.5, 6.5, 15.5, 0));   // 앞발
                c.add(b(9.5, 3.6, 12, 12, 6.5, 15.5, 0));
                c.add(b(3.4, 3.6, 2, 5, 8, 6, 1));         // 뒷다리
                c.add(b(11, 3.6, 2, 12.6, 8, 6, 1));
                c.add(b(7, 5, 0.5, 9, 6.5, 2, 0));         // 꼬리
                c.add(b(6.5, 6, 0, 9.5, 8.5, 1, 1));
            }
            case "beast/accent" -> {
                c.add(b(5.5, 10.4, 15.5, 6.8, 11.4, 15.9, 2)); // 눈
                c.add(b(9.2, 10.4, 15.5, 10.5, 11.4, 15.9, 2));
                c.add(b(4, 5, 11.6, 12, 6, 12.8, 0));          // 목걸이
                c.add(b(7.2, 3.8, 12.6, 8.8, 5.4, 13.4, 2));   // 목걸이 보석
            }
            // ---- 기념비
            case "monument/pedestal" -> {
                c.add(col(16, 16, 0, 2, 1));
                c.add(col(14, 14, 2, 4, 0));
                c.add(col(12, 12, 4, 6, 1));
            }
            case "monument/body" -> {
                c.add(col(6, 6, 6, 20, 0));
                c.add(col(5, 5, 20, 28, 0));
                c.add(col(4, 4, 28, 30.5, 1));
                c.add(col(7, 7, 12, 13, 1));    // 띠
                c.add(col(6, 6, 20, 21, 1));
            }
            case "monument/accent" -> {
                c.add(col(2, 2, 30.5, 32, 0));  // 꼭대기
                c.add(b(6, 8, 4.4, 10, 11, 5, 0));    // 사면 명판
                c.add(b(6, 8, 11, 10, 11, 11.6, 0));
                c.add(b(4.4, 8, 6, 5, 11, 10, 0));
                c.add(b(11, 8, 6, 11.6, 11, 10, 0));
                c.add(col(7.4, 7.4, 16, 16.6, 2)); // 빛나는 띠
            }
            // ---- 분수
            case "fountain/pedestal" -> {
                c.add(b(0, 0, 0, 16, 4, 1.5, 0));
                c.add(b(0, 0, 14.5, 16, 4, 16, 0));
                c.add(b(0, 0, 1.5, 1.5, 4, 14.5, 0));
                c.add(b(14.5, 0, 1.5, 16, 4, 14.5, 0));
                c.add(b(1.5, 0, 1.5, 14.5, 1, 14.5, 1));
                c.add(b(-0.4, 4, -0.4, 16.4, 4.6, 2, 1));      // 테두리 (네 변 — 가운데 물이 보이게)
                c.add(b(-0.4, 4, 14, 16.4, 4.6, 16.4, 1));
                c.add(b(-0.4, 4, 2, 2, 4.6, 14, 1));
                c.add(b(14, 4, 2, 16.4, 4.6, 14, 1));
            }
            case "fountain/body" -> {
                c.add(col(3, 3, 1, 10, 0));
                c.add(col(8, 8, 10, 11.5, 0));
                c.add(col(9, 9, 11.5, 12.3, 1));
                c.add(col(2, 2, 12.3, 14, 0));
            }
            case "fountain/accent" -> {
                c.add(b(1.5, 3.4, 1.5, 14.5, 3.7, 14.5, 2));    // 물 (보석빛)
                c.add(col(2.2, 2.2, 14, 16.4, 2));              // 꼭대기 보석
                c.add(col(6.6, 6.6, 11.6, 11.9, 2));            // 위 수반의 물
            }
            default -> { }
        }
        // 짐승상은 머리가 +z 로 깎여 있다 → 다른 작품처럼 앞(-z)을 보게 뒤집는다
        if (kind.equals("beast")) c.replaceAll(q -> new ModelKit.Cube(q.x1(), q.y1(), 16 - q.z2(), q.x2(), q.y2(), 16 - q.z1(), q.mat(), q.deco()));
        return c;
    }

    /** 모든 모양이 마인크래프트 모델 범위(-16 ~ 32) 안에 있는가 (테스트용) */
    static boolean inBounds(Map<String, List<ModelKit.Cube>> all) {
        for (List<ModelKit.Cube> l : all.values())
            for (ModelKit.Cube q : l)
                if (Math.min(Math.min(q.x1(), q.y1()), q.z1()) < -16 || Math.max(Math.max(q.x2(), q.y2()), q.z2()) > 32) return false;
        return true;
    }
}
