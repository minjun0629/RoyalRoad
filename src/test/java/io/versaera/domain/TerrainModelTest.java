package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.terrain.TerrainModel;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static io.versaera.Scale.s;
import static org.junit.jupiter.api.Assertions.*;

class TerrainModelTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
    private final RegionIndex regions = new RegionIndex(c.regions());
    private final TerrainModel t = new TerrainModel(regions, "world", 42);

    /** 지역 안 표본 중 해수면 아래인 몫 (휜 해안 · 섬이 있어 상자 하나로는 재지 않는다) */
    private double wetShare(String id) {
        var r = regions.byId(id);
        int wet = 0, n = 0;
        for (int z = r.minZ(); z <= r.maxZ(); z += Math.max(1, (r.maxZ() - r.minZ()) / 30))
            for (int x = r.minX(); x <= r.maxX(); x += Math.max(1, (r.maxX() - r.minX()) / 30), n++)
                if (t.height(x, z) < TerrainModel.SEA_LEVEL) wet++;
        return wet / (double) n;
    }

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
        assertTrue(wetShare("eastern_sea") > 0.5, "동쪽 바다(좁은 해협)는 절반 넘게 해수면 아래 (섬 · 휜 해안 빼고)");
        var harden = regions.byId("harden");
        int hx = (harden.minX() + harden.maxX()) / 2, hz = (harden.minZ() + harden.maxZ()) / 2;
        assertTrue(spread(hx - 120, hz - 120, hx + 120, hz + 120) <= 6, "도시(하르덴) 성벽 안은 거의 평평");
        assertTrue(avg(s(-1500), s(-3100), s(-500), s(-2500)) > avg(s(-2900), s(1600), s(-2000), s(2300)) + 20, "토르의 울타 산맥이 네스트 들판보다 높다");
        assertTrue(avg(s(3250), s(1000), s(3450), s(3000)) > avg(s(-600), s(-600), s(-200), s(0)) + 15, "바로크 산맥이 하벤 들판보다 높다");
        assertTrue(avg(s(4600), s(1300), s(4650), s(1350)) < avg(s(4405), s(1105), s(4440), s(1140)) - 20, "분화구 가운데가 꺼져 있다");
        assertEquals(TerrainModel.Surface.SAND, t.surface(s(0), s(5200), t.height(s(0), s(5200))).name().equals("RED_SAND") ? TerrainModel.Surface.SAND : t.surface(s(0), s(5200), t.height(s(0), s(5200))));
        assertEquals(TerrainModel.Surface.SNOW, t.surface(s(0), s(-5500), 80));
        assertTrue(t.height(s(4650), s(2725)) < TerrainModel.SEA_LEVEL, "자작나무 호수에는 물이 찬다");
        assertTrue(avg(s(5100), s(-1400), s(5150), s(-1000)) < avg(s(5000), s(1000), s(5300), s(1400)) - 15, "유노프 협곡은 깊다");
        assertTrue(t.height(s(-5300), s(-2000)) < t.height(s(-5600), s(-1500)) - 60, "엠비뉴의 성지는 거대한 구멍");
        assertTrue(t.dry(s(-5300), s(-2000)) && !t.dry(s(-1000), s(0)), "구멍에는 물이 차지 않는다");
        assertEquals(TerrainModel.Surface.MUD, t.surface(s(150), s(-2200), t.height(s(150), s(-2200))), "썩은 거품의 늪은 진흙");
        assertTrue(wetShare("southern_sea") > 0.5 && wetShare("south_continent") < 0.5, "남쪽 바다 건너 남쪽 대륙");
        assertEquals(TerrainModel.Surface.SNOW, t.surface(s(0), s(7800), t.height(s(0), s(7800))), "남극은 눈");
        assertEquals(TerrainModel.Surface.SNOW, t.surface(s(-2500), s(4600), t.height(s(-2500), s(4600))), "하얀 소금 평원");
        TerrainModel realms = new TerrainModel(regions, "versa_realms", 42);
        assertTrue(realms.height(-1600, -1000) > realms.height(-4600, -2400) + 40, "신계는 거인계보다 높은 곳");
        assertTrue(realms.height(0, -1600) < TerrainModel.SEA_LEVEL, "차원 사이는 빈 바다");
        assertTrue(t.height(s(1900), s(-5650)) > t.height(s(1250), s(-5400)) + 50, "지골라스는 솟은 화산");
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
        for (int z : new int[]{s(-2000), s(-800), s(700), s(2400)})   // 하벤 · 브리튼 · 바로크 산맥 · 브렌트 · 로자임 경계를 가로지름
            for (int x = s(-5000); x < s(5500); x += 3, checked++) if (Math.abs(t.height(x, z) - t.height(x + 1, z)) > 6) cliffs++;
        // 50000 판 · 휜 해안: 남는 급경사는 산 · 황무지 노이즈와 섬 가장자리에 흩어져 있다 (약 0.4%)
        assertTrue(cliffs < checked / 200, "경계가 부드럽다: " + cliffs + "/" + checked);
    }

    @Test
    void ruinsGetPillarsOnlyInRuins() {
        int inRuins = 0;
        var ruins = regions.byId("calamor_ruins");
        for (int cx = ruins.minX() >> 6; cx < ruins.maxX() >> 6; cx++) for (int cz = ruins.minZ() >> 6; cz < ruins.maxZ() >> 6; cz++) if (t.ruinPillar(cx, cz) != null) inRuins++;
        assertTrue(inRuins > 20, "칼라모르 유적에는 기둥이 있다: " + inRuins);
        var harden = regions.byId("harden");
        for (int cx = harden.minX() >> 6; cx < harden.maxX() >> 6; cx++) for (int cz = harden.minZ() >> 6; cz < harden.maxZ() >> 6; cz++) assertNull(t.ruinPillar(cx, cz), "도시(하르덴)에는 없다");
    }

    @Test
    void writesPreviewImage() throws Exception {
        int size = 500, span = 50_000;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < size; py++)
            for (int px = 0; px < size; px++) {
                int x = -25000 + px * span / size, z = -25000 + py * span / size, h = t.height(x, z);
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
