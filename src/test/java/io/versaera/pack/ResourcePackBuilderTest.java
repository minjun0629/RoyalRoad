package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.pack.PackIds;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackBuilderTest {
    private static Map<String, byte[]> unzip(byte[] zip) throws Exception {
        Map<String, byte[]> m = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) m.put(e.getName(), z.readAllBytes());
        }
        return m;
    }


    /** 모델의 상자 좌표(from · to)만 — uv · 회전 각도는 빼고 */
    private static java.util.List<Double> boxCoords(String model) {
        java.util.List<Double> out = new java.util.ArrayList<>();
        var m = java.util.regex.Pattern.compile("\"(?:from|to)\":\\[([^\\]]*)\\]").matcher(model);
        while (m.find()) for (String v : m.group(1).split(",")) out.add(Double.parseDouble(v));
        return out;
    }

    @Test
    void packHasModelsForEveryBossAndIsDeterministic() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        var a = ResourcePackBuilder.build(c);
        var b = ResourcePackBuilder.build(c);
        assertArrayEquals(a.sha1(), b.sha1(), "같은 콘텐츠 = 같은 팩");
        assertEquals(40, a.sha1Hex().length());
        assertTrue(a.zip().length < 1_000_000, "팩이 가볍다: " + a.zip().length);
        Map<String, byte[]> files = unzip(a.zip());
        String meta = new String(files.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(meta.contains("\"pack_format\":15"));
        String paper = new String(files.get("assets/minecraft/models/item/paper.json"), StandardCharsets.UTF_8);
        var ids = new HashSet<Integer>();
        for (var boss : c.bosses()) {
            assertNotNull(files.get("assets/versaera/models/" + boss.model() + ".json"), boss.id());
            byte[] png = files.get("assets/versaera/textures/boss/" + boss.model().replace('/', '_') + ".png");
            assertNotNull(ImageIO.read(new ByteArrayInputStream(png)), "텍스처가 실제 PNG");
            int id = PackIds.modelData(boss.model());
            assertTrue(ids.add(id), "모델 번호가 겹치지 않는다");
            assertTrue(paper.contains("\"custom_model_data\":" + id));
            String model = new String(files.get("assets/versaera/models/" + boss.model() + ".json"), StandardCharsets.UTF_8);
            for (double v : boxCoords(model)) assertTrue(v >= -16 && v <= 32, "모델 좌표는 -16 ~ 32: " + v);
        }
        assertNotNull(ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/ui/menu6.png"))));
        for (String key : ResourcePackBuilder.UI_ICONS) {
            var img = ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/ui/" + key + ".png")));
            assertEquals(32, img.getWidth(), key);
            int opaque = 0;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
            assertTrue(opaque >= 20, "아이콘이 비어 있지 않다: " + key);
            int id = PackIds.modelData("ui/" + key);
            assertTrue(ids.add(id), "UI 아이콘 번호가 보스 모델과 겹치지 않는다: " + key);
            assertTrue(paper.contains("\"custom_model_data\":" + id), key);
        }
        assertTrue(new String(files.get("assets/minecraft/font/default.json"), StandardCharsets.UTF_8).contains("\"type\":\"space\""));
    }

    @Test
    void everyItemBossAndArmorLookHasAModel() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        var pack = ResourcePackBuilder.build(c);
        Map<String, byte[]> files = unzip(pack.zip());
        var ids = new HashSet<Integer>(pack.models().values());
        assertEquals(pack.models().size(), ids.size(), "모델 번호가 모두 다르다");
        for (var t : c.items()) {
            if (!ResourcePackBuilder.modeled(t)) continue;
            String model = new String(files.get("assets/versaera/models/item/" + t.id() + ".json"), StandardCharsets.UTF_8);
            boolean solid = Sculpt.of(t) != null;
            assertTrue(model.contains(solid ? "versaera:item3d/" + t.id() : "versaera:item/" + t.id()), t.id());
            if (solid) {   // 입체 모델: 상자 좌표는 -16 ~ 32, 회전은 Minecraft 가 허용하는 각도만
                var nums = java.util.regex.Pattern.compile("\"(?:from|to)\":\\[([^\\]]*)\\]").matcher(model);
                int n = 0;
                while (nums.find()) for (String v : nums.group(1).split(",")) { double d = Double.parseDouble(v); assertTrue(d >= -16 && d <= 32, t.id() + " " + d); n++; }
                assertTrue(n >= 6, "모델에 상자가 있다: " + t.id());
                var ang = java.util.regex.Pattern.compile("\"angle\":(-?[0-9.]+)").matcher(model);
                while (ang.find()) assertTrue(java.util.Set.of("-45", "-22.5", "0", "22.5", "45").contains(ang.group(1)), t.id() + " angle " + ang.group(1));
                assertTrue(model.contains("\"display\""), "손 · 아이콘 표시 변환: " + t.id());
            }
            var img = ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/item/" + t.id() + ".png")));
            assertEquals(32, img.getWidth(), t.id());
            int opaque = 0;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if ((img.getRGB(x, y) >>> 24) != 0) opaque++;
            assertTrue(opaque >= 60, "아이콘이 비어 있지 않다: " + t.id());
            byte[] vanilla = files.get("assets/minecraft/models/item/" + t.material().toLowerCase(java.util.Locale.ROOT) + ".json");
            assertNotNull(vanilla, "바닐라 모델 덮어쓰기: " + t.material());
            String v = new String(vanilla, StandardCharsets.UTF_8);
            assertTrue(v.contains("\"custom_model_data\":" + PackIds.item(t.id()) + "}"), t.id());
            // overrides 의 custom_model_data 는 오름차순 (마지막으로 맞는 것이 쓰인다)
            var m = java.util.regex.Pattern.compile("custom_model_data\":(\\d+)").matcher(v);
            int last = -1;
            while (m.find()) {
                int n = Integer.parseInt(m.group(1));
                assertTrue(n > last, "오름차순: " + t.material());
                last = n;
            }
            assertEquals(v.chars().filter(ch -> ch == '{').count(), v.chars().filter(ch -> ch == '}').count(), "괄호 짝: " + t.material());
        }
        assertTrue(new String(files.get("assets/minecraft/models/item/bow.json"), StandardCharsets.UTF_8).contains("bow_pulling_2"), "활 당기기 모습은 그대로");
        for (var fb : c.fieldBosses()) for (var part : ModelKit.rig(fb.look())) {
            String model = new String(files.get("assets/versaera/models/fboss/" + fb.id() + "/" + part.name() + ".json"), StandardCharsets.UTF_8);
            for (double val : boxCoords(model)) assertTrue(val >= -16 && val <= 32, "모델 좌표는 -16 ~ 32: " + fb.id() + " " + val);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/fboss/" + fb.id() + ".png"))));
            assertTrue(pack.models().containsKey("fboss/" + fb.id() + "/" + part.name()));
        }
        assertNotNull(files.get("assets/versaera/models/mob/iron_mask.json"));
        // 입체 투구: 투구 재질이 아니어야 머리에 3D 모델이 보인다
        for (String h : List.of("graham_helm", "talok_helm", "van_hawk_helm", "emperor_crown")) {
            var t = c.items().stream().filter(x -> x.id().equals(h)).findFirst().orElseThrow();
            assertFalse(t.material().endsWith("_HELMET"), h);
            assertNotNull(Sculpt.of(t), h);
        }
        // 입은 갑옷: 모습마다 무늬 텍스처 2장 + 아틀라스 + 데이터팩 무늬
        var looks = new java.util.TreeSet<>(ArmorLooks.looks(c.items()).values());
        assertTrue(looks.contains("graham") && looks.contains("talok"), "세트는 한 모습");
        String atlas = new String(files.get("assets/minecraft/atlases/armor_trims.json"), StandardCharsets.UTF_8);
        Map<String, byte[]> dp = ArmorLooks.datapack(c);
        for (String l : looks) {
            var img = ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/trims/models/armor/" + l + ".png")));
            assertEquals(128, img.getWidth(), "갑옷 그림은 2배 해상도");
            assertNotNull(files.get("assets/versaera/textures/trims/models/armor/" + l + "_leggings.png"));
            assertTrue(atlas.contains("versaera:trims/models/armor/" + l + "\""), l);
            String pat = new String(dp.get("data/versaera/trim_pattern/" + l + ".json"), StandardCharsets.UTF_8);
            assertTrue(pat.contains("\"asset_id\":\"versaera:" + l + "\""), l);
        }
        assertTrue(atlas.contains("iron_darker"), "철 갑옷에 철 장식 = iron_darker 팔레트");
        assertTrue(new String(dp.get("pack.mcmeta"), StandardCharsets.UTF_8).contains("\"pack_format\":15"));
        assertTrue(pack.zip().length < 1_000_000, "팩이 가볍다: " + pack.zip().length);
    }
}
