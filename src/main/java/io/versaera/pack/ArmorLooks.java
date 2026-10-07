package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.item.ItemType;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 입은 갑옷의 겉모습 (RP-01). Minecraft 1.20.1 은 아이템마다 다른 갑옷 텍스처를 쓰는 기능이 없어서 <b>갑옷 장식(armor trim)</b>을 쓴다:
 * <ul>
 *   <li>데이터팩: 장식 무늬(trim_pattern) versaera:&lt;모습&gt; 를 등록 — 플러그인이 세계 폴더 datapacks 에 쓴다 (처음 한 번은 서버 재시작 필요)</li>
 *   <li>리소스팩: 무늬 텍스처를 갑옷 전체를 덮는 그림으로 그린다 → 같은 철 갑옷이라도 아이템마다 다른 모습</li>
 *   <li>모습 = 세트가 있으면 세트 id (그라함 4벌이 한 벌처럼 보이게), 없으면 아이템 id</li>
 * </ul>
 * 무늬 색은 장식 재료 팔레트와 겹치지 않는 색으로 그려 어떤 장식 재료에서도 같은 그림이 나온다.
 */
public final class ArmorLooks {
    /** 장식 재료 (색 팔레트) — 그림이 팔레트와 겹치지 않아 결과는 같지만, 아이템에 붙일 재료는 하나여야 한다 */
    public static final String TRIM_MATERIAL = "iron";
    static final List<String> PALETTES = List.of("quartz", "iron", "iron_darker", "netherite", "netherite_darker", "redstone", "copper", "gold",
            "gold_darker", "emerald", "diamond", "diamond_darker", "lapis", "amethyst");

    private ArmorLooks() {
    }

    /** 갑옷 재질(투구 · 갑옷 · 각반 · 장화)로 입는 아이템 → 모습 id */
    public static Map<String, String> looks(Collection<ItemType> items) {
        Map<String, String> out = new TreeMap<>();
        for (ItemType t : items) {
            String m = t.material();
            if (!(m.endsWith("_HELMET") || m.endsWith("_CHESTPLATE") || m.endsWith("_LEGGINGS") || m.endsWith("_BOOTS"))) continue;
            out.put(t.id(), t.set() != null ? t.set() : t.id());
        }
        return out;
    }

