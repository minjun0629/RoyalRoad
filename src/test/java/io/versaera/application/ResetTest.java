package io.versaera.application;

import io.versaera.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResetTest {
    @Test
    void resetClearsDiscoveriesWorldFirstsAndOneTimeRewards() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertTrue(w.s.exploration.discover(p, "A", "region", "harden").worldFirst());
            w.s.growth.record(p, "kill.monster", 1);
            long reward = w.s.achievements.achievement("first_blood").money();
            assertEquals(reward, w.s.economy.balance(p));

            w.s.reset.player(p, "admin");
            assertFalse(w.s.progress.discovered(p, "region", "harden"), "발견 기록 초기화");
            assertTrue(w.s.exploration.worldFirst("region", "harden").isEmpty(), "서버 최초 기록 초기화");
            assertEquals(0, w.s.economy.balance(p));

            var again = w.s.exploration.discover(p, "A", "region", "harden");
            assertTrue(again.isNew() && again.worldFirst(), "다시 발견하면 다시 최초");
            w.s.growth.record(p, "kill.monster", 1);
            assertEquals(reward, w.s.economy.balance(p), "최초 보상(업적)을 다시 받는다");
        }
    }
}
