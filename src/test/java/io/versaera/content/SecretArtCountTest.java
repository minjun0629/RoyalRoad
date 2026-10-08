package io.versaera.content;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SecretArtCountTest {
    private static final Set<String> POTIONS = Set.of("NIGHT_VISION", "LUCK", "SPEED", "REGENERATION", "GLOWING", "DAMAGE_RESISTANCE",
            "INCREASE_DAMAGE", "SATURATION", "FIRE_RESISTANCE", "WATER_BREATHING", "DOLPHINS_GRACE", "ABSORPTION", "HERO_OF_THE_VILLAGE", "JUMP");

    @Test
    void everyJobHasTenSecretArts() {
        var c = ContentBundle.fromClasspath(getClass().getClassLoader());
        var byJob = c.arts().stream().collect(Collectors.groupingBy(a -> a.job(), Collectors.counting()));
        for (String job : Set.of("sculptor", "swordsman", "cook")) assertEquals(10L, byJob.get(job), job + " 비기 수");
        for (var a : c.arts())
            if (a.effect().equals("BUFF"))
                for (String p : a.params().get("potions").split(","))
                    assertTrue(POTIONS.contains(p.trim().split(":")[0]), a.id() + " 효과 이름: " + p);
    }

    private static final Set<String> PARTICLES = Set.of("END_ROD", "GLOW", "SNOWFLAKE", "SWEEP_ATTACK", "CLOUD", "CRIT", "SOUL_FIRE_FLAME", "HEART",
            "ENCHANTMENT_TABLE", "VILLAGER_HAPPY", "FLAME", "DRIP_WATER", "LAVA", "TOTEM");
    private static final Set<String> PATTERNS = Set.of("ring", "spiral", "pillar", "burst", "rain", "trail");

    @Test
    void newArtsEachLookDifferent() {
        var c = ContentBundle.fromClasspath(getClass().getClassLoader());
        Set<String> seen = new java.util.HashSet<>();
        for (var a : c.arts()) {
            if (!a.effect().equals("BUFF") && !a.effect().equals("STRIKE")) continue;
            assertTrue(PARTICLES.contains(a.params().get("fx")), a.id() + " 입자: " + a.params().get("fx"));
            assertTrue(PATTERNS.contains(a.params().get("pattern")), a.id() + " 모양");
            assertNotNull(a.params().get("sound"), a.id() + " 소리");
            assertTrue(seen.add(a.params().get("fx") + "/" + a.params().get("pattern") + "/" + a.params().get("sound")), a.id() + " 모습이 다른 비기와 같다");
        }
        assertEquals(18, seen.size());
    }
}
