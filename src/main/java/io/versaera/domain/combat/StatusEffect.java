package io.versaera.domain.combat;

/**
 * 상태 이상 (ORIGINAL 수치). tickPct: 초당 최대 체력 대비 피해. 같은 효과를 다시 걸면 시간이 갱신되고 세기는 큰 쪽.
 */
public enum StatusEffect {
    BLEED("출혈", 0.02, false, 0),
    BURN("화상", 0.025, false, 0),
    POISON("중독", 0.015, false, 0),
    SLOW("둔화", 0, false, 2),
    FREEZE("빙결", 0, false, 5),
    STUN("기절", 0, true, 0),
    WEAKEN("쇠약", 0, false, 0),
    GUARD("방어 태세", 0, false, 0),
    HASTE("신속", 0, false, 0);

    public final String label;
    public final double tickPct;
    public final boolean disables;
    public final int slowLevel;

    StatusEffect(String label, double tickPct, boolean disables, int slowLevel) {
        this.label = label;
        this.tickPct = tickPct;
        this.disables = disables;
        this.slowLevel = slowLevel;
    }

    public boolean harmful() {
        return this != GUARD && this != HASTE;
    }
}
