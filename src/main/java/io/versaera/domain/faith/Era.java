package io.versaera.domain.faith;

/** 역사 시대 (content/history.yml) — 원작 연대기 + 이 게임의 후대 */
public record Era(String id, String name, String when, String summary, String source) {}
