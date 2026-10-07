package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkillBookTest {
    @Test
    void loadoutDependsOnWeaponMasteryAndJob() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals(List.of("quick_slash"), w.s.skills.loadout(p, "sword").stream().map(x -> x.id()).toList());
            assertEquals(List.of("aimed_shot"), w.s.skills.loadout(p, "bow").stream().map(x -> x.id()).toList());
            assertTrue(w.s.skills.loadout(p, null).isEmpty(), "맨손엔 무기 스킬 없음");
            w.s.tx.inTx(() -> { w.s.progress.setMasteryXp(p, "swordsmanship", Mastery.cumulative(8)); return null; });
            w.s.growth.record(p, "kill.monster", 30);
            w.s.jobs.advance(p, "swordsman", w.s.facts(p, null, 12));
            var ids = w.s.skills.loadout(p, "sword").stream().map(x -> x.id()).toList();
            assertEquals("quick_slash", ids.get(0));
            assertEquals("guard_break", ids.get(1), "직업 스킬이 두 번째 칸");
            assertFalse(w.s.skills.canFinish(TestWorld.player(), "combo_rising"), "숙련이 모자라면 콤보 마무리 불가");
            assertTrue(w.s.skills.canFinish(p, "combo_rising"));
        }
    }

    @Test
    void discoveriesAndCraftsAdvanceQuests() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.quests.accept(p, "harden.tavern_menu", w.s.facts(p, null, 12));
            assertEquals(QuestDefinition.Type.CRAFT, w.s.quests.active(p).get(0).def().objectives().get(0).type());
            w.s.bus.publish(new io.versaera.domain.event.GameEvents.PlayerCrafted(p, "cook_stew", "x", 500));
            assertEquals(1, w.s.quests.active(p).get(0).progress().get(0));
        }
    }
}
