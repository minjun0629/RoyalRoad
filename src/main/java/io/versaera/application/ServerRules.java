package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.time.GameTime;

/**
 * 서버 규칙 (config.yml). 기본은 원작대로: 시간 4배 · 사망하면 숙련 레벨 · 행동 스탯 하락 · 아이템 드롭 (접속 제한은 없음).
 *
 * @param deathMode canon (원작) · soft (완화판: 레벨 · 스탯 유지 · 드롭 없음)
 */
public record ServerRules(String deathMode, GameTime time) {
    public static final ServerRules CANON = new ServerRules("canon", new GameTime(4));

    public ServerRules {
        DomainException.require("canon".equals(deathMode) || "soft".equals(deathMode), "rules.death_mode", "death.mode 는 canon 또는 soft");
    }

    public boolean canonDeath() {
        return "canon".equals(deathMode);
    }
}
