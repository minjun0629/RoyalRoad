package io.versaera.domain.achievement;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.Condition;

/**
 * 업적 (ACH-01): 행동 기록 · 숙련 · 발견 · 관계 조건을 채우면 한 번 얻는다. 지역 · 시각 조건은 쓰지 않는다 (언제 확인해도 같은 답).
 *
 * @param hidden 얻기 전에는 목록에 "???" 로만 보인다
 * @param title  얻으면 같이 받는 칭호 id (없으면 null)
 */
public record Achievement(String id, String name, String category, String desc, Condition when, long money, int fame, String title,
                          boolean hidden, int points) {
    public Achievement {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "ach.bad_id", "업적 id 형식: " + id);
        DomainException.require(money >= 0 && fame >= 0 && points >= 0, "ach.bad_reward", "업적 보상이 잘못되었습니다: " + id);
        DomainException.require(!usesPlace(when), "ach.place", "업적 조건에 지역 · 시각을 쓸 수 없습니다: " + id);
    }

    private static boolean usesPlace(Condition c) {
        return switch (c) {
            case Condition.InRegion r -> true;
            case Condition.Hours h -> true;
            case Condition.All a -> a.parts().stream().anyMatch(Achievement::usesPlace);
            case Condition.Any a -> a.parts().stream().anyMatch(Achievement::usesPlace);
            default -> false;
        };
    }
}
