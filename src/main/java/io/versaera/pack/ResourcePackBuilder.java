package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.pack.PackIds;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * VersaEra 전용 리소스팩 생성기 (RP-01 · UI-01). 외부 그림 파일 없이 코드로 만든다 → 저장소에 저작권 있는 에셋이 없다.
 * <ul>
 *   <li>거대 보스 모델: 보스마다 몸통 모양 템플릿(거상 · 날개 짐승 · 결정 · 마디 벌레 · 갑각)을 큐브로 조립 + 절차 텍스처</li>
 *   <li>모델은 종이(paper)의 CustomModelData 로 연결 (번호 = PackIds.modelData)</li>
 *   <li>UI 아이콘 16종(의뢰 · 상점 · 길드 · 경매 …): 종이 CustomModelData (번호 = PackIds.modelData("ui/&lt;키&gt;"))</li>
 *   <li>UI: 기본 폰트에 사설 영역 글자(U+E000~)로 메뉴 배경 · 뒤로 당기기 공백을 넣는다 → 상자 창 제목에 배경을 깐다</li>
 *   <li>아이템: 모든 아이템 종류의 16×16 아이콘 ({@link PixelArt}) — 바닐라 재질 모델에 CustomModelData 덮어쓰기 (번호 = PackIds.item)</li>
 *   <li>입은 갑옷: 갑옷 장식(trim) 무늬로 아이템마다 다른 겉모습 ({@link ArmorLooks}, 데이터팩과 짝)</li>
 *   <li>필드 보스: 모양 템플릿(기사 · 리치 · 악마 · 용 · 짐승 · 골렘 · 히드라 · 불도마뱀 · 비행체 · 뱀파이어) 큐브 모델 ({@link ModelKit})</li>
 *   <li>몬스터 머리: 철인의 쇠 가면</li>
 *   <li>결과는 결정적(같은 콘텐츠 = 같은 바이트 = 같은 SHA-1) → 클라이언트 캐시가 잘 맞는다</li>
 * </ul>
 * Minecraft 1.20.1 = pack_format 15.
 */
public final class ResourcePackBuilder {
    public static final int PACK_FORMAT = 15;
    /** 메뉴 배경 글자 · 왼쪽으로 당기는 공백 글자 */
    public static final char MENU_BG_6 = '', MENU_BG_3 = '', SHIFT_LEFT_8 = '', SHIFT_LEFT_169 = '';

    public record Pack(byte[] zip, byte[] sha1, Map<String, Integer> models) {
        public String sha1Hex() {
            StringBuilder sb = new StringBuilder();
            for (byte b : sha1) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }

    private final Map<String, byte[]> files = new TreeMap<>();

    private ResourcePackBuilder() {
    }

