package io.versaera.domain.faith;

import io.versaera.domain.common.DomainException;

/**
 * 신 (content/gods.yml, 이름 · 권능 CANON). 선한 신의 신전에 기부하면 악명이 씻기고 축복을 받는다 (규칙 ORIGINAL).
 *
 * @param blessing 축복 효과 (포션 효과 이름: REGENERATION · SPEED · DAMAGE_RESISTANCE · LUCK · SATURATION · INCREASE_DAMAGE · FAST_DIGGING · NIGHT_VISION). 악신은 null
 */
public record God(String id, String name, String domain, boolean evil, String blessing, String source) {
    public static final java.util.Set<String> BLESSINGS = java.util.Set.of("REGENERATION", "SPEED", "DAMAGE_RESISTANCE", "LUCK", "SATURATION",
            "INCREASE_DAMAGE", "FAST_DIGGING", "NIGHT_VISION");

    public God {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "god.bad_id", "신 id 형식: " + id);
        DomainException.require(evil == (blessing == null), "god.blessing", "선한 신만 축복이 있습니다: " + id);
        DomainException.require(blessing == null || BLESSINGS.contains(blessing), "god.bad_blessing", "없는 축복: " + blessing);
    }
}
