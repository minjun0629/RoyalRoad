package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.time.GameTime;

/**
 * 서버 규칙 (config.yml). 기본은 원작대로: 시간 4배 · 사망하면 현실 24시간 접속 불가 · 숙련 레벨 하락 · 아이템 드롭.
 *
 * @param deathMode    canon (원작) · soft (완화판: 레벨 유지 · 드롭 · 접속 제한 없음)
 * @param lockoutHours canon 모드의 접속 제한 (현실 시간)
 */
public record ServerRules(String deathMode, double lockoutHours, GameTime time) {
    public static final ServerRules CANON = new ServerRules("canon", 24, new GameTime(4));

    public ServerRules {
        DomainException.require("canon".equals(deathMode) || "soft".equals(deathMode), "rules.death_mode", "death.mode 는 canon 또는 soft");
        DomainException.require(lockoutHours >= 0 && lockoutHours <= 168, "rules.lockout", "접속 제한은 0 ~ 168 시간");
    }

    public boolean canonDeath() {
        return "canon".equals(deathMode);
    }
}
