package io.versaera.domain.art;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.Condition;

/**
 * 비기 (content/secret_arts.yml, 이름 CANON). 원작: 각 직업에는 마스터 직전에 만들 수 있는 최상급 스킬 '비기'가 있고,
 * 비기에 관련된 물건(조각사는 조각상)으로 배우거나 스스로 깨우친다. 그 직업의 비기를 모두 모으면 '최후의 비기'에 도전할 수 있다.
 *
 * @param relic    이 고유 아이템(손에 듦)을 바치면 배운다 (없으면 null)
 * @param discover 이 조건을 채우면 스스로 깨우친다 (없으면 null)
 * @param effect   COMPANION · TRANSFORM · REVIVE · SPIRITS · TIME · FEAST
 * @param finalArt 최후의 비기: 같은 직업의 다른 비기를 모두 배워야 한다
 */
public record SecretArt(String id, String name, String job, String discipline, int minLevel, String relic, Condition discover,
                        String effect, long cooldownMs, boolean finalArt, String description, String source) {
    public static final java.util.Set<String> EFFECTS = java.util.Set.of("COMPANION", "TRANSFORM", "REVIVE", "SPIRITS", "TIME", "FEAST");

    public SecretArt {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "art.bad_id", "비기 id 형식: " + id);
        DomainException.require(EFFECTS.contains(effect), "art.bad_effect", "없는 비기 효과: " + effect);
        DomainException.require(minLevel >= 1 && minLevel <= 31, "art.bad_level", "비기 숙련 레벨은 1 ~ 31: " + id);
        DomainException.require(relic != null || discover != null || finalArt, "art.no_way", "배울 방법이 없는 비기: " + id);
    }

    /** 시간 조각술의 단계: 29 초급(시간 가속) · 30 중급(시간 정지) · 31 고급(시간 여행) — 원작의 초급 · 중급 · 고급 */
    public static int timeTier(int level) {
        return level >= 31 ? 3 : level >= 30 ? 2 : 1;
    }
}
