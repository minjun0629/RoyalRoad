package io.versaera.domain.origin;

/** 캐릭터 성별 — 원작은 캐릭터 제작 때 남성 · 여성 · 중성을 지원한다 (CANON). 능력 차이는 없다 */
public enum Gender {
    MALE("남성"), FEMALE("여성"), NEUTRAL("중성");

    public final String label;

    Gender(String label) {
        this.label = label;
    }
}
