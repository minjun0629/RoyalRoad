package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.pack.PackIds;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
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
            for (String num : model.replaceAll("[^0-9.,\\-\\[\\]]", " ").split("[\\[\\], ]+"))
                if (!num.isBlank() && num.matches("-?[0-9.]+")) {
                    double v = Double.parseDouble(num);
                    assertTrue(v >= -16 && v <= 32, "모델 좌표는 -16 ~ 32: " + v);
                }
        }
        assertNotNull(ImageIO.read(new ByteArrayInputStream(files.get("assets/versaera/textures/ui/menu6.png"))));
        assertTrue(new String(files.get("assets/minecraft/font/default.json"), StandardCharsets.UTF_8).contains("\"type\":\"space\""));
    }
}
