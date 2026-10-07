package io.versaera.pack;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 큐브를 조립해 만드는 3D 모델 (필드 보스 · 몬스터 머리). 텍스처는 32×32 한 장을 네 칸(몸 · 둘째 · 강조 · 어둠)으로 나눠 쓴다.
 * 좌표는 Minecraft 모델 한계(-16 ~ 32) 안. 크기는 ItemDisplay 의 배율로 키운다.
 */
public final class ModelKit {
    /** 텍스처 칸: 0 몸 · 1 둘째 · 2 강조 · 3 어둠 */
    public record Cube(double x1, double y1, double z1, double x2, double y2, double z2, int part) {}

    private ModelKit() {
    }

    private static Cube c(double x1, double y1, double z1, double x2, double y2, double z2, int part) {
        return new Cube(x1, y1, z1, x2, y2, z2, part);
    }

    public static List<Cube> template(String look) {
        List<Cube> l = new ArrayList<>();
        switch (look) {
            case "KNIGHT", "VAMPIRE" -> {
                l.add(c(5, 0, 6, 7.5, 7, 10, 3)); l.add(c(8.5, 0, 6, 11, 7, 10, 3));            // 다리
                l.add(c(4, 7, 5, 12, 15, 11, 0));                                                // 몸통
                l.add(c(5.5, 15, 5.5, 10.5, 20, 10.5, 1));                                       // 머리
                l.add(c(2, 8, 6.5, 4, 15, 9.5, 0)); l.add(c(12, 8, 6.5, 14, 15, 9.5, 0));       // 팔
                if (look.equals("KNIGHT")) {
                    l.add(c(13, 4, 7.5, 14, 22, 8.5, 2));                                        // 대검
                    l.add(c(11.5, 12, 6.5, 15.5, 13, 9.5, 2));                                   // 코등이
                    l.add(c(7.5, 20, 7, 8.5, 23, 9, 2));                                         // 투구 깃
                    l.add(c(4.5, 5, 11, 11.5, 15, 11.5, 2));                                     // 망토
                } else {
                    l.add(c(2, 2, 11, 14, 16, 11.5, 2));                                         // 넓은 망토
                    l.add(c(4, 15, 9, 12, 18, 11, 2));                                           // 높은 깃
                }
            }
            case "CASTER" -> {
                l.add(c(4, 0, 4, 12, 14, 12, 0));                                                // 로브
                l.add(c(5.5, 14, 5.5, 10.5, 19, 10.5, 3));                                       // 해골
                l.add(c(5, 17, 5, 11, 20.5, 11, 0)); l.add(c(5, 14, 10.5, 11, 18, 11, 0));      // 두건
                l.add(c(2.5, 7, 6.5, 4, 13, 9.5, 0)); l.add(c(12, 7, 6.5, 13.5, 13, 9.5, 0));
                l.add(c(13.5, 0, 7.5, 14.5, 22, 8.5, 1));                                        // 지팡이
                l.add(c(12.5, 22, 6.5, 15.5, 25, 9.5, 2));                                       // 보주
            }
            case "DEMON" -> {
                l.add(c(4.5, 0, 6, 7.5, 8, 10, 3)); l.add(c(8.5, 0, 6, 11.5, 8, 10, 3));
                l.add(c(3, 8, 4.5, 13, 17, 11.5, 0));
                l.add(c(5, 17, 5, 11, 22, 11, 0));
                l.add(c(5, 21, 7, 6, 26, 8, 3)); l.add(c(10, 21, 7, 11, 26, 8, 3));             // 뿔
                l.add(c(0.5, 9, 6.5, 3, 17, 9.5, 0)); l.add(c(13, 9, 6.5, 15.5, 17, 9.5, 0));
                l.add(c(-8, 10, 11, 3, 20, 12, 1)); l.add(c(13, 10, 11, 24, 20, 12, 1));         // 날개
                l.add(c(7, 4, 11, 9, 6, 20, 3));                                                 // 꼬리
                l.add(c(6, 19, 4.5, 10, 20, 5, 2));                                              // 눈
            }
            case "DRAGON" -> {
                l.add(c(4, 5, 2, 12, 12, 16, 0));                                                // 몸
                l.add(c(6, 9, -3, 10, 14, 2, 0));                                                // 목
                l.add(c(5, 11, -10, 11, 16, -3, 1)); l.add(c(5.5, 9, -9, 10.5, 11, -4, 3));     // 머리 · 턱
                l.add(c(5.5, 16, -6, 6.5, 19, -5, 3)); l.add(c(9.5, 16, -6, 10.5, 19, -5, 3));  // 뿔
                l.add(c(-12, 11, 3, 4, 12, 13, 1)); l.add(c(12, 11, 3, 28, 12, 13, 1));         // 날개
                l.add(c(6, 6, 16, 10, 10, 24, 0)); l.add(c(7, 6.5, 24, 9, 9, 31, 0));           // 꼬리
                l.add(c(4, 0, 3, 6, 5, 6, 3)); l.add(c(10, 0, 3, 12, 5, 6, 3));                 // 다리
                l.add(c(4, 0, 11, 6, 5, 14, 3)); l.add(c(10, 0, 11, 12, 5, 14, 3));
                l.add(c(7.5, 12, 3, 8.5, 14, 15, 2));                                            // 등 가시
                l.add(c(5.5, 13, -10.2, 10.5, 14, -10, 2));                                      // 눈
            }
            case "BEAST" -> {
                l.add(c(3, 5, 2, 13, 12, 14, 0));
                l.add(c(5, 7, -4, 11, 13, 2, 1));
                l.add(c(4, 12, -3, 5, 16, -2, 2)); l.add(c(11, 12, -3, 12, 16, -2, 2));         // 엄니 · 뿔
                l.add(c(3.5, 0, 3, 6, 5, 6, 3)); l.add(c(10, 0, 3, 12.5, 5, 6, 3));
                l.add(c(3.5, 0, 10, 6, 5, 13, 3)); l.add(c(10, 0, 10, 12.5, 5, 13, 3));
                l.add(c(7, 9, 14, 9, 11, 19, 0));
                l.add(c(6, 12, 3, 10, 14, 12, 3));                                               // 갈기
            }
            case "GOLEM" -> {
                l.add(c(2, 8, 4, 14, 21, 12, 0));
                l.add(c(6, 21, 6, 10, 25, 10, 1)); l.add(c(6.5, 22.5, 5.8, 9.5, 23.5, 6, 2));   // 머리 · 빛나는 눈
                l.add(c(-3, 3, 5, 2, 21, 11, 0)); l.add(c(14, 3, 5, 19, 21, 11, 0));            // 큰 팔
                l.add(c(3.5, 0, 5, 7.5, 8, 11, 3)); l.add(c(8.5, 0, 5, 12.5, 8, 11, 3));
                l.add(c(5, 12, 3.6, 11, 17, 4, 2));                                              // 가슴 문양
            }
            case "HYDRA" -> {
                l.add(c(2, 0, 3, 14, 9, 15, 0));
                l.add(c(7, 9, 6, 9, 22, 8, 0)); l.add(c(6, 21, 2, 10, 25, 8, 1));               // 가운데 목 · 머리
                l.add(c(1, 8, 6, 3, 18, 8, 0)); l.add(c(0, 17, 2, 4, 20, 7, 1));                // 왼쪽
                l.add(c(13, 8, 6, 15, 18, 8, 0)); l.add(c(12, 17, 2, 16, 20, 7, 1));            // 오른쪽
                l.add(c(-3, 6, 8, 1, 13, 10, 0)); l.add(c(-5, 12, 5, -1, 15, 9, 1));            // 바깥 목
                l.add(c(15, 6, 8, 19, 13, 10, 0)); l.add(c(17, 12, 5, 21, 15, 9, 1));
                l.add(c(7, 2, 15, 9, 5, 24, 3));
                l.add(c(6.5, 23, 1.8, 9.5, 24, 2, 2));
            }
            case "SALAMANDER" -> {
                l.add(c(4, 2, 0, 12, 7, 18, 0));
                l.add(c(5, 3, -6, 11, 8, 0, 1));
                l.add(c(6, 7, 1, 10, 11, 16, 2));                                                // 불꽃 볏
                l.add(c(6, 2, 18, 10, 5, 26, 0)); l.add(c(7, 2.5, 26, 9, 4, 31, 2));
                l.add(c(2, 0, 2, 4, 3, 5, 3)); l.add(c(12, 0, 2, 14, 3, 5, 3));
                l.add(c(2, 0, 12, 4, 3, 15, 3)); l.add(c(12, 0, 12, 14, 3, 15, 3));
            }
            default -> {   // FLYER
                l.add(c(2, 6, 2, 14, 18, 14, 0));
                l.add(c(4, 11, 1.6, 7, 14, 2, 2)); l.add(c(9, 11, 1.6, 12, 14, 2, 2));          // 눈
                l.add(c(-10, 13, 6, 2, 14, 10, 1)); l.add(c(14, 13, 6, 26, 14, 10, 1));         // 날개
                for (int i = 0; i < 4; i++) l.add(c(3 + i * 3, 0, 6 + (i % 2) * 2, 4.5 + i * 3, 6, 7.5 + (i % 2) * 2, 3));   // 촉수
            }
        }
        return l;
    }

