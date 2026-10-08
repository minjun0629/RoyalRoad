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
    /** 1 · 2 · 4 · 5 줄 상자 창 배경 */
    public static final char MENU_BG_1 = '\uE004', MENU_BG_2 = '\uE005', MENU_BG_4 = '\uE006', MENU_BG_5 = '\uE007';

    /** 상자 창 줄 수에 맞는 배경 글자 (1 ~ 6) */
    public static char menuGlyph(int rows) {
        return switch (Math.max(1, Math.min(6, rows))) { case 1 -> MENU_BG_1; case 2 -> MENU_BG_2; case 3 -> MENU_BG_3; case 4 -> MENU_BG_4; case 5 -> MENU_BG_5; default -> MENU_BG_6; };
    }

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
            for (var part : ModelKit.rig(fb.look())) put(models, "fboss/" + fb.id() + "/" + part.name());
            b.fieldBossModel(fb);
        }
        put(models, "mob/iron_mask");
        // 철인의 쇠 가면 = 입체 큰 투구 (머리 중심 = 모델 중심)
        var maskType = new io.versaera.domain.item.ItemType("iron_mask", "쇠 가면", io.versaera.domain.item.ItemCategory.ARMOR, "IRON_HELMET", 1, 1,
                Set.of("helmet"), Map.of(), Map.of(), "ORIGINAL");
        Sculpt.Made maskMade = Sculpt.of(maskType);
        var mask = ModelKit.build("iron_mask", List.of(new ModelKit.Part("mask", "NONE", 8, 8, 8, 0, maskMade.cubes())), maskMade.style(), "mob/iron_mask", maskMade.display());
        b.text("assets/versaera/models/mob/iron_mask.json", mask.models().get("mask"));
        b.png("assets/versaera/textures/mob/iron_mask.png", mask.texture());
        // 대형 조각 작품: 종류 × 자리 × 재료 모양 (ART-02) — 종이 덮어쓰기
        for (var k : content.expansion().artworks())
            for (var part : k.parts())
                for (String look : io.versaera.domain.art.ArtMaterials.LOOKS) {
                    String key = ArtworkModels.key(k.id(), part.slot(), look);
                    put(models, key);
                    ModelKit.Built built = ArtworkModels.build(k.id(), part.slot(), look);
                    b.text("assets/versaera/models/" + key + ".json", built.models().get("art"));
                    b.png("assets/versaera/textures/artwork/" + k.id() + "_" + part.slot() + "_" + look + ".png", built.texture());
                }
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
            if (b.hands.contains(t.id())) {   // 손에 들었을 때의 입체 모델
                String hm = "item/" + t.id() + "_hand";
                int hid = PackIds.itemHand(t.id());
                if (all.containsValue(hid)) throw new IllegalStateException("모델 번호 충돌: " + hm);
                all.put(hm, hid);
                byMaterial.get(t.material()).put(hm, hid);
            }
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
        // 블록 · 아이템 아틀라스 (1.19.3+): 기본으로는 textures/block · item 만 읽으므로, 팩이 쓰는 다른 폴더를 등록한다 — 없으면 보라 · 검정 격자
        b.text("assets/minecraft/atlases/blocks.json", blockAtlas(b.files.keySet()));
        models = all;
        b.uiFont();
        b.png("pack.png", icon());
        return b.zip(models);
    }

    /** versaera 텍스처 중 item · block 밖의 폴더를 블록 아틀라스에 넣는다 (directory 소스는 모든 이름공간에 적용) */
    static String blockAtlas(Collection<String> paths) {
        Set<String> dirs = new TreeSet<>();
        for (String f : paths) {
            if (!f.startsWith("assets/versaera/textures/") || !f.endsWith(".png")) continue;
            String rest = f.substring("assets/versaera/textures/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;
            String dir = rest.substring(0, slash);
            if (!dir.equals("item") && !dir.equals("block") && !dir.equals("trims")) dirs.add(dir);
        }
        StringBuilder sb = new StringBuilder("{\"sources\":[");
        int i = 0;
        for (String d : dirs) sb.append(i++ == 0 ? "" : ",").append("{\"type\":\"directory\",\"source\":\"").append(d).append("\",\"prefix\":\"").append(d).append("/\"}");
        return sb.append("]}").toString();
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

    /** 비스듬히 쥐는 무기 · 도구 (입체 모델을 무기 축으로 뒤집는다) */
    static final Set<String> WIELDED = Set.of("sword", "dagger", "knife", "axe", "spear", "staff", "torch", "hammer", "mace", "pickaxe", "pickaxe_weapon",
            "scythe", "rake", "plow", "bow", "arrow");

    /** 이 아이템은 인벤토리 카드와 손 모델(item/&lt;id&gt;_hand)이 따로 있나 (서버가 손에 든 칸만 손 모델 번호로 바꾼다) */
    public static boolean hasHandModel(io.versaera.domain.item.ItemType t) {
        return modeled(t) && t.category().unique() && MmoIcon.handles(PixelArt.kind(t)) && Sculpt.of(t) == null;
    }

    /** 손 모델(item/<id>_hand)이 따로 있는 아이템 */
    private final Set<String> hands = new HashSet<>();

    private void itemModel(io.versaera.domain.item.ItemType t) {
        String kind = PixelArt.kind(t);
        String parent = switch (t.material()) {
            case "BOW" -> "minecraft:item/bow";
            case "FISHING_ROD" -> "minecraft:item/handheld_rod";
            default -> PixelArt.handheld(kind) ? "minecraft:item/handheld" : "minecraft:item/generated";
        };
        Sculpt.Made made = Sculpt.of(t);
        if (made == null && t.category().unique() && MmoIcon.handles(kind)) {   // 장비: 인벤토리 카드 + 손에 든 입체 모델
            MmoIcon.Drawn d = MmoIcon.render(t, kind);
            if (d != null) {
                VoxelSmith.Build vox = VoxelSmith.handles(kind) ? VoxelSmith.build(t, kind, d.look()) : null;
                // 무기 · 도구의 카드 그림은 큐브 모델을 렌더링한 입체 그림 (손에 든 모습과 같다)
                png("assets/versaera/textures/item/" + t.id() + ".png", MmoCard.card(t, kind, vox == null ? d : new MmoIcon.Drawn(VoxelRender.icon(vox, 52), d.look(), d.grade())));
                // 인벤토리 = 납작한 카드 (item/<id>), 손 = 입체 모델 (item/<id>_hand) — 1.20.1 은 둘을 한 모델로 가를 수 없어서,
                // 손에 든 칸의 아이템만 서버가 CustomModelData 를 손 모델 번호로 바꾼다 (HandModels)
                text("assets/versaera/models/item/" + t.id() + ".json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"versaera:item/" + t.id() + "\"}}");
                if (vox != null) {   // 무기 · 도구: Armourer's Workshop 식 큐브 모델 (면마다 칠한 텍스처)
                    VoxelPaint.Painted paint = VoxelPaint.paint(vox.boxes);
                    png("assets/versaera/textures/item3d/" + t.id() + ".png", paint.atlas());
                    text("assets/versaera/models/item/" + t.id() + "_hand.json", VoxelSmith.json(vox, paint, "versaera:item3d/" + t.id(), "versaera:item/" + t.id(), false));
                } else {
                    png("assets/versaera/textures/item3d/" + t.id() + ".png", d.art());
                    text("assets/versaera/models/item/" + t.id() + "_hand.json", Model3D.json(d.art(), d.look(), WIELDED.contains(kind),
                            "versaera:item3d/" + t.id(), "versaera:item/" + t.id(), false));
                }
                hands.add(t.id());
                return;
            }
        }
        png("assets/versaera/textures/item/" + t.id() + ".png", PixelArt.item(t));
        if (made != null) {   // 입체 조각 모델 (그림은 부서질 때 파티클 · 문서용으로 남긴다)
            var built = ModelKit.build(t.id(), List.of(new ModelKit.Part("item", "NONE", 8, 8, 8, 0, made.cubes())), made.style(), "item3d/" + t.id(), made.display());
            text("assets/versaera/models/item/" + t.id() + ".json", built.models().get("item"));
            png("assets/versaera/textures/item3d/" + t.id() + ".png", built.texture());
            return;
        }
        text("assets/versaera/models/item/" + t.id() + ".json", "{\"parent\":\"" + parent + "\",\"textures\":{\"layer0\":\"versaera:item/" + t.id() + "\"}}");
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
    /** 필드 보스의 색 · 결 (언데드 = 뼈 · 악마 = 붉은 비늘 · 용 = 비늘 …) */
    public static ModelKit.Style fieldBossStyle(io.versaera.domain.fieldboss.FieldBoss fb) {
        ModelKit.Style b = baseStyle(fb);
        // 같은 모양 · 종족이라도 보스마다 색이 조금씩 다르게: 몸색 색상을 비틀고, 강조색은 보스마다 고른다
        float h = ((fb.id().hashCode() * 31) & 0xffff) / 65535f;
        Color[] glows = {new Color(255, 170, 40), new Color(110, 255, 200), new Color(190, 110, 255), new Color(255, 70, 70), new Color(120, 200, 255), new Color(255, 236, 110)};
        Color glow = fb.look().equals("SALAMANDER") ? b.accent() : glows[Math.floorMod(fb.id().hashCode(), glows.length)];
        float shift = (h - 0.5f) * (fb.look().equals("DEMON") || fb.look().equals("DRAGON") ? 0.34f : 0.12f);
        return new ModelKit.Style(shiftHue(b.main(), shift), shiftHue(b.second(), shift * 0.8f), glow, b.dark(), b.metal(), b.grain(), b.secondGrain());
    }

    private static Color shiftHue(Color c, float d) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor((hsb[0] + d + 1) % 1, hsb[1], hsb[2]);
    }

    private static ModelKit.Style baseStyle(io.versaera.domain.fieldboss.FieldBoss fb) {
        var kinds = fb.kinds();
        boolean undead = kinds.contains(io.versaera.domain.item.ItemOptions.Kind.UNDEAD), demon = kinds.contains(io.versaera.domain.item.ItemOptions.Kind.DEMON);
        float h = (fb.id().hashCode() & 0xffff) / 65535f;
        Color steel = new Color(176, 184, 198);
        return switch (fb.look()) {
            case "KNIGHT" -> undead ? new ModelKit.Style(new Color(206, 198, 178), new Color(58, 56, 70), new Color(90, 230, 190), new Color(34, 30, 40), steel, "bone", "metal")
                    : new ModelKit.Style(new Color(150, 120, 90), new Color(150, 156, 172), new Color(200, 60, 50), new Color(50, 40, 40), steel, "hide", "metal");
            case "VAMPIRE" -> new ModelKit.Style(new Color(214, 206, 214), new Color(120, 14, 32), new Color(255, 60, 60), new Color(30, 22, 34), steel, "cloth", "cloth");
            case "CASTER" -> new ModelKit.Style(new Color(222, 214, 192), new Color(54, 40, 82), new Color(110, 255, 200), new Color(40, 30, 44), steel, "bone", "cloth");
            case "DEMON" -> new ModelKit.Style(new Color(150, 34, 32), new Color(56, 30, 40), new Color(255, 160, 40), new Color(28, 18, 22), steel, "scale", "membrane");
            case "DRAGON" -> undead ? new ModelKit.Style(new Color(214, 206, 186), new Color(70, 66, 84), new Color(100, 240, 180), new Color(40, 36, 46), steel, "bone", "membrane")
                    : new ModelKit.Style(Color.getHSBColor(h, 0.55f, 0.55f), Color.getHSBColor((h + 0.05f) % 1, 0.45f, 0.4f), new Color(255, 210, 80),
                    Color.getHSBColor(h, 0.4f, 0.22f), steel, "scale", "membrane");
            case "GOLEM" -> new ModelKit.Style(new Color(124, 118, 108), new Color(96, 92, 88), new Color(120, 220, 255), new Color(56, 52, 50), steel, "stone", "stone");
            case "HYDRA" -> new ModelKit.Style(new Color(60, 110, 76), new Color(84, 130, 90), new Color(240, 220, 60), new Color(30, 50, 36), steel, "scale", "scale");
            case "SALAMANDER" -> new ModelKit.Style(new Color(186, 66, 30), new Color(150, 50, 26), new Color(255, 196, 60), new Color(60, 24, 18), steel, "scale", "scale");
            case "FLYER" -> new ModelKit.Style(demon ? new Color(220, 216, 220) : new Color(110, 120, 150), new Color(90, 80, 110), new Color(255, 80, 60),
                    new Color(50, 44, 60), steel, "hide", "membrane");
            default -> new ModelKit.Style(Color.getHSBColor(h, 0.4f, 0.5f), Color.getHSBColor(h, 0.3f, 0.4f), new Color(255, 210, 80), new Color(40, 36, 40), steel, "hide", "hide");
        };
    }

    private void fieldBossModel(io.versaera.domain.fieldboss.FieldBoss fb) {
        String tex = "fboss/" + fb.id();
        var built = ModelKit.build(fb.id(), ModelKit.rig(fb.look()), fieldBossStyle(fb), tex, "{\"fixed\":{\"scale\":[1,1,1]},\"head\":{\"scale\":[1,1,1]}}");
        for (var e : built.models().entrySet()) text("assets/versaera/models/fboss/" + fb.id() + "/" + e.getKey() + ".json", e.getValue());
        png("assets/versaera/textures/" + tex + ".png", built.texture());
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

    private void bossModel(BossDefinition boss) {
        String name = boss.model().replace('/', '_');
        Body body = bodyOf(boss.id());
        String look = switch (body) { case COLOSSUS -> "COLOSSUS"; case WINGED -> "DRAGON"; case CRYSTAL -> "CRYSTAL"; case WORM -> "WORM"; case SHELL -> "CRAB"; };
        Color steel = new Color(176, 184, 198);
        ModelKit.Style st = switch (body) {
            case COLOSSUS -> new ModelKit.Style(new Color(132, 124, 112), new Color(100, 96, 90), new Color(255, 196, 64), new Color(60, 56, 52), steel, "stone", "stone");
            case WINGED -> new ModelKit.Style(new Color(92, 120, 168), new Color(70, 84, 130), new Color(255, 214, 90), new Color(34, 40, 60), steel, "scale", "membrane");
            case CRYSTAL -> new ModelKit.Style(new Color(150, 205, 235), new Color(180, 230, 255), new Color(240, 250, 255), new Color(60, 90, 120), steel, "crystal", "crystal");
            case WORM -> new ModelKit.Style(new Color(196, 160, 102), new Color(120, 40, 50), new Color(255, 200, 120), new Color(90, 66, 40), steel, "hide", "hide");
            case SHELL -> new ModelKit.Style(new Color(62, 92, 110), new Color(90, 130, 150), new Color(255, 200, 70), new Color(30, 44, 54), steel, "scale", "scale");
        };
        // 등 약점 표시: 강조색이 등(남쪽) 문양으로 들어가도록 몸 상자에 RUNE 대신 빛 — 모양 템플릿을 그대로 쓰고 색으로 알린다
        var built = ModelKit.build(boss.id(), ModelKit.merged(look), st, "boss/" + name, "{\"fixed\":{\"scale\":[2,2,2]},\"head\":{\"scale\":[2,2,2]}}");
        text("assets/versaera/models/" + boss.model() + ".json", built.models().get("all"));
        png("assets/versaera/textures/boss/" + name + ".png", built.texture());
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ UI 아이콘 (메뉴 버튼)
    /** 메뉴 아이콘 키 — Menu.ui(key, …) 가 같은 키를 쓴다 */
    public static final List<String> UI_ICONS = List.of("quest", "quest_active", "shop", "gift", "news", "combat", "life", "guild", "money",
            "auction", "sell", "stat", "map_known", "map_unknown", "member", "reputation",
            "arts", "fieldboss", "appraise", "bandage", "land", "castle", "nation", "party", "trial", "gods", "history", "character", "close", "job",
            "rumor", "train", "inn", "heal", "repair", "song", "people",
            "achievement", "achievement_locked", "title", "record", "pet", "mount", "raid", "weather", "vault", "gquest", "carriage", "ship", "sculpt");

    private void uiIcon(String key) {
        text("assets/versaera/models/ui/" + key + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"versaera:ui/" + key + "\"}}");
        png("assets/versaera/textures/ui/" + key + ".png", icon16(key));
    }

    /** 32×32 메뉴 아이콘 — 아이템 아이콘과 같은 자동 음영 붓({@link Canvas}) */
    static BufferedImage icon16(String key) {
        Canvas c = new Canvas(32, 32, "ui/" + key);
        Color gold = new Color(222, 176, 52), paper = new Color(232, 222, 196), red = new Color(184, 50, 44), green = new Color(78, 160, 74),
                blue = new Color(70, 112, 196), steel = new Color(180, 188, 200), wood = new Color(132, 88, 48), purple = new Color(128, 70, 170),
                skin = new Color(230, 190, 150), dark = new Color(40, 34, 40);
        switch (key) {
            case "quest", "quest_active" -> {
                c.layer().rect(7, 6, 18, 20).commit(Canvas.ramp(paper), 0.1);
                for (int y = 10; y <= 21; y += 3) c.layer().rect(10, y, 12, 1).commit(Canvas.ramp(new Color(120, 100, 80)), 0);
                Color rod = key.equals("quest") ? gold : green;
                c.layer().ellipse(16, 5, 11, 2.5).ellipse(16, 27, 11, 2.5).commit(Canvas.ramp(wood), 0.1);
                c.layer().ellipse(4.5, 5, 2, 2.5).ellipse(27.5, 5, 2, 2.5).ellipse(4.5, 27, 2, 2.5).ellipse(27.5, 27, 2, 2.5).commit(Canvas.ramp(rod), 0);
                if (key.equals("quest_active")) c.sparkle(26, 14, new Color(150, 255, 150));
            }
            case "shop" -> {
                c.layer().ellipse(16, 19, 11, 10).commit(Canvas.ramp(new Color(150, 104, 58)), 0.2);
                c.layer().rect(11, 5, 10, 5).commit(Canvas.ramp(new Color(120, 80, 44)), 0.2);
                c.layer().line(10, 9, 22, 9, 1.5).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(16, 20, 4, 4).commit(Canvas.ramp(gold), 0);
            }
            case "gift" -> {
                c.layer().rect(5, 13, 22, 15).commit(Canvas.ramp(red), 0.05);
                c.layer().rect(4, 10, 24, 5).commit(Canvas.ramp(Canvas.mix(red, Color.WHITE, 0.1)), 0.05);
                c.layer().rect(14, 10, 4, 18).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(11, 7, 5, 3.5).ellipse(21, 7, 5, 3.5).commit(Canvas.ramp(gold), 0);
            }
            case "news" -> {
                c.layer().ellipse(16, 16, 13, 13).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(16, 16, 10.5, 10.5).commit(Canvas.ramp(paper), 0.05);
                c.layer().line(16, 16, 16, 8, 2).line(16, 16, 22, 16, 2).commit(Canvas.ramp(dark), 0);
            }
            case "combat" -> {
                c.layer().line(5, 27, 24, 8, 3.4).commit(Canvas.ramp(steel), 0);
                c.layer().line(27, 27, 8, 8, 3.4).commit(Canvas.ramp(steel), 0);
                c.layer().line(3, 22, 10, 29, 2.6).line(29, 22, 22, 29, 2.6).commit(Canvas.ramp(gold), 0);
            }
            case "life" -> {
                c.layer().line(6, 27, 20, 13, 3).commit(Canvas.ramp(wood), 0.25);
                c.layer().poly(new double[]{15, 24, 30, 21}, new double[]{9, 2, 11, 17}).commit(Canvas.ramp(steel), 0.05);
                c.layer().ellipse(9, 9, 5, 4).commit(Canvas.ramp(green), 0.1);
            }
            case "guild" -> {
                c.layer().rect(6, 3, 3, 27).commit(Canvas.ramp(wood), 0.2);
                c.layer().poly(new double[]{9, 28, 25, 28, 9}, new double[]{4, 4, 11, 18, 18}).commit(Canvas.ramp(blue), 0.05);
                c.layer().ellipse(17, 11, 3.5, 3.5).commit(Canvas.ramp(gold), 0);
            }
            case "money" -> {
                c.layer().ellipse(12, 20, 9, 9).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(21, 13, 9, 9).commit(Canvas.ramp(Canvas.mix(gold, Color.WHITE, 0.1)), 0);
                c.layer().ellipse(21, 13, 5, 5).commit(Canvas.ramp(Canvas.mix(gold, Color.BLACK, 0.15)), 0);
                c.sparkle(25, 7, Color.WHITE);
            }
            case "auction" -> {
                c.layer().line(6, 27, 18, 15, 3).commit(Canvas.ramp(wood), 0.25);
                c.layer().poly(new double[]{14, 22, 28, 20}, new double[]{9, 3, 11, 17}).commit(Canvas.ramp(new Color(150, 104, 58)), 0.15);
                c.layer().rect(3, 26, 14, 4).commit(Canvas.ramp(dark), 0.1);
            }
            case "sell" -> {
                c.layer().poly(new double[]{4, 28, 16}, new double[]{6, 6, 20}).commit(Canvas.ramp(steel), 0.05);
                c.layer().ellipse(16, 25, 6, 4).commit(Canvas.ramp(gold), 0);
            }
            case "stat" -> {
                double[] xs = new double[10], ys = new double[10];
                for (int i = 0; i < 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? 14 : 6; xs[i] = 16 + Math.cos(a) * r; ys[i] = 17 + Math.sin(a) * r; }
                c.layer().poly(xs, ys).commit(Canvas.ramp(gold), 0);
            }
            case "map_known", "map_unknown" -> {
                boolean known = key.equals("map_known");
                c.layer().poly(new double[]{3, 11, 21, 29, 29, 21, 11, 3}, new double[]{6, 4, 6, 4, 26, 28, 26, 28}).commit(Canvas.ramp(known ? paper : new Color(100, 100, 112)), 0.15);
                if (known) {
                    c.layer().ellipse(11, 14, 5, 4).commit(Canvas.ramp(green), 0.2);
                    c.layer().ellipse(21, 20, 4, 3).commit(Canvas.ramp(blue), 0.1);
                    c.layer().line(20, 9, 24, 13, 1.6).line(24, 9, 20, 13, 1.6).commit(Canvas.ramp(red), 0);
                } else {
                    Canvas.Layer q = c.layer();
                    q.rect(13, 9, 6, 2).rect(18, 10, 2, 5).rect(15, 14, 4, 2).rect(15, 16, 2, 3).rect(15, 21, 2, 2);
                    q.commit(Canvas.ramp(paper), 0);
                }
            }
            case "member" -> {
                c.layer().ellipse(16, 10, 6, 6).commit(Canvas.ramp(skin), 0);
                c.layer().ellipse(16, 26, 11, 8).cutRect(0, 29, 32, 3).commit(Canvas.ramp(blue), 0.05);
            }
            case "reputation" -> {
                c.layer().poly(new double[]{5, 27, 27, 16, 5}, new double[]{4, 4, 17, 29, 17}).commit(Canvas.ramp(blue), 0.05);
                c.layer().poly(new double[]{16, 19, 16, 13}, new double[]{8, 15, 23, 15}).commit(Canvas.ramp(gold), 0);
            }
            case "arts" -> {
                c.layer().rect(6, 4, 21, 25).commit(Canvas.ramp(purple), 0.1);
                c.layer().rect(6, 4, 3, 25).commit(Canvas.ramp(gold), 0);
                double[] xs = new double[10], ys = new double[10];
                for (int i = 0; i < 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? 7 : 3; xs[i] = 17.5 + Math.cos(a) * r; ys[i] = 16 + Math.sin(a) * r; }
                c.layer().poly(xs, ys).commit(Canvas.ramp(gold), 0);
                c.sparkle(25, 6, new Color(255, 230, 255));
            }
            case "fieldboss" -> {
                c.layer().ellipse(16, 13, 11, 10).rect(9, 18, 14, 9).commit(Canvas.ramp(new Color(226, 218, 196)), 0.1);
                Color[] hole = Canvas.ramp(new Color(30, 20, 26));
                c.layer().ellipse(11.5, 14, 3, 3.2).ellipse(20.5, 14, 3, 3.2).commit(hole, 0);
                c.set(11, 14, new Color(255, 70, 50)); c.set(20, 14, new Color(255, 70, 50));
                c.layer().poly(new double[]{16, 14, 18}, new double[]{18, 21, 21}).commit(hole, 0);
                for (int x = 11; x < 22; x += 3) c.layer().rect(x, 24, 2, 3).commit(Canvas.ramp(new Color(240, 234, 216)), 0);
                c.layer().poly(new double[]{6, 3, 9}, new double[]{6, 0, 4}).poly(new double[]{26, 29, 23}, new double[]{6, 0, 4}).commit(Canvas.ramp(new Color(70, 40, 40)), 0);
            }
            case "appraise" -> {
                c.layer().line(4, 28, 13, 19, 3.4).commit(Canvas.ramp(wood), 0.2);
                c.layer().ellipse(19, 13, 10, 10).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(19, 13, 7.5, 7.5).commit(Canvas.ramp(new Color(160, 214, 250)), 0);
                c.set(16, 9, Color.WHITE); c.set(17, 9, Color.WHITE); c.set(16, 10, Color.WHITE);
            }
            case "bandage" -> {
                c.layer().ellipse(16, 16, 13, 9).commit(Canvas.ramp(new Color(236, 230, 214)), 0.1);
                c.layer().rect(14, 8, 4, 16).rect(8, 14, 16, 4).commit(Canvas.ramp(red), 0);
            }
            case "land" -> {
                c.layer().ellipse(16, 27, 15, 6).commit(Canvas.ramp(green), 0.25);
                c.layer().rect(10, 4, 2, 22).commit(Canvas.ramp(wood), 0.1);
                c.layer().poly(new double[]{12, 27, 12}, new double[]{4, 9, 15}).commit(Canvas.ramp(red), 0.05);
            }
            case "castle" -> {
                c.layer().rect(5, 12, 22, 17).rect(3, 7, 7, 22).rect(22, 7, 7, 22).commit(Canvas.ramp(steel), 0.3);
                for (int x = 3; x < 29; x += 4) c.layer().rect(x, 4, 2, 3).commit(Canvas.ramp(steel), 0.2);
                c.layer().ellipse(16, 22, 3.5, 4).rect(13, 22, 7, 7).commit(Canvas.ramp(dark), 0);
                c.layer().rect(15, 2, 1, 8).commit(Canvas.ramp(wood), 0);
                c.layer().poly(new double[]{16, 23, 16}, new double[]{2, 4, 6}).commit(Canvas.ramp(red), 0);
            }
            case "nation" -> {
                c.layer().rect(4, 16, 24, 10).poly(new double[]{4, 8, 12}, new double[]{16, 5, 16}).poly(new double[]{12, 16, 20}, new double[]{16, 3, 16})
                        .poly(new double[]{20, 24, 28}, new double[]{16, 5, 16}).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(10, 21, 2, 2).commit(Canvas.ramp(red), 0);
                c.layer().ellipse(16, 21, 2.2, 2.2).commit(Canvas.ramp(blue), 0);
                c.layer().ellipse(22, 21, 2, 2).commit(Canvas.ramp(green), 0);
            }
            case "party" -> {
                c.layer().ellipse(10, 9, 5, 5).ellipse(22, 9, 5, 5).commit(Canvas.ramp(skin), 0);
                c.layer().ellipse(10, 25, 8, 9).cutRect(0, 29, 32, 3).commit(Canvas.ramp(green), 0.05);
                c.layer().ellipse(22, 25, 8, 9).cutRect(0, 29, 32, 3).commit(Canvas.ramp(blue), 0.05);
            }
            case "trial" -> {
                c.layer().rect(15, 4, 3, 26).rect(6, 11, 21, 3).commit(Canvas.ramp(new Color(176, 136, 84)), 0.2);
                c.layer().ellipse(16.5, 7, 5, 4.5).commit(Canvas.ramp(new Color(220, 200, 150)), 0.2);
                c.layer().ellipse(16.5, 20, 5, 5).commit(Canvas.ramp(red), 0);
                c.layer().ellipse(16.5, 20, 2.4, 2.4).commit(Canvas.ramp(paper), 0);
            }
            case "gods" -> {
                for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; c.layer().line(16, 16, 16 + Math.cos(a) * 14, 16 + Math.sin(a) * 14, 2).commit(Canvas.ramp(gold), 0); }
                c.layer().ellipse(16, 16, 8, 8).commit(Canvas.ramp(new Color(255, 214, 90)), 0);
            }
            case "history" -> {
                c.layer().rect(4, 5, 24, 22).commit(Canvas.ramp(new Color(126, 62, 40)), 0.15);
                c.layer().rect(6, 7, 9, 18).rect(17, 7, 9, 18).commit(Canvas.ramp(paper), 0.05);
                for (int y = 10; y < 23; y += 3) { c.layer().rect(8, y, 6, 1).commit(Canvas.ramp(new Color(120, 100, 80)), 0); c.layer().rect(18, y, 6, 1).commit(Canvas.ramp(new Color(120, 100, 80)), 0); }
            }
            case "character" -> {
                c.layer().ellipse(16, 9, 6, 6.5).commit(Canvas.ramp(skin), 0);
                c.layer().rect(10, 3, 12, 4).commit(Canvas.ramp(new Color(90, 60, 40)), 0.2);
                c.layer().rect(9, 15, 14, 10).commit(Canvas.ramp(blue), 0.05);
                c.layer().rect(10, 25, 5, 5).rect(17, 25, 5, 5).commit(Canvas.ramp(dark), 0.1);
                c.layer().rect(9, 22, 14, 2).commit(Canvas.ramp(new Color(110, 70, 40)), 0);
            }
            case "job" -> {
                c.layer().rect(3, 14, 26, 14).commit(Canvas.ramp(wood), 0.25);
                c.layer().rect(3, 11, 26, 4).commit(Canvas.ramp(Canvas.mix(wood, Color.WHITE, 0.15)), 0.2);
                c.layer().line(8, 10, 14, 2, 2.2).commit(Canvas.ramp(steel), 0);
                c.layer().poly(new double[]{18, 26, 28, 20}, new double[]{4, 2, 8, 10}).commit(Canvas.ramp(steel), 0.05);
            }
            case "close" -> {
                c.layer().line(7, 7, 25, 25, 4.5).line(25, 7, 7, 25, 4.5).commit(Canvas.ramp(red), 0);
            }
            case "rumor" -> {   // 말풍선 + 물음표
                c.layer().ellipse(16, 13, 13, 10).poly(new double[]{8, 14, 6}, new double[]{19, 21, 28}).commit(Canvas.ramp(paper), 0.08);
                c.layer().line(13, 10, 16, 7.5, 2).line(16, 7.5, 19, 10, 2).line(19, 10, 16, 13.5, 2).line(16, 13.5, 16, 15.5, 2).commit(Canvas.ramp(blue), 0);
                c.layer().ellipse(16, 18.5, 1.3, 1.3).commit(Canvas.ramp(blue), 0);
            }
            case "train" -> {   // 펼친 책 위의 별
                c.layer().poly(new double[]{3, 16, 16, 3}, new double[]{12, 15, 28, 25}).commit(Canvas.ramp(paper), 0.1);
                c.layer().poly(new double[]{16, 29, 29, 16}, new double[]{15, 12, 25, 28}).commit(Canvas.ramp(Canvas.mix(paper, dark, 0.12)), 0.1);
                c.layer().line(16, 15, 16, 28, 1.2).commit(Canvas.ramp(wood), 0);
                c.layer().poly(new double[]{16, 18, 23, 19, 20.5, 16, 11.5, 13, 9, 14}, new double[]{2, 6.5, 7, 9.5, 14, 11.5, 14, 9.5, 7, 6.5}).commit(Canvas.ramp(gold), 0);
            }
            case "inn" -> {   // 침대
                c.layer().rect(4, 9, 3, 18).rect(25, 15, 3, 12).commit(Canvas.ramp(wood), 0.1);
                c.layer().rect(7, 18, 18, 6).commit(Canvas.ramp(red), 0.05);
                c.layer().ellipse(11, 15.5, 4, 2.6).commit(Canvas.ramp(paper), 0.05);
                c.layer().rect(7, 22, 18, 2).commit(Canvas.ramp(wood), 0);
                c.sparkle(22, 8, new Color(200, 210, 255));
            }
            case "heal" -> {   // 붉은 십자 물약
                c.layer().ellipse(16, 20, 9, 8).rect(13, 5, 6, 9).commit(Canvas.ramp(Canvas.mix(paper, blue, 0.2)), 0.05);
                c.layer().ellipse(16, 21, 7.5, 6).commit(Canvas.ramp(red), 0.05);
                c.layer().rect(15, 17, 2, 8).rect(12, 20, 8, 2).commit(Canvas.ramp(Color.WHITE), 0);
                c.layer().rect(12, 3, 8, 3).commit(Canvas.ramp(wood), 0);
            }
            case "repair" -> {   // 망치와 모루
                c.layer().poly(new double[]{5, 27, 24, 21, 11, 8}, new double[]{18, 18, 22, 22, 22, 22}).rect(12, 22, 8, 3).rect(9, 25, 14, 3).commit(Canvas.ramp(steel), 0.1);
                c.layer().line(10, 15, 22, 4, 2.2).commit(Canvas.ramp(wood), 0);
                c.layer().poly(new double[]{17, 24, 27, 20}, new double[]{4, 1, 6, 9}).commit(Canvas.ramp(dark), 0.05);
                c.sparkle(8, 14, new Color(255, 200, 90));
            }
            case "song" -> {   // 류트 + 음표
                c.layer().ellipse(12, 21, 8, 7).commit(Canvas.ramp(wood), 0.1);
                c.layer().ellipse(12, 21, 2, 2).commit(Canvas.ramp(dark), 0);
                c.layer().line(15, 17, 24, 6, 2.5).commit(Canvas.ramp(Canvas.mix(wood, dark, 0.3)), 0);
                c.layer().ellipse(24, 22, 2.5, 2).line(26, 22, 26, 12, 1.2).commit(Canvas.ramp(purple), 0);
            }
            case "people" -> {   // 세 사람
                c.layer().ellipse(9, 11, 3.5, 3.5).ellipse(23, 11, 3.5, 3.5).commit(Canvas.ramp(skin), 0.05);
                c.layer().ellipse(9, 23, 6, 6).ellipse(23, 23, 6, 6).cutRect(0, 27, 32, 5).commit(Canvas.ramp(blue), 0.05);
                c.layer().ellipse(16, 9, 4, 4).commit(Canvas.ramp(skin), 0.05);
                c.layer().ellipse(16, 23, 7, 7).cutRect(0, 28, 32, 4).commit(Canvas.ramp(green), 0.05);
            }
            case "achievement", "achievement_locked" -> {   // 별이 박힌 메달
                Color m = key.equals("achievement") ? gold : new Color(110, 110, 120);
                c.layer().poly(new double[]{10, 14, 16, 12}, new double[]{2, 2, 14, 14}).poly(new double[]{22, 18, 16, 20}, new double[]{2, 2, 14, 14}).commit(Canvas.ramp(key.equals("achievement") ? red : dark), 0.05);
                c.layer().ellipse(16, 20, 10, 10).commit(Canvas.ramp(m), 0.1);
                c.layer().poly(new double[]{16, 18.2, 23, 19.2, 20.5, 16, 11.5, 12.8, 9, 13.8}, new double[]{13, 17.6, 18, 21, 26, 23.2, 26, 21, 18, 17.6})
                        .commit(Canvas.ramp(key.equals("achievement") ? Canvas.mix(gold, Color.WHITE, 0.35) : new Color(150, 150, 160)), 0);
            }
            case "title" -> {   // 이름표 + 끈
                c.layer().poly(new double[]{4, 22, 29, 22, 4}, new double[]{9, 9, 16, 23, 23}).commit(Canvas.ramp(paper), 0.08);
                c.layer().ellipse(23.5, 16, 1.6, 1.6).commit(Canvas.ramp(dark), 0);
                c.layer().line(7, 13, 18, 13, 1.2).line(7, 17, 16, 17, 1.2).line(7, 20, 13, 20, 1.2).commit(Canvas.ramp(purple), 0);
                c.layer().line(25, 16, 30, 5, 1.2).commit(Canvas.ramp(red), 0);
            }
            case "record" -> {   // 가죽 장부 + 깃펜
                c.layer().rect(5, 4, 18, 25).commit(Canvas.ramp(new Color(110, 64, 40)), 0.15);
                c.layer().rect(7, 6, 14, 21).commit(Canvas.ramp(Canvas.mix(new Color(110, 64, 40), Color.WHITE, 0.15)), 0.1);
                c.layer().ellipse(14, 14, 4.5, 4.5).commit(Canvas.ramp(gold), 0);
                c.layer().line(20, 28, 29, 6, 1.4).commit(Canvas.ramp(paper), 0);
                c.layer().poly(new double[]{26, 31, 29}, new double[]{4, 2, 10}).commit(Canvas.ramp(Color.WHITE), 0);
            }
            case "pet" -> {   // 발바닥
                c.layer().ellipse(16, 21, 7.5, 6.5).commit(Canvas.ramp(new Color(150, 100, 70)), 0.1);
                c.layer().ellipse(7.5, 12, 3, 3.6).ellipse(13, 7, 3, 3.6).ellipse(19, 7, 3, 3.6).ellipse(24.5, 12, 3, 3.6).commit(Canvas.ramp(new Color(150, 100, 70)), 0.1);
            }
            case "mount" -> {   // 말 머리 옆모습
                c.layer().poly(new double[]{9, 15, 25, 28, 26, 20, 18, 12, 9}, new double[]{29, 10, 4, 9, 13, 14, 20, 29, 29}).commit(Canvas.ramp(new Color(140, 92, 52)), 0.12);
                c.layer().poly(new double[]{15, 18, 12, 9}, new double[]{10, 6, 18, 26}).commit(Canvas.ramp(dark), 0.05);
                c.layer().ellipse(22, 8, 1.2, 1.2).commit(Canvas.ramp(dark), 0);
                c.layer().line(20, 14, 26, 11, 1.2).commit(Canvas.ramp(gold), 0);
            }
            case "raid" -> {   // 용 머리 방패 + 엇갈린 검
                c.layer().line(5, 27, 27, 5, 2.2).line(27, 27, 5, 5, 2.2).commit(Canvas.ramp(steel), 0);
                c.layer().poly(new double[]{9, 23, 23, 16, 9}, new double[]{7, 7, 18, 27, 18}).commit(Canvas.ramp(purple), 0.1);
                c.layer().poly(new double[]{12, 16, 20, 18, 14}, new double[]{12, 9, 12, 19, 19}).commit(Canvas.ramp(gold), 0);
            }
            case "weather" -> {   // 해 + 구름 + 빗방울
                c.layer().ellipse(11, 11, 6, 6).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(17, 17, 7, 5).ellipse(23, 15, 6, 5).ellipse(12, 19, 5, 4).commit(Canvas.ramp(Canvas.mix(paper, blue, 0.15)), 0.08);
                c.layer().line(12, 25, 11, 29, 1.2).line(18, 25, 17, 29, 1.2).line(24, 24, 23, 28, 1.2).commit(Canvas.ramp(blue), 0);
            }
            case "vault" -> {   // 쇠테 두른 궤짝
                c.layer().rect(4, 12, 24, 15).commit(Canvas.ramp(wood), 0.15);
                c.layer().ellipse(16, 12, 12, 5).commit(Canvas.ramp(Canvas.mix(wood, Color.WHITE, 0.1)), 0.1);
                c.layer().rect(4, 15, 24, 2).rect(9, 7, 2, 20).rect(21, 7, 2, 20).commit(Canvas.ramp(steel), 0);
                c.layer().rect(14, 16, 4, 5).commit(Canvas.ramp(gold), 0);
            }
            case "gquest" -> {   // 깃발 + 두루마리
                c.layer().rect(6, 3, 2, 26).commit(Canvas.ramp(wood), 0);
                c.layer().poly(new double[]{8, 22, 18, 22, 8}, new double[]{4, 4, 9, 14, 14}).commit(Canvas.ramp(blue), 0.1);
                c.layer().rect(12, 18, 16, 10).commit(Canvas.ramp(paper), 0.08);
                c.layer().line(14, 21, 25, 21, 1).line(14, 24, 22, 24, 1).commit(Canvas.ramp(new Color(120, 100, 80)), 0);
            }
            case "carriage" -> {   // 마차
                c.layer().rect(5, 8, 19, 12).commit(Canvas.ramp(red), 0.1);
                c.layer().rect(8, 10, 5, 5).rect(15, 10, 5, 5).commit(Canvas.ramp(Canvas.mix(paper, blue, 0.3)), 0);
                c.layer().rect(3, 6, 23, 2).commit(Canvas.ramp(gold), 0);
                c.layer().ellipse(9, 24, 4.5, 4.5).ellipse(21, 24, 4.5, 4.5).commit(Canvas.ramp(wood), 0.1);
                c.layer().ellipse(9, 24, 1.3, 1.3).ellipse(21, 24, 1.3, 1.3).commit(Canvas.ramp(dark), 0);
                c.layer().line(24, 18, 30, 22, 1.2).commit(Canvas.ramp(wood), 0);
            }
            case "ship" -> {   // 돛배
                c.layer().poly(new double[]{3, 29, 25, 7}, new double[]{21, 21, 27, 27}).commit(Canvas.ramp(wood), 0.12);
                c.layer().rect(15, 3, 2, 18).commit(Canvas.ramp(Canvas.mix(wood, dark, 0.3)), 0);
                c.layer().poly(new double[]{17, 27, 17}, new double[]{4, 17, 17}).poly(new double[]{15, 15, 6}, new double[]{6, 18, 18}).commit(Canvas.ramp(paper), 0.08);
                c.layer().line(2, 29, 30, 29, 1.4).commit(Canvas.ramp(blue), 0);
            }
            case "sculpt" -> {   // 받침 위의 흉상 + 조각칼
                c.layer().rect(8, 23, 16, 6).commit(Canvas.ramp(new Color(130, 126, 120)), 0.12);
                c.layer().poly(new double[]{9, 23, 21, 11}, new double[]{23, 23, 17, 17}).commit(Canvas.ramp(new Color(230, 226, 218)), 0.08);
                c.layer().ellipse(16, 11, 5, 6).commit(Canvas.ramp(new Color(230, 226, 218)), 0.08);
                c.layer().line(24, 3, 29, 14, 1.4).commit(Canvas.ramp(steel), 0);
                c.layer().line(29, 14, 30, 17, 2).commit(Canvas.ramp(wood), 0);
            }
            default -> c.layer().rect(8, 8, 16, 16).commit(Canvas.ramp(gold), 0);
        }
        return c.image(true);
    }

    // ------------------------------------------------------------------ UI
    private void uiFont() {
        StringBuilder bitmaps = new StringBuilder();
        for (int rows = 1; rows <= 6; rows++) {   // 상자 창 높이 = 114 + 줄 × 18 (바닐라 배치)
            int h = 114 + rows * 18;
            png("assets/versaera/textures/ui/menu" + rows + ".png", panel(176, h, rows));
            bitmaps.append("{\"type\":\"bitmap\",\"file\":\"versaera:ui/menu").append(rows).append(".png\",\"ascent\":13,\"height\":").append(h)
                    .append(",\"chars\":[\"").append(esc(menuGlyph(rows))).append("\"]},");
        }
        text("assets/minecraft/font/default.json", "{\"providers\":[" + bitmaps
                + "{\"type\":\"space\",\"advances\":{\"" + esc(SHIFT_LEFT_8) + "\":-8,\"" + esc(SHIFT_LEFT_169) + "\":-169}}"
                + "]}");
    }

    private static String esc(char c) {
        return String.format("\\u%04x", (int) c);
    }

    /**
     * 메뉴 배경: 짙은 가죽 바탕 + 금빛 테두리(모서리 장식) + 제목 띠 + 칸 자리(우묵하게) — 상자 창 칸과 플레이어 인벤토리 칸 위치에 맞춘다.
     * 설명 문장은 그리지 않는다.
     */
    private static BufferedImage panel(int w, int h, int rows) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        SplittableRandom r = new SplittableRandom(rows * 7919L);
        Color leather = new Color(34, 28, 32), gold = new Color(206, 162, 54), goldDark = new Color(120, 86, 30), goldLight = new Color(250, 222, 140);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                // 가장자리로 갈수록 어두워지는 바탕 + 가죽 결
                double ex = Math.min(x, w - 1 - x) / 20.0, ey = Math.min(y, h - 1 - y) / 20.0, vig = Math.min(1, Math.min(ex, ey));
                int v = (int) (r.nextInt(-5, 6) + vig * 10 - 8);
                img.setRGB(x, y, new Color(c(leather.getRed() + v), c(leather.getGreen() + v), c(leather.getBlue() + v), 240).getRGB());
            }
        Graphics2D g = img.createGraphics();
        // 테두리: 바깥 어두운 금 · 금 · 안쪽 밝은 금 한 줄
        g.setColor(goldDark); g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(gold); g.drawRect(1, 1, w - 3, h - 3); g.drawRect(2, 2, w - 5, h - 5);
        g.setColor(goldLight); g.drawLine(2, 2, w - 3, 2); g.drawLine(2, 2, 2, h - 3);
        g.setColor(goldDark); g.drawRect(3, 3, w - 7, h - 7);
        // 모서리 장식 (마름모 + 점)
        for (int[] p : new int[][]{{0, 0}, {w - 11, 0}, {0, h - 11}, {w - 11, h - 11}}) {
            g.setColor(gold); g.fillPolygon(new int[]{p[0] + 5, p[0] + 10, p[0] + 5, p[0]}, new int[]{p[1], p[1] + 5, p[1] + 10, p[1] + 5}, 4);
            g.setColor(goldLight); g.fillRect(p[0] + 4, p[1] + 3, 2, 2);
            g.setColor(new Color(150, 30, 40)); g.fillRect(p[0] + 4, p[1] + 5, 2, 2);
        }
        // 제목 띠 아래 금줄 (제목 글자는 y≈6)
        g.setColor(goldDark); g.drawLine(8, 15, w - 9, 15);
        g.setColor(gold); g.drawLine(8, 16, w - 9, 16);
        int inv = 18 + rows * 18 + 13;
        g.setColor(goldDark); g.drawLine(8, inv - 5, w - 9, inv - 5);
        g.setColor(gold); g.drawLine(8, inv - 4, w - 9, inv - 4);
        // 칸: 상자 칸 rows 줄 + 인벤토리 3줄 + 단축 1줄
        for (int row = 0; row < rows; row++) for (int col = 0; col < 9; col++) slot(img, 7 + col * 18, 17 + row * 18);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(img, 7 + col * 18, inv + row * 18 - 1);
        for (int col = 0; col < 9; col++) slot(img, 7 + col * 18, inv + 58 - 1);
        g.dispose();
        return img;
    }

    /** 우묵한 칸 18×18: 위 · 왼쪽은 그늘, 아래 · 오른쪽은 빛 */
    private static void slot(BufferedImage img, int x, int y) {
        Color in = new Color(18, 14, 18, 245), shadow = new Color(8, 6, 8), light = new Color(92, 78, 70);
        for (int j = 0; j < 18; j++)
            for (int i = 0; i < 18; i++) {
                int px = x + i, py = y + j;
                if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) continue;
                Color col = (i == 0 || j == 0) ? shadow : (i == 17 || j == 17) ? light : in;
                img.setRGB(px, py, col.getRGB());
            }
    }

    private static int c(int v) {
        return Math.max(0, Math.min(255, v));
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
            byte[] zip = PackProtector.write(files);   // 게임은 읽고 압축 프로그램은 못 여는 zip (RP-02)
            return new Pack(zip, MessageDigest.getInstance("SHA-1").digest(zip), Map.copyOf(models));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
