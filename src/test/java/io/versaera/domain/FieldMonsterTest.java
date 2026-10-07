package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.world.FieldMonster;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 들판 몬스터 (monsters.yml): 원작 서식지 · 지역마다 몬스터 · 전리품 · 몸 */
class FieldMonsterTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
    private final RegionIndex regions = new RegionIndex(c.regions());
    private final List<FieldMonster> all = c.expansion().monsters();

    /** 1.20.1 에서 들판에 내보낼 수 있는 몸 (좀비화하는 피글린 · 호글린은 쓰지 않는다) */
    static final Set<String> BODIES = Set.of("RABBIT", "FOX", "GOAT", "WOLF", "POLAR_BEAR", "ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY",
            "WITHER_SKELETON", "SPIDER", "CAVE_SPIDER", "VINDICATOR", "PILLAGER", "WITCH", "RAVAGER", "VEX", "PHANTOM", "IRON_GOLEM", "ZOMBIE_VILLAGER");

    private Set<String> names(Region r) {
        return FieldMonster.table(all, r, regions).keySet().stream().map(FieldMonster::name).collect(Collectors.toSet());
    }

    @Test
    void canonHabitatsGetTheirMonsters() {
        assertTrue(names(regions.byId("plains_of_despair")).containsAll(Set.of("오크 전사", "미노타우로스", "평원의 사냥꾼")));
        assertTrue(names(regions.byId("yurokina_range")).containsAll(Set.of("다크엘프 전사", "다크엘프 궁수", "다크 랜서", "다크 샤먼")));
        assertTrue(names(regions.byId("sendeim_valley")).containsAll(Set.of("스켈레톤", "좀비", "구울", "데스 나이트")));
        assertTrue(names(regions.byId("rosenheim_frontier")).contains("리자드맨"));
        assertTrue(names(regions.byId("exile_wall")).contains("도플갱어"));
        // 초보 땅: 토끼 · 여우, 오크는 없다
        Set<String> rosenheim = names(regions.byId("quiet_plains"));
        assertTrue(rosenheim.containsAll(Set.of("토끼", "여우")), rosenheim.toString());
        assertFalse(rosenheim.contains("오크 전사"));
        // 원작 서식지 몬스터가 가장 자주 나온다
        Map<FieldMonster, Integer> t = FieldMonster.table(all, regions.byId("plains_of_despair"), regions);
        FieldMonster top = t.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        assertEquals("오크 전사", top.name());
    }

    @Test
    void everyLandRegionHasSomethingToHunt() {
        for (Region r : regions.all()) {
            if (!r.world().equals("world") || r.tags().contains("sea") || r.tags().contains("city") || r.tags().contains("portal")) continue;
            assertFalse(FieldMonster.table(all, r, regions).isEmpty(), "몬스터 없는 지역: " + r.id() + " " + r.tags() + " 위험도 " + r.danger());
        }
    }

    @Test
    void dropsBodiesAndHabitatsAreValid() {
        Set<String> items = c.items().stream().map(ItemType::id).collect(Collectors.toSet());
        Set<String> ids = new HashSet<>();
        for (FieldMonster m : all) {
            assertTrue(ids.add(m.id()));
            assertTrue(BODIES.contains(m.entity()), m.id() + " 몸: " + m.entity());
            for (var d : m.drops()) assertTrue(items.contains(d.item()), m.id() + " 없는 전리품 " + d.item());
            for (String r : m.regions()) assertNotNull(regions.byId(r), m.id() + " 없는 지역 " + r);
        }
        long canon = all.stream().filter(m -> m.source().equals("CANON")).count();
        assertTrue(canon >= 25, "원작 몬스터 " + canon);
        assertEquals(1, FieldMonster.pick(Map.of(all.get(0), 1), 0.99) == all.get(0) ? 1 : 0);
    }
}
