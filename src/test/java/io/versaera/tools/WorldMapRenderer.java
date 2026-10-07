package io.versaera.tools;

import io.versaera.content.ContentBundle;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.terrain.TerrainModel;
import io.versaera.domain.terrain.TerrainModel.Surface;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 베르사 대륙 지도 그리기 (개발 도구). 실제 지형(TerrainModel) · 지역(regions.yml)으로 양피지 느낌의 판타지 지도를 만든다.
 * 실행: java -cp … io.versaera.tools.WorldMapRenderer <출력.png> [픽셀] [글꼴 폴더(NanumMyeongjo-*.ttf)]
 */
public final class WorldMapRenderer {
    static final int HALF = 25000;
    static final long SEED = 42;

    private final int size;
    private final double bpp;   // 픽셀당 블록
    private final RegionIndex regions;
    private final TerrainModel t;
    private final Set<String> starts = new HashSet<>();
    private Font titleFont, boldFont, plainFont;

    private int[] height;
    private Surface[] surf;
    private boolean[] water;

    WorldMapRenderer(int size, File fonts) throws Exception {
        this.size = size;
        this.bpp = 2.0 * HALF / size;
        ContentBundle c = ContentBundle.fromClasspath(WorldMapRenderer.class.getClassLoader());
        this.regions = new RegionIndex(c.regions());
        this.t = new TerrainModel(regions, "world", SEED);
        for (var city : c.origins().cities()) starts.add(city.region());
        titleFont = load(fonts, "NanumMyeongjo-ExtraBold.ttf");
        boldFont = load(fonts, "NanumMyeongjo-Bold.ttf");
        plainFont = load(fonts, "NanumMyeongjo-Regular.ttf");
    }

    private static Font load(File dir, String name) {
        try {
            if (dir != null && new File(dir, name).exists()) return Font.createFont(Font.TRUETYPE_FONT, new File(dir, name));
        } catch (Exception ignored) {
        }
        return new Font("WenQuanYi Zen Hei", Font.PLAIN, 12);
    }

    int bx(int px) {
        return (int) Math.round(-HALF + (px + 0.5) * bpp);
    }

    double px(int x) {
        return (x + HALF) / bpp;
    }

    // ------------------------------------------------------------------ 땅 재기
    void sample() {
        height = new int[size * size];
        surf = new Surface[size * size];
        water = new boolean[size * size];
        IntStream.range(0, size).parallel().forEach(py -> {
            for (int px = 0; px < size; px++) {
                int x = bx(px), z = bx(py), h = t.height(x, z), i = py * size + px;
                height[i] = h;
                water[i] = h < TerrainModel.SEA_LEVEL && !t.dry(x, z);
                surf[i] = t.surface(x, z, h);
            }
        });
    }

