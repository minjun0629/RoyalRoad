package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.item.ItemType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 원작 아이템 그림: 장신구 · 물건마다 제 모양 (뭉치 그림 "blob" 이 아니다), 한 장에 모아 build/canon-items.png 로 */
class CanonItemSheetTest {
    @Test
    void canonItemsHaveTheirOwnShapes() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        List<ItemType> canon = c.items().stream().filter(t -> "CANON".equals(t.source())).toList();
        for (ItemType t : canon) assertNotEquals("blob", PixelArt.kind(t), t.id() + " 모양이 없다");
        int cols = 10, rows = (canon.size() + cols - 1) / cols, cell = 102;
        BufferedImage sheet = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < canon.size(); i++) {
            BufferedImage img = PixelArt.item(canon.get(i));
            int ox = (i % cols) * cell + 3, oy = (i / cols) * cell + 3;
            for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) sheet.setRGB(ox + x, oy + y, 0xff3a3a46 | 0);
            int k = 96 / img.getWidth();
            if (k == 0) k = 1;
            for (int y = 0; y < img.getHeight() * k && y < 96; y++) for (int x = 0; x < img.getWidth() * k && x < 96; x++) {
                int p = img.getRGB(x / k, y / k);
                if ((p >>> 24) != 0) sheet.setRGB(ox + x, oy + y, p);
            }
        }
        File out = new File("build/canon-items.png");
        out.getParentFile().mkdirs();
        assertTrue(ImageIO.write(sheet, "png", out));
    }
}
