package io.versaera.domain.origin;

import io.versaera.domain.common.DomainException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 캐릭터 만들기 선택지 묶음 + 초보 기간 규칙 */
public record Origins(List<Race> races, List<StartCity> cities, int beginnerGameDays, List<String> startingKit) {
    public Origins {
        races = List.copyOf(races);
        cities = List.copyOf(cities);
        startingKit = List.copyOf(startingKit == null ? List.of() : startingKit);
        DomainException.require(!races.isEmpty() && !cities.isEmpty(), "origins.empty", "종족 · 시작 도시가 하나는 있어야 합니다");
        DomainException.require(beginnerGameDays >= 0 && beginnerGameDays <= 60, "origins.bad_days", "초보 기간은 0 ~ 60 게임일");
        Map<String, Object> seen = new LinkedHashMap<>();
        for (Race r : races) DomainException.require(seen.put("r:" + r.id(), r) == null, "origins.dup", "종족 id 중복: " + r.id());
        for (StartCity c : cities) DomainException.require(seen.put("c:" + c.id(), c) == null, "origins.dup", "시작 도시 id 중복: " + c.id());
    }

    public Race race(String id) {
        for (Race r : races) if (r.id().equals(id)) return r;
        throw DomainException.of("origins.no_race", "없는 종족: " + id);
    }

    public StartCity city(String id) {
        for (StartCity c : cities) if (c.id().equals(id)) return c;
        throw DomainException.of("origins.no_city", "없는 시작 도시: " + id);
    }
}
