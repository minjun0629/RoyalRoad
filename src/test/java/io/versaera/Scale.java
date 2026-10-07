package io.versaera;

/** 테스트 좌표는 16000 판 지도 기준으로 적고, 세계 world (50000 × 50000) 로 늘려 쓴다 — tools/gen_regions.py 의 SCALE 과 같다 */
public final class Scale {
    public static final double SCALE = 50000 / 16000.0;

    private Scale() {}

    public static int s(int v) {
        return (int) Math.round(v * SCALE);
    }
}
