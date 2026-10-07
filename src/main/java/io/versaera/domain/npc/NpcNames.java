package io.versaera.domain.npc;

import java.util.SplittableRandom;

/** 주민 이름 (결정적). 원작 인물 이름을 쓰지 않도록 음절 조합으로 새로 짓는다 */
public final class NpcNames {
    private static final String[] A = {"아", "브", "카", "다", "에", "라", "미", "세", "토", "하", "렌", "로", "베", "유", "오", "시", "루", "페", "이", "제",
            "니", "솔", "테", "벨", "가", "모", "키", "델", "파", "엘", "마", "노", "후", "비", "실", "코"};
    private static final String[] B = {"렌", "리", "엘", "나", "온", "크", "스", "안", "델", "린", "로", "하", "빈", "르", "아", "트", "민", "란", "샤", "엔",
            "윈", "디", "레", "오", "니", "벤", "라", "카"};
    private static final String[] F = {"브리크", "헤일", "오스틴", "마렌", "카르델", "델트", "루엔", "파렐", "솔베", "그란", "웨스", "토르니", "하엘",
            "벤로", "실바", "로웬", "카이른", "펠", "에른", "미스트", "라니", "보른", "헤스", "칼린", "도르", "아벨", "케인", "노엘", "리브", "스탄"};

    private NpcNames() {
    }

    public static String given(SplittableRandom r) {
        return A[r.nextInt(A.length)] + B[r.nextInt(B.length)] + (r.nextInt(3) == 0 ? B[r.nextInt(B.length)] : "");
    }

    public static String family(SplittableRandom r) {
        return F[r.nextInt(F.length)];
    }
}
