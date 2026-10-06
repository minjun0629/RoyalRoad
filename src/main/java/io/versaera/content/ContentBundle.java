package io.versaera.content;

import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.combat.CombatState;
import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.job.JobDefinition;
import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.gathering.ResourceNode;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcSchedule;
import io.versaera.domain.skill.ActionStat;
import io.versaera.domain.skill.Discipline;
import io.versaera.domain.world.Region;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 모든 콘텐츠 정의 묶음. 파일을 여는 방법(클래스패스 · 데이터 폴더)은 opener 가 정한다. */
public record ContentBundle(List<ItemType> items, List<Discipline> disciplines, List<ActionStat> stats, List<Recipe> recipes,
                            List<ResourceNode> resources, List<Region> regions, List<NpcDefinition> npcs, List<BossDefinition> bosses,
                            List<JobDefinition> jobs, List<SkillDefinition> skills, List<CombatState.Combo> combos, List<QuestDefinition> quests,
                            MarketCatalog market, Map<String, Map<String, NpcSchedule.Point>> places,
                            List<io.versaera.domain.dungeon.DungeonDefinition> dungeons,
                            List<io.versaera.domain.worldevent.WorldEventDefinition> worldEvents,
                            List<io.versaera.domain.world.Gate> gates) {
    public static final List<String> FILES = List.of("items.yml", "disciplines.yml", "action_stats.yml", "recipes.yml", "resources.yml",
            "regions.yml", "npcs.yml", "bosses.yml", "jobs.yml", "skills.yml", "quests.yml", "market.yml", "places.yml", "dungeons.yml", "world_events.yml", "gates.yml");

    public static ContentBundle load(Function<String, InputStream> opener) {
        Map<String, Object> skills = read(opener, "skills.yml");
        return new ContentBundle(
                ContentLoader.items(read(opener, "items.yml"), "items.yml"),
                ContentLoader.disciplines(read(opener, "disciplines.yml"), "disciplines.yml"),
                ContentLoader.stats(read(opener, "action_stats.yml"), "action_stats.yml"),
                ContentLoader.recipes(read(opener, "recipes.yml"), "recipes.yml"),
                ContentLoader.resources(read(opener, "resources.yml"), "resources.yml"),
                ContentLoader.regions(read(opener, "regions.yml"), "regions.yml"),
                ContentLoader.npcs(read(opener, "npcs.yml"), "npcs.yml"),
                ContentLoader.bosses(read(opener, "bosses.yml"), "bosses.yml"),
                ContentLoader.jobs(read(opener, "jobs.yml"), "jobs.yml"),
                ContentLoader.skills(skills, "skills.yml"),
                ContentLoader.combos(skills, "skills.yml"),
                ContentLoader.quests(read(opener, "quests.yml"), "quests.yml"),
                ContentLoader.market(read(opener, "market.yml"), "market.yml"),
                ContentLoader.places(read(opener, "places.yml"), "places.yml"),
                ContentLoader.dungeons(read(opener, "dungeons.yml"), "dungeons.yml"),
                ContentLoader.worldEvents(read(opener, "world_events.yml"), "world_events.yml"),
                ContentLoader.gates(read(opener, "gates.yml"), "gates.yml"));
    }

    public static ContentBundle fromClasspath(ClassLoader cl) {
        return load(f -> cl.getResourceAsStream("content/" + f));
    }

    static Map<String, Object> read(Function<String, InputStream> opener, String file) {
        try (InputStream in = opener.apply(file)) {
            if (in == null) throw new ContentLoader.ContentException("콘텐츠 파일 없음: " + file);
            return ContentLoader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), file);
        } catch (IOException e) {
            throw new ContentLoader.ContentException(file, e);
        }
    }
}
