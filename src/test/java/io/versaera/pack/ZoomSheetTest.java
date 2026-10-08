package io.versaera.pack;

import io.versaera.content.ContentBundle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** 확인용: 고른 장비 아이콘을 3 배로 (build/zoom-items.png) */
class ZoomSheetTest {
    @Test
    void zoom() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        List<String> ids = List.of("hard_iron_sword", "red_star", "roa_masterpiece", "coldrim_demon_sword", "lu_divine_sword", "chaos_axe", "dragon_slaying_axe", "sealed_thunder_spear",
                "saint_staff", "yerika_bow", "talok_armor", "goddess_knight_armor", "conqueror_leather", "van_hawk_helm", "kubicha_boots", "ring_of_extinction",
                "death_knight_necklace", "baharan_bracelet", "dimension_gloves", "space_cloak", "great_victor_belt", "ancient_shield", "herein_cup", "necromancer_tome");
        int cols = 6, cell = 200;
        BufferedImage out = new BufferedImage(cols * cell, (ids.size() + cols - 1) / cols * cell, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, 0xff2c2a34);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            BufferedImage img = PixelArt.item(c.items().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow());
            int k = 192 / img.getWidth(), ox = (i % cols) * cell + 4, oy = (i / cols) * cell + 4;
            for (int y = 0; y < img.getHeight() * k; y++) for (int x = 0; x < img.getWidth() * k; x++) {
                int p = img.getRGB(x / k, y / k);
                int a = p >>> 24;
                if (a == 0) continue;
                int bg = out.getRGB(ox + x, oy + y);
                int r = (((p >> 16) & 255) * a + ((bg >> 16) & 255) * (255 - a)) / 255, gg = (((p >> 8) & 255) * a + ((bg >> 8) & 255) * (255 - a)) / 255,
                        bb = ((p & 255) * a + (bg & 255) * (255 - a)) / 255;
                out.setRGB(ox + x, oy + y, 0xff000000 | (r << 16) | (gg << 8) | bb);
            }
        }
        ImageIO.write(out, "png", new File("build/zoom-items.png"));
    }
}
