package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.origin.Gender;
import io.versaera.domain.quest.QuestDefinition.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 튜토리얼: 시작 도시를 고르면 열리고, 단계마다 저절로 끝나고 다음이 열린다 */
class TutorialTest {
    @Test
    void walksThroughEveryStepAndPays() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertTrue(w.s.tutorial.tick(p, "p").done(), "출신을 고르기 전에는 시작하지 않는다");
            w.s.origins.create(p, "human", Gender.MALE, "serabourg");
            var v = w.s.tutorial.tick(p, "p");
            assertEquals(7, v.steps());
            assertEquals("tutorial.1_training", v.current().id());
            assertEquals("tutorial.1_training", v.started().id());
            long before = w.s.economy.balance(p);
            w.s.quests.record(p, Type.TRAIN, "dummy", 30, 0);
            v = w.s.tutorial.tick(p, "p");
            assertEquals("tutorial.1_training", v.finished().id());
            assertEquals("tutorial.2_first_hunt", v.current().id());
            assertEquals("tutorial.2_first_hunt", v.started().id());
            assertTrue(w.s.economy.balance(p) > before, "단계 보상");
            w.s.quests.record(p, Type.KILL, "rabbit", 5, 0);
            assertEquals("tutorial.3_flax", w.s.tutorial.tick(p, "p").current().id());
            w.s.quests.record(p, Type.GATHER, "flax_fiber", 6, 300);
            assertEquals("tutorial.4_weave", w.s.tutorial.tick(p, "p").current().id());
            w.s.quests.record(p, Type.CRAFT, "weave_linen", 1, 300);
            assertEquals("tutorial.5_people", w.s.tutorial.tick(p, "p").current().id());
            w.s.quests.record(p, Type.TALK, "anyone_at_all", 3, 0);
            assertEquals("tutorial.6_skill", w.s.tutorial.tick(p, "p").current().id());
            w.s.quests.record(p, Type.SKILL, "quick_slash", 3, 0);
            v = w.s.tutorial.tick(p, "p");
            assertEquals("tutorial.7_graduation", v.current().id());
            assertArrayEquals(new int[]{0}, v.progress());
            w.s.quests.record(p, Type.KILL, "fox", 20, 0);
            v = w.s.tutorial.tick(p, "p");
            assertTrue(v.done());
            assertEquals("tutorial.7_graduation", v.finished().id());
            assertTrue(w.s.tutorial.tick(p, "p").done(), "끝난 뒤에는 조용하다");
            assertNull(w.s.tutorial.tick(p, "p").finished(), "보상은 한 번");
        }
    }

    @Test
    void canBeSkipped() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.origins.create(p, "human", Gender.FEMALE, "serabourg");
            assertFalse(w.s.tutorial.tick(p, "p").done());
            w.s.tutorial.skip(p);
            assertTrue(w.s.tutorial.tick(p, "p").done());
            assertNull(w.s.quests.active(p).stream().filter(a -> a.def().id().startsWith("tutorial.")).findAny().orElse(null), "진행 중이던 단계를 내려놓는다");
        }
    }
}
