package io.versaera.pack;

import io.versaera.content.ContentBundle;
import io.versaera.domain.item.ItemType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 확인용: 인벤토리 카드 (build/cards.png) · 손에 든 입체 모델을 비스듬히 본 모습 (build/models3d.png) */
class CardAnd3DPreviewTest {
    static final List<String> IDS = List.of("hard_iron_sword", "red_star", "roa_masterpiece", "coldrim_demon_sword", "lu_divine_sword", "dragon_slaying_axe",
            "sealed_thunder_spear", "saint_staff", "yerika_bow", "talok_armor", "conqueror_leather", "kubicha_boots", "ring_of_extinction", "death_knight_necklace",
            "dimension_gloves", "space_cloak", "ancient_shield", "herein_cup");

    private static ItemType item(ContentBundle c, String id) {
        return c.items().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void cardsAndModels() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        int cols = 6, cell = 200;
        BufferedImage cards = new BufferedImage(cols * cell, 3 * cell, BufferedImage.TYPE_INT_ARGB), models = new BufferedImage(cols * cell, 3 * cell, BufferedImage.TYPE_INT_ARGB);
        for (BufferedImage b : List.of(cards, models)) for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) b.setRGB(x, y, 0xff3c3c46);
        for (int i = 0; i < IDS.size(); i++) {
            ItemType t = item(c, IDS.get(i));
            String kind = PixelArt.kind(t);
            MmoIcon.Drawn d = MmoIcon.render(t, kind);
            VoxelSmith.Build v0 = VoxelSmith.handles(kind) ? VoxelSmith.build(t, kind, d.look()) : null;
            BufferedImage card = MmoCard.card(t, kind, v0 == null ? d : new MmoIcon.Drawn(VoxelRender.icon(v0, 52), d.look(), d.grade()));
            assertEquals(64, card.getWidth());
            int ox = (i % cols) * cell + 4, oy = (i / cols) * cell + 4;
            for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) cards.setRGB(ox + x, oy + y, card.getRGB(x / 3, y / 3) | 0xff000000);
            VoxelSmith.Build vox = VoxelSmith.handles(kind) ? VoxelSmith.build(t, kind, d.look()) : null;
            if (vox != null) {
                String json = VoxelSmith.json(vox, "a", "b");
                assertTrue(json.contains("\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#card\"}"), "카드 판");
                for (Matcher m = Pattern.compile("\"(from|to)\":\\[([^\\]]*)\\]").matcher(json); m.find(); )
                    for (double v : nums(m.group(2))) assertTrue(v >= -16 && v <= 32, t.id() + " 좌표 " + v);
                assertTrue(VoxelSmith.colors(vox).size() <= 256);
                renderBoxes(models, ox, oy, VoxelSmith.fitted(vox));
                continue;
            }
            String json = Model3D.json(d.art(), d.look(), ResourcePackBuilder.WIELDED.contains(kind), "a", "b");
            assertTrue(json.contains("\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#card\"}"), "카드 판");
            render(models, ox, oy, json, d.art());
        }
        ImageIO.write(cards, "png", new File("build/cards.png"));
        ImageIO.write(models, "png", new File("build/models3d.png"));
    }

    /** 무기 종류마다 하나씩 크게 (build/weapons3d.png) */
    @Test
    void weaponsBig() throws Exception {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        java.util.LinkedHashMap<String, ItemType> pick = new java.util.LinkedHashMap<>();
        for (String id : List.of("lu_divine_sword", "red_star", "coldrim_demon_sword", "glacier_sword", "roa_masterpiece", "hellfire_sword", "rusted_famed_sword", "annihilation_sword", "dragon_slaying_axe", "sealed_thunder_spear", "saint_staff", "yerika_bow"))
            pick.put(id, item(c, id));
        for (ItemType t : c.items()) {
            String k = PixelArt.kind(t) + (t.hasTag("trident") ? "/t" : "") + (t.hasTag("crook") ? "/c" : "") + (t.hasTag("stone_head") ? "/s" : "") + (t.hasTag("curved") ? "/cv" : "") + (t.hasTag("jagged") ? "/j" : "");
            if (t.category().unique() && VoxelSmith.handles(PixelArt.kind(t)) && pick.values().stream().noneMatch(o -> (PixelArt.kind(o) + (o.hasTag("trident") ? "/t" : "") + (o.hasTag("crook") ? "/c" : "") + (o.hasTag("stone_head") ? "/s" : "") + (o.hasTag("curved") ? "/cv" : "") + (o.hasTag("jagged") ? "/j" : "")).equals(k)))
                pick.put(t.id(), t);
        }
        int cols = 5, cell = 360, rows = (pick.size() + cols - 1) / cols;
        BufferedImage out = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, 0xff3c3c46);
        int i = 0;
        for (ItemType t : pick.values()) {
            String kind = PixelArt.kind(t);
            VoxelSmith.Build b = VoxelSmith.build(t, kind, MmoIcon.render(t, kind).look());
            BufferedImage ic = VoxelRender.icon(b, 52);
            int bx = (i % cols) * cell + 50, by = (i / cols) * cell + 50;
            for (int y = 0; y < 260; y++) for (int x = 0; x < 260; x++) {
                int col = ic.getRGB(x / 5, y / 5), a = col >>> 24;
                if (a == 0) continue;
                int bg = 0x3c3c46;
                int r = (((col >> 16) & 255) * a + ((bg >> 16) & 255) * (255 - a)) / 255, gg = (((col >> 8) & 255) * a + ((bg >> 8) & 255) * (255 - a)) / 255, bb = ((col & 255) * a + (bg & 255) * (255 - a)) / 255;
                out.setRGB(bx + x, by + y, 0xff000000 | (r << 16) | (gg << 8) | bb);
            }
            System.out.println(i + " " + t.id() + " " + kind + " boxes=" + b.boxes.size());
            i++;
        }
        ImageIO.write(out, "png", new File("build/weapons3d.png"));
    }

    /** 상자들을 비스듬히 (오른쪽 위에서) 본 모습 — 카드 판은 빼고, 깊이 버퍼로 */
    static void render(BufferedImage out, int ox, int oy, String json, BufferedImage tex) {
        double ay = Math.toRadians(-35), ax = Math.toRadians(25);
        double[] zbuf = new double[192 * 192];
        java.util.Arrays.fill(zbuf, -1e9);
        Matcher m = Pattern.compile("\\{\"from\":\\[([^\\]]*)\\],\"to\":\\[([^\\]]*)\\],\"faces\":\\{\"south\":\\{\"uv\":\\[([^\\]]*)\\],\"texture\":\"#art\"").matcher(json);
        while (m.find()) {
            double[] f = nums(m.group(1)), t = nums(m.group(2)), uv = nums(m.group(3));
            // 상자 겉면을 촘촘히 찍는다
            double step = 0.08;
            for (double x = f[0]; x <= t[0]; x += step)
                for (double y = f[1]; y <= t[1]; y += step)
                    for (double z = f[2]; z <= t[2]; z += step) {
                        boolean surface = x - f[0] < step || t[0] - x < step || y - f[1] < step || t[1] - y < step || z - f[2] < step || t[2] - z < step;
                        if (!surface) continue;
                        int tx = (int) Math.min(63, Math.max(0, (uv[0] + (x - f[0]) / Math.max(1e-6, t[0] - f[0]) * (uv[2] - uv[0])) * 4));
                        int ty = (int) Math.min(63, Math.max(0, (uv[1] + (t[1] - y) / Math.max(1e-6, t[1] - f[1]) * (uv[3] - uv[1])) * 4));
                        int col = tex.getRGB(tx, ty);
                        if ((col >>> 24) < 100) continue;
                        double px = x - 8, py = y - 8, pz = z - 8;
                        double rx = px * Math.cos(ay) + pz * Math.sin(ay), rz = -px * Math.sin(ay) + pz * Math.cos(ay);
                        double ry = py * Math.cos(ax) - rz * Math.sin(ax), rz2 = py * Math.sin(ax) + rz * Math.cos(ax);
                        int sx = (int) (96 + rx * 10), sy = (int) (96 - ry * 10);
                        if (sx < 0 || sy < 0 || sx >= 192 || sy >= 192) continue;
                        if (rz2 > zbuf[sy * 192 + sx]) {
                            zbuf[sy * 192 + sx] = rz2;
                            boolean side = z - f[2] >= step && t[2] - z >= step;   // 옆면은 조금 어둡게
                            int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
                            if (side) { r = r * 3 / 4; g = g * 3 / 4; b = b * 3 / 4; }
                            out.setRGB(ox + sx, oy + sy, 0xff000000 | (r << 16) | (g << 8) | b);
                        }
                    }
        }
    }

    /** 큐브 모델 — z 축 −45° 로 눕힌 뒤 비스듬히 본 모습, 면마다 빛 방향으로 밝기 */
    static void renderBoxes(BufferedImage out, int ox, int oy, List<VoxelSmith.Box> boxes) {
        renderBoxes(out, ox, oy, boxes, 192);
    }

    static void renderBoxes(BufferedImage out, int ox, int oy, List<VoxelSmith.Box> boxes, int size) {
        int half = size / 2;
        double scale = size / 192.0 * 11;
        double ay = Math.toRadians(-30), ax = Math.toRadians(20), c45 = Math.cos(Math.toRadians(-45)), s45 = Math.sin(Math.toRadians(-45));
        double[] zbuf = new double[size * size];
        java.util.Arrays.fill(zbuf, -1e9);
        double[][] normals = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        double[] light = {0.35, 0.75, 0.55};
        for (VoxelSmith.Box b : boxes) {
            double[] f = {b.x0(), b.y0(), b.z0()}, t = {b.x1(), b.y1(), b.z1()};
            for (int fi = 0; fi < 6; fi++) {
                double[] nrm = normals[fi];
                int axis = nrm[0] != 0 ? 0 : nrm[1] != 0 ? 1 : 2;
                int a1 = (axis + 1) % 3, a2 = (axis + 2) % 3;
                double fixed = (nrm[axis] > 0 ? t : f)[axis];
                // 면 법선도 같이 돌린다 → 빛
                double nx = nrm[0] * c45 - nrm[1] * s45, ny = nrm[0] * s45 + nrm[1] * c45, nz = nrm[2];
                double rx0 = nx * Math.cos(ay) + nz * Math.sin(ay), rz0 = -nx * Math.sin(ay) + nz * Math.cos(ay);
                double ry0 = ny * Math.cos(ax) - rz0 * Math.sin(ax), rzz = ny * Math.sin(ax) + rz0 * Math.cos(ax);
                double lit = 0.45 + 0.6 * Math.max(0, rx0 * light[0] + ry0 * light[1] + rzz * light[2]);
                int col = b.c().getRGB();
                int r = (int) Math.min(255, ((col >> 16) & 255) * lit), g = (int) Math.min(255, ((col >> 8) & 255) * lit), bl = (int) Math.min(255, (col & 255) * lit);
                double step = 0.04 * 192 / size;
                for (double u = f[a1]; u <= t[a1]; u += step)
                    for (double v = f[a2]; v <= t[a2]; v += step) {
                        double[] p = new double[3];
                        p[axis] = fixed; p[a1] = u; p[a2] = v;
                        double px = p[0] - 8, py = p[1] - 8, pz = p[2] - 8;
                        double qx = px * c45 - py * s45, qy = px * s45 + py * c45;
                        double rx = qx * Math.cos(ay) + pz * Math.sin(ay), rz = -qx * Math.sin(ay) + pz * Math.cos(ay);
                        double ry = qy * Math.cos(ax) - rz * Math.sin(ax), rz2 = qy * Math.sin(ax) + rz * Math.cos(ax);
                        int sx = (int) (half + rx * scale), sy = (int) (half - ry * scale);
                        if (sx < 0 || sy < 0 || sx >= size || sy >= size) continue;
                        if (rz2 + 1e-4 * fi > zbuf[sy * size + sx]) {
                            zbuf[sy * size + sx] = rz2;
                            out.setRGB(ox + sx, oy + sy, 0xff000000 | (r << 16) | (g << 8) | bl);
                        }
                    }
            }
        }
    }

    static double[] nums(String s) {
        String[] p = s.split(",");
        double[] d = new double[p.length];
        for (int i = 0; i < p.length; i++) d[i] = Double.parseDouble(p[i]);
        return d;
    }
}
