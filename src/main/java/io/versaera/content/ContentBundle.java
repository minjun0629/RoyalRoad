package io.versaera.content;

import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcPopulation;
import io.versaera.domain.npc.NpcSchedule;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.world.Region;

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
import java.util.*;
import java.util.function.Function;

/** 모든 콘텐츠 정의 묶음. 파일을 여는 방법(클래스패스 · 데이터 폴더)은 opener 가 정한다. */
public record ContentBundle(List<ItemType> items, List<Discipline> disciplines, List<ActionStat> stats, List<Recipe> recipes,
                            List<ResourceNode> resources, List<Region> regions, List<NpcDefinition> npcs, List<BossDefinition> bosses,
                            List<JobDefinition> jobs, List<SkillDefinition> skills, List<CombatState.Combo> combos, List<QuestDefinition> quests,
                            MarketCatalog market, Map<String, Map<String, NpcSchedule.Point>> places,
                            List<io.versaera.domain.dungeon.DungeonDefinition> dungeons,
                            List<io.versaera.domain.worldevent.WorldEventDefinition> worldEvents,
                            List<io.versaera.domain.world.Gate> gates,
                            io.versaera.domain.origin.Origins origins,
                            List<io.versaera.domain.faith.God> gods,
                            List<io.versaera.domain.faith.Temple> temples,
                            List<io.versaera.domain.faith.Era> eras,
                            List<io.versaera.domain.art.SecretArt> arts,
                            List<io.versaera.domain.item.ItemSet> sets,
                            List<io.versaera.domain.fieldboss.FieldBoss> fieldBosses,
                            List<io.versaera.domain.npc.NpcProfile> npcProfiles,
                            Map<String, io.versaera.domain.npc.Archetype> archetypes,
                            Expansion expansion) {
    public static final List<String> FILES = List.of("items.yml", "disciplines.yml", "action_stats.yml", "recipes.yml", "resources.yml",
            "regions.yml", "npcs.yml", "bosses.yml", "jobs.yml", "skills.yml", "quests.yml", "market.yml", "places.yml", "dungeons.yml", "world_events.yml", "gates.yml", "origins.yml", "gods.yml", "history.yml", "secret_arts.yml", "field_bosses.yml", "npc_population.yml",
            "achievements.yml", "pets.yml", "travel.yml", "weather.yml", "raids.yml", "artworks.yml", "guild_quests.yml", "monsters.yml");

    public static ContentBundle load(Function<String, InputStream> opener) {
        Map<String, Object> skills = read(opener, "skills.yml"), gods = read(opener, "gods.yml"), items = read(opener, "items.yml");
        List<Region> loaded = ContentLoader.regions(read(opener, "regions.yml"), "regions.yml");
        // 항구 도시에 port 태그 (선장이 살고 배가 닿는 곳) — 노선망과 주민 생성이 같은 기준을 쓴다
        Object pr = read(opener, "travel.yml").get("port_range");
        Set<String> ports = io.versaera.domain.travel.TravelNetwork.ports(loaded, pr instanceof Number n ? n.intValue() : 900);
        List<Region> regions = loaded.stream().map(r -> ports.contains(r.id()) ? r.withTag("port") : r).toList();
        List<NpcDefinition> npcs = new ArrayList<>(ContentLoader.npcs(read(opener, "npcs.yml"), "npcs.yml"));
        Map<String, Map<String, NpcSchedule.Point>> places = new LinkedHashMap<>(ContentLoader.places(read(opener, "places.yml"), "places.yml"));
        MarketCatalog market = ContentLoader.market(read(opener, "market.yml"), "market.yml");
        List<QuestDefinition> quests = new ArrayList<>(ContentLoader.quests(read(opener, "quests.yml"), "quests.yml"));
        // 주민 생성: 손으로 만든 NPC 와 같은 체계(정의 · 일과 장소 · 상점 · 의뢰)로 합친다
        NpcPopulation.Rules rules = ContentLoader.population(read(opener, "npc_population.yml"), "npc_population.yml");
        Set<String> taken = new HashSet<>();
        for (NpcDefinition n : npcs) taken.add(n.id());
        NpcPopulation.Result pop = NpcPopulation.generate(regions, rules, market.markets().values(), taken);
        npcs.addAll(pop.npcs());
        places.putAll(pop.places());
        Map<String, MarketCatalog.Shop> shops = new LinkedHashMap<>(market.shops());
        shops.putAll(pop.shops());
        market = new MarketCatalog(market.markets(), market.prices(), shops);
        quests.addAll(pop.quests());
        return new ContentBundle(
                ContentLoader.items(items, "items.yml"),
                ContentLoader.disciplines(read(opener, "disciplines.yml"), "disciplines.yml"),
                ContentLoader.stats(read(opener, "action_stats.yml"), "action_stats.yml"),
                ContentLoader.recipes(read(opener, "recipes.yml"), "recipes.yml"),
                ContentLoader.resources(read(opener, "resources.yml"), "resources.yml"),
                regions,
                List.copyOf(npcs),
                ContentLoader.bosses(read(opener, "bosses.yml"), "bosses.yml"),
                ContentLoader.jobs(read(opener, "jobs.yml"), "jobs.yml"),
                ContentLoader.skills(skills, "skills.yml"),
                ContentLoader.combos(skills, "skills.yml"),
                List.copyOf(quests),
                market,
                places,
                ContentLoader.dungeons(read(opener, "dungeons.yml"), "dungeons.yml"),
                ContentLoader.worldEvents(read(opener, "world_events.yml"), "world_events.yml"),
                ContentLoader.gates(read(opener, "gates.yml"), "gates.yml"),
                ContentLoader.origins(read(opener, "origins.yml"), "origins.yml"),
                ContentLoader.gods(gods, "gods.yml"),
                ContentLoader.temples(gods, "gods.yml"),
                ContentLoader.eras(read(opener, "history.yml"), "history.yml"),
                ContentLoader.arts(read(opener, "secret_arts.yml"), "secret_arts.yml"),
                ContentLoader.itemSets(items, "items.yml"),
                ContentLoader.fieldBosses(read(opener, "field_bosses.yml"), "field_bosses.yml"),
                pop.profiles(),
                rules.archetypes(),
                ContentLoader.expansion(f -> read(opener, f), regions));
    }

    public static ContentBundle fromClasspath(ClassLoader cl) {
        return load(f -> cl.getResourceAsStream("content/" + f));
    }

    /** 손으로 둔 NPC 일과 장소만 (places.yml) — 지형 생성기가 건물을 비워 둘 자리. 생성 주민은 길 · 광장에 서므로 넣지 않는다 */
    public static Map<String, Map<String, NpcSchedule.Point>> handPlaces(Function<String, InputStream> opener) {
        return ContentLoader.places(read(opener, "places.yml"), "places.yml");
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
