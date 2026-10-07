package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.party.Parties;
import io.versaera.domain.potion.PotionRule;
import io.versaera.domain.time.GameTime;
import io.versaera.domain.world.RegionIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 시간 4배 · 물약 · 파티 · 캐릭터 만들기 데이터 */
class CanonDomainTest {
    @Test
    void gameTimeRunsFourTimesFaster() {
        GameTime t = new GameTime(4);
        long day = 86_400_000L;
        assertEquals(4, t.day(day) - t.day(0), "현실 하루 = 게임 4일");
        assertEquals(6, t.hour(day / 16), "현실 1.5시간 = 게임 6시간");
        assertEquals(0, t.minecraftTime(day / 16), "게임 아침 6시 = 마인크래프트 시간 0");
        assertEquals(6_000, t.minecraftTime(day / 8), "정오 = 6000");
        assertEquals((long) (7.5 * day), t.realMillisFor(30), "게임 한 달 = 현실 7.5일");
        assertEquals(30L * 1_200_000L, new GameTime(0).realMillisFor(30), "비율 0 = 마인크래프트 하루 20분");
        assertThrows(DomainException.class, () -> new GameTime(-1));
    }

    @Test
    void potionsBoostRecoveryDoNotStackAndFadeWithLevel() {
        PotionRule.Effect low = PotionRule.effect(1, 5), high = PotionRule.effect(1, 25), useless = PotionRule.effect(1, 31);
        assertTrue(low.seconds() > high.seconds(), "레벨이 높을수록 효율이 떨어진다");
        assertTrue(useless.none(), "아주 높으면 의미가 없다");
        assertEquals(PotionRule.effect(2, 20), PotionRule.effect(2, 20), "확률 없음");
        assertFalse(PotionRule.canDrink(1000, 2000), "효과가 남아 있으면 이어 마실 수 없다");
        assertTrue(PotionRule.canDrink(2000, 2000));
    }

    @Test
    void partiesInviteAcceptLeave() {
        Parties p = new Parties();
        p.invite("a", "b");
        assertEquals("a", p.accept("b"));
        assertTrue(p.together("a", "b"));
        assertThrows(DomainException.class, () -> p.invite("b", "c"), "파티장만 초대");
        assertThrows(DomainException.class, () -> p.accept("c"), "초대 없음");
        p.invite("a", "c");
        p.accept("c");
        assertEquals(java.util.List.of("a", "b", "c"), p.members("b"));
        p.leave("a");
        assertEquals("b", p.leader("c").orElseThrow(), "파티장이 나가면 다음 사람이 이어받는다");
        p.leave("b");
        assertTrue(p.leader("c").isEmpty(), "혼자 남으면 해산");
        for (int i = 1; i < Parties.MAX; i++) {
            p.invite("x", "m" + i);
            p.accept("m" + i);
        }
        assertThrows(DomainException.class, () -> p.invite("x", "late"), "8명까지");
    }

    @Test
    void originsContentMatchesTheMap() {
        ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());
        RegionIndex regions = new RegionIndex(c.regions());
        assertEquals(java.util.List.of("human", "elf", "dwarf", "orc", "birdfolk"), c.origins().races().stream().map(r -> r.id()).toList());
        for (var city : c.origins().cities()) assertNotNull(regions.byId(city.region()), city.id());
        assertEquals(30, c.origins().beginnerGameDays());
        assertTrue(c.gods().stream().anyMatch(g -> g.name().equals("프레야")) && c.gods().stream().filter(g -> g.evil()).count() == 2);
        assertTrue(c.eras().size() >= 7);
        assertTrue(c.npcs().stream().anyMatch(n -> n.evil()), "악한 NPC 가 하나는 있다");
    }
}
