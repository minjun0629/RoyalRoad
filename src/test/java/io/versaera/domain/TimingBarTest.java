package io.versaera.domain;

import io.versaera.domain.combat.TimingBar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimingBarTest {
    @Test
    void markerSweepsBackAndForth() {
        assertEquals(0, TimingBar.position(0, 1000), 1e-9);
        assertEquals(1, TimingBar.position(500, 1000), 1e-9);
        assertEquals(0.5, TimingBar.position(750, 1000), 1e-9);
        assertEquals(0, TimingBar.position(1000, 1000), 1e-9);
    }

    @Test
    void onlyHitsInsideTheZoneCount() {
        assertEquals(TimingBar.Grade.PERFECT, TimingBar.judge(0.5, 0.5, 0.24));
        assertEquals(TimingBar.Grade.GOOD, TimingBar.judge(0.6, 0.5, 0.24));
        assertEquals(TimingBar.Grade.MISS, TimingBar.judge(0.7, 0.5, 0.24));
        assertEquals(0, TimingBar.Grade.MISS.hits);
    }

    @Test
    void spammingClicksMostlyMisses() {
        // 0.05초마다 무작정 누르면 대부분 빗나간다 (칸 밖)
        int counted = 0, tries = 0;
        for (long t = 0; t < 16_000; t += 50, tries++) if (TimingBar.judge(TimingBar.position(t, TimingBar.period(0)), 0.5, TimingBar.width(0)) != TimingBar.Grade.MISS) counted++;
        assertTrue(counted < tries * 0.3, counted + "/" + tries);
    }

    @Test
    void getsHarderWithCombo() {
        assertTrue(TimingBar.period(10) < TimingBar.period(0));
        assertTrue(TimingBar.width(10) < TimingBar.width(0));
        assertEquals(TimingBar.CELLS * 3, TimingBar.render(0.3, 0.5, 0.2).length());
    }
}
