package io.versaera.domain;

import io.versaera.content.ContentLoader;
import io.versaera.domain.hidden.Condition;
import io.versaera.domain.hidden.HiddenRuleGenerator;
import io.versaera.domain.hidden.PlayerFacts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HiddenGeneratorTest {
    private static final HiddenRuleGenerator.Pools POOLS = new HiddenRuleGenerator.Pools(
            Map.of("gather.fishing", "물가에서 오래 낚싯대를 드리운", "talk.npc", "사람들과 자주 이야기하는", "kill.monster", "싸움을 피하지 않는",
                    "craft.sculpting", "돌을 깎는"),
            Map.of("rosaim_harbor", "항구", "calamor_ruins", "옛 서고", "fallen_crater", "분화구", "north_reach", "북쪽 숲"),
            Map.of("tobi_fisher", "어부", "sena_archivist", "기록관"), List.of("fishing", "sculpting"),
            List.of(Map.of("recipe", "craft_wind_chime", "label", "바람의 노래"), Map.of("quest", "hidden.wind_song", "title", "바람을 읽는 자")));

    @Test
    void sameSeedSameRulesDifferentServersDiffer() {
        String a = HiddenRuleGenerator.generate(1, 6, POOLS), b = HiddenRuleGenerator.generate(1, 6, POOLS), c = HiddenRuleGenerator.generate(2, 6, POOLS);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void generatedYamlLoadsAndIsAchievableWithoutLuck() {
        var rules = ContentLoader.hidden(ContentLoader.parse(HiddenRuleGenerator.generate(77, 8, POOLS), "gen"), "gen");
        assertEquals(8, rules.size());
        for (var r : rules) {
            assertTrue(r.when() instanceof Condition.All);
            assertEquals(3, ((Condition.All) r.when()).parts().size(), "조건 3개 조합");
            assertFalse(r.reward().isEmpty());
            assertFalse(r.rumor().isBlank());
            // 모든 것을 충분히 이룬 사람은 (맞는 장소 · 시간에서) 반드시 해금된다
            String region = ((Condition.All) r.when()).parts().stream().filter(p -> p instanceof Condition.InRegion).map(p -> ((Condition.InRegion) p).region())
                    .findFirst().orElseThrow();
            boolean ok = false;
            for (int h = 0; h < 24 && !ok; h++) {
                int hour = h;
                ok = r.when().test(new PlayerFacts() {
                    public long counter(String key) { return 1_000_000; }
                    public int mastery(String d) { return 31; }
                    public int affinity(String npc) { return 1000; }
                    public String region() { return region; }
                    public boolean discovered(String kind, String ref) { return true; }
                    public int hour() { return hour; }
                });
            }
            assertTrue(ok, r.id());
        }
    }
}
