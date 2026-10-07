package io.versaera.domain.faith;

/** 신전: 그 지역 안에서 기부할 수 있다 */
public record Temple(String id, String god, String region, String source, String note) {}
