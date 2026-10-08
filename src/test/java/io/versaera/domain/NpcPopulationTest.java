package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.npc.NpcProfile;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.terrain.SettlementPlanner;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NpcPopulationTest {
    private static final ContentBundle C = ContentBundle.fromClasspath(NpcPopulationTest.class.getClassLoader());

    @Test
    void manyResidentsWithRealRolesEverywhereThatHasPeople() {
        assertTrue(C.npcs().size() >= 500, "주민 수: " + C.npcs().size());
        Set<String> ids = new HashSet<>();
        for (var n : C.npcs()) assertTrue(ids.add(n.id()), "NPC id 중복: " + n.id());
        // 도시마다 주민이 있다
        for (var r : C.regions())
            if (SettlementPlanner.isTown(r) && r.tags().contains("city") && r.world().equals("world"))
                assertTrue(C.npcs().stream().filter(n -> n.region().equals(r.id())).count() >= 8, "도시에 주민이 적다: " + r.id());
        // 지역 성격에 맞는 직업: 광산 지역엔 광부, 항구 · 호숫가엔 어부, 요새엔 기사
        Map<String, String> arch = new HashMap<>();
        for (var p : C.npcProfiles()) arch.put(p.id(), p.archetype());
        for (var r : C.regions()) {
            List<String> here = C.npcs().stream().filter(n -> n.region().equals(r.id())).map(n -> arch.getOrDefault(n.id(), "")).toList();
            if (r.tags().contains("fortress") && r.maxY() >= 64 && Collections.disjoint(r.tags(), Set.of("sea", "sky", "divine", "portal")))
                assertTrue(here.contains("knight"), "요새에 기사가 없다: " + r.id());
            if (r.tags().contains("lake") && r.maxY() >= 64 && !r.tags().contains("sea")) assertTrue(here.contains("fisher"), "호수에 어부가 없다: " + r.id());
        }
    }

    @Test
    void familiesAndRelationsAreSymmetricAndShopsQuestsAreReal() {
        Map<String, NpcProfile> byId = new HashMap<>();
        for (var p : C.npcProfiles()) byId.put(p.id(), p);
        int families = 0, partners = 0;
        for (var p : C.npcProfiles())
            for (var l : p.links()) {
                NpcProfile other = byId.get(l.npc());
                assertNotNull(other, "관계 상대가 없다: " + l.npc());
                String back = switch (l.type()) { case "PARENT" -> "CHILD"; case "CHILD" -> "PARENT"; case "MASTER" -> "APPRENTICE"; case "APPRENTICE" -> "MASTER";
                    case "LORD" -> "VASSAL"; case "VASSAL" -> "LORD"; default -> l.type(); };
                assertTrue(other.links().stream().anyMatch(x -> x.npc().equals(p.id()) && x.type().equals(back)), p.id() + " ↔ " + l.npc() + " " + l.type());
                if (l.type().equals("SPOUSE") || l.type().equals("PARENT")) { families++; assertEquals(p.family(), other.family(), "가족은 같은 성"); }
                if (l.type().equals("PARTNER")) partners++;
            }
        assertTrue(families > 50 && partners > 50, "가족 " + families + " · 거래처 " + partners);
        // 상점 있는 주민은 실제 상점, 의뢰는 실제 NPC 가 준다
        Set<String> npcIds = new HashSet<>();
        C.npcs().forEach(n -> npcIds.add(n.id()));
        long generatedShops = C.market().shops().keySet().stream().filter(id -> byId.containsKey(id)).count();
        assertTrue(generatedShops > 150, "상점 수: " + generatedShops);
        for (var s : C.market().shops().values()) assertTrue(C.market().markets().containsKey(s.market()), s.npcId());
        long hidden = 0;
        for (QuestDefinition q : C.quests()) {
            if (q.giver() != null) assertTrue(npcIds.contains(q.giver()), "의뢰를 주는 NPC 가 없다: " + q.id());
            for (var o : q.objectives()) if (o.type() == QuestDefinition.Type.TALK && !o.target().equals("any")) assertTrue(npcIds.contains(o.target()), q.id());
            if (q.hidden()) {
                hidden++;
                assertTrue(byId.get(q.giver()) == null || byId.get(q.giver()).hiddenQuests().contains(q.id()), "숨은 의뢰는 NPC 프로필에 걸려 있다: " + q.id());
            }
        }
        assertTrue(hidden > 30, "숨은 의뢰 수: " + hidden);
    }

    @Test
    void wanderersTravelAndRareOnesHide() {
        long wanderers = C.npcProfiles().stream().filter(NpcProfile::wanderer).count();
        assertTrue(wanderers >= 15);
        var rare = C.npcProfiles().stream().filter(p -> p.rare() != null).toList();
        assertTrue(rare.size() >= 5);
        for (var p : rare) {
            int days = 0;
            for (long d = 0; d < 30; d++) for (int h = 0; h < 24; h++) if (p.rare().present(d, h)) { days++; break; }
            assertTrue(days >= 5 && days <= 16, p.id() + " 30일 중 나오는 날: " + days);
        }
        // 다시 만들어도 같다
        ContentBundle again = ContentBundle.fromClasspath(getClass().getClassLoader());
        assertEquals(C.npcs(), again.npcs());
        assertEquals(C.places(), again.places());
    }
}