    public static Pack build(ContentBundle content) {
        ResourcePackBuilder b = new ResourcePackBuilder();
        b.text("pack.mcmeta", "{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"VersaEra\"}}");
        Map<String, Integer> models = new TreeMap<>();
        for (BossDefinition boss : content.bosses()) {
            if (boss.model() == null) continue;
            int id = PackIds.modelData(boss.model());
            if (models.containsValue(id)) throw new IllegalStateException("모델 번호 충돌: " + boss.model());
            models.put(boss.model(), id);
            b.bossModel(boss);
        }
        for (String key : UI_ICONS) {
            String model = "ui/" + key;
            int id = PackIds.modelData(model);
            if (models.containsValue(id)) throw new IllegalStateException("모델 번호 충돌: " + model);
            models.put(model, id);
            b.uiIcon(key);
        }
        // 필드 보스 · 몬스터 머리 (종이)
        for (var fb : content.fieldBosses()) {
            String model = "fboss/" + fb.id();
            put(models, model);
            b.fieldBossModel(fb);
        }
        put(models, "mob/iron_mask");
        b.text("assets/versaera/models/mob/iron_mask.json", ModelKit.json(ModelKit.ironMask(), "mob/iron_mask",
                "{\"head\":{\"translation\":[0,-6.5,0],\"scale\":[1.1,1.1,1.1]},\"fixed\":{\"scale\":[1,1,1]}}"));
        b.png("assets/versaera/textures/mob/iron_mask.png", ModelKit.texture("iron_mask", new Color(150, 154, 162), new Color(110, 80, 50),
                new Color(30, 26, 30), new Color(90, 92, 100), "metal"));
        // 아이템: 바닐라 재질마다 덮어쓰기 목록
        Map<String, Map<String, Integer>> byMaterial = new TreeMap<>();
        byMaterial.put("PAPER", models);
        Map<String, Integer> all = new TreeMap<>(models);
        for (var t : content.items()) {
            if (!modeled(t)) continue;
            String model = "item/" + t.id();
            int id = PackIds.item(t.id());
            if (all.containsValue(id)) throw new IllegalStateException("모델 번호 충돌: " + model);
            all.put(model, id);
            byMaterial.computeIfAbsent(t.material(), k -> new TreeMap<>()).put(model, id);
            b.itemModel(t);
        }
        for (var e : byMaterial.entrySet()) b.vanillaOverrides(e.getKey(), e.getValue());
        // 입은 갑옷 (장식 무늬)
        Map<String, String> looks = ArmorLooks.looks(content.items());
        Map<String, String> lookMaterial = new TreeMap<>();
        for (var t : content.items()) if (looks.containsKey(t.id())) lookMaterial.putIfAbsent(looks.get(t.id()), t.material());
        for (var e : lookMaterial.entrySet()) {
            b.png("assets/versaera/textures/trims/models/armor/" + e.getKey() + ".png", ArmorLooks.texture(e.getKey(), e.getValue(), false));
            b.png("assets/versaera/textures/trims/models/armor/" + e.getKey() + "_leggings.png", ArmorLooks.texture(e.getKey(), e.getValue(), true));
        }
        if (!lookMaterial.isEmpty()) b.text("assets/minecraft/atlases/armor_trims.json", ArmorLooks.atlas(lookMaterial.keySet()));
        models = all;
        b.uiFont();
        b.png("pack.png", icon());
        return b.zip(models);
    }

    private static void put(Map<String, Integer> models, String model) {
        int id = PackIds.modelData(model);
        if (models.containsValue(id)) throw new IllegalStateException("모델 번호 충돌: " + model);
        models.put(model, id);
    }

    // ------------------------------------------------------------------ 아이템 모델
    /** 리소스팩 모델을 붙이지 않는 재질 — 바닐라 모델이 특수(엔티티 렌더러)해서 덮어쓰면 깨진다 */
    public static final Set<String> UNMODELED = Set.of("SHIELD", "TRIDENT", "CROSSBOW", "COMPASS", "CLOCK", "GOAT_HORN", "PLAYER_HEAD");
    static final Set<String> BLOCK_ITEMS = Set.of("OAK_LOG", "SPRUCE_LOG", "CALCITE", "SANDSTONE", "SAND", "WHITE_WOOL");
    static final Set<String> HANDHELD = Set.of("STICK", "BLAZE_ROD", "BONE");

    /** 이 아이템에 리소스팩 모델(CustomModelData)을 붙이는가 */
    public static boolean modeled(io.versaera.domain.item.ItemType t) {
        return !UNMODELED.contains(t.material());
    }

    private void itemModel(io.versaera.domain.item.ItemType t) {
        String kind = PixelArt.kind(t);
        String parent = switch (t.material()) {
            case "BOW" -> "minecraft:item/bow";
            case "FISHING_ROD" -> "minecraft:item/handheld_rod";
            default -> PixelArt.handheld(kind) ? "minecraft:item/handheld" : "minecraft:item/generated";
        };
        text("assets/versaera/models/item/" + t.id() + ".json", "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"versaera:item/" + t.id() + "\"}}");
        png("assets/versaera/textures/item/" + t.id() + ".png", PixelArt.item(t));
    }

