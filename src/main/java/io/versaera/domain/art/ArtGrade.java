package io.versaera.domain.art;

/**
 * 조각품 등급 (ORIGINAL 이름, 원작의 졸작 · 명작 · 대작 느낌): 품질 0 ~ 1000 → 졸작 · 평작 · 수작 · 명작 · 대작.
 * 대작은 서버 전체에 알려진다.
 */
public final class ArtGrade {
    public static final String[] NAMES = {"졸작", "평작", "수작", "명작", "대작"};
    private static final int[] FROM = {0, 250, 500, 720, 900};

    private ArtGrade() {
    }

    public static int of(int quality) {
        int g = 0;
        for (int i = 0; i < FROM.length; i++) if (quality >= FROM[i]) g = i;
        return g;
    }

    public static String name(int quality) {
        return NAMES[of(quality)];
    }
}
