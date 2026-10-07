package io.versaera.domain.art;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.Condition;

/**
 * 비기 (content/secret_arts.yml, 이름 CANON). 원작: 각 직업에는 마스터 직전에 만들 수 있는 최상급 스킬 '비기'가 있고,
 * 비기에 관련된 물건(조각사는 조각상)으로 배우거나 스스로 깨우친다. 그 직업의 비기를 모두 모으면 '최후의 비기'에 도전할 수 있다.
 *
 * @param relic    이 고유 아이템(손에 듦)을 바치면 배운다 (없으면 null)
 * @param discover 이 조건을 채우면 스스로 깨우친다 (없으면 null)
 * @param effect   COMPANION · TRANSFORM · REVIVE · SPIRITS · TIME · FEAST · BLADE · DISASTER · RADIANT · SPLIT · OATH · TWIN
 * @param finalArt 최후의 비기: 같은 직업의 다른 비기를 모두 배워야 한다
 */
public record SecretArt(String id, String name, String job, String discipline, int minLevel, String relic, Condition discover,
                        String effect, long cooldownMs, boolean finalArt, String description, String source, java.util.Map<String, String> params) {
    /** BUFF · STRIKE 는 params 로 모양을 정하는 범용 효과 (나머지는 비기마다 따로 짠 효과) */
    public static final java.util.Set<String> EFFECTS = java.util.Set.of("COMPANION", "TRANSFORM", "REVIVE", "SPIRITS", "TIME", "FEAST",
            "BLADE", "DISASTER", "RADIANT", "SPLIT", "OATH", "TWIN", "BUFF", "STRIKE");
    public static final java.util.Set<String> SHAPES = java.util.Set.of("cone", "line", "circle");
    public static final java.util.Set<String> TARGETS = java.util.Set.of("self", "party", "near");

    public SecretArt {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "art.bad_id", "비기 id 형식: " + id);
        DomainException.require(EFFECTS.contains(effect), "art.bad_effect", "없는 비기 효과: " + effect);
        DomainException.require(minLevel >= 1 && minLevel <= 31, "art.bad_level", "비기 숙련 레벨은 1 ~ 31: " + id);
        DomainException.require(relic != null || discover != null || finalArt, "art.no_way", "배울 방법이 없는 비기: " + id);
        params = java.util.Map.copyOf(params == null ? java.util.Map.of() : params);
        if (effect.equals("BUFF")) {
            DomainException.require(params.containsKey("potions"), "art.bad_params", "BUFF 비기에 potions 가 없음: " + id);
            for (String p : params.get("potions").split(","))
                DomainException.require(p.trim().split(":").length == 3, "art.bad_params", "potions 는 \"효과:세기:초\" 목록: " + id);
            DomainException.require(TARGETS.contains(params.getOrDefault("target", "self")), "art.bad_params", "target 은 self · party · near: " + id);
        }
        if (effect.equals("STRIKE")) {
            DomainException.require(SHAPES.contains(params.getOrDefault("shape", "")), "art.bad_params", "STRIKE 의 shape 는 cone · line · circle: " + id);
            DomainException.require(Double.parseDouble(params.getOrDefault("range", "0")) > 0 && Double.parseDouble(params.getOrDefault("damage", "0")) > 0,
                    "art.bad_params", "STRIKE 에 range · damage: " + id);
        }
    }

    public double param(String key, double def) {
        String v = params.get(key);
        return v == null ? def : Double.parseDouble(v);
    }

    /** 시간 조각술의 단계: 29 초급(시간 가속) · 30 중급(시간 정지) · 31 고급(시간 여행) — 원작의 초급 · 중급 · 고급 */
    public static int timeTier(int level) {
        return level >= 31 ? 3 : level >= 30 ? 2 : 1;
    }
}
