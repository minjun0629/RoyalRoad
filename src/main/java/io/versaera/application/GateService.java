package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.skill.Mastery;
import io.versaera.domain.world.Gate;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;

/**
 * 문 (WLD-03). 문 지역에 들어선 사람을 다른 땅 · 다른 차원으로 옮겨도 되는지 판정한다.
 * 판정은 서버가 DB 의 탐험 숙련으로 한다 (클라이언트 값을 믿지 않음). 이동 자체는 platform 이 한다.
 */
public final class GateService {
    public record Decision(boolean allowed, Gate gate, int have, int need) {
        public String reason() {
            return allowed ? "" : "탐험 숙련 " + Mastery.label(need) + " 이상이 되어야 「" + gate.name() + "」을(를) 건널 수 있다 (지금 " + Mastery.label(have) + ")";
        }
    }

    private final Map<String, Gate> byRegion = new LinkedHashMap<>();
    private final ToIntBiFunction<String, String> level;

    /** @param level (uuid, 분야) → 숙련 레벨. 보통 GrowthService::level (DB 를 읽으므로 DB 스레드에서 부른다) */
    public GateService(Collection<Gate> gates, RegionIndex regions, ToIntBiFunction<String, String> level) {
        this.level = level;
        for (Gate g : gates) {
            Region from = regions.byId(g.region());
            DomainException.require(from != null, "gate.no_region", "문 지역이 없습니다: " + g.id() + " → " + g.region());
            DomainException.require(byRegion.putIfAbsent(g.region(), g) == null, "gate.dup_region", "한 지역에 문이 둘: " + g.region());
        }
        for (Gate g : gates) {
            Region to = regions.at(g.toWorld(), g.toX(), 70, g.toZ());
            DomainException.require(to != null, "gate.no_destination", "도착점이 어느 지역에도 없습니다: " + g.id());
            DomainException.require(!byRegion.containsKey(to.id()), "gate.loop", "도착점이 다른 문 안입니다 (왕복 반복): " + g.id());
        }
    }

    public Optional<Gate> at(String regionId) {
        return Optional.ofNullable(regionId == null ? null : byRegion.get(regionId));
    }

    public Set<String> gateRegions() {
        return byRegion.keySet();
    }

    public Collection<Gate> all() {
        return byRegion.values();
    }

    public Decision check(String uuid, Gate g) {
        int have = level.applyAsInt(uuid, "exploration");
        return new Decision(have >= g.minExploration(), g, have, g.minExploration());
    }
}
