package io.versaera.domain.achievement;

import io.versaera.domain.common.DomainException;

/** 칭호 (ACH-02): 이름 앞에 붙는 글. 업적 · 히든 · 레이드 · 의뢰로 얻는다 (얻은 기록 = discovery kind 'title') */
public record Title(String id, String name, String color, String desc) {
    public Title {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "title.bad_id", "칭호 id 형식: " + id);
        DomainException.require(name != null && !name.isBlank() && name.length() <= 16, "title.bad_name", "칭호 이름은 1~16자: " + id);
        DomainException.require(color != null && color.matches("&[0-9a-f]"), "title.bad_color", "칭호 색은 &0~&f: " + id);
    }
}
