package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 관절이 있는 3D 모델 (필드 보스 · 거대 보스 · 몬스터 머리, RP-01).
 * <ul>
 *   <li>모델 = 부위(몸 · 머리 · 팔 · 다리 · 날개 · 꼬리 · 무기)의 묶음. 부위마다 관절(pivot)과 움직임 종류(anim)가 있어
 *       플러그인이 부위별 ItemDisplay 를 보간(interpolation)으로 돌려 걷기 · 날갯짓 · 휘두르기를 보여 준다</li>
 *   <li>텍스처 = 모든 상자 면을 한 장(최대 512×512)에 펼쳐 붙인다 — 면마다 따로 칠해 이음새 없이 깔끔하다.
 *       1 모델 단위 = 2 텍셀. 면마다 결(쇠 판 · 뼈 · 비늘 · 천 · 가죽 · 막 · 수정), 가장자리 음영, 위 밝고 아래 어두운 빛, 얼굴(눈 · 입) · 문장</li>
 *   <li>좌표는 Minecraft 모델 한계(-16 ~ 32) 안. 앞은 -z (북쪽)</li>
 * </ul>
 */
public final class ModelKit {
    /** 재질 칸: 0 몸 · 1 둘째(갑옷 · 날개막) · 2 강조(눈 · 빛) · 3 어둠(관절 · 발톱) · 4 쇠(무기 날) */
    public record Cube(double x1, double y1, double z1, double x2, double y2, double z2, int mat, String deco) {}

    /**
     * @param anim BODY · HEAD · ARM_A(무기 팔, 공격 때 휘두름) · ARM_B · LEG_A · LEG_B · WING_L · WING_R · TAIL · NONE
     * @param phase 같은 종류끼리 어긋나게 (히드라의 여러 머리)
     */
    public record Part(String name, String anim, double px, double py, double pz, double phase, List<Cube> cubes) {}

    private ModelKit() {
    }

    private static Cube c(double x1, double y1, double z1, double x2, double y2, double z2, int mat) {
        return new Cube(x1, y1, z1, x2, y2, z2, mat, null);
    }

    private static Cube c(double x1, double y1, double z1, double x2, double y2, double z2, int mat, String deco) {
        return new Cube(x1, y1, z1, x2, y2, z2, mat, deco);
    }

    private static Part p(String name, String anim, double px, double py, double pz, Cube... cubes) {
        return new Part(name, anim, px, py, pz, 0, List.of(cubes));
    }

    private static Part p(String name, String anim, double px, double py, double pz, double phase, Cube... cubes) {
        return new Part(name, anim, px, py, pz, phase, List.of(cubes));
    }

