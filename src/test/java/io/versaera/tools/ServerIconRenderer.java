package io.versaera.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 서버 목록 아이콘 (64 × 64) — 32 × 32 픽셀 그림을 2 배로 (부드럽게 하지 않음). 적은 색 · 또렷한 테두리 · 디더링 하늘.
 * 그림: 밤하늘의 초승달 · 별, 달빛을 받는 돌 조각상(흉상)과 받침 — 「달빛 조각사」.
 * 실행: java -cp … io.versaera.tools.ServerIconRenderer <출력.png>
 */
public final class ServerIconRenderer {
    static final int N = 32;

    // 팔레트 (16 색 안쪽)
    static final int SKY0 = 0x141833, SKY1 = 0x1f2550, SKY2 = 0x2d2f6b, SKY3 = 0x45387d,
            MOON = 0xf6e7a8, MOON_D = 0xd9b45a, MOON_O = 0x7a5a24,
            STAR = 0xfff6d0,
            STONE = 0xb8b4ac, STONE_L = 0xe2ded2, STONE_D = 0x7c776f, STONE_O = 0x2a2630,
            GROUND = 0x1a1424, GRASS = 0x2f3d2a, FRAME = 0x0b0a14;

    static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    final int[] px = new int[N * N];

    void set(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < N && y < N) px[y * N + x] = c;
    }

    int get(int x, int y) {
        return x >= 0 && y >= 0 && x < N && y < N ? px[y * N + x] : -1;
    }

    /** 흉상: 문자 그림 (. 빈칸 · o 테두리 · s 돌 · l 밝은 돌(달 쪽) · d 어두운 돌) */
    static final String[] BUST = {
            "....oooo....",
            "...ollsso...",
            "..olllssso..",
            "..olllsssd..",
            "..ollsosdd..",
            "..olllssdd..",
            "...ollsdd...",
            "...olsssd...",
            "....olsd....",
            "..oollssdoo.",
            ".olllllsssdo",
            "ollllllssssd",
            "olllllsssssd",
            "oooooooooooo",
            "..olssssdd..",
            "..olssssdd..",
            "..olssssdd..",
            ".oooooooooo.",
    };

    void draw() {
        // 하늘: 위로 갈수록 어두운 4 단계, 단계 사이는 4×4 디더
        int[] sky = {SKY0, SKY1, SKY2, SKY3};
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double v = y / (double) (N - 1) * 3;
                int band = (int) Math.floor(v);
                double f = v - band;
                int c = band >= 3 ? sky[3] : (f * 16 > BAYER[y % 4][x % 4] ? sky[band + 1] : sky[band]);
                set(x, y, c);
            }
        // 별 (한 칸 · 십자)
        int[][] stars = {{4, 4, 1}, {11, 2, 0}, {2, 13, 0}, {28, 20, 0}, {15, 7, 0}, {18, 3, 0}, {29, 14, 0}};
        for (int[] s : stars) {
            set(s[0], s[1], STAR);
            if (s[2] == 1) { set(s[0] - 1, s[1], SKY3); set(s[0] + 1, s[1], SKY3); set(s[0], s[1] - 1, SKY3); set(s[0], s[1] + 1, SKY3); }
        }
        // 초승달: 큰 원 - 비낀 원, 픽셀 단위 (부드럽게 하지 않음) + 어두운 안쪽 + 테두리
        double cx = 23.0, cy = 8.0, r = 5.6, ox = 25.6, oy = 6.2, or = 4.8;
        boolean[] moon = new boolean[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double a = Math.hypot(x + 0.5 - cx, y + 0.5 - cy), b = Math.hypot(x + 0.5 - ox, y + 0.5 - oy);
                if (a <= r && b > or) moon[y * N + x] = true;
            }
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (!moon[y * N + x]) continue;
                double b = Math.hypot(x + 0.5 - ox, y + 0.5 - oy);
                set(x, y, b < or + 1.1 ? MOON_D : MOON);   // 안쪽 가장자리는 어둡게
            }
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (moon[y * N + x]) continue;
                boolean edge = false;
                for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + o[0], ny = y + o[1];
                    if (nx >= 0 && ny >= 0 && nx < N && ny < N && moon[ny * N + nx]) edge = true;
                }
                if (edge) set(x, y, MOON_O);
            }
        // 땅: 언덕 두 줄 (풀 · 흙)
        for (int x = 0; x < N; x++) {
            int top = 27 + (int) Math.round(Math.sin(x / 5.0) * 0.8);
            for (int y = top; y < N; y++) set(x, y, y == top ? GRASS : GROUND);
        }
        // 흉상 (왼쪽 아래, 달빛은 오른쪽 위에서 → 문자 그림의 l 이 밝은 쪽이 되도록 좌우를 뒤집어 놓는다)
        int bx = 4, by = 10;
        for (int row = 0; row < BUST.length; row++)
            for (int col = 0; col < BUST[row].length(); col++) {
                char ch = BUST[row].charAt(BUST[row].length() - 1 - col);
                int c = switch (ch) {
                    case 'o' -> STONE_O;
                    case 's' -> STONE;
                    case 'l' -> STONE_L;
                    case 'd' -> STONE_D;
                    default -> -1;
                };
                if (c != -1) set(bx + col, by + row, c);
            }
        // 조각칼: 흉상 오른쪽 땅에 비스듬히 꽂힌 칼 (날 · 코등이 · 손잡이)
        int[][] blade = {{24, 19}, {23, 20}, {22, 21}};
        for (int[] b : blade) { set(b[0], b[1], STONE_L); set(b[0] + 1, b[1], STONE_D); }
        set(25, 18, STAR);
        set(21, 22, MOON_D); set(22, 22, MOON_D); set(21, 21, MOON_D);
        int[][] handle = {{20, 23}, {19, 24}, {18, 25}, {17, 26}};
        for (int[] h : handle) { set(h[0], h[1], 0x8a5530); set(h[0] + 1, h[1], 0x5a3418); }
        // 테두리 1 칸 + 모서리 깎기
        for (int i = 0; i < N; i++) { set(i, 0, FRAME); set(i, N - 1, FRAME); set(0, i, FRAME); set(N - 1, i, FRAME); }
        for (int[] c : new int[][]{{1, 1}, {N - 2, 1}, {1, N - 2}, {N - 2, N - 2}}) set(c[0], c[1], FRAME);
    }

    public static void main(String[] args) throws Exception {
        ServerIconRenderer r = new ServerIconRenderer();
        r.draw();
        int scale = 2;
        BufferedImage icon = new BufferedImage(N * scale, N * scale, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * scale; y++)
            for (int x = 0; x < N * scale; x++) {
                int c = r.get(x / scale, y / scale);
                boolean corner = (x / scale == 0 || x / scale == N - 1) && (y / scale == 0 || y / scale == N - 1);
                icon.setRGB(x, y, corner ? 0 : 0xff000000 | c);   // 네 귀퉁이는 투명
            }
        File out = new File(args.length > 0 ? args[0] : "server-icon.png");
        ImageIO.write(icon, "png", out);
        // 확인용 큰 그림 (8 배)
        BufferedImage big = new BufferedImage(N * 8, N * 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N * 8; y++) for (int x = 0; x < N * 8; x++) big.setRGB(x, y, icon.getRGB(x / 4, y / 4));
        ImageIO.write(big, "png", new File(out.getAbsoluteFile().getParentFile(), "server-icon-preview.png"));
        System.out.println("아이콘 → " + out);
    }
}