    /** 데이터팩 파일 (경로 → 내용). pack_format 15 = 1.20.1 */
    public static Map<String, byte[]> datapack(ContentBundle content) {
        Map<String, byte[]> f = new TreeMap<>();
        f.put("pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"VersaEra armor looks\"}}".getBytes(StandardCharsets.UTF_8));
        Map<String, String> names = new HashMap<>();
        for (var st : content.sets()) names.put(st.id(), st.name());
        for (ItemType t : content.items()) names.putIfAbsent(t.id(), t.name());
        for (String look : new TreeSet<>(looks(content.items()).values()))
            f.put("data/versaera/trim_pattern/" + look + ".json", ("{\"asset_id\":\"versaera:" + look + "\",\"template_item\":\"minecraft:paper\","
                    + "\"description\":{\"text\":\"" + names.getOrDefault(look, look).replace("\"", "") + "\"},\"decal\":false}").getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** 리소스팩 아틀라스: 무늬 텍스처를 장식 재료 팔레트마다 등록 */
    static String atlas(Collection<String> looks) {
        StringBuilder tex = new StringBuilder();
        for (String l : looks) {
            if (tex.length() > 0) tex.append(',');
            tex.append("\"versaera:trims/models/armor/").append(l).append("\",\"versaera:trims/models/armor/").append(l).append("_leggings\"");
        }
        StringBuilder perm = new StringBuilder();
        for (String p : PALETTES) {
            if (perm.length() > 0) perm.append(',');
            perm.append('"').append(p).append("\":\"minecraft:trims/color_palettes/").append(p).append('"');
        }
        return "{\"sources\":[{\"type\":\"paletted_permutations\",\"textures\":[" + tex + "],\"palette_key\":\"minecraft:trims/color_palettes/trim_palette\","
                + "\"permutations\":{" + perm + "}}]}";
    }

    /** 무늬 종류: 판금 · 사슬 · 비늘 · 털가죽 · 로브 */
    static String style(String look, String material) {
        if (material.startsWith("CHAIN")) return "chain";
        if (look.contains("robe")) return "robe";
        if (material.startsWith("LEATHER") || look.equals("talok")) return "fur";
        if (look.contains("hawk") || material.startsWith("NETHERITE")) return "scale";
        return "plate";
    }

    /** 갑옷 그림 배율: 64×32 배치를 2배(128×64)로 — 바닐라 갑옷보다 촘촘한 그림 */
    static final int S = 2;

    /**
     * 갑옷 그림 (128×64, 사람 모델 갑옷 배치 × 2). leggings=false: 머리 · 몸 · 팔 · 장화, true: 다리 · 허리.
     * 판 무늬 위에 얼굴 가리개(투구 앞면) · 가슴 문장 · 깃 · 허리띠 · 어깨받이 · 팔목 · 무릎받이 · 장화 목을 그린다.
     */
    static BufferedImage texture(String look, String material, boolean leggings) {
        Painter p = new Painter(look, material, leggings);
        if (!leggings) {
            p.base(0, 0, 32, 16);     // 머리
            p.base(16, 16, 24, 16);   // 몸
            p.base(40, 16, 16, 16);   // 팔
            p.base(0, 26, 16, 6);     // 장화: 다리 아랫부분만 (위는 비워 둬야 장화가 바지처럼 보이지 않는다)
            p.helmet();
            p.chest();
            p.arms();
            p.boots();
        } else {
            p.base(0, 16, 16, 16);    // 다리
            p.base(16, 26, 24, 6);    // 몸의 허리 부분만 (위는 비워 둬야 각반만 입었을 때 윗옷처럼 보이지 않는다)
            p.legs();
        }
        return p.img;
    }

    /** 무늬를 칠하는 붓 (좌표는 64×32 기준 단위, 칠은 S 배) */
    private static final class Painter {
        final BufferedImage img = new BufferedImage(64 * S, 32 * S, BufferedImage.TYPE_INT_ARGB);
        final SplittableRandom r;
        final String style, look, material;
        final boolean leggings;
        final Color[] base, acc, trim, dark;

        Painter(String look, String material, boolean leggings) {
            this.look = look;
            this.material = material;
            this.leggings = leggings;
            this.style = style(look, material);
            r = new SplittableRandom((look + leggings).hashCode());
            Color b = material.startsWith("NETHERITE") ? new Color(66, 58, 74) : material.startsWith("GOLD") ? new Color(226, 184, 64)
                    : material.startsWith("LEATHER") ? (look.contains("robe") ? new Color(220, 212, 190) : new Color(132, 88, 52))
                    : material.startsWith("CHAIN") ? new Color(138, 142, 154) : new Color(186, 192, 204);
            if (look.equals("talok")) b = new Color(96, 72, 52);
            float h = (look.hashCode() & 0xffff) / 65535f;
            Color a = look.contains("hawk") ? new Color(156, 26, 36) : look.equals("graham") ? new Color(48, 86, 192)
                    : look.contains("robe") ? new Color(70, 96, 170) : Color.getHSBColor(h, 0.7f, 0.72f);
            Color t = material.startsWith("GOLD") || material.startsWith("NETHERITE") || look.contains("hawk") ? new Color(214, 170, 64) : new Color(206, 176, 92);
            if (style.equals("fur")) t = new Color(206, 186, 150);
            base = Canvas.ramp(b);
            acc = Canvas.ramp(a);
            trim = Canvas.ramp(t);
            dark = Canvas.ramp(new Color(30, 26, 34));
        }

        void dot(int x, int y, Color c) {
            if (x < 0 || y < 0 || x >= 64 * S || y >= 32 * S) return;
            int red = c.getRed(), green = c.getGreen(), blue = c.getBlue();
            if (red == green && green == blue) blue = blue < 255 ? blue + 1 : blue - 1;   // 팔레트 키(회색 단계)와 겹치지 않게
            img.setRGB(x, y, new Color(red, green, blue).getRGB());
        }

        /** 단위 칸(64×32 기준)을 S×S 로 칠한다 */
        void unit(double ux, double uy, Color c) {
            for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) dot((int) (ux * S) + i, (int) (uy * S) + j, c);
        }

        /** 사각 영역을 S 배 픽셀로 (x0..x1 은 단위 좌표) — 테두리 음영 포함 */
        void fill(double x0, double y0, double w, double h, Color[] ramp, boolean bevel) {
            int px0 = (int) Math.round(x0 * S), py0 = (int) Math.round(y0 * S), pw = (int) Math.round(w * S), ph = (int) Math.round(h * S);
            for (int y = py0; y < py0 + ph; y++)
                for (int x = px0; x < px0 + pw; x++) {
                    int i = 2;
                    if (bevel) {
                        if (y == py0 || x == px0) i = 4;
                        else if (y == py0 + ph - 1 || x == px0 + pw - 1) i = 0;
                        else if (y == py0 + 1) i = 3;
                    }
                    dot(x, y, ramp[i]);
                }
        }

        /** 바탕 무늬 */
        void base(int ux, int uy, int uw, int uh) {
            for (int y = uy * S; y < (uy + uh) * S; y++)
                for (int x = ux * S; x < (ux + uw) * S; x++) {
                    int lx = x - ux * S, ly = y - uy * S;
                    int i = 2;
                    switch (style) {
                        case "plate" -> {
                            int band = ly % (4 * S);
                            i = band == 0 ? 4 : band == 1 ? 3 : band == 4 * S - 1 ? 0 : band == 4 * S - 2 ? 1 : 2;
                            if (i == 2 && r.nextInt(9) == 0) i = 3;
                        }
                        case "chain" -> {   // 고리: 2×2 마다 밝은 점 하나 · 엇갈린 줄
                            int ox = (lx + ((ly / 2) % 2)) % 2, oy = ly % 2;
                            i = ox == 0 && oy == 0 ? 4 : ox == 1 && oy == 1 ? 1 : 2;
                        }
                        case "scale" -> {
                            // 비늘: 아래로 둥근 조각이 엇갈려 겹친다 (위쪽이 밝고 아래 가장자리가 어둡다)
                            int row = ly / 4, sx = (lx + (row % 2) * 3) % 6, sy = ly % 4;
                            double d = Math.abs(sx - 2.5) / 3.0 + sy / 4.0;
                            i = d > 1.05 ? 0 : sy == 0 ? 3 : d > 0.85 ? 1 : 2;
                        }
                        case "fur" -> i = Math.max(0, Math.min(4, 2 + r.nextInt(-2, 3) / 2 + (ly % 5 == 0 ? -1 : 0)));
                        default -> i = (lx % (4 * S) == 0) ? 1 : (lx % (4 * S) == 1) ? 3 : 2;   // 로브 주름
                    }
                    dot(x, y, base[i]);
                }
        }

        // ---- 부위 장식 (단위 좌표: 머리 앞면 8..16 × 8..16 · 몸 앞면 20..28 × 20..32 · 팔 앞면 44..48 × 20..32 · 다리 앞면 4..8 × 20..32)
        void helmet() {
            boolean hood = style.equals("fur") || style.equals("robe");
            // 아래 테두리 (머리 네 옆면의 맨 아랫줄)
            fill(0, 15, 32, 1, trim, false);
            if (hood) {
                fill(9, 9.5, 6, 6.5, dark, false);                      // 얼굴이 드러나는 구멍 (그늘)
                fill(8.5, 8.5, 7, 1, trim, false);
                if (look.equals("talok")) for (int i = 0; i < 4; i++) { unit(8.5 + i * 2, 7.5, trim[4]); }   // 이빨 장식
            } else if (look.equals("emperor_crown")) {
                fill(8, 12, 8, 2, trim, true);
                unit(10, 12.5, new Color(220, 40, 50)); unit(12, 12.5, new Color(60, 110, 230)); unit(14, 12.5, new Color(50, 190, 90));
                for (int x = 0; x < 32; x += 3) fill(x, 8, 1, 3, trim, false);
            } else {
                fill(8, 11, 8, 1.5, dark, false);                       // 눈 가리개 틈
                fill(11.5, 11, 1, 4, dark, false);                      // 코 · 입 틈 (T)
                fill(8, 9.5, 8, 1, base, true);                         // 이마띠
                fill(11.5, 0, 1, 8, acc, false);                        // 정수리 볏 (윗면)
                fill(11.5, 8, 1, 1.5, acc, false);
                for (int y = 13; y < 15; y++) { unit(9.5, y, base[0]); unit(14, y, base[0]); }   // 숨구멍 (작게)
                if (look.contains("hawk")) { fill(9, 11, 2, 1, acc, false); fill(13, 11, 2, 1, acc, false); }   // 붉은 눈빛
            }
        }

        void chest() {
            fill(20, 20, 8, 1.5, trim, true);                           // 깃
            fill(16, 20, 4, 1.5, trim, true); fill(28, 20, 12, 1.5, trim, true);
            if (style.equals("robe")) {
                fill(23.5, 21.5, 1, 10.5, acc, false);                  // 앞섶 띠
                fill(16, 30.5, 24, 1.5, trim, false);                   // 밑단
            } else {
                // 가슴 문장
                double cx = 24, cy = 24.5;
                for (double dy = -2; dy <= 2; dy += 0.5) for (double dx = -2; dx <= 2; dx += 0.5)
                    if (Math.abs(dx) + Math.abs(dy) <= 2) unit(cx + dx - 0.5, cy + dy - 0.5, Math.abs(dx) + Math.abs(dy) > 1.5 ? trim[1] : acc[dx + dy < 0 ? 3 : 2]);
            }
            fill(16, 29, 24, 1.5, Canvas.ramp(new Color(96, 62, 38)), true);   // 허리띠
            fill(23, 28.5, 2, 2.5, trim, true);                         // 버클
        }

        void arms() {
            boolean soft = style.equals("fur") || style.equals("robe");
            fill(40, 16, 16, 4, soft ? trim : base, true);             // 어깨 윗면
            fill(40, 20, 16, soft ? 1.5 : 3, soft ? trim : base, true); // 어깨받이
            if (!soft) fill(40, 22.5, 16, 0.5, trim, false);
            fill(40, 29.5, 16, 1, trim, false);                         // 팔목
            if (look.equals("talok")) for (int x = 40; x < 56; x += 2) unit(x, 21, trim[4]);
        }

        void boots() {
            fill(0, 26, 16, 1, trim, false);                            // 장화 목
            fill(0, 30, 16, 2, Canvas.ramp(Canvas.mix(base[2], new Color(40, 30, 24), 0.45)), true);   // 밑창 · 코
            if (look.contains("kubicha")) for (int x = 0; x < 16; x += 4) { unit(x + 1, 27.5, new Color(240, 240, 255)); unit(x + 2, 28, new Color(200, 220, 255)); }   // 날개 깃
        }

        void legs() {
            fill(16, 26, 24, 2, Canvas.ramp(new Color(96, 62, 38)), true);   // 허리띠
            fill(23, 25.5, 2, 3, trim, true);
            if (!style.equals("robe") && !style.equals("fur")) {
                fill(4, 24.5, 4, 2.5, base, true);                      // 무릎받이
                unit(5.5, 25.5, acc[3]);
                fill(0, 24.5, 4, 2.5, base, true); fill(8, 24.5, 8, 2.5, base, true);
            } else fill(0, 30.5, 16, 1.5, trim, false);
            fill(4, 22, 4, 0.5, acc, false);                            // 앞 띠
        }
    }

    private static int cl(int v) {
        return Math.max(0, Math.min(254, v));
    }
}