    /** 바닐라 아이템 모델을 그대로 두고 CustomModelData 덮어쓰기만 더한다 */
    private void vanillaOverrides(String material, Map<String, Integer> models) {
        String id = material.toLowerCase(Locale.ROOT);
        String base;
        List<String> pre = new ArrayList<>();   // 바닐라의 원래 overrides (먼저)
        if (BLOCK_ITEMS.contains(material)) base = "\"parent\":\"minecraft:block/" + id + "\"";
        else if (material.equals("BOW")) {
            base = "\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/bow\"},\"display\":{"
                    + "\"thirdperson_righthand\":{\"rotation\":[-80,260,-40],\"translation\":[-1,-2,2.5],\"scale\":[0.9,0.9,0.9]},"
                    + "\"thirdperson_lefthand\":{\"rotation\":[-80,-280,40],\"translation\":[-1,-2,2.5],\"scale\":[0.9,0.9,0.9]},"
                    + "\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
                    + "\"firstperson_lefthand\":{\"rotation\":[0,90,-25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]}}";
            pre.add("{\"predicate\":{\"pulling\":1},\"model\":\"minecraft:item/bow_pulling_0\"}");
            pre.add("{\"predicate\":{\"pulling\":1,\"pull\":0.65},\"model\":\"minecraft:item/bow_pulling_1\"}");
            pre.add("{\"predicate\":{\"pulling\":1,\"pull\":0.9},\"model\":\"minecraft:item/bow_pulling_2\"}");
        } else if (material.equals("FISHING_ROD")) {
            base = "\"parent\":\"minecraft:item/handheld_rod\",\"textures\":{\"layer0\":\"minecraft:item/fishing_rod\"}";
            pre.add("{\"predicate\":{\"cast\":1},\"model\":\"minecraft:item/fishing_rod_cast\"}");
        } else if (material.startsWith("LEATHER_")) {
            base = "\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/" + id + "\",\"layer1\":\"minecraft:item/" + id + "_overlay\"}";
        } else if (material.equals("POTION")) {
            base = "\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/potion_overlay\",\"layer1\":\"minecraft:item/potion\"}";
        } else if (material.equals("FERN") || material.equals("DEAD_BUSH")) {
            base = "\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:block/" + id + "\"}";
        } else {
            boolean hand = HANDHELD.contains(material) || material.endsWith("_SWORD") || material.endsWith("_AXE") || material.endsWith("_PICKAXE")
                    || material.endsWith("_SHOVEL") || material.endsWith("_HOE");
            base = "\"parent\":\"minecraft:item/" + (hand ? "handheld" : "generated") + "\",\"textures\":{\"layer0\":\"minecraft:item/" + id + "\"}";
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(models.entrySet());
        list.sort(Map.Entry.comparingByValue());   // 번호 오름차순
        StringBuilder o = new StringBuilder(String.join(",", pre));
        for (Map.Entry<String, Integer> e : list) {
            if (o.length() > 0) o.append(',');
            o.append("{\"predicate\":{\"custom_model_data\":").append(e.getValue()).append("},\"model\":\"versaera:").append(e.getKey()).append("\"}");
        }
        text("assets/minecraft/models/item/" + id + ".json", "{" + base + ",\"overrides\":[" + o + "]}");
    }

    // ------------------------------------------------------------------ 필드 보스 모델
    private void fieldBossModel(io.versaera.domain.fieldboss.FieldBoss fb) {
        var kinds = fb.kinds();
        boolean undead = kinds.contains(io.versaera.domain.item.ItemOptions.Kind.UNDEAD), demon = kinds.contains(io.versaera.domain.item.ItemOptions.Kind.DEMON);
        float h = (fb.id().hashCode() & 0xffff) / 65535f;
        Color main, second, accent, dark;
        String grain;
        if (undead) {
            main = new Color(212, 206, 188); second = new Color(70, 66, 82); accent = new Color(90, 230, 180); dark = new Color(40, 36, 48); grain = "bone";
        } else if (demon) {
            main = new Color(150, 32, 30); second = new Color(46, 30, 34); accent = new Color(255, 150, 40); dark = new Color(28, 20, 24); grain = "scale";
        } else {
            main = Color.getHSBColor(h, 0.5f, 0.6f); second = Color.getHSBColor((h + 0.08f) % 1f, 0.45f, 0.45f);
            accent = new Color(255, 214, 90); dark = Color.getHSBColor(h, 0.4f, 0.25f); grain = fb.look().equals("GOLEM") ? "metal" : "scale";
        }
        if (fb.look().equals("KNIGHT") || fb.look().equals("VAMPIRE")) {
            second = undead ? new Color(60, 58, 70) : new Color(170, 175, 186);
            if (fb.look().equals("VAMPIRE")) accent = new Color(150, 10, 30);
            grain = "metal";
        }
        if (fb.look().equals("CASTER")) grain = "cloth";
        if (fb.look().equals("SALAMANDER")) { main = new Color(190, 70, 30); accent = new Color(255, 200, 60); }
        String tex = "fboss/" + fb.id();
        text("assets/versaera/models/fboss/" + fb.id() + ".json", ModelKit.json(ModelKit.template(fb.look()), tex,
                "{\"fixed\":{\"scale\":[1,1,1]},\"head\":{\"scale\":[1,1,1]}}"));
        png("assets/versaera/textures/fboss/" + fb.id() + ".png", ModelKit.texture(fb.id(), main, second, accent, dark, grain));
    }

    // ------------------------------------------------------------------ 보스 모델
    private enum Body { COLOSSUS, WINGED, CRYSTAL, WORM, SHELL }

    private static Body bodyOf(String id) {
        if (id.contains("worm")) return Body.WORM;
        if (id.contains("warden") || id.contains("wing")) return Body.WINGED;
        if (id.contains("frost") || id.contains("husk") || id.contains("shard")) return Body.CRYSTAL;
        if (id.contains("carapace") || id.contains("shell") || id.contains("crab")) return Body.SHELL;
        return Body.COLOSSUS;
    }

    private record Cube(double x1, double y1, double z1, double x2, double y2, double z2) {}

    private static List<Cube> cubes(Body body) {
        return switch (body) {
            case COLOSSUS -> List.of(new Cube(4, 6, 5, 12, 14, 11), new Cube(5.5, 14, 6, 10.5, 18, 10),      // 몸통 · 머리
                    new Cube(1, 7, 6.5, 4, 14, 9.5), new Cube(12, 7, 6.5, 15, 14, 9.5),                          // 팔
                    new Cube(4.5, 0, 6, 7.5, 6, 10), new Cube(8.5, 0, 6, 11.5, 6, 10));                         // 다리
            case WINGED -> List.of(new Cube(5, 5, 4, 11, 10, 13), new Cube(6, 8, 0, 10, 12, 4),
                    new Cube(-6, 8, 6, 5, 9, 11), new Cube(11, 8, 6, 22, 9, 11),                                  // 날개
                    new Cube(6, 0, 6, 8, 5, 8), new Cube(8, 0, 9, 10, 5, 11), new Cube(7, 6, 13, 9, 7, 19));    // 다리 · 꼬리
            case CRYSTAL -> List.of(new Cube(4, 0, 4, 12, 12, 12), new Cube(6, 12, 6, 10, 20, 10),
                    new Cube(1, 6, 7, 4, 16, 9), new Cube(12, 4, 7, 15, 14, 9), new Cube(7, 6, 1, 9, 15, 4), new Cube(7, 3, 12, 9, 13, 15));
            case WORM -> List.of(new Cube(5, 0, 0, 11, 6, 4), new Cube(5.5, 2, 4, 10.5, 9, 8), new Cube(6, 5, 8, 10, 13, 12),
                    new Cube(6.5, 9, 12, 9.5, 18, 15), new Cube(5, 16, 13, 11, 20, 17));                         // 마디 + 입
            case SHELL -> List.of(new Cube(2, 2, 3, 14, 7, 13), new Cube(3, 7, 4, 13, 9, 12),
                    new Cube(-3, 3, 0, 2, 6, 4), new Cube(14, 3, 0, 19, 6, 4), new Cube(3, 0, 4, 5, 2, 12), new Cube(11, 0, 4, 13, 2, 12));
        };
    }

    private void bossModel(BossDefinition boss) {
        String name = boss.model().replace('/', '_');
        Body body = bodyOf(boss.id());
        StringBuilder els = new StringBuilder();
        for (Cube c : cubes(body)) {
            if (els.length() > 0) els.append(',');
            els.append("{\"from\":[").append(n(c.x1)).append(',').append(n(c.y1)).append(',').append(n(c.z1)).append("],\"to\":[")
                    .append(n(c.x2)).append(',').append(n(c.y2)).append(',').append(n(c.z2)).append("],\"faces\":{");
            String[] faces = {"north", "south", "east", "west", "up", "down"};
            for (int i = 0; i < faces.length; i++) {
                if (i > 0) els.append(',');
                els.append('"').append(faces[i]).append("\":{\"uv\":[0,0,16,16],\"texture\":\"#body\"}");
            }
            els.append("}}");
        }
        text("assets/versaera/models/" + boss.model() + ".json", "{\"textures\":{\"body\":\"versaera:boss/" + name + "\",\"particle\":\"versaera:boss/"
                + name + "\"},\"elements\":[" + els + "],\"display\":{\"fixed\":{\"scale\":[2,2,2]},\"head\":{\"scale\":[2,2,2]}}}");
        png("assets/versaera/textures/boss/" + name + ".png", texture(boss.id(), body));
    }

    private static String n(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** 절차 텍스처: 보스 id 로 정한 색 + 돌결 · 결정면 · 비늘 무늬 (결정적) */
    private static BufferedImage texture(String id, Body body) {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        SplittableRandom r = new SplittableRandom(id.hashCode());
        Color base = switch (body) {
            case COLOSSUS -> new Color(118, 112, 104);
            case WINGED -> new Color(92, 120, 168);
            case CRYSTAL -> new Color(150, 205, 235);
            case WORM -> new Color(196, 160, 102);
            case SHELL -> new Color(62, 92, 110);
        };
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                int noise = r.nextInt(-14, 15);
                int pattern = switch (body) {
                    case COLOSSUS -> (y % 8 == 0 || (x + (y / 8) * 4) % 16 == 0) ? -40 : 0;        // 돌 벽돌 줄눈
                    case WINGED -> ((x + y) % 6 == 0) ? 25 : 0;                                      // 깃 결
                    case CRYSTAL -> (Math.abs(x - y) % 9 == 0 || (x + y) % 11 == 0) ? 45 : 0;        // 결정면
                    case WORM -> (y % 6 < 1) ? -35 : (x % 5 == 0 ? 10 : 0);                          // 마디
                    case SHELL -> ((x / 4 + y / 4) % 2 == 0) ? 15 : -10;                             // 비늘
                };
                int v = noise + pattern;
                img.setRGB(x, y, new Color(clamp(base.getRed() + v), clamp(base.getGreen() + v), clamp(base.getBlue() + v)).getRGB());
            }
        // 약점(등) 표시: 빛나는 줄 — 등 뒤를 노리라는 시각 단서
        for (int y = 12; y < 20; y++) for (int x = 26; x < 30; x++) img.setRGB(x, y, new Color(255, 196, 64).getRGB());
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ UI 아이콘 (메뉴 버튼)
    /** 메뉴 아이콘 키 — Menu.ui(key, …) 가 같은 키를 쓴다 */
    public static final List<String> UI_ICONS = List.of("quest", "quest_active", "shop", "gift", "news", "combat", "life", "guild", "money",
            "auction", "sell", "stat", "map_known", "map_unknown", "member", "reputation",
            "arts", "fieldboss", "appraise", "bandage", "land", "castle", "nation", "party", "trial", "gods", "history", "character", "close", "job");

    private void uiIcon(String key) {
        text("assets/versaera/models/ui/" + key + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"versaera:ui/" + key + "\"}}");
        png("assets/versaera/textures/ui/" + key + ".png", icon16(key));
    }

    /** 16×16 픽셀 아이콘 (짧은 모양 하나 + 테두리) — 결정적 */
    static BufferedImage icon16(String key) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color gold = new Color(201, 162, 39), dark = new Color(40, 34, 30), light = new Color(236, 226, 200), red = new Color(170, 52, 44),
                green = new Color(70, 150, 70), blue = new Color(70, 110, 180), steel = new Color(180, 186, 196);
        switch (key) {
            case "quest", "quest_active" -> {   // 두루마리
                g.setColor(light); g.fillRect(3, 3, 10, 10);
                g.setColor(dark); for (int y = 5; y <= 11; y += 2) g.drawLine(5, y, 11, y);
                g.setColor(key.equals("quest") ? gold : green); g.fillRect(2, 2, 12, 2); g.fillRect(2, 12, 12, 2);
            }
            case "shop" -> { g.setColor(new Color(130, 90, 50)); g.fillOval(3, 5, 10, 9); g.setColor(gold); g.fillRect(6, 2, 4, 4); }
            case "gift" -> { g.setColor(red); g.fillRect(3, 6, 10, 8); g.setColor(gold); g.fillRect(7, 6, 2, 8); g.fillRect(3, 9, 10, 2); g.fillOval(4, 3, 4, 4); g.fillOval(8, 3, 4, 4); }
            case "news" -> { g.setColor(light); g.fillOval(2, 2, 12, 12); g.setColor(dark); g.drawLine(8, 8, 8, 4); g.drawLine(8, 8, 11, 8); g.setColor(gold); g.drawOval(2, 2, 11, 11); }
            case "combat" -> { g.setColor(steel); for (int i = 0; i < 9; i++) g.fillRect(4 + i, 11 - i, 2, 2); g.setColor(gold); g.fillRect(3, 10, 4, 2); g.fillRect(4, 12, 2, 2); }
            case "life" -> { g.setColor(new Color(130, 90, 50)); for (int i = 0; i < 8; i++) g.fillRect(4 + i, 13 - i, 2, 2); g.setColor(steel); g.fillRect(9, 2, 6, 4); }
            case "guild" -> { g.setColor(new Color(130, 90, 50)); g.fillRect(3, 1, 2, 14); g.setColor(blue); g.fillPolygon(new int[]{5, 14, 14, 5}, new int[]{2, 2, 9, 9}, 4); g.setColor(gold); g.fillRect(8, 4, 3, 3); }
            case "money" -> { g.setColor(gold); g.fillOval(2, 2, 12, 12); g.setColor(new Color(150, 110, 20)); g.drawOval(4, 4, 7, 7); }
            case "auction" -> { g.setColor(new Color(130, 90, 50)); for (int i = 0; i < 8; i++) g.fillRect(3 + i, 12 - i, 2, 2); g.fillRect(8, 2, 6, 4); g.setColor(dark); g.fillRect(2, 13, 7, 2); }
            case "sell" -> { g.setColor(steel); g.fillPolygon(new int[]{2, 14, 8}, new int[]{3, 3, 10}, 3); g.setColor(gold); g.fillRect(6, 11, 4, 3); }
            case "stat" -> { g.setColor(gold); g.fillPolygon(new int[]{8, 10, 15, 11, 12, 8, 4, 5, 1, 6}, new int[]{1, 6, 6, 9, 14, 11, 14, 9, 6, 6}, 10); }
            case "map_known" -> { g.setColor(light); g.fillRect(2, 3, 12, 10); g.setColor(green); g.fillOval(4, 5, 5, 4); g.setColor(red); g.fillRect(10, 5, 2, 5); }
            case "map_unknown" -> { g.setColor(new Color(90, 90, 100)); g.fillRect(2, 3, 12, 10); g.setColor(light); g.fillRect(6, 5, 4, 1); g.fillRect(10, 6, 1, 2); g.fillRect(8, 8, 2, 1); g.fillRect(8, 9, 1, 1); g.fillRect(8, 11, 1, 1); }   // 글꼴 없이 그린 "?" (JVM 마다 같은 그림)
            case "member" -> { g.setColor(light); g.fillOval(5, 2, 6, 6); g.setColor(blue); g.fillRoundRect(3, 8, 10, 7, 4, 4); }
            case "reputation" -> { g.setColor(blue); g.fillPolygon(new int[]{3, 13, 13, 8, 3}, new int[]{2, 2, 10, 14, 10}, 5); g.setColor(gold); g.fillRect(7, 5, 2, 5); }
            case "arts" -> { g.setColor(new Color(110, 60, 150)); g.fillRect(3, 2, 10, 12); g.setColor(gold); g.fillRect(3, 2, 1, 12); g.fillPolygon(new int[]{8, 9, 11, 9, 10, 8, 6, 7, 5, 7}, new int[]{4, 6, 7, 8, 11, 9, 11, 8, 7, 6}, 10); }
            case "fieldboss" -> { g.setColor(light); g.fillOval(3, 2, 10, 9); g.fillRect(5, 10, 6, 4); g.setColor(dark); g.fillRect(5, 6, 2, 2); g.fillRect(9, 6, 2, 2); g.fillRect(7, 9, 2, 1); g.setColor(red); g.fillRect(1, 1, 2, 2); g.fillRect(13, 1, 2, 2); }
            case "appraise" -> { g.setColor(new Color(130, 90, 50)); for (int i = 0; i < 5; i++) g.fillRect(2 + i, 13 - i, 2, 2); g.setColor(steel); g.drawOval(6, 2, 8, 8); g.setColor(new Color(170, 220, 255)); g.fillOval(7, 3, 7, 7); }
            case "bandage" -> { g.setColor(light); g.fillRect(2, 5, 12, 6); g.setColor(red); g.fillRect(7, 3, 2, 10); g.fillRect(4, 7, 8, 2); }
            case "land" -> { g.setColor(green); g.fillRect(1, 11, 14, 4); g.setColor(new Color(130, 90, 50)); g.fillRect(5, 2, 1, 10); g.setColor(red); g.fillPolygon(new int[]{6, 13, 6}, new int[]{2, 4, 7}, 3); }
            case "castle" -> { g.setColor(steel); g.fillRect(3, 6, 10, 9); for (int x = 3; x < 13; x += 3) g.fillRect(x, 3, 2, 3); g.setColor(dark); g.fillRect(7, 10, 2, 5); }
            case "nation" -> { g.setColor(gold); g.fillRect(2, 8, 12, 5); for (int x = 2; x < 14; x += 4) g.fillPolygon(new int[]{x, x + 2, x + 4}, new int[]{8, 3, 8}, 3); g.setColor(red); g.fillRect(7, 9, 2, 2); }
            case "party" -> { g.setColor(light); g.fillOval(2, 3, 5, 5); g.fillOval(9, 3, 5, 5); g.setColor(green); g.fillRoundRect(1, 8, 7, 7, 3, 3); g.setColor(blue); g.fillRoundRect(8, 8, 7, 7, 3, 3); }
            case "trial" -> { g.setColor(new Color(170, 130, 80)); g.fillRect(7, 2, 2, 13); g.fillRect(3, 5, 10, 2); g.setColor(new Color(220, 200, 150)); g.fillOval(5, 1, 6, 5); g.setColor(red); g.fillOval(6, 8, 4, 4); }
            case "gods" -> { g.setColor(gold); g.fillOval(4, 4, 8, 8); for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; g.fillRect(7 + (int) Math.round(Math.cos(a) * 6), 7 + (int) Math.round(Math.sin(a) * 6), 2, 2); } }
            case "history" -> { g.setColor(new Color(120, 60, 40)); g.fillRect(3, 2, 10, 12); g.setColor(light); g.fillRect(5, 3, 7, 10); g.setColor(dark); for (int y = 5; y < 12; y += 2) g.drawLine(6, y, 10, y); }
            case "character" -> { g.setColor(new Color(230, 190, 150)); g.fillOval(5, 1, 6, 6); g.setColor(blue); g.fillRect(4, 7, 8, 5); g.setColor(dark); g.fillRect(5, 12, 2, 3); g.fillRect(9, 12, 2, 3); }
            case "job" -> { g.setColor(new Color(130, 90, 50)); g.fillRect(2, 6, 12, 8); g.setColor(new Color(170, 120, 70)); g.fillRect(2, 4, 12, 3); g.setColor(steel); g.fillRect(4, 1, 2, 5); g.fillRect(10, 2, 3, 3); }
            case "close" -> { g.setColor(red); for (int i = 0; i < 10; i++) { g.fillRect(3 + i, 3 + i, 2, 2); g.fillRect(12 - i, 3 + i, 2, 2); } }
            default -> { g.setColor(gold); g.fillRect(4, 4, 8, 8); }
        }
        g.dispose();
        // 어두운 바탕에서 보이게 1픽셀 외곽선
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                int a = img.getRGB(x, y);
                if ((a >>> 24) != 0) { out.setRGB(x, y, a); continue; }
                boolean near = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (img.getRGB(nx, ny) >>> 24) != 0) near = true;
                }
                if (near) out.setRGB(x, y, 0xFF1A1714);
            }
        return out;
    }

    // ------------------------------------------------------------------ UI
    private void uiFont() {
        png("assets/versaera/textures/ui/menu6.png", panel(176, 222, 6));
        png("assets/versaera/textures/ui/menu3.png", panel(176, 168, 3));
        text("assets/minecraft/font/default.json", "{\"providers\":["
                + "{\"type\":\"bitmap\",\"file\":\"versaera:ui/menu6.png\",\"ascent\":13,\"height\":222,\"chars\":[\"" + esc(MENU_BG_6) + "\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"versaera:ui/menu3.png\",\"ascent\":13,\"height\":168,\"chars\":[\"" + esc(MENU_BG_3) + "\"]},"
                + "{\"type\":\"space\",\"advances\":{\"" + esc(SHIFT_LEFT_8) + "\":-8,\"" + esc(SHIFT_LEFT_169) + "\":-169}}"
                + "]}");
    }

    private static String esc(char c) {
        return String.format("\\u%04x", (int) c);
    }

    /** 메뉴 배경: 어두운 판 + 얇은 금색 테두리 + 칸 자리 (설명 문장 없음) */
    private static BufferedImage panel(int w, int h, int rows) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 22, 28, 235));
        g.fillRoundRect(0, 0, w, h, 8, 8);
        g.setColor(new Color(201, 162, 39));
        g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(new Color(255, 255, 255, 18));
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < 9; c++) g.fillRect(7 + c * 18, 17 + r * 18, 16, 16);
        g.dispose();
        return img;
    }

    private static BufferedImage icon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 22, 28));
        g.fillRect(0, 0, 64, 64);
        g.setColor(new Color(201, 162, 39));
        g.fillPolygon(new int[]{32, 52, 32, 12}, new int[]{8, 32, 56, 32}, 4);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ zip
    private void text(String path, String s) {
        files.put(path, s.getBytes(StandardCharsets.UTF_8));
    }

    private void png(String path, BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            files.put(path, out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Pack zip(Map<String, Integer> models) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> f : files.entrySet()) {
                    ZipEntry e = new ZipEntry(f.getKey());
                    e.setTime(315532800000L);   // 1980-01-01 고정 → 결정적 바이트
                    z.putNextEntry(e);
                    z.write(f.getValue());
                    z.closeEntry();
                }
            }
            byte[] zip = bytes.toByteArray();
            return new Pack(zip, MessageDigest.getInstance("SHA-1").digest(zip), Map.copyOf(models));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
