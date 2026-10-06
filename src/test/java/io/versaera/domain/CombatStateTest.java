package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.combat.CombatState;
import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.combat.StatusEffect;
import io.versaera.domain.combat.StatusTracker;
import io.versaera.domain.common.DomainException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.versaera.domain.combat.CombatState.Input.HEAVY;
import static io.versaera.domain.combat.CombatState.Input.LIGHT;
import static org.junit.jupiter.api.Assertions.*;

class CombatStateTest {
    private static final ContentBundle C = ContentBundle.fromClasspath(CombatStateTest.class.getClassLoader());

    private static SkillDefinition skill(String id) {
        return C.skills().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void cooldownAndResourceAreServerSide() {
        CombatState c = new CombatState(100, 30, 0);
        SkillDefinition s = skill("slash_arc");
        c.use(s, 0, 1);
        assertEquals(75, c.stamina());
        assertEquals("skill.cooldown", assertThrows(DomainException.class, () -> c.use(s, 1000, 1)).code());
        c.use(s, s.cooldownMs(), 1);
        SkillDefinition mana = skill("frost_nova");
        c.use(mana, 7000, 1);
        assertEquals("skill.no_resource", assertThrows(DomainException.class, () -> c.use(skill("fire_lance"), 7000, 1)).code());
    }

    @Test
    void dodgeGivesShortInvulnerability() {
        CombatState c = new CombatState(100, 0, 0);
        c.dodge(1000);
        assertTrue(c.invulnerable(1000 + CombatState.DODGE_IFRAMES - 1));
        assertFalse(c.invulnerable(1000 + CombatState.DODGE_IFRAMES));
        assertThrows(DomainException.class, () -> c.dodge(1100));
    }

    @Test
    void combosNeedTheRightOrderInsideTheWindow() {
        CombatState c = new CombatState(100, 0, 0);
        var combos = C.combos();
        assertTrue(c.input(LIGHT, 0, combos).isEmpty());
        assertTrue(c.input(LIGHT, 300, combos).isEmpty());
        assertEquals("combo_flurry", c.input(HEAVY, 600, combos).orElseThrow().finisher());
        c.input(LIGHT, 10_000, combos);
        c.input(LIGHT, 10_300, combos);
        assertTrue(c.input(HEAVY, 13_000, combos).isEmpty(), "시간 창을 넘기면 콤보가 아니다");
    }

    @Test
    void repeatedStunsGetShorterAndDotsTick() {
        StatusTracker t = new StatusTracker();
        UUID u = UUID.randomUUID();
        assertEquals(2000, t.apply(u, StatusEffect.STUN, 2, 1, 0));
        assertTrue(t.disabled(u, 1000));
        assertEquals(1000, t.apply(u, StatusEffect.STUN, 2, 1, 3000), "10초 안에 다시 걸면 절반");
        t.apply(u, StatusEffect.BURN, 4, 1, 5000);
        assertFalse(t.tick(5500).isEmpty());
        t.tick(20_000);
        assertTrue(t.of(u).isEmpty(), "만료 정리");
    }
}