    // ------------------------------------------------------------------ 뼈대
    public static List<Part> rig(String look) {
        return switch (look) {
            case "KNIGHT" -> List.of(
                    p("body", "BODY", 8, 9, 8, c(4, 9, 5.5, 12, 19, 10.5, 1, "EMBLEM"), c(4.5, 8, 6, 11.5, 9.5, 10, 3), c(4.5, 6, 10.5, 11.5, 18, 11, 2)),
                    p("head", "HEAD", 8, 19, 8, c(5, 19, 5, 11, 25, 11, 1, "VISOR"), c(7.5, 25, 6, 8.5, 28, 10, 2)),
                    p("arm_a", "ARM_A", 3, 18, 8, c(1, 10, 6.5, 4, 19, 9.5, 1), c(0.5, 16.5, 6, 4.5, 19.5, 10, 1),
                            c(1.5, 9, 2, 3.5, 11, 9, 3), c(2, 10, -14, 3, 11, 2, 4), c(0.5, 9.5, 1.5, 4.5, 11.5, 2.5, 2)),   // 손 · 대검 · 코등이
                    p("arm_b", "ARM_B", 13, 18, 8, c(12, 10, 6.5, 15, 19, 9.5, 1), c(11.5, 16.5, 6, 15.5, 19.5, 10, 1), c(12, 6, 3, 16, 15, 4, 1, "EMBLEM")),   // 방패
                    p("leg_a", "LEG_A", 6.4, 9, 8, c(4.8, 0, 6, 7.8, 9, 10, 1), c(4.6, 0, 5.4, 8, 2, 10.2, 3)),
                    p("leg_b", "LEG_B", 9.6, 9, 8, c(8.2, 0, 6, 11.2, 9, 10, 1), c(8, 0, 5.4, 11.4, 2, 10.2, 3)));
            case "VAMPIRE" -> List.of(
                    p("body", "BODY", 8, 9, 8, c(4.5, 9, 6, 11.5, 19, 10, 0, "EMBLEM"), c(2, 3, 10, 14, 19, 10.8, 1), c(3.5, 17, 9, 12.5, 22, 11, 1)),   // 몸 · 망토 · 깃
                    p("head", "HEAD", 8, 19, 8, c(5.5, 19, 5.5, 10.5, 24.5, 10.5, 0, "FACE"), c(5.2, 23.5, 5.2, 10.8, 25, 10.8, 3)),
                    p("arm_a", "ARM_A", 3.5, 18, 8, c(2, 10, 6.8, 4.5, 19, 9.2, 0), c(2, 8.5, 6.8, 4.5, 10, 9.2, 3)),
                    p("arm_b", "ARM_B", 12.5, 18, 8, c(11.5, 10, 6.8, 14, 19, 9.2, 0), c(11.5, 8.5, 6.8, 14, 10, 9.2, 3)),
                    p("leg_a", "LEG_A", 6.6, 9, 8, c(5.2, 0, 6.5, 7.8, 9, 9.5, 3)),
                    p("leg_b", "LEG_B", 9.4, 9, 8, c(8.2, 0, 6.5, 10.8, 9, 9.5, 3)));
            case "CASTER" -> List.of(
                    p("body", "BODY", 8, 6, 8, c(3.5, 0, 4, 12.5, 6, 12, 1), c(4, 6, 4.5, 12, 16, 11.5, 1, "EMBLEM"), c(3.5, 14, 4, 12.5, 17, 12, 2)),
                    p("head", "HEAD", 8, 16, 8, c(5.5, 16, 5.5, 10.5, 21, 10.5, 0, "SKULL"), c(5, 19, 5, 11, 22.5, 11, 1), c(5, 16, 10, 11, 21, 11, 1)),
                    p("arm_a", "ARM_A", 3, 15, 8, c(1.5, 8, 6.5, 4, 15.5, 9.5, 1), c(2, 6.5, 7, 3.5, 8, 9, 0),
                            c(2.2, -2, 7.4, 3.2, 24, 8.6, 3), c(1.2, 24, 6.4, 4.2, 27, 9.6, 2, "GLOW")),   // 지팡이 · 보주
                    p("arm_b", "ARM_B", 13, 15, 8, c(12, 8, 6.5, 14.5, 15.5, 9.5, 1), c(12.5, 6.5, 7, 14, 8, 9, 0)));
            case "DEMON" -> List.of(
                    p("body", "BODY", 8, 9, 8, c(3, 9, 4.5, 13, 18, 11.5, 0, "EMBLEM"), c(4, 8, 5, 12, 9.5, 11, 3)),
                    p("head", "HEAD", 8, 18, 8, c(5, 18, 5, 11, 23, 11, 0, "FACE"), c(4.5, 22, 7, 5.5, 26, 8, 3), c(10.5, 22, 7, 11.5, 26, 8, 3),
                            c(4, 25, 7.2, 5, 27.5, 7.8, 3), c(11, 25, 7.2, 12, 27.5, 7.8, 3)),
                    p("arm_a", "ARM_A", 2, 17, 8, c(0, 9, 6, 3, 18, 10, 0), c(0, 7.5, 5.5, 3, 9, 10, 3)),
                    p("arm_b", "ARM_B", 14, 17, 8, c(13, 9, 6, 16, 18, 10, 0), c(13, 7.5, 5.5, 16, 9, 10, 3)),
                    p("wing_r", "WING_R", 4, 16, 11.5, c(-9, 10, 11, 4, 20, 11.8, 1, "MEMBRANE"), c(-9, 19, 10.8, 4, 20.5, 12, 3)),
                    p("wing_l", "WING_L", 12, 16, 11.5, c(12, 10, 11, 25, 20, 11.8, 1, "MEMBRANE"), c(12, 19, 10.8, 25, 20.5, 12, 3)),
                    p("tail", "TAIL", 8, 9, 11, c(7, 7, 11, 9, 9, 19, 0), c(6.5, 6, 19, 9.5, 9, 21, 2)),
                    p("leg_a", "LEG_A", 6, 9, 8, c(4.5, 0, 6, 7.5, 9, 10, 0), c(4.3, 0, 5, 7.7, 1.5, 10, 3)),
                    p("leg_b", "LEG_B", 10, 9, 8, c(8.5, 0, 6, 11.5, 9, 10, 0), c(8.3, 0, 5, 11.7, 1.5, 10, 3)));
            case "DRAGON" -> List.of(
                    p("body", "BODY", 8, 9, 9, c(4, 5, 2, 12, 13, 16, 0, "BELLY"), c(7.4, 13, 3, 8.6, 15, 14, 2)),
                    p("head", "HEAD", 8, 11, 3, c(6, 9, -3, 10, 14, 3, 0), c(5, 11, -10.5, 11, 16, -3, 0, "DRAGONFACE"), c(5.5, 9, -10, 10.5, 11, -4, 3),
                            c(5.2, 16, -6, 6.4, 19, -4.8, 3), c(9.6, 16, -6, 10.8, 19, -4.8, 3)),
                    p("wing_r", "WING_R", 4, 12, 7, c(-12, 11.5, 3, 4, 12.5, 13, 1, "MEMBRANE"), c(-12, 12, 2.5, 4, 13.5, 3.5, 3)),
                    p("wing_l", "WING_L", 12, 12, 7, c(12, 11.5, 3, 28, 12.5, 13, 1, "MEMBRANE"), c(12, 12, 2.5, 28, 13.5, 3.5, 3)),
                    p("tail", "TAIL", 8, 8, 16, c(6, 6, 16, 10, 10, 24, 0), c(7, 6.5, 24, 9, 9, 31, 0), c(7.6, 9, 24, 8.4, 11, 30, 2)),
                    p("leg_fa", "LEG_A", 5, 6, 5, c(3.5, 0, 3, 6.5, 6, 6.5, 0), c(3.2, 0, 2, 6.8, 1.2, 4, 3)),
                    p("leg_fb", "LEG_B", 11, 6, 5, c(9.5, 0, 3, 12.5, 6, 6.5, 0), c(9.2, 0, 2, 12.8, 1.2, 4, 3)),
                    p("leg_ba", "LEG_B", 5, 6, 13, c(3.5, 0, 11.5, 6.5, 6, 15, 0), c(3.2, 0, 10.5, 6.8, 1.2, 12.5, 3)),
                    p("leg_bb", "LEG_A", 11, 6, 13, c(9.5, 0, 11.5, 12.5, 6, 15, 0), c(9.2, 0, 10.5, 12.8, 1.2, 12.5, 3)));
            case "BEAST" -> List.of(
                    p("body", "BODY", 8, 8, 8, c(3, 5, 2, 13, 13, 14, 0), c(5, 12, 2, 11, 15, 10, 1)),
                    p("head", "HEAD", 8, 10, 2, c(4.5, 6, -5, 11.5, 13, 2, 0, "FACE"), c(3.5, 6, -5.5, 4.5, 10, -4.5, 2), c(11.5, 6, -5.5, 12.5, 10, -4.5, 2),
                            c(4, 13, -2, 5.5, 16, -0.5, 3), c(10.5, 13, -2, 12, 16, -0.5, 3)),
                    p("tail", "TAIL", 8, 11, 14, c(7.2, 9, 14, 8.8, 11, 20, 1)),
                    p("leg_fa", "LEG_A", 5, 5, 4.5, c(3.5, 0, 3, 6.5, 5.5, 6, 3)), p("leg_fb", "LEG_B", 11, 5, 4.5, c(9.5, 0, 3, 12.5, 5.5, 6, 3)),
                    p("leg_ba", "LEG_B", 5, 5, 11.5, c(3.5, 0, 10, 6.5, 5.5, 13, 3)), p("leg_bb", "LEG_A", 11, 5, 11.5, c(9.5, 0, 10, 12.5, 5.5, 13, 3)));
            case "GOLEM", "COLOSSUS" -> List.of(
                    p("body", "BODY", 8, 8, 8, c(2, 8, 4, 14, 21, 12, 0, "RUNE"), c(3, 20, 4.5, 13, 22, 11.5, 1)),
                    p("head", "HEAD", 8, 21, 8, c(5.5, 21, 5.5, 10.5, 25.5, 10.5, 1, "GOLEMFACE")),
                    p("arm_a", "ARM_A", 0, 19, 8, c(-3, 2, 5, 2, 20, 11, 0), c(-3.5, 1, 4.5, 2.5, 5, 11.5, 1), c(-3.5, 17, 4, 2.5, 21, 12, 1)),
                    p("arm_b", "ARM_B", 16, 19, 8, c(14, 2, 5, 19, 20, 11, 0), c(13.5, 1, 4.5, 19.5, 5, 11.5, 1), c(13.5, 17, 4, 19.5, 21, 12, 1)),
                    p("leg_a", "LEG_A", 5.5, 8, 8, c(3.5, 0, 5, 7.5, 8, 11, 0), c(3.2, 0, 4.4, 7.8, 2, 11.2, 1)),
                    p("leg_b", "LEG_B", 10.5, 8, 8, c(8.5, 0, 5, 12.5, 8, 11, 0), c(8.2, 0, 4.4, 12.8, 2, 11.2, 1)));
            case "HYDRA" -> List.of(
                    p("body", "BODY", 8, 4, 9, c(2, 0, 3, 14, 9, 15, 0, "BELLY"), c(3, 8, 4, 13, 10, 14, 1)),
                    p("head_c", "HEAD", 8, 9, 7, 0.0, c(7, 9, 6, 9, 21, 8, 0), c(6, 20, 1.5, 10, 24, 8, 1, "DRAGONFACE")),
                    p("head_l", "HEAD", 3, 8, 7, 1.7, c(2, 8, 6, 4, 18, 8, 0), c(1, 17, 1.5, 5, 20.5, 7.5, 1, "DRAGONFACE")),
                    p("head_r", "HEAD", 13, 8, 7, 3.4, c(12, 8, 6, 14, 18, 8, 0), c(11, 17, 1.5, 15, 20.5, 7.5, 1, "DRAGONFACE")),
                    p("head_ll", "HEAD", 0, 6, 9, 2.4, c(-3, 6, 8, 1, 13, 10, 0), c(-5, 12, 4.5, -1, 15.5, 9.5, 1, "DRAGONFACE")),
                    p("head_rr", "HEAD", 16, 6, 9, 4.1, c(15, 6, 8, 19, 13, 10, 0), c(17, 12, 4.5, 21, 15.5, 9.5, 1, "DRAGONFACE")),
                    p("tail", "TAIL", 8, 3, 15, c(6.5, 1, 15, 9.5, 5, 25, 0), c(7.2, 1.5, 25, 8.8, 4, 30, 0)));
            case "SALAMANDER" -> List.of(
                    p("body", "BODY", 8, 4, 9, c(4, 2, 0, 12, 7, 18, 0, "BELLY"), c(6, 7, 1, 10, 10, 16, 2, "FLAME")),
                    p("head", "HEAD", 8, 4.5, 0, c(5, 2.5, -7, 11, 7.5, 0, 0, "DRAGONFACE"), c(6.5, 7.5, -5, 9.5, 9.5, -1, 2, "FLAME")),
                    p("tail", "TAIL", 8, 4, 18, c(6, 2, 18, 10, 6, 25, 0), c(7, 2.5, 25, 9, 5, 31, 2, "FLAME")),
                    p("leg_fa", "LEG_A", 3, 3, 3.5, c(1.5, 0, 2, 4, 3.5, 5, 3)), p("leg_fb", "LEG_B", 13, 3, 3.5, c(12, 0, 2, 14.5, 3.5, 5, 3)),
                    p("leg_ba", "LEG_B", 3, 3, 13.5, c(1.5, 0, 12, 4, 3.5, 15, 3)), p("leg_bb", "LEG_A", 13, 3, 13.5, c(12, 0, 12, 14.5, 3.5, 15, 3)));
            case "FLYER" -> List.of(
                    p("body", "BODY", 8, 12, 8, c(2, 6, 2, 14, 18, 14, 0, "EYEBALL")),
                    p("wing_r", "WING_R", 2, 13, 8, c(-11, 12.5, 5, 2, 13.5, 11, 1, "MEMBRANE"), c(-11, 13, 4.5, 2, 14.5, 5.5, 3)),
                    p("wing_l", "WING_L", 14, 13, 8, c(14, 12.5, 5, 27, 13.5, 11, 1, "MEMBRANE"), c(14, 13, 4.5, 27, 14.5, 5.5, 3)),
                    p("tent_a", "TAIL", 4.5, 6, 6.5, 0.0, c(3.8, -2, 5.8, 5.2, 6, 7.2, 3)), p("tent_b", "TAIL", 8, 6, 9, 1.5, c(7.3, -4, 8.3, 8.7, 6, 9.7, 3)),
                    p("tent_c", "TAIL", 11.5, 6, 6.5, 3.0, c(10.8, -2, 5.8, 12.2, 6, 7.2, 3)), p("tent_d", "TAIL", 8, 6, 4, 4.5, c(7.3, -1, 3.3, 8.7, 6, 4.7, 3)));
            case "CRYSTAL" -> List.of(
                    p("body", "BODY", 8, 6, 8, c(4, 0, 4, 12, 12, 12, 0, "RUNE"), c(6, 12, 6, 10, 20, 10, 1, "GLOW"), c(1, 6, 7, 4, 16, 9, 1, "GLOW"),
                            c(12, 4, 7, 15, 14, 9, 1, "GLOW"), c(7, 6, 1, 9, 15, 4, 1, "GLOW"), c(7, 3, 12, 9, 13, 15, 1, "GLOW")));
            case "WORM" -> List.of(
                    p("body", "BODY", 8, 4, 8, c(5, 0, 0, 11, 6, 4, 0), c(5.5, 2, 4, 10.5, 9, 8, 0), c(6, 5, 8, 10, 13, 12, 0), c(6.5, 9, 12, 9.5, 18, 15, 0),
                            c(5, 16, 13, 11, 21, 18, 1, "MAW")));
            case "CRAB" -> List.of(
                    p("body", "BODY", 8, 4, 8, c(2, 2, 3, 14, 7, 13, 0, "FACE"), c(3, 7, 4, 13, 9, 12, 1)),
                    p("arm_a", "ARM_A", 2, 4, 3, c(-3, 3, 0, 2, 6, 4, 1), c(-4, 3, -3, 0, 7, 0, 1)),
                    p("arm_b", "ARM_B", 14, 4, 3, c(14, 3, 0, 19, 6, 4, 1), c(16, 3, -3, 20, 7, 0, 1)),
                    p("legs", "NONE", 8, 1, 8, c(0, 0, 5, 2, 2, 6, 3), c(14, 0, 5, 16, 2, 6, 3), c(0, 0, 9, 2, 2, 10, 3), c(14, 0, 9, 16, 2, 10, 3)));
            case "MASK" -> List.of(p("mask", "NONE", 8, 8, 8, c(3.5, 0, 3.5, 12.5, 9, 12.5, 0, "VISOR"), c(3, 3.5, 3, 13, 4.5, 13, 1), c(7.5, 9, 6, 8.5, 12, 10, 2)));
            default -> rig("KNIGHT");
        };
    }

