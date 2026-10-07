package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoonlightSculptorTest {
    @Test
    void hiddenJobAppearsOnlyAfterAMasterpieceAndReplacesSculptor() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            w.s.tx.inTx(() -> {
                w.s.progress.setMasteryXp(p, "sculpting", Mastery.cumulative(20));
                w.s.progress.addCounter(p, "art.experience", 600);
                return null;
            });
            w.s.jobs.advance(p, "sculptor", w.s.facts(p, null, 12));
            assertTrue(w.s.jobs.available(p, w.s.facts(p, null, 12)).stream().noneMatch(j -> j.id().equals("moonlight_sculptor")), "명작 전엔 보이지 않는다");
            w.s.tx.inTx(() -> { w.s.progress.addCounter(p, "art.masterpiece", 1); return null; });
            assertTrue(w.s.jobs.available(p, w.s.facts(p, null, 12)).stream().anyMatch(j -> j.id().equals("moonlight_sculptor")));
            assertEquals("달빛 조각사", w.s.jobs.advance(p, "moonlight_sculptor", w.s.facts(p, null, 12)).name());
            assertEquals("moonlight_sculptor", w.s.jobs.held(p).get("LIFE").jobId());
        }
    }
}
