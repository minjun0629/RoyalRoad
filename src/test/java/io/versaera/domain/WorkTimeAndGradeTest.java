package io.versaera.domain;

import io.versaera.domain.art.ArtGrade;
import io.versaera.domain.craft.WorkTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkTimeAndGradeTest {
    @Test
    void gradesFollowQuality() {
        assertEquals("졸작", ArtGrade.name(0));
        assertEquals("평작", ArtGrade.name(250));
        assertEquals("수작", ArtGrade.name(600));
        assertEquals("명작", ArtGrade.name(720));
        assertEquals("대작", ArtGrade.name(1000));
    }

    @Test
    void harderTakesLongerAndSkillSpeedsUpToHalf() {
        assertTrue(WorkTime.artwork(30, 30) > WorkTime.artwork(0, 0));
        assertTrue(WorkTime.artwork(10, 31) < WorkTime.artwork(10, 10));
        assertTrue(WorkTime.artwork(10, 31) >= WorkTime.artwork(10, 10) / 2);
        assertTrue(WorkTime.craft(0, 0) >= 2 && WorkTime.craft(31, 31) <= 9);
        assertTrue(WorkTime.craft(5, 30) < WorkTime.craft(5, 5));
    }
}
