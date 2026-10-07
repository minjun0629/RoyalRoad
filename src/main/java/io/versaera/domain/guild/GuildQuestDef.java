package io.versaera.domain.guild;

import io.versaera.domain.common.DomainException;

/**
 * 길드 주간 의뢰 (GLD-03): 길드원 모두의 행동 기록(counter)을 합쳐 target 을 채우면 길드 금고에 money, 활동 점수 activity.
 * deposit = true 면 행동 기록 대신 길드 창고에 그 태그의 재료를 넣은 개수로 센다 (counter 가 태그).
 */
public record GuildQuestDef(String id, String name, String counter, long target, long money, long activity, boolean deposit, String desc) {
    public GuildQuestDef {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "gq.bad_id", "길드 의뢰 id 형식: " + id);
        DomainException.require(counter != null && !counter.isBlank() && target > 0 && money >= 0 && activity >= 0, "gq.bad", "길드 의뢰 수치: " + id);
    }
}
