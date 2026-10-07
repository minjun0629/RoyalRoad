package io.versaera.pack;

import io.versaera.content.ContentBundle;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** 수련용 목검 · 활은 진짜 무기와 다른 그림 (쇠 칼날 · 놋쇠 장식이 아니라 나무 · 끈) */
class PracticeIconPreviewTest {
    @Test
    void practiceWeaponsLookWooden() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        BufferedImage sw = PixelArt.item(item(c, "practice_sword")), bow = PixelArt.item(item(c, "practice_bow"));
        var realSword = c.items().stream().filter(t -> !t.hasTag("practice") && PixelArt.kind(t).equals("sword")).findFirst().orElseThrow();
        var realBow = c.items().stream().filter(t -> !t.hasTag("practice") && PixelArt.kind(t).equals("bow")).findFirst().orElseThrow();
        assertFalse(same(sw, PixelArt.item(realSword)), "목검 = 쇠 검 그림");
        assertFalse(same(bow, PixelArt.item(realBow)), "수련용 활 = 보통 활 그림");
        File dir = new File("build/practice-icons");
        dir.mkdirs();
        for (var e : java.util.Map.of("practice_sword", sw, "practice_bow", bow, "real_sword", PixelArt.item(realSword), "real_bow", PixelArt.item(realBow)).entrySet()) {
            BufferedImage big = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) big.setRGB(x, y, e.getValue().getRGB(x / 8, y / 8));
            ImageIO.write(big, "png", new File(dir, e.getKey() + ".png"));
        }
    }

    private static io.versaera.domain.item.ItemType item(ContentBundle c, String id) {
        return c.items().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }
}
