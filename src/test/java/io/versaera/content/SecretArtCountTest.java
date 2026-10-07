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
}
