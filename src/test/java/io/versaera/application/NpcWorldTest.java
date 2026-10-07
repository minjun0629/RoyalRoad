package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.npc.NpcProfile;
import io.versaera.domain.npc.Relation;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NpcWorldTest {
    private static NpcProfile find(TestWorld w, String arch, boolean withLinks) {
        return w.s.npcWorld.profiles().stream().filter(p -> p.archetype().equals(arch) && !p.wanderer() && p.rare() == null
                && (!withLinks || p.links().stream().anyMatch(l -> l.type().equals("SPOUSE") || l.type().equals("PARTNER")))
                && (!withLinks || !p.hiddenQuests().isEmpty())).findFirst().orElseThrow();
    }

    @Test
    void stagesUnlockServicesAndHiddenQuestsAndRelationsSpread() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            NpcProfile smith = find(w, "blacksmith", true);
            var talk = w.s.npcWorld.talk(p, smith.id());
            assertEquals(Relation.Stage.KNOWN, talk.stage(), "첫 대화 = 인지");
            assertNotNull(talk.line());
            assertThrows(DomainException.class, () -> w.s.npcWorld.train(p, smith.id()), "우호 전에는 지도하지 않는다");
            // 신뢰에 이르면 숨은 의뢰가 열린다
            String hidden = smith.hiddenQuests().get(0);
            assertFalse(w.s.progress.discovered(p, "quest", hidden));
            w.s.relations.adjust(p, smith.id(), 350);
            assertEquals(Relation.Stage.TRUST, w.s.npcWorld.stage(p, smith.id()));
            assertTrue(w.s.progress.discovered(p, "quest", hidden), "신뢰 = 숨은 의뢰");
            assertTrue(w.s.npcWorld.shopDiscount(p, smith.id()) >= 0.07);
            // 숙련 지도: 하루 한 번, 돈을 내고
            w.s.economy.deposit(p, 10_000, "test", "t1");
            var lesson = w.s.npcWorld.train(p, smith.id());
            assertEquals("smithing", lesson.discipline());
            assertTrue(w.s.growth.xp(p, "smithing") > 0);
            assertThrows(DomainException.class, () -> w.s.npcWorld.train(p, smith.id()), "같은 날 두 번 배우지 않는다");
            // 관계가 퍼진다: 가족 · 거래처도 조금 가까워진다
            NpcProfile.Link l = smith.links().stream().filter(x -> !x.type().equals("RIVAL")).findFirst().orElseThrow();
            int before = w.s.relations.affinity(p, l.npc());
            w.s.npcWorld.spread(p, smith.id(), 100);
            assertTrue(w.s.relations.affinity(p, l.npc()) > before, "관계가 퍼진다");
            assertEquals("FAMILY", w.s.npcWorld.memories(p, l.npc(), 1).get(0).kind(), "퍼진 쪽에도 기억이 남는다");
            assertNotNull(w.s.npcWorld.talk(p, l.npc()).memoryLine(), "기억을 대사로 꺼낸다");
        }
    }

    @Test
    void giftsAreRememberedRumorsPointToUnseenPlacesHostileRefusesTrade() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            NpcProfile smith = find(w, "blacksmith", false);
            int g = w.s.npcWorld.gift(p, smith.id(), Set.of("metal"), 800, "철 주괴");
            assertTrue(g >= 10);
            assertEquals("GIFT_LIKED", w.s.npcWorld.memories(p, smith.id(), 1).get(0).kind());
            assertTrue(w.s.npcWorld.talk(p, smith.id()).memoryLine().contains("철 주괴"));
            // 소문: 관심 이상, 아직 안 가 본 곳의 방향 · 거리
            NpcProfile gossip = w.s.npcWorld.profiles().stream().filter(x -> !x.rumors().isEmpty() && x.rare() == null && !x.wanderer()
                    && w.s.content.archetypes().get(x.archetype()).offers("RUMOR")).findFirst().orElseThrow();
            assertThrows(DomainException.class, () -> w.s.npcWorld.rumor(p, gossip.id()), "처음엔 소문을 들려주지 않는다");
            w.s.npcWorld.talk(p, gossip.id());
            w.s.relations.adjust(p, gossip.id(), 80);
            var rumor = w.s.npcWorld.rumor(p, gossip.id());
            assertNotNull(rumor);
            assertTrue(rumor.distance() >= 0 && !rumor.direction().isEmpty());
            assertTrue(w.s.progress.discovered(p, "rumor", rumor.regionId()));
            // 적대면 거래하지 않는다
            w.s.relations.adjust(p, smith.id(), -2000);
            assertEquals(Relation.Stage.HOSTILE, w.s.npcWorld.stage(p, smith.id()));
            assertThrows(DomainException.class, () -> w.s.npcWorld.shopDiscount(p, smith.id()));
            assertThrows(DomainException.class, () -> w.s.market.buy(p, smith.id(), 0, 1, "hostile-buy"));
        }
    }

    @Test
    void npcRepairCostsMoneyAndRestoresDurability() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            NpcProfile smith = find(w, "blacksmith", false);
            var it = w.s.items.create("iron_longsword", 600, null, "t", "t", Map.of(), p, "rep:" + UUID.randomUUID());
            w.s.items.confirmDelivered(it.id(), p);
            w.s.items.wear(it.id(), p, 100, false);
            assertThrows(DomainException.class, () -> w.s.npcWorld.repair(p, smith.id(), it.id(), "r0"), "돈이 없으면 못 고친다");
            w.s.economy.deposit(p, 5000, "test", "t2");
            w.s.npcWorld.repair(p, smith.id(), it.id(), "r1");
            var after = w.s.items.find(it.id()).orElseThrow();
            assertEquals(after.maxDurability(), after.durability());
            assertTrue(w.s.economy.balance(p) < 5000);
        }
    }

    @Test
    void prosperityRisesWithHelpDecaysAndMovesLuxuryMerchants() throws Exception {
        try (TestWorld w = new TestWorld()) {
            NpcProfile lux = w.s.npcWorld.profiles().stream().filter(x -> NpcWorldService.LUXURY.contains(x.archetype()) && !x.wanderer()).findFirst().orElseThrow();
            String region = w.s.relations.npc(lux.id()).region();
            assertEquals(NpcWorldService.Tier.NORMAL, w.s.npcWorld.tier(region));
            assertTrue(w.s.npcWorld.present(lux.id()));
            w.s.npcWorld.contribute(region, 650, "k1");
            assertEquals(NpcWorldService.Tier.FLOURISHING, w.s.npcWorld.tier(region));
            assertEquals(650, w.s.npcWorld.contribute(region, 650, "k1"), "같은 일로 두 번 오르지 않는다");
            w.now.addAndGet(7L * 86_400_000L);
            assertEquals(325, w.s.npcWorld.prosperity(region), "1주 반감기");
            w.s.npcWorld.contribute(region, -800, "attack");
            assertEquals(NpcWorldService.Tier.DECLINE, w.s.npcWorld.tier(region));
            assertFalse(w.s.npcWorld.present(lux.id()), "쇠퇴하면 귀한 상인이 떠난다");
            // 지역 경제: 생산자가 시장 공급을 늘린다
            assertTrue(w.s.npcWorld.economyTick() > 0);
        }
    }

    @Test
    void questCompletionIsRememberedAndHelpsTheRegion() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var q = w.s.quests.all().stream().filter(x -> x.giver() != null && w.s.npcWorld.profile(x.giver()).isPresent() && !x.hidden()
                    && x.objectives().size() == 1 && x.objectives().get(0).type() == io.versaera.domain.quest.QuestDefinition.Type.KILL).findFirst().orElseThrow();
            String region = w.s.relations.npc(q.giver()).region();
            var f = w.s.facts(p, null, 12);
            w.s.quests.accept(p, q.id(), f);
            var o = q.objectives().get(0);
            w.s.quests.record(p, o.type(), o.target().equals("any") ? "zombie" : o.target().toLowerCase(java.util.Locale.ROOT), o.amount(), 0);
            w.s.quests.complete(p, "P", q.id(), null, java.util.List.of());
            assertEquals("QUEST", w.s.npcWorld.memories(p, q.giver(), 1).get(0).kind());
            assertTrue(w.s.npcWorld.prosperity(region) > 0, "의뢰가 지역을 번영시킨다");
        }
    }
}