    /** 바다에서 가장 가까운 땅까지의 거리 (픽셀, 최대 cap) */
    int[] coastDistance(int cap) {
        int[] d = new int[size * size];
        Arrays.fill(d, cap);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < d.length; i++) if (!water[i]) { d[i] = 0; q.add(i); }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % size, y = i / size;
            if (d[i] >= cap) continue;
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + o[0], ny = y + o[1];
                if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue;
                int j = ny * size + nx;
                if (d[j] > d[i] + 1) { d[j] = d[i] + 1; q.add(j); }
            }
        }
        return d;
    }

    // ------------------------------------------------------------------ 색
    static Color mix(Color a, Color b, double f) {
        f = Math.max(0, Math.min(1, f));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * f), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }

    static Color land(Surface s) {
        return switch (s) {
            case GRASS -> new Color(186, 186, 128);
            case PODZOL -> new Color(140, 152, 98);
            case SAND -> new Color(226, 205, 150);
            case RED_SAND -> new Color(212, 160, 108);
            case SNOW -> new Color(240, 236, 226);
            case STONE -> new Color(170, 160, 140);
            case MUD -> new Color(150, 146, 104);
            case GRAVEL -> new Color(168, 160, 140);
            case BASALT -> new Color(112, 102, 94);
            case DIRT_PATH -> new Color(190, 168, 124);
        };
    }

    /** 양피지 결: 여러 겹의 값 노이즈 */
    double paper(int x, int y) {
        double v = 0, amp = 1, f = 1 / 180.0, sum = 0;
        for (int o = 0; o < 5; o++) {
            v += amp * vnoise(x * f, y * f, o);
            sum += amp;
            amp *= 0.5;
            f *= 2.1;
        }
        return v / sum;
    }

    static double hash(int x, int y, int s) {
        long h = x * 374761393L + y * 668265263L + s * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535.0;
    }

    static double vnoise(double x, double y, int s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    // ------------------------------------------------------------------ 그리기
    BufferedImage render() {
        sample();
        int[] dist = coastDistance(48);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Color deep = new Color(96, 128, 138), shallow = new Color(150, 178, 172), ink = new Color(66, 48, 32);
        int maxH = 0;
        for (int i = 0; i < height.length; i++) if (!water[i]) maxH = Math.max(maxH, height[i]);
        final int top = Math.max(TerrainModel.SEA_LEVEL + 40, maxH);
        IntStream.range(0, size).parallel().forEach(py -> {
            for (int px = 0; px < size; px++) {
                int i = py * size + px;
                Color c;
                if (water[i]) {
                    double f = Math.min(1, dist[i] / 40.0);
                    c = mix(shallow, deep, f);
                    // 해안선을 따라 번지는 물결 줄
                    if (dist[i] > 2 && dist[i] < 30 && dist[i] % 7 == 0) c = mix(c, new Color(72, 100, 112), 0.35 - dist[i] / 120.0);
                } else {
                    int h = height[i];
                    c = land(surf[i]);
                    double e = (h - TerrainModel.SEA_LEVEL) / (double) (top - TerrainModel.SEA_LEVEL);
                    if (surf[i] != Surface.SNOW) c = mix(c, new Color(150, 128, 98), Math.max(0, e - 0.35) * 0.9);
                    // 언덕 그림자: 북서쪽에서 빛
                    int l = px > 0 ? height[i - 1] : h, u = py > 0 ? height[i - size] : h;
                    double shade = ((h - l) + (h - u)) / (bpp * 0.55);
                    shade = Math.max(-0.45, Math.min(0.45, shade));
                    c = shade > 0 ? mix(c, new Color(250, 240, 214), shade) : mix(c, new Color(70, 58, 44), -shade);
                    // 해안 잉크 선
                    boolean coast = false;
                    for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = px + o[0], ny = py + o[1];
                        if (nx >= 0 && ny >= 0 && nx < size && ny < size && water[ny * size + nx]) coast = true;
                    }
                    if (coast) c = ink;
                }
                // 양피지 결 · 얼룩 · 가장자리 어둡게
                double pv = paper(px, py), stain = vnoise(px / 420.0, py / 420.0, 99);
                double cx = px / (double) size - 0.5, cy = py / (double) size - 0.5, vig = Math.sqrt(cx * cx + cy * cy) * 1.25;
                c = mix(c, new Color(236, 220, 182), 0.18 + (pv - 0.5) * 0.25);
                c = mix(c, new Color(120, 86, 48), Math.max(0, stain - 0.72) * 0.9 + Math.max(0, vig - 0.45) * 0.55);
                img.setRGB(px, py, c.getRGB());
            }
        });
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        glyphs(g, top);
        borders(img);
        labels(g);
        frame(g);
        g.dispose();
        return img;
    }

    /** 숲 나무 · 산 봉우리 그림 (흩뿌린 격자) */
    void glyphs(Graphics2D g, int top) {
        int step = Math.max(10, size / 170);
        Random rng = new Random(7);
        Color tree = new Color(70, 88, 52), treeDark = new Color(44, 56, 34), mountInk = new Color(78, 62, 44);
        List<double[]> mounts = new ArrayList<>(), trees = new ArrayList<>();
        for (int gy = 0; gy < size; gy += step)
            for (int gx = 0; gx < size; gx += step) {
                int px = gx + rng.nextInt(step), py = gy + rng.nextInt(step);
                if (px >= size || py >= size) continue;
                int i = py * size + px;
                if (water[i]) continue;
                double e = (height[i] - TerrainModel.SEA_LEVEL) / (double) (top - TerrainModel.SEA_LEVEL);
                if (e > 0.42 && rng.nextDouble() < 0.85) mounts.add(new double[]{px, py, 0.7 + e});
                else if (surf[i] == Surface.PODZOL && rng.nextDouble() < 0.9) trees.add(new double[]{px, py});
            }
        trees.sort(Comparator.comparingDouble(a -> a[1]));
        for (double[] p : trees) {
            double s = step * 0.42;
            g.setColor(treeDark);
            g.fill(new Ellipse2D.Double(p[0] - s * 0.55, p[1] - s * 1.1, s * 1.1, s * 1.1));
            g.setColor(tree);
            g.fill(new Ellipse2D.Double(p[0] - s * 0.45, p[1] - s * 1.05, s * 0.85, s * 0.85));
            g.setColor(treeDark);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(p[0], p[1] - s * 0.1, p[0], p[1] + s * 0.25));
        }
        mounts.sort(Comparator.comparingDouble(a -> a[1]));
        for (double[] p : mounts) {
            double s = step * 0.75 * p[2];
            Path2D.Double m = new Path2D.Double();
            m.moveTo(p[0] - s, p[1]);
            m.lineTo(p[0] - s * 0.1, p[1] - s * 1.15);
            m.lineTo(p[0] + s, p[1]);
            m.closePath();
            g.setColor(new Color(214, 198, 166));
            g.fill(m);
            Path2D.Double shadow = new Path2D.Double();
            shadow.moveTo(p[0] - s * 0.1, p[1] - s * 1.15);
            shadow.lineTo(p[0] + s, p[1]);
            shadow.lineTo(p[0] + s * 0.15, p[1]);
            shadow.closePath();
            g.setColor(new Color(150, 128, 98));
            g.fill(shadow);
            g.setColor(mountInk);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double edge = new Path2D.Double();
            edge.moveTo(p[0] - s, p[1]);
            edge.lineTo(p[0] - s * 0.1, p[1] - s * 1.15);
            edge.lineTo(p[0] + s, p[1]);
            g.draw(edge);
        }
    }

    /** 나라 경계: 땅 위에서 1단계 지역(나라 · 큰 땅)이 바뀌는 곳에 붉은 점선 */
    void borders(BufferedImage img) {
        String[] realm = new String[size * size];
        IntStream.range(0, size).parallel().forEach(py -> {
            for (int px = 0; px < size; px++) {
                int i = py * size + px;
                if (water[i]) continue;
                realm[i] = realmOf(t.shapeRegionAt(bx(px), bx(py)));   // 지형과 같은 휜 경계
            }
        });
        Color dash = new Color(140, 46, 32);
        for (int py = 1; py < size - 1; py++)
            for (int px = 1; px < size - 1; px++) {
                int i = py * size + px;
                if (realm[i] == null) continue;
                String a = realm[i];
                boolean edge = (realm[i + 1] != null && !realm[i + 1].equals(a)) || (realm[i + size] != null && !realm[i + size].equals(a));
                if (!edge || ((px + py) / 4) % 3 == 2) continue;
                img.setRGB(px, py, mix(new Color(img.getRGB(px, py)), dash, 0.85).getRGB());
                img.setRGB(px + 1, py, mix(new Color(img.getRGB(px + 1, py)), dash, 0.5).getRGB());
            }
    }

    /** 경계로 나눌 단위: 최상위 바로 아래 지역 (중앙 대륙 → 하벤 · 로자임 …), 최상위가 작은 땅이면 그 자신 */
    String realmOf(Region r) {
        if (r == null) return null;
        List<Region> chain = new ArrayList<>();
        for (Region x = r; x != null; x = x.parent() == null ? null : regions.byId(x.parent())) chain.add(0, x);
        if (chain.get(0).tags().contains("sea")) return null;
        return chain.size() >= 2 ? chain.get(1).id() : chain.get(0).id();
    }

    // ------------------------------------------------------------------ 이름
    private final List<Rectangle2D> taken = new ArrayList<>();

    private boolean free(Rectangle2D r) {
        for (Rectangle2D o : taken) if (o.intersects(r)) return false;
        return true;
    }

    static String clean(String name) {
        return name.replaceAll("\\s*\\(.*?\\)", "").trim();
    }

    /** 자간을 벌린 글자를 테두리(양피지색)와 함께. 자리가 겹치면 그리지 않는다 */
    boolean text(Graphics2D g, String s, double cx, double cy, Font f, Color fill, double spacing, float halo, boolean force) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        double w = 0;
        for (char ch : s.toCharArray()) w += fm.charWidth(ch) + spacing;
        w -= spacing;
        Rectangle2D box = new Rectangle2D.Double(cx - w / 2 - 4, cy - fm.getAscent() * 0.8 - 2, w + 8, fm.getAscent() + 4);
        if (!force && !free(box)) return false;
        taken.add(box);
        double x = cx - w / 2;
        Path2D.Double all = new Path2D.Double();
        for (char ch : s.toCharArray()) {
            GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), String.valueOf(ch));
            Shape sh = gv.getOutline((float) x, (float) (cy + fm.getAscent() * 0.35));
            all.append(sh, false);
            x += fm.charWidth(ch) + spacing;
        }
        if (halo > 0) {
            g.setColor(new Color(240, 226, 192, 220));
            g.setStroke(new BasicStroke(halo, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(all);
        }
        g.setColor(fill);
        g.fill(all);
        return true;
    }

    void labels(Graphics2D g) {
        double k = size / 2500.0;
        List<Region> all = new ArrayList<>();
        for (Region r : regions.all()) if (r.world().equals("world")) all.add(r);
        // 도시 그림 자리부터 잡는다
        List<Region> towns = new ArrayList<>(all.stream().filter(r -> r.tags().contains("city") || r.tags().contains("fortress") || r.tags().contains("outpost")).toList());
        towns.sort(Comparator.comparing((Region r) -> !starts.contains(r.id())).thenComparing(r -> !r.tags().contains("city")));
        for (Region r : towns) {
            double x = px((r.minX() + r.maxX()) / 2), y = px((r.minZ() + r.maxZ()) / 2);
            taken.add(new Rectangle2D.Double(x - 9 * k, y - 12 * k, 18 * k, 16 * k));
        }
        // 1) 바다 이름 (기울인 파란 글씨)
        for (Region r : all) {
            if (!r.tags().contains("sea") || r.id().equals("outer_ocean")) continue;
            double x = px((r.minX() + r.maxX()) / 2), y = px((r.minZ() + r.maxZ()) / 2);
            int w = r.maxX() - r.minX();
            Font f = plainFont.deriveFont(Font.ITALIC, (float) (Math.min(30, 18 + w / 3000.0) * k));
            text(g, clean(r.name()), x, y, f, new Color(52, 82, 96), 6 * k, 0, false);
        }
        // 2) 나라 · 큰 땅 이름 (크고 흐리게, 자간 넓게)
        List<Region> realms = new ArrayList<>();
        for (Region r : all) {
            if (r.tags().contains("sea") || r.tags().contains("city") || r.tags().contains("fortress") || r.tags().contains("outpost")) continue;
            long area = (long) (r.maxX() - r.minX()) * (r.maxZ() - r.minZ());
            if (area < 9_000_000L || area > 900_000_000L) continue;
            realms.add(r);
        }
        realms.sort(Comparator.comparingLong((Region r) -> -(long) (r.maxX() - r.minX()) * (r.maxZ() - r.minZ())));
        for (Region r : realms) {
            double x = px((r.minX() + r.maxX()) / 2), y = px((r.minZ() + r.maxZ()) / 2);
            long area = (long) (r.maxX() - r.minX()) * (r.maxZ() - r.minZ());
            float fs = (float) (Math.min(46, 16 + Math.sqrt(area) / 450.0) * k);
            text(g, clean(r.name()), x, y, titleFont.deriveFont(fs), new Color(92, 58, 36, 170), fs * 0.35, 0, false);
        }
        // 3) 던전 · 명소 · 문 그림
        for (Region r : all) {
            double x = px((r.minX() + r.maxX()) / 2), y = px((r.minZ() + r.maxZ()) / 2);
            if (r.tags().contains("portal")) portal(g, x, y, k);
            else if (r.tags().contains("dungeon_site")) dungeon(g, x, y, k);
            else if (r.tags().contains("landmark") || SettlementPlanner.landmarkRegions().contains(r.id()) && !r.tags().contains("city")) landmark(g, x, y, k);
        }
        // 4) 도시: 성 그림 + 이름 (시작 도시는 붉은 깃발 · 큰 글씨)
        for (Region r : towns) {
            double x = px((r.minX() + r.maxX()) / 2), y = px((r.minZ() + r.maxZ()) / 2);
            boolean start = starts.contains(r.id()), city = r.tags().contains("city");
            castle(g, x, y, k * (start ? 1.35 : city ? 1.0 : 0.8), start);
            Font f = boldFont.deriveFont((float) ((start ? 21 : city ? 16 : 13) * k));
            String name = clean(r.name());
            if (!text(g, name, x, y + 22 * k, f, start ? new Color(120, 20, 18) : new Color(44, 30, 20), 1.2 * k, (float) (4 * k), false))
                text(g, name, x, y - 22 * k, f, start ? new Color(120, 20, 18) : new Color(44, 30, 20), 1.2 * k, (float) (4 * k), false);
        }
    }

    void castle(Graphics2D g, double x, double y, double s, boolean start) {
        Color stone = new Color(232, 222, 196), line = new Color(52, 38, 26);
        Path2D.Double c = new Path2D.Double();
        double w = 9 * s, h = 9 * s;
        c.moveTo(x - w, y + 3 * s);
        c.lineTo(x - w, y - h);
        c.lineTo(x - w + 3 * s, y - h);
        c.lineTo(x - w + 3 * s, y - h + 2.5 * s);
        c.lineTo(x - 1.5 * s, y - h + 2.5 * s);
        c.lineTo(x - 1.5 * s, y - h - 4 * s);
        c.lineTo(x + 1.5 * s, y - h - 4 * s);
        c.lineTo(x + 1.5 * s, y - h + 2.5 * s);
        c.lineTo(x + w - 3 * s, y - h + 2.5 * s);
        c.lineTo(x + w - 3 * s, y - h);
        c.lineTo(x + w, y - h);
        c.lineTo(x + w, y + 3 * s);
        c.closePath();
        g.setColor(stone);
        g.fill(c);
        g.setColor(line);
        g.setStroke(new BasicStroke((float) (1.4 * s)));
        g.draw(c);
        g.fill(new Arc2D.Double(x - 2.2 * s, y - 2 * s, 4.4 * s, 8 * s, 0, 180, Arc2D.CHORD));   // 성문
        if (start) {   // 깃발
            g.setStroke(new BasicStroke((float) (1.2 * s)));
            g.draw(new Line2D.Double(x, y - h - 4 * s, x, y - h - 11 * s));
            Path2D.Double flag = new Path2D.Double();
            flag.moveTo(x, y - h - 11 * s);
            flag.lineTo(x + 7 * s, y - h - 9 * s);
            flag.lineTo(x, y - h - 7 * s);
            flag.closePath();
            g.setColor(new Color(168, 28, 24));
            g.fill(flag);
        }
    }

    void dungeon(Graphics2D g, double x, double y, double k) {
        double r = 4.5 * k;
        g.setColor(new Color(40, 28, 22));
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setColor(new Color(214, 196, 160));
        g.setStroke(new BasicStroke((float) (1.4 * k)));
        g.draw(new Line2D.Double(x - r * 0.5, y - r * 0.5, x + r * 0.5, y + r * 0.5));
        g.draw(new Line2D.Double(x + r * 0.5, y - r * 0.5, x - r * 0.5, y + r * 0.5));
    }

    void landmark(Graphics2D g, double x, double y, double k) {
        Path2D.Double s = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5, rr = (i % 2 == 0 ? 6.5 : 2.8) * k;
            double px = x + Math.cos(a) * rr, py = y - Math.sin(a) * rr;
            if (i == 0) s.moveTo(px, py); else s.lineTo(px, py);
        }
        s.closePath();
        g.setColor(new Color(196, 150, 40));
        g.fill(s);
        g.setColor(new Color(70, 48, 20));
        g.setStroke(new BasicStroke((float) (0.9 * k)));
        g.draw(s);
    }

    void portal(Graphics2D g, double x, double y, double k) {
        g.setStroke(new BasicStroke((float) (1.8 * k)));
        for (int i = 0; i < 3; i++) {
            double r = (2.5 + i * 2.2) * k;
            g.setColor(new Color(110 + i * 20, 60, 150, 220 - i * 50));
            g.draw(new Arc2D.Double(x - r, y - r, 2 * r, 2 * r, i * 70, 260, Arc2D.OPEN));
        }
    }

    // ------------------------------------------------------------------ 테두리 · 제목 · 나침반 · 축척
    void frame(Graphics2D g) {
        double k = size / 2500.0;
        Color ink = new Color(60, 40, 24);
        int m = (int) (26 * k);
        g.setColor(new Color(70, 46, 26));
        g.setStroke(new BasicStroke((float) (m)));
        g.draw(new Rectangle2D.Double(m / 2.0, m / 2.0, size - m, size - m));
        g.setColor(new Color(214, 188, 128));
        g.setStroke(new BasicStroke((float) (2.2 * k)));
        g.draw(new Rectangle2D.Double(m + 6 * k, m + 6 * k, size - 2 * m - 12 * k, size - 2 * m - 12 * k));
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) (1.2 * k)));
        g.draw(new Rectangle2D.Double(m + 11 * k, m + 11 * k, size - 2 * m - 22 * k, size - 2 * m - 22 * k));
        // 위도 · 경도 눈금 (5000 블록마다)
        g.setFont(plainFont.deriveFont((float) (11 * k)));
        for (int v = -HALF + 5000; v < HALF; v += 5000) {
            double p = px(v);
            g.setColor(new Color(60, 40, 24, 60));
            g.setStroke(new BasicStroke((float) (0.8 * k), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{(float) (6 * k), (float) (8 * k)}, 0));
            g.draw(new Line2D.Double(p, m + 12 * k, p, size - m - 12 * k));
            g.draw(new Line2D.Double(m + 12 * k, p, size - m - 12 * k, p));
        }
        // 모서리 장식
        for (int cx : new int[]{0, 1})
            for (int cy : new int[]{0, 1}) {
                double x = cx == 0 ? m : size - m, y = cy == 0 ? m : size - m;
                g.setColor(new Color(214, 188, 128));
                g.fill(new Ellipse2D.Double(x - 14 * k, y - 14 * k, 28 * k, 28 * k));
                g.setColor(ink);
                g.setStroke(new BasicStroke((float) (2 * k)));
                g.draw(new Ellipse2D.Double(x - 14 * k, y - 14 * k, 28 * k, 28 * k));
                g.fill(new Ellipse2D.Double(x - 5 * k, y - 5 * k, 10 * k, 10 * k));
            }
        // 제목 두루마리
        double tx = size / 2.0, ty = m + 70 * k, tw = 520 * k, th = 92 * k;
        RoundRectangle2D scroll = new RoundRectangle2D.Double(tx - tw / 2, ty - th / 2, tw, th, 30 * k, 30 * k);
        g.setColor(new Color(238, 222, 184));
        g.fill(scroll);
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) (3 * k)));
        g.draw(scroll);
        g.setStroke(new BasicStroke((float) (1.2 * k)));
        g.draw(new RoundRectangle2D.Double(tx - tw / 2 + 7 * k, ty - th / 2 + 7 * k, tw - 14 * k, th - 14 * k, 22 * k, 22 * k));
        for (int sgn : new int[]{-1, 1}) {   // 두루마리 끝
            g.setColor(new Color(214, 192, 146));
            g.fill(new Ellipse2D.Double(tx + sgn * tw / 2 - 16 * k, ty - th / 2 - 6 * k, 32 * k, th + 12 * k));
            g.setColor(ink);
            g.setStroke(new BasicStroke((float) (2.4 * k)));
            g.draw(new Ellipse2D.Double(tx + sgn * tw / 2 - 16 * k, ty - th / 2 - 6 * k, 32 * k, th + 12 * k));
        }
        text(g, "베르사 대륙", tx, ty - 10 * k, titleFont.deriveFont((float) (46 * k)), new Color(70, 30, 18), 10 * k, 0, true);
        text(g, "VERSA · 달빛 아래의 왕국들", tx, ty + 28 * k, plainFont.deriveFont((float) (15 * k)), new Color(90, 60, 36), 3 * k, 0, true);
        compass(g, size - m - 150 * k, size - m - 160 * k, 95 * k, ink);
        scale(g, m + 60 * k, size - m - 70 * k, ink, k);
        legend(g, m + 50 * k, size - m - 250 * k, ink, k);
    }

    void compass(Graphics2D g, double x, double y, double r, Color ink) {
        g.setColor(new Color(238, 222, 184, 210));
        g.fill(new Ellipse2D.Double(x - r * 1.05, y - r * 1.05, r * 2.1, r * 2.1));
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) (r / 60)));
        g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.draw(new Ellipse2D.Double(x - r * 0.8, y - r * 0.8, 1.6 * r, 1.6 * r));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, len = i % 2 == 0 ? r * 0.95 : r * 0.55, wid = i % 2 == 0 ? r * 0.13 : r * 0.08;
            double ex = x + Math.sin(a) * len, ey = y - Math.cos(a) * len;
            double lx = x + Math.sin(a - Math.PI / 2) * wid, ly = y - Math.cos(a - Math.PI / 2) * wid;
            double rx = x + Math.sin(a + Math.PI / 2) * wid, ry = y - Math.cos(a + Math.PI / 2) * wid;
            Path2D.Double left = new Path2D.Double(), right = new Path2D.Double();
            left.moveTo(x, y); left.lineTo(lx, ly); left.lineTo(ex, ey); left.closePath();
            right.moveTo(x, y); right.lineTo(rx, ry); right.lineTo(ex, ey); right.closePath();
            g.setColor(i == 0 ? new Color(150, 30, 24) : ink);
            g.fill(left);
            g.setColor(new Color(238, 222, 184));
            g.fill(right);
            g.setColor(ink);
            g.draw(left);
            g.draw(right);
        }
        String[] dirs = {"북", "동", "남", "서"};
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            text(g, dirs[i], x + Math.sin(a) * r * 1.25, y - Math.cos(a) * r * 1.25 + r * 0.08, boldFont.deriveFont((float) (r * 0.26)), ink, 0, (float) (r / 25), true);
        }
    }

    void scale(Graphics2D g, double x, double y, Color ink, double k) {
        double len = 5000 / bpp;
        for (int i = 0; i < 5; i++) {
            g.setColor(i % 2 == 0 ? ink : new Color(238, 222, 184));
            g.fill(new Rectangle2D.Double(x + i * len / 5, y, len / 5, 9 * k));
        }
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) (1.4 * k)));
        g.draw(new Rectangle2D.Double(x, y, len, 9 * k));
        text(g, "0", x, y - 12 * k, plainFont.deriveFont((float) (13 * k)), ink, 0, 0, true);
        text(g, "5,000 블록 (말로 약 15분)", x + len, y - 12 * k, plainFont.deriveFont((float) (13 * k)), ink, 0, 0, true);
    }

    void legend(Graphics2D g, double x, double y, Color ink, double k) {
        double w = 210 * k, h = 160 * k;
        g.setColor(new Color(238, 222, 184, 225));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 14 * k, 14 * k));
        g.setColor(ink);
        g.setStroke(new BasicStroke((float) (1.6 * k)));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 14 * k, 14 * k));
        Font f = plainFont.deriveFont((float) (14 * k));
        double ly = y + 28 * k, lx = x + 26 * k;
        castle(g, lx, ly + 3 * k, k * 1.0, true);
        g.setFont(f); g.setColor(ink); g.drawString("시작 도시", (float) (lx + 22 * k), (float) (ly + 5 * k));
        ly += 30 * k;
        castle(g, lx, ly + 3 * k, k * 0.9, false);
        g.setColor(ink); g.drawString("도시 · 요새", (float) (lx + 22 * k), (float) (ly + 5 * k));
        ly += 28 * k;
        dungeon(g, lx, ly, k);
        g.setColor(ink); g.drawString("던전", (float) (lx + 22 * k), (float) (ly + 5 * k));
        ly += 26 * k;
        landmark(g, lx, ly, k);
        g.setColor(ink); g.drawString("명소", (float) (lx + 22 * k), (float) (ly + 5 * k));
        ly += 26 * k;
        portal(g, lx, ly, k);
        g.setColor(ink); g.drawString("다른 세계로 가는 문", (float) (lx + 22 * k), (float) (ly + 5 * k));
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "docs/img/versa-map.png");
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 2500;
        File fonts = args.length > 2 ? new File(args[2]) : null;
        long t0 = System.currentTimeMillis();
        BufferedImage img = new WorldMapRenderer(size, fonts).render();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("지도 " + size + "px → " + out + " (" + (System.currentTimeMillis() - t0) / 1000 + "초)");
    }
}
