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

    /**
     * 갑옷 그림 (64×32, 사람 모델 갑옷 배치). leggings=false: 머리 · 몸 · 팔 · 발, true: 다리 · 허리.
     * 색: 재질 바탕 + 모습 id 에서 정한 강조색, 위쪽이 밝고 아래가 어두운 음영.
     */
    static BufferedImage texture(String look, String material, boolean leggings) {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        SplittableRandom r = new SplittableRandom((look + leggings).hashCode());
        String style = style(look, material);
        Color base = material.startsWith("NETHERITE") ? new Color(58, 52, 64) : material.startsWith("GOLD") ? new Color(224, 182, 62)
                : material.startsWith("LEATHER") ? (look.contains("robe") ? new Color(214, 206, 186) : new Color(126, 84, 50))
                : material.startsWith("CHAIN") ? new Color(132, 136, 146) : new Color(184, 190, 200);
        if (look.equals("talok")) base = new Color(92, 70, 52);
        float h = (look.hashCode() & 0xffff) / 65535f;
        Color accent = look.contains("hawk") ? new Color(150, 24, 32) : look.equals("graham") ? new Color(52, 92, 196) : Color.getHSBColor(h, 0.7f, 0.75f);
        Color rim = material.startsWith("GOLD") ? new Color(255, 236, 150) : new Color(212, 176, 72);
        int[][] regions = leggings ? new int[][]{{0, 16, 16, 16}, {16, 16, 24, 16}} : new int[][]{{0, 0, 32, 16}, {16, 16, 24, 16}, {40, 16, 16, 16}, {0, 16, 16, 16}};
        for (int[] g : regions)
            for (int y = g[1]; y < g[1] + g[3]; y++)
                for (int x = g[0]; x < g[0] + g[2]; x++) {
                    int lx = x - g[0], ly = y - g[1];
                    int v = r.nextInt(-7, 8) + (8 - (ly % 16)) * 2;   // 위가 밝다
                    Color c = base;
                    switch (style) {
                        case "plate" -> {
                            if (ly % 8 == 7) { c = base; v -= 45; }                     // 판 아래 그림자
                            else if (ly % 8 == 0) v += 30;                               // 판 위 반사
                            if (lx % 16 == 7 || lx % 16 == 8) c = accent;               // 가운데 띠
                            if (ly % 16 == 1 && lx % 4 == 1) c = rim;                    // 리벳
                        }
                        case "chain" -> { if ((lx + (ly % 2)) % 2 == 0) v += 25; else v -= 30; if (ly % 16 == 0) c = rim; }
                        case "scale" -> {
                            int sx = (lx + ((ly / 3) % 2) * 2) % 4, sy = ly % 3;
                            v += sy == 0 ? 25 : sy == 2 ? -30 : 0;
                            if (sx == 0) v -= 20;
                            if (lx % 16 == 7 && ly % 4 < 2) c = accent;                 // 붉은 이음
                        }
                        case "fur" -> {
                            v += r.nextInt(-18, 19);
                            if (ly % 16 < 3) { c = new Color(196, 176, 140); v += r.nextInt(-10, 11); }   // 털 목둘레 · 허리
                            if (ly % 16 == 9 && lx % 6 < 4) c = accent;                 // 가죽 끈
                        }
                        default -> {   // robe
                            if (lx % 16 == 0 || lx % 16 == 15) c = accent;              // 옷깃 띠
                            if (ly % 16 == 14 || ly % 16 == 15) c = rim;                // 밑단 금실
                            if (lx % 4 == 0) v -= 10;                                    // 주름
                        }
                    }
                    int red = cl(c.getRed() + v), green = cl(c.getGreen() + v), blue = cl(c.getBlue() + v);
                    if (red == green && green == blue) blue = blue ^ 1;   // 팔레트 키(회색 단계)와 겹치지 않게
                    img.setRGB(x, y, new Color(red, green, blue).getRGB());
                }
        return img;
    }

    private static int cl(int v) {
        return Math.max(0, Math.min(254, v));
    }
}
