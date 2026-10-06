package io.versaera.application;

import io.versaera.content.ContentBundle;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.world.Gate;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GateServiceTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
    private final RegionIndex regions = new RegionIndex(c.regions());

    @Test
    void explorationLevelDecidesAndNothingIsRandom() {
        Map<String, Integer> lv = new HashMap<>(Map.of("a", 10, "b", 11, "c", 31));
        GateService g = new GateService(c.gates(), regions, (u, d) -> {
            assertEquals("exploration", d);
            return lv.get(u);
        });
        Gate spirit = g.at("spirit_spring").orElseThrow(), divine = g.at("divine_gate").orElseThrow();
        assertFalse(g.check("a", spirit).allowed());
        assertTrue(g.check("a", spirit).reason().contains("중급"));
        assertTrue(g.check("b", spirit).allowed());
        assertFalse(g.check("b", divine).allowed(), "신계는 탐험 마스터만");
        assertTrue(g.check("c", divine).allowed());
        for (int i = 0; i < 50; i++) assertTrue(g.check("b", spirit).allowed(), "같은 사람은 언제나 같은 결과");
        assertTrue(g.at("harden").isEmpty());
    }

    @Test
    void everyRealmHasAWayInAndAWayBack() {
        GateService g = new GateService(c.gates(), regions, (u, d) -> 1);
        Set<String> realms = Set.of("giant_realm", "divine_realm", "spirit_realm", "fairy_realm", "demonkind_realm", "devil_realm", "hell_realm", "todum_realm");
        for (String realm : realms) {
            Region r = regions.byId(realm);
            assertEquals("versa_realms", r.world(), realm);
            boolean in = false, out = false;
            for (Gate x : g.all()) {
                Region from = regions.byId(x.region()), to = regions.at(x.toWorld(), x.toX(), 70, x.toZ());
                if (realm.equals(top(to).id()) && !realm.equals(top(from).id())) in = true;
                if (realm.equals(top(from).id()) && !realm.equals(top(to).id())) out = true;
                assertTrue(from.tags().contains("portal"), "문 지역은 portal 태그: " + x.region());
            }
            assertTrue(in && out, "들어가는 문과 나오는 문: " + realm);
        }
        // 다른 차원에서 나오는 문은 결국 베르사(world)로 이어진다 (신계 → 거인계 → 베르사)
        for (Gate x : g.all()) if (x.minExploration() == 1) assertTrue(x.id().startsWith("from_"), x.id());
    }

    @Test
    void badGatesAreRejected() {
        assertThrows(DomainException.class, () -> new GateService(List.of(new Gate("x", "x", "nowhere", "world", 0, 0, 1, "ORIGINAL", "")), regions, (u, d) -> 1));
        assertThrows(DomainException.class, () -> new GateService(List.of(new Gate("x", "x", "spirit_spring", "world", center(regions.byId("spirit_spring"))[0], center(regions.byId("spirit_spring"))[1], 1, "ORIGINAL", "")), regions, (u, d) -> 1),
                "도착점이 문 안이면 왕복 반복");
        assertThrows(DomainException.class, () -> new Gate("x", "x", "spirit_spring", "world", 0, 0, 40, "ORIGINAL", ""));
    }

    private static int[] center(Region r) {
        return new int[]{(r.minX() + r.maxX()) / 2, (r.minZ() + r.maxZ()) / 2};
    }

    private Region top(Region r) {
        while (r.parent() != null && !r.id().endsWith("_realm")) r = regions.byId(r.parent());
        return r;
    }
}
