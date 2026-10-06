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
        assertTrue(avg(5880, -500, 5990, 500) < TerrainModel.SEA_LEVEL - 10, "동쪽 바다는 해수면 아래");
        assertTrue(spread(-1200, -200, -800, 200) <= 6, "도시(하르덴)는 거의 평평");
        assertTrue(avg(-1500, -3100, -500, -2500) > avg(-2900, 1600, -2000, 2300) + 20, "토르의 울타 산맥이 네스트 들판보다 높다");
        assertTrue(avg(3250, 1000, 3450, 3000) > avg(-600, -600, -200, 0) + 15, "바로크 산맥이 하벤 들판보다 높다");
        assertTrue(avg(4600, 1300, 4650, 1350) < avg(4405, 1105, 4440, 1140) - 20, "분화구 가운데가 꺼져 있다");
        assertEquals(TerrainModel.Surface.SAND, t.surface(0, 5200, t.height(0, 5200)).name().equals("RED_SAND") ? TerrainModel.Surface.SAND : t.surface(0, 5200, t.height(0, 5200)));
        assertEquals(TerrainModel.Surface.SNOW, t.surface(0, -5500, 80));
        assertTrue(t.height(4650, 2725) < TerrainModel.SEA_LEVEL, "자작나무 호수에는 물이 찬다");
        assertTrue(avg(5100, -1400, 5150, -1000) < avg(5000, 1000, 5300, 1400) - 15, "유노프 협곡은 깊다");
        assertTrue(t.height(-5300, -2000) < t.height(-5600, -1500) - 60, "엠비뉴의 성지는 거대한 구멍");
        assertTrue(t.dry(-5300, -2000) && !t.dry(-1000, 0), "구멍에는 물이 차지 않는다");
        assertEquals(TerrainModel.Surface.MUD, t.surface(150, -2200, t.height(150, -2200)), "썩은 거품의 늪은 진흙");
        assertTrue(t.height(1900, -5650) > t.height(1250, -5400) + 50, "지골라스는 솟은 화산");
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
        int cliffs = 0, checked = 0;
        for (int z : new int[]{-2000, -800, 700, 2400})   // 하벤 · 브리튼 · 바로크 산맥 · 브렌트 · 로자임 경계를 가로지름
            for (int x = -5000; x < 5500; x += 3, checked++) if (Math.abs(t.height(x, z) - t.height(x + 1, z)) > 6) cliffs++;
        assertTrue(cliffs < checked / 500, "경계가 부드럽다: " + cliffs + "/" + checked);
    }

    @Test
    void ruinsGetPillarsOnlyInRuins() {
        int inRuins = 0;
        for (int cx = -53; cx < -43; cx++) for (int cz = 0; cz < 11; cz++) if (t.ruinPillar(cx, cz) != null) inRuins++;
        assertTrue(inRuins > 20, "칼라모르 유적에는 기둥이 있다: " + inRuins);
        for (int cx = -20; cx < -11; cx++) for (int cz = -4; cz < 4; cz++) assertNull(t.ruinPillar(cx, cz), "도시(하르덴)에는 없다");
    }

    @Test
    void writesPreviewImage() throws Exception {
        int size = 300, span = 12_000;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                int x = -6000 + px * span / size, z = -6000 + py * span / size, h = t.height(x, z);
                int rgb;
                if (h < TerrainModel.SEA_LEVEL && !t.dry(x, z)) rgb = 0x1f4f8f;
                else rgb = switch (t.surface(x, z, h)) {
                    case SAND -> 0xd8c58a;
                    case RED_SAND -> 0xc0703a;
                    case SNOW -> 0xf0f4f8;
                    case STONE -> 0x8a8a8a;
                    case PODZOL -> 0x4f6b2f;
                    case BASALT -> 0x3a3640;
                    case DIRT_PATH -> 0xb08b5a;
                    case GRAVEL -> 0x9a948c;
                    case MUD -> 0x4a3f35;
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