    /** 부위를 모두 합친 한 덩어리 (움직이지 않는 거대 보스 · 아이콘용) */
    public static List<Part> merged(String look) {
        List<Cube> all = new ArrayList<>();
        for (Part p : rig(look)) all.addAll(p.cubes());
        return List.of(new Part("all", "NONE", 8, 8, 8, 0, all));
    }

    // ------------------------------------------------------------------ 색 · 결
    /**
     * @param grain 몸 결: metal · bone · scale · cloth · hide · stone · crystal
     */
    public record Style(Color main, Color second, Color accent, Color dark, Color metal, String grain, String secondGrain) {}

    /** 만든 결과: 부위 이름 → 모델 JSON, 그리고 텍스처 한 장 */
    public record Built(Map<String, String> models, BufferedImage texture) {}

    private static final int D = 2;   // 모델 1 단위 = 2 텍셀
    private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};

    private record Slot(Cube cube, String face, int w, int h) {}

    public static Built build(String seed, List<Part> parts, Style st, String texPath, String display) {
        // 1) 모든 면의 크기를 모아 선반식으로 붙인다
        List<Slot> slots = new ArrayList<>();
        for (Part p : parts)
            for (Cube q : p.cubes())
                for (String f : FACES) {
                    double fw = switch (f) { case "east", "west" -> q.z2() - q.z1(); default -> q.x2() - q.x1(); };
                    double fh = switch (f) { case "up", "down" -> q.z2() - q.z1(); default -> q.y2() - q.y1(); };
                    slots.add(new Slot(q, f, Math.max(1, (int) Math.round(fw * D)), Math.max(1, (int) Math.round(fh * D))));
                }
        List<Slot> order = new ArrayList<>(slots);
        order.sort((a, b) -> b.h() != a.h() ? Integer.compare(b.h(), a.h()) : Integer.compare(b.w(), a.w()));
        int size = 64;
        Map<Slot, int[]> at;
        while (true) {
            at = pack(order, size);
            if (at != null) break;
            size *= 2;
            if (size > 512) throw new IllegalStateException("텍스처가 너무 큼: " + seed);
        }
        // 2) 면마다 칠한다
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        SplittableRandom r = new SplittableRandom(seed.hashCode());
        for (Slot s : slots) paint(img, at.get(s), s, st, r);
        // 3) 부위별 모델 JSON
        Map<String, String> models = new LinkedHashMap<>();
        for (Part p : parts) {
            StringBuilder els = new StringBuilder();
            for (Cube q : p.cubes()) {
                if (els.length() > 0) els.append(',');
                els.append("{\"from\":[").append(n(q.x1())).append(',').append(n(q.y1())).append(',').append(n(q.z1())).append("],\"to\":[")
                        .append(n(q.x2())).append(',').append(n(q.y2())).append(',').append(n(q.z2())).append("],\"faces\":{");
                boolean first = true;
                for (Slot s : slots) {
                    if (s.cube() != q) continue;
                    int[] a = at.get(s);
                    double k = 16.0 / size;
                    if (!first) els.append(',');
                    first = false;
                    els.append('"').append(s.face()).append("\":{\"uv\":[").append(n(a[0] * k)).append(',').append(n(a[1] * k)).append(',')
                            .append(n((a[0] + s.w()) * k)).append(',').append(n((a[1] + s.h()) * k)).append("],\"texture\":\"#body\"}");
                }
                els.append("}}");
            }
            models.put(p.name(), "{\"textures\":{\"body\":\"versaera:" + texPath + "\",\"particle\":\"versaera:" + texPath + "\"},\"elements\":["
                    + els + "],\"display\":" + display + "}");
        }
        return new Built(models, img);
    }

    private static Map<Slot, int[]> pack(List<Slot> order, int size) {
        Map<Slot, int[]> at = new IdentityHashMap<>();
        int x = 0, y = 0, rowH = 0;
        for (Slot s : order) {
            if (s.w() > size) return null;
            if (x + s.w() > size) { x = 0; y += rowH; rowH = 0; }
            if (y + s.h() > size) return null;
            at.put(s, new int[]{x, y});
            x += s.w();
            rowH = Math.max(rowH, s.h());
        }
        return at;
    }

    private static Color[] ramp(Style st, int mat) {
        return Canvas.ramp(switch (mat) { case 1 -> st.second(); case 2 -> st.accent(); case 3 -> st.dark(); case 4 -> st.metal(); default -> st.main(); });
    }

    private static void paint(BufferedImage img, int[] at, Slot s, Style st, SplittableRandom r) {
        Color[] ramp = ramp(st, s.cube().mat());
        String grain = switch (s.cube().mat()) { case 1 -> st.secondGrain(); case 2 -> "glow"; case 3 -> "hide"; case 4 -> "polish"; default -> st.grain(); };
        if ("MEMBRANE".equals(s.cube().deco())) grain = "membrane";
        if ("GLOW".equals(s.cube().deco()) || "FLAME".equals(s.cube().deco())) grain = "glow";
        int w = s.w(), h = s.h(), x0 = at[0], y0 = at[1];
        int faceShift = switch (s.face()) { case "up" -> 1; case "down" -> -1; default -> 0; };
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double vy = h <= 1 ? 0.5 : y / (double) (h - 1);
                int i = 2 + faceShift;
                if (!s.face().equals("up") && !s.face().equals("down")) i += vy < 0.25 ? 1 : vy > 0.8 ? -1 : 0;   // 위 밝고 아래 어둡게
                switch (grain) {
                    case "metal" -> {   // 판 + 리벳
                        if (y % 6 == 0) i += 1;
                        if (y % 6 == 5) i -= 1;
                        if (y % 6 == 2 && (x % 6 == 1)) i = 4;
                    }
                    case "polish" -> { if (x == y % Math.max(1, w) || x == (y + 1) % Math.max(1, w)) i = 4; }
                    case "bone" -> {
                        if (r.nextInt(14) == 0) i -= 1;
                        if (y % 5 == 0 && x % 3 != 0) i -= 1;   // 마디
                    }
                    case "scale" -> {   // 둥근 비늘이 엇갈려 겹친다 (위가 밝고 아래 테두리가 어둡다)
                        int row = y / 3, sx = (x + (row % 2) * 2) % 4, sy = y % 3;
                        double d = Math.abs(sx - 1.5) / 2.0 + sy / 3.0;
                        if (d > 1.0) i -= 1;
                        else if (sy == 0 && sx > 0 && sx < 3) i += 1;
                    }
                    case "cloth" -> { if (x % 4 == 0) i -= 1; if (r.nextInt(18) == 0) i += 1; }
                    case "hide" -> { if (r.nextInt(5) == 0) i += r.nextBoolean() ? 1 : -1; }
                    case "stone" -> { if ((y % 5 == 0) || ((x + (y / 5) * 3) % 7 == 0)) i -= 1; else if (r.nextInt(7) == 0) i += 1; }
                    case "crystal" -> { if ((x + y) % 7 == 0) i += 2; else if ((x - y + 64) % 9 == 0) i -= 1; }
                    case "membrane" -> {   // 날개막: 뼈대 줄기 + 반투명 느낌의 얼룩
                        if (x % 8 == 0 || (x + y) % 11 == 0) { i = 0; break; }
                        i = 2 + (r.nextInt(6) == 0 ? 1 : 0);
                    }
                    case "glow" -> {
                        double cx = Math.abs(x - (w - 1) / 2.0) / Math.max(1, w / 2.0), cy = Math.abs(y - (h - 1) / 2.0) / Math.max(1, h / 2.0);
                        i = cx + cy < 0.6 ? 4 : cx + cy < 1.1 ? 3 : 2;
                    }
                    default -> { }
                }
                // 가장자리 그늘 (모서리를 또렷하게)
                if (w > 2 && h > 2 && (x == 0 || y == h - 1 || x == w - 1)) i -= 1;
                if (w > 2 && h > 2 && y == 0 && !s.face().equals("down")) i += 1;
                img.setRGB(x0 + x, y0 + y, ramp[Math.max(0, Math.min(4, i))].getRGB());
            }
        // 앞면 장식
        if (s.cube().deco() != null && s.face().equals("north")) deco(img, x0, y0, w, h, s.cube().deco(), st);
        if ("BELLY".equals(s.cube().deco()) && s.face().equals("down")) {
            Color[] b = Canvas.ramp(Canvas.mix(st.main(), new Color(230, 210, 160), 0.55));
            for (int y = 0; y < h; y++) for (int x = 1; x < w - 1; x++) img.setRGB(x0 + x, y0 + y, b[y % 3 == 0 ? 1 : 2].getRGB());
        }
    }

    private static void px(BufferedImage img, int x, int y, Color c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, c.getRGB());
    }

    private static void box(BufferedImage img, int x, int y, int w, int h, Color c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(img, x + i, y + j, c);
    }

    /** 얼굴 · 문장 (앞면에만). 좌표는 면 안에서의 비율 */
    private static void deco(BufferedImage img, int x0, int y0, int w, int h, String deco, Style st) {
        Color[] acc = Canvas.ramp(st.accent()), dark = Canvas.ramp(new Color(24, 20, 28)), metal = Canvas.ramp(st.metal());
        int ew = Math.max(1, w / 6), eh = Math.max(1, h / 8);
        switch (deco) {
            case "FACE", "SKULL", "DRAGONFACE", "GOLEMFACE" -> {
                int ey = y0 + (deco.equals("DRAGONFACE") ? h / 4 : h * 2 / 5), lx = x0 + w / 4 - ew / 2, rx = x0 + w * 3 / 4 - ew / 2;
                if (deco.equals("SKULL")) {   // 움푹한 눈구멍 + 빛나는 눈동자 + 이빨
                    box(img, lx - 1, ey - 1, ew + 2, eh + 2, dark[1]); box(img, rx - 1, ey - 1, ew + 2, eh + 2, dark[1]);
                    box(img, lx, ey, ew, eh, acc[4]); box(img, rx, ey, ew, eh, acc[4]);
                    box(img, x0 + w / 2 - 1, ey + eh + 1, 2, Math.max(1, h / 8), dark[1]);
                    for (int x = x0 + w / 4; x < x0 + w * 3 / 4; x += 2) box(img, x, y0 + h * 3 / 4, 1, Math.max(1, h / 8), dark[0]);
                } else {
                    box(img, lx - 1, ey - 1, ew + 2, 1, dark[0]); box(img, rx - 1, ey - 1, ew + 2, 1, dark[0]);   // 눈썹 그늘
                    box(img, lx, ey, ew, eh, acc[4]); box(img, rx, ey, ew, eh, acc[4]);
                    px(img, lx, ey, Color.WHITE); px(img, rx, ey, Color.WHITE);
                    if (deco.equals("DRAGONFACE")) {   // 콧구멍 + 이빨 줄
                        px(img, x0 + w / 3, y0 + h * 3 / 5, dark[0]); px(img, x0 + w * 2 / 3, y0 + h * 3 / 5, dark[0]);
                        for (int x = x0 + 1; x < x0 + w - 1; x += 2) px(img, x, y0 + h - 2, new Color(236, 230, 210));
                    } else if (deco.equals("GOLEMFACE")) {
                        box(img, x0 + w / 3, y0 + h * 3 / 4, w / 3, Math.max(1, h / 10), dark[1]);
                    } else {
                        box(img, x0 + w / 3, y0 + h * 3 / 4, w / 3, 1, dark[0]);
                        px(img, x0 + w / 3, y0 + h * 3 / 4 + 1, new Color(236, 230, 210)); px(img, x0 + w * 2 / 3 - 1, y0 + h * 3 / 4 + 1, new Color(236, 230, 210));   // 송곳니
                    }
                }
            }
            case "VISOR" -> {   // 투구 앞: T 자 틈 + 숨구멍 + 이마 테
                box(img, x0 + 1, y0 + h * 2 / 5, w - 2, Math.max(1, h / 7), dark[0]);
                box(img, x0 + w / 2 - Math.max(1, w / 12), y0 + h * 2 / 5, Math.max(2, w / 6), h / 3, dark[0]);
                box(img, x0, y0 + h / 4, w, 1, metal[4]);
                for (int y = y0 + h * 2 / 3; y < y0 + h - 2; y += 2) { px(img, x0 + w / 5, y, dark[1]); px(img, x0 + w * 4 / 5, y, dark[1]); }
                box(img, x0 + w / 4, y0 + h * 2 / 5, Math.max(1, w / 8), 1, acc[3]); box(img, x0 + w * 5 / 8, y0 + h * 2 / 5, Math.max(1, w / 8), 1, acc[3]);   // 틈 속 눈빛
            }
            case "EMBLEM", "RUNE" -> {
                int cx = x0 + w / 2, cy = y0 + h / 2 - (deco.equals("RUNE") ? 0 : h / 8), rr = Math.max(2, Math.min(w, h) / 4);
                for (int y = -rr; y <= rr; y++) for (int x = -rr; x <= rr; x++) {
                    int d = Math.abs(x) + Math.abs(y);
                    if (d <= rr) px(img, cx + x, cy + y, d == rr ? metal[4] : d >= rr - 1 ? acc[1] : acc[(x + y) < 0 ? 4 : 2]);
                }
                if (deco.equals("RUNE")) for (int y = y0 + 2; y < y0 + h - 2; y += 4) { px(img, x0 + 2, y, acc[3]); px(img, x0 + w - 3, y, acc[3]); }
            }
            case "EYEBALL" -> {   // 커다란 눈 하나
                int cx = x0 + w / 2, cy = y0 + h / 2, rr = Math.max(3, Math.min(w, h) / 3);
                for (int y = -rr; y <= rr; y++) for (int x = -rr; x <= rr; x++) {
                    double d = Math.hypot(x, y);
                    if (d <= rr) px(img, cx + x, cy + y, d > rr - 1 ? dark[1] : d < rr / 3.0 ? new Color(20, 10, 20) : d < rr / 1.8 ? acc[3] : new Color(236, 228, 214));
                }
                px(img, cx - rr / 4, cy - rr / 4, Color.WHITE);
            }
            case "MAW" -> {
                box(img, x0 + 1, y0 + 1, w - 2, h - 2, new Color(70, 10, 20));
                for (int x = x0 + 1; x < x0 + w - 1; x += 2) { px(img, x, y0 + 1, new Color(236, 230, 210)); px(img, x + 1, y0 + h - 2, new Color(236, 230, 210)); }
            }
            default -> { }
        }
    }

    static String n(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(Math.round(v * 10000) / 10000.0);
    }
}
