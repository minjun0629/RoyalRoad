package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.terrain.TerrainModel;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class TerrainModelTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
    private final RegionIndex regions = new RegionIndex(c.regions());
    private final TerrainModel t = new TerrainModel(regions, "world", 42);

    private double avg(int x1, int z1, int x2, int z2) {
        double sum = 0;
        int n = 0;
        for (int x = x1; x <= x2; x += 16) for (int z = z1; z <= z2; z += 16) { sum += t.height(x, z); n++; }
        return sum / n;
    }

    private double spread(int x1, int z1, int x2, int z2) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int x = x1; x <= x2; x += 8) for (int z = z1; z <= z2; z += 8) { int h = t.height(x, z); min = Math.min(min, h); max = Math.max(max, h); }
        return max - min;
    }

    @Test
    void regionsShapeTheLand() {
        assertTrue(avg(5700, -500, 5950, 500) < TerrainModel.SEA_LEVEL - 10, "동쪽 바다는 해수면 아래");
        assertTrue(spread(-200, -200, 200, 200) <= 6, "도시(하르덴)는 거의 평평");
        assertTrue(avg(-3500, -4500, -2500, -3500) > avg(-1500, 800, 300, 2000) + 20, "산(깊은 망치)이 들판보다 높다");
        assertTrue(avg(1650, 850, 1750, 950) < avg(1250, 450, 1300, 500) - 20, "분화구 가운데가 꺼져 있다");
        assertEquals(TerrainModel.Surface.SAND, t.surface(0, 5200, t.height(0, 5200)).name().equals("RED_SAND") ? TerrainModel.Surface.SAND : t.surface(0, 5200, t.height(0, 5200)));
        assertEquals(TerrainModel.Surface.SNOW, t.surface(0, -5500, 80));
    }

    @Test
    void deterministicPerSeedAndSmoothAcrossBorders() {
        TerrainModel same = new TerrainModel(regions, "world", 42), other = new TerrainModel(regions, "world", 7);
        int diff = 0;
        for (int i = 0; i < 200; i++) {
            int x = i * 37 - 3000, z = i * 53 - 4000;
            assertEquals(t.height(x, z), same.height(x, z));
            if (t.height(x, z) != other.height(x, z)) diff++;
        }
        assertTrue(diff > 100, "다른 시드는 다른 지형");
        // 경계를 건너도 한 블록에 급경사(절벽 > 6)가 거의 없다
        int cliffs = 0;
        for (int x = -2600; x < 2600; x += 3) if (Math.abs(t.height(x, -2000) - t.height(x + 1, -2000)) > 6) cliffs++;
        assertTrue(cliffs < 5, "경계가 부드럽다: " + cliffs);
    }

    @Test
    void ruinsGetPillarsOnlyInRuins() {
        int inRuins = 0;
        for (int cx = -12; cx < 6; cx++) for (int cz = 37; cz < 53; cz++) if (t.ruinPillar(cx, cz) != null) inRuins++;
        assertTrue(inRuins > 20, "칼라모르 유적에는 기둥이 있다: " + inRuins);
        for (int cx = -4; cx < 4; cx++) for (int cz = -4; cz < 4; cz++) assertNull(t.ruinPillar(cx, cz), "도시에는 없다");
    }

    @Test
    void writesPreviewImage() throws Exception {
        int size = 300, span = 12_000;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                int x = -6000 + px * span / size, z = -6000 + py * span / size, h = t.height(x, z);
                int rgb;
                if (h < TerrainModel.SEA_LEVEL) rgb = 0x1f4f8f;
                else rgb = switch (t.surface(x, z, h)) {
                    case SAND -> 0xd8c58a;
                    case RED_SAND -> 0xc0703a;
                    case SNOW -> 0xf0f4f8;
                    case STONE -> 0x8a8a8a;
                    case PODZOL -> 0x4f6b2f;
                    case BASALT -> 0x3a3640;
                    case DIRT_PATH -> 0xb08b5a;
                    case GRAVEL -> 0x9a948c;
                    default -> 0x6aa84f;
                };
                int shade = Math.max(-40, Math.min(40, (h - 70)));
                int r = Math.max(0, Math.min(255, ((rgb >> 16) & 255) + shade)), g = Math.max(0, Math.min(255, ((rgb >> 8) & 255) + shade)),
                        b = Math.max(0, Math.min(255, (rgb & 255) + shade));
                img.setRGB(px, py, (r << 16) | (g << 8) | b);
            }
        File out = new File("build/world-preview.png");
        out.getParentFile().mkdirs();
        assertTrue(ImageIO.write(img, "png", out));
        assertTrue(out.length() > 10_000);
    }
}