    /** 철인의 쇠 가면 (머리에 씌우는 모델) */
    public static List<Cube> ironMask() {
        return List.of(c(3.5, 0, 3.5, 12.5, 9, 12.5, 0), c(3, 4, 3, 13, 5, 13, 3), c(5, 5.5, 3.2, 11, 6.5, 3.5, 2), c(7.5, 9, 6, 8.5, 12, 10, 1));
    }

    /** 모델 JSON (텍스처 versaera:&lt;tex&gt;) */
    public static String json(List<Cube> cubes, String tex, String display) {
        StringBuilder els = new StringBuilder();
        String[] faces = {"north", "south", "east", "west", "up", "down"};
        for (Cube c : cubes) {
            if (els.length() > 0) els.append(',');
            int u = (c.part() % 2) * 8, v = (c.part() / 2) * 8;
            els.append("{\"from\":[").append(n(c.x1())).append(',').append(n(c.y1())).append(',').append(n(c.z1())).append("],\"to\":[")
                    .append(n(c.x2())).append(',').append(n(c.y2())).append(',').append(n(c.z2())).append("],\"faces\":{");
            for (int i = 0; i < faces.length; i++) {
                if (i > 0) els.append(',');
                els.append('"').append(faces[i]).append("\":{\"uv\":[").append(u).append(',').append(v).append(',').append(u + 8).append(',').append(v + 8)
                        .append("],\"texture\":\"#body\"}");
            }
            els.append("}}");
        }
        return "{\"textures\":{\"body\":\"versaera:" + tex + "\",\"particle\":\"versaera:" + tex + "\"},\"elements\":[" + els + "],\"display\":" + display + "}";
    }

    static String n(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 네 칸 텍스처: 각 칸에 색 + 결 (비늘 · 뼈 · 쇠 · 천) */
    public static BufferedImage texture(String seed, Color main, Color second, Color accent, Color dark, String grain) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        SplittableRandom r = new SplittableRandom(seed.hashCode());
        Color[] parts = {main, second, accent, dark};
        for (int p = 0; p < 4; p++) {
            int ox = (p % 2) * 16, oy = (p / 2) * 16;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++) {
                    int v = r.nextInt(-12, 13);
                    if (p != 2) v += switch (grain) {
                        case "scale" -> ((x / 3 + y / 3) % 2 == 0) ? 14 : -10;
                        case "bone" -> (y % 5 == 0) ? -30 : 0;
                        case "metal" -> (y % 8 == 0 || x % 8 == 0) ? -35 : (x + y) % 9 == 0 ? 20 : 0;
                        case "cloth" -> (x % 4 == 0) ? -15 : 0;
                        default -> 0;
                    };
                    Color c = parts[p];
                    img.setRGB(ox + x, oy + y, new Color(cl(c.getRed() + v), cl(c.getGreen() + v), cl(c.getBlue() + v)).getRGB());
                }
        }
        return img;
    }

    private static int cl(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
