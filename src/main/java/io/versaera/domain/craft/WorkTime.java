package io.versaera.domain.craft;

/**
 * 손으로 만드는 데 걸리는 시간 (초). 어려울수록 오래, 숙련이 높을수록 빨리 (최대 절반까지).
 * 그동안 작업 자리 곁에 머물러야 한다 — 떠나면 멈추고 재료는 돌려받는다.
 */
public final class WorkTime {
    private WorkTime() {
    }

    /** 대형 조각: 작품 종류의 요구 숙련 kindLevel, 내 조각 숙련 skill */
    public static double artwork(int kindLevel, int skill) {
        double t = (12 + kindLevel * 0.9) * (1 - Math.min(0.5, Math.max(0, skill - kindLevel) * 0.015));
        return Math.max(8, Math.min(60, t));
    }

    /** 제작대 제작: 제조법 요구 숙련 recipeLevel, 내 숙련 skill */
    public static double craft(int recipeLevel, int skill) {
        double t = (3 + recipeLevel * 0.1) * (1 - Math.min(0.5, Math.max(0, skill - recipeLevel) * 0.02));
        return Math.max(2, Math.min(9, t));
    }
}
