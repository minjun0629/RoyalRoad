package io.versaera.tools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 아이콘 카드에 쓰는 글자 (Lv. · 숫자)를 한 번 그려 src/main/resources/pack/labels.png 로 저장한다.
 * 서버에는 한글 글꼴이 없을 수 있어서, 팩을 만들 때는 이 그림을 붙이기만 한다 (같은 팩이 나온다).
 * 실행: java -cp … io.versaera.tools.LabelSheetRenderer <저장소 루트>
 */
public final class LabelSheetRenderer {
    public static final String[] WORDS = {"Lv.",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        Font font = null;
        for (String name : new String[]{"NanumGothic", "Noto Sans CJK KR", "WenQuanYi Zen Hei"}) {
            Font f = new Font(name, Font.BOLD, Integer.getInteger("labelSize", 12));
            if (f.canDisplay('검') && !f.getFamily().equals("Dialog")) { font = f; break; }
        }
        if (font == null) throw new IllegalStateException("한글 글꼴이 없습니다");
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics fm = probe.createGraphics().getFontMetrics(font);
        int h = fm.getAscent() + fm.getDescent() + 2, width = 256;
        List<String> index = new ArrayList<>();
        int x = 0, y = 0;
        List<int[]> pos = new ArrayList<>();
        for (String w : WORDS) {
            int ww = fm.stringWidth(w) + 2;
            if (x + ww > width) { x = 0; y += h; }
            pos.add(new int[]{x, y, ww, h});
            x += ww;
        }
        BufferedImage img = new BufferedImage(width, y + h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        for (int i = 0; i < WORDS.length; i++) {
            int[] p = pos.get(i);
            g.setColor(Color.WHITE);
            g.drawString(WORDS[i], p[0] + 1, p[1] + 1 + fm.getAscent());
            index.add(WORDS[i] + "=" + p[0] + "," + p[1] + "," + p[2] + "," + p[3]);
        }
        File dir = new File(root, "src/main/resources/pack");
        dir.mkdirs();
        ImageIO.write(img, "png", new File(dir, "labels.png"));
        Files.writeString(new File(dir, "labels.txt").toPath(), String.join("\n", index) + "\n");
        System.out.println("글자 " + WORDS.length + " → " + dir + " (" + font.getFontName() + ")");
    }
}
