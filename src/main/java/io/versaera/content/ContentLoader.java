package io.versaera.content;

import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.Shape;
import io.versaera.domain.combat.CombatState;
import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.combat.StatusEffect;
import io.versaera.domain.job.JobDefinition;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.crafting.MaterialSlot;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.gathering.ResourceNode;
import io.versaera.domain.hidden.Condition;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.skill.ActionStat;
import io.versaera.domain.skill.Discipline;
import io.versaera.domain.world.Region;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.*;

/**
 * content/*.yml → 도메인 정의. SafeConstructor 만 써서 YAML 로 임의 자바 객체가 만들어지지 않게 한다.
 * 잘못된 항목은 어느 파일 · 어느 id 인지 알려 주는 예외로 실패한다 (조용히 건너뛰지 않음).
 */
public final class ContentLoader {
    public static final class ContentException extends RuntimeException {
        public ContentException(String where, Throwable cause) {
            super(where + ": " + cause.getMessage(), cause);
        }

        public ContentException(String msg) {
            super(msg);
        }
    }

    private ContentLoader() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String yamlText, String file) {
        LoaderOptions o = new LoaderOptions();
        o.setAllowDuplicateKeys(false);
        o.setMaxAliasesForCollections(20);
        Object root = new Yaml(new SafeConstructor(o)).load(yamlText);
        if (root == null) return Map.of();
        if (!(root instanceof Map)) throw new ContentException(file + ": 최상위가 맵이 아닙니다");
        return (Map<String, Object>) root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key, String file) {
        Object v = root.get(key);
        if (v == null) return Map.of();
        if (!(v instanceof Map)) throw new ContentException(file + ": '" + key + "' 는 맵이어야 합니다");
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    private static String req(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("'" + k + "' 가 없습니다");
        return String.valueOf(v);
    }

    private static int i(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private static long l(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : v == null ? def : Long.parseLong(String.valueOf(v));
    }

    private static double d(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : v == null ? def : Double.parseDouble(String.valueOf(v));
    }

    private static boolean b(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean x ? x : v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }

    private static List<String> list(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return List.of(String.valueOf(v));
    }

    private static Map<String, Integer> intMap(Map<String, Object> m, String k) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(m.get(k)).entrySet()) out.put(e.getKey(), ((Number) e.getValue()).intValue());
        return out;
    }

    private interface Builder<T> {
        T build(String id, Map<String, Object> m);
    }

    private static <T> List<T> each(Map<String, Object> root, String key, String file, Builder<T> b) {
        List<T> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : section(root, key, file).entrySet()) {
            try {
                out.add(b.build(e.getKey(), map(e.getValue())));
            } catch (RuntimeException ex) {
                throw new ContentException(file + " / " + e.getKey(), ex);
            }
        }
        return out;
    }

    public static List<ItemType> items(Map<String, Object> root, String file) {
        return each(root, "items", file, (id, m) -> new ItemType(id, req(m, "name"), ItemCategory.valueOf(req(m, "category")), req(m, "material"),
                i(m, "durability", 0), i(m, "weight", 0), new LinkedHashSet<>(list(m, "tags")), intMap(m, "stats"), intMap(m, "requires"),
                str(m, "source", "ORIGINAL"), str(m, "lore", null), str(m, "set", null)));
    }

    /** items.yml 의 sets (없어도 됨) */
    public static List<io.versaera.domain.item.ItemSet> itemSets(Map<String, Object> root, String file) {
        if (!root.containsKey("sets")) return List.of();
        return each(root, "sets", file, (id, m) -> {
            Map<Integer, Map<String, Integer>> b = new LinkedHashMap<>();
            Object raw = m.get("bonuses");   // 키가 숫자라 YAML 이 Integer 로 읽는다
            if (raw instanceof Map<?, ?> rb) for (var e : rb.entrySet()) {
                Map<String, Integer> v = new LinkedHashMap<>();
                if (e.getValue() instanceof Map<?, ?> rv) for (var x : rv.entrySet()) v.put(x.getKey().toString(), ((Number) x.getValue()).intValue());
                b.put(Integer.parseInt(e.getKey().toString()), v);
            }
            return new io.versaera.domain.item.ItemSet(id, req(m, "name"), b, str(m, "source", "ORIGINAL"));
        });
    }

    // ------------------------------------------------------------------ 주민 생성 규칙 (npc_population.yml)
    public static io.versaera.domain.npc.NpcPopulation.Rules population(Map<String, Object> root, String file) {
        Map<String, io.versaera.domain.npc.Archetype> arch = new LinkedHashMap<>();
        for (var a : each(root, "archetypes", file, (id, m) -> {
            List<io.versaera.domain.npc.Archetype.QuestTemplate> qs = new ArrayList<>();
            if (m.get("quests") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> q = map(o);
                qs.add(new io.versaera.domain.npc.Archetype.QuestTemplate(req(q, "target"), i(q, "amount", 1), l(q, "money", 0), intMap(q, "xp"),
                        i(q, "affinity", 5), b(q, "daily", false), b(q, "hidden", false), req(q, "title"), str(q, "label", "")));
            }
            List<String> lv = list(m, "level");
            int lo = lv.isEmpty() ? 5 : Integer.parseInt(lv.get(0)), hi = lv.size() < 2 ? lo + 10 : Integer.parseInt(lv.get(1));
            return new io.versaera.domain.npc.Archetype(id, req(m, "job"), str(m, "category", "LIFE"), new LinkedHashSet<>(list(m, "services")),
                    new LinkedHashSet<>(list(m, "likes")), new LinkedHashSet<>(list(m, "dislikes")), list(m, "personalities"), list(m, "schedule"),
                    list(m, "stock"), new LinkedHashSet<>(list(m, "buys")), new LinkedHashSet<>(list(m, "produces")), new LinkedHashSet<>(list(m, "consumes")),
                    list(m, "lines"), lo, hi, b(m, "evil", false), str(m, "trains", null), qs, list(m, "gifts"));
        })) arch.put(a.id(), a);
        Map<String, Map<String, Integer>> cultures = new LinkedHashMap<>();
        for (var e : section(root, "cultures", file).entrySet()) {
            Map<String, Integer> c = new LinkedHashMap<>();
            for (var x : map(e.getValue()).entrySet()) {
                if (!arch.containsKey(x.getKey())) throw new ContentException(file + " / cultures." + e.getKey() + ": 없는 직업 틀 " + x.getKey());
                c.put(x.getKey(), ((Number) x.getValue()).intValue());
            }
            cultures.put(e.getKey(), c);
        }
        Map<String, io.versaera.domain.npc.NpcPopulation.Wander> wanderers = new LinkedHashMap<>();
        for (var e : section(root, "wanderers", file).entrySet()) {
            Map<String, Object> m = map(e.getValue());
            wanderers.put(e.getKey(), new io.versaera.domain.npc.NpcPopulation.Wander(i(m, "count", 1), i(m, "stops", 4), d(m, "speed", 100)));
        }
        List<io.versaera.domain.npc.NpcPopulation.RareSpec> rare = new ArrayList<>(each(root, "rare", file, (id, m) -> {
            List<String> h = list(m, "hours");
            return new io.versaera.domain.npc.NpcPopulation.RareSpec(id, req(m, "name"), req(m, "archetype"), req(m, "region"),
                    Integer.parseInt(h.get(0)), Integer.parseInt(h.get(1)), i(m, "every_days", 1), i(m, "level", 50), str(m, "line", null), str(m, "trains", null));
        }));
        Object max = root.get("max_per_region");
        return new io.versaera.domain.npc.NpcPopulation.Rules(arch, cultures, max instanceof Number n ? n.intValue() : 16, wanderers, rare);
    }

    public static List<io.versaera.domain.fieldboss.FieldBoss> fieldBosses(Map<String, Object> root, String file) {
        return each(root, "field_bosses", file, (id, m) -> {
            List<io.versaera.domain.fieldboss.FieldBoss.Drop> drops = new ArrayList<>();
            for (String x : list(m, "drops")) {   // "item:품질:확률"
                String[] p = x.split(":");
                drops.add(new io.versaera.domain.fieldboss.FieldBoss.Drop(p[0], Integer.parseInt(p[1]), Double.parseDouble(p[2])));
            }
            Set<io.versaera.domain.item.ItemOptions.Kind> kinds = new LinkedHashSet<>();
            for (String k : list(m, "kinds")) kinds.add(io.versaera.domain.item.ItemOptions.Kind.valueOf(k));
            return new io.versaera.domain.fieldboss.FieldBoss(id, req(m, "name"), req(m, "entity"), req(m, "region"), d(m, "hp", 100), d(m, "damage", 6),
                    i(m, "respawn_minutes", 60), new LinkedHashSet<>(list(m, "mechanics")), str(m, "minion", null), kinds, drops,
                    reward(m.get("reward")), str(m, "description", ""), str(m, "source", "CANON"),
                    str(m, "look", "KNIGHT"), d(m, "size", 2));
        });
    }

    public static List<Discipline> disciplines(Map<String, Object> root, String file) {
        return each(root, "disciplines", file, (id, m) -> new Discipline(id, req(m, "name"), Discipline.Category.valueOf(req(m, "category")),
                b(m, "hand", false), str(m, "source", "ORIGINAL")));
    }

    public static List<ActionStat> stats(Map<String, Object> root, String file) {
        return each(root, "stats", file, (id, m) -> new ActionStat(id, req(m, "name"), req(m, "counter"), l(m, "per", 1), l(m, "unlock_at", 0),
                str(m, "effect", ""), str(m, "source", "ORIGINAL")));
    }

    public static List<Recipe> recipes(Map<String, Object> root, String file) {
        return each(root, "recipes", file, (id, m) -> {
            List<MaterialSlot> slots = new ArrayList<>();
            for (Map.Entry<String, Object> s : map(m.get("slots")).entrySet()) {
                Map<String, Object> sm = map(s.getValue());
                slots.add(new MaterialSlot(s.getKey(), req(sm, "accepts"), i(sm, "count", 1), d(sm, "weight", 1), b(sm, "optional", false), i(sm, "bonus", 0)));
            }
            return new Recipe(id, req(m, "name"), req(m, "discipline"), i(m, "min_level", 1), i(m, "action_level", 1), req(m, "output"),
                    i(m, "output_count", 1), slots, str(m, "tool", null), i(m, "time_ticks", 40), l(m, "xp", 10), str(m, "discovery", null),
                    str(m, "source", "ORIGINAL"));
        });
    }

    public static List<ResourceNode> resources(Map<String, Object> root, String file) {
        return each(root, "resources", file, (id, m) -> new ResourceNode(id, req(m, "name"), req(m, "discipline"), i(m, "min_level", 1),
                i(m, "action_level", 1), new LinkedHashSet<>(list(m, "blocks")), req(m, "yield"), i(m, "amount", 1), i(m, "bonus", 0),
                new LinkedHashSet<>(list(m, "rich_in")), i(m, "respawn_seconds", 60), str(m, "tool", null), l(m, "xp", 5), str(m, "source", "ORIGINAL")));
    }

    public static List<Region> regions(Map<String, Object> root, String file) {
        return each(root, "regions", file, (id, m) -> {
            List<String> min = list(m, "min"), max = list(m, "max");
            if (min.size() != 3 || max.size() != 3) throw new IllegalArgumentException("min / max 는 [x, y, z]");
            return new Region(id, req(m, "name"), str(m, "source", "ORIGINAL"), i(m, "danger", 0), str(m, "world", "world"),
                    Integer.parseInt(min.get(0)), Integer.parseInt(min.get(1)), Integer.parseInt(min.get(2)),
                    Integer.parseInt(max.get(0)), Integer.parseInt(max.get(1)), Integer.parseInt(max.get(2)),
                    i(m, "priority", 0), str(m, "parent", null), new LinkedHashSet<>(list(m, "tags")), str(m, "purpose", null),
                    str(m, "changed", null), list(m, "resources"), list(m, "factions"));
        });
    }

    public static List<NpcDefinition> npcs(Map<String, Object> root, String file) {
        return each(root, "npcs", file, (id, m) -> new NpcDefinition(id, req(m, "name"), req(m, "job"), str(m, "personality", ""),
                str(m, "faction", null), req(m, "region"), new LinkedHashSet<>(list(m, "likes")), new LinkedHashSet<>(list(m, "dislikes")),
                list(m, "schedule"), str(m, "source", "ORIGINAL"), b(m, "evil", false)));
    }

    public static List<BossDefinition> bosses(Map<String, Object> root, String file) {
        return each(root, "bosses", file, (id, m) -> {
            Map<String, BossDefinition.Pattern> pats = new LinkedHashMap<>();
            for (Map.Entry<String, Object> p : map(m.get("patterns")).entrySet()) {
                Map<String, Object> pm = map(p.getValue());
                pats.put(p.getKey(), new BossDefinition.Pattern(p.getKey(), Shape.valueOf(req(pm, "shape")), d(pm, "radius", 4), d(pm, "inner", 0),
                        d(pm, "width", 3), d(pm, "height", 4), l(pm, "telegraph_ms", 1500), d(pm, "damage", 10), l(pm, "cooldown_ms", 4000),
                        str(pm, "effect", null)));
            }
            List<BossDefinition.Phase> phases = new ArrayList<>();
            Object pl = m.get("phases");
            if (pl instanceof List<?> l) for (Object o : l) {
                Map<String, Object> pm = map(o);
                phases.add(new BossDefinition.Phase(d(pm, "hp_below", 1.0), list(pm, "patterns"), str(pm, "announce", "")));
            }
            return new BossDefinition(id, req(m, "name"), d(m, "scale", 1), d(m, "hit_radius", 2), d(m, "max_hp", 1000), d(m, "arena_radius", 40),
                    d(m, "weak_arc", 90), l(m, "enrage_ms", 0), phases, pats, str(m, "model", null), d(m, "speed", 2.5), reward(m.get("reward")),
                    str(m, "source", "ORIGINAL"));
        });
    }

    private static Map<String, Double> doubleMap(Map<String, Object> m) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.put(e.getKey(), ((Number) e.getValue()).doubleValue());
        return out;
    }

    public static List<JobDefinition> jobs(Map<String, Object> root, String file) {
        return each(root, "jobs", file, (id, m) -> new JobDefinition(id, req(m, "name"), req(m, "slot"), i(m, "tier", 1), str(m, "parent", null),
                condition(m.get("requires")), doubleMap(map(m.get("perks"))), list(m, "skills"), str(m, "source", "ORIGINAL")));
    }

    private static SkillDefinition skill(String id, Map<String, Object> m) {
        String shape = str(m, "shape", null), effect = str(m, "effect", null);
        return new SkillDefinition(id, req(m, "name"), SkillDefinition.Kind.valueOf(req(m, "kind")), str(m, "weapon", null), req(m, "discipline"),
                i(m, "min_level", 1), SkillDefinition.Resource.valueOf(str(m, "resource", "STAMINA")), i(m, "cost", 10), l(m, "cooldown_ms", 3000),
                shape == null ? null : Shape.valueOf(shape), d(m, "radius", 3), d(m, "width", 90), d(m, "damage", 1),
                effect == null ? null : StatusEffect.valueOf(effect), i(m, "effect_seconds", 0), b(m, "basic", false), str(m, "source", "ORIGINAL"));
    }

    public static List<SkillDefinition> skills(Map<String, Object> root, String file) {
        List<SkillDefinition> out = new ArrayList<>(each(root, "skills", file, ContentLoader::skill));
        out.addAll(each(root, "combo_skills", file, ContentLoader::skill));
        return out;
    }

    public static List<CombatState.Combo> combos(Map<String, Object> root, String file) {
        return each(root, "combos", file, (id, m) -> new CombatState.Combo(id,
                list(m, "sequence").stream().map(CombatState.Input::valueOf).toList(), l(m, "window_ms", 1500), req(m, "finisher")));
    }

    @SuppressWarnings("unchecked")
    private static QuestDefinition.Reward reward(Object o) {
        Map<String, Object> m = map(o);
        if (m.isEmpty()) return QuestDefinition.Reward.NONE;
        return new QuestDefinition.Reward(l(m, "money", 0), list(m, "items"), intMap(m, "xp"), intMap(m, "affinity"), intMap(m, "reputation"),
                i(m, "fame", 0), list(m, "unlocks"));
    }

    public static List<QuestDefinition> quests(Map<String, Object> root, String file) {
        return each(root, "quests", file, (id, m) -> {
            List<QuestDefinition.Objective> objs = new ArrayList<>();
            if (m.get("objectives") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> om = map(o);
                objs.add(new QuestDefinition.Objective(QuestDefinition.Type.valueOf(req(om, "type")), req(om, "target"), i(om, "amount", 1),
                        i(om, "min_quality", 0), str(om, "label", "")));
            }
            List<QuestDefinition.Choice> choices = new ArrayList<>();
            if (m.get("choices") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> cm = map(o);
                choices.add(new QuestDefinition.Choice(req(cm, "id"), req(cm, "label"), reward(cm.get("reward"))));
            }
            return new QuestDefinition(id, req(m, "title"), str(m, "giver", null), QuestDefinition.Grade.valueOf(str(m, "grade", "DAILY")),
                    m.containsKey("requires") ? condition(m.get("requires")) : null, list(m, "after"), b(m, "hidden", false), b(m, "daily", false),
                    objs, reward(m.get("reward")), choices, str(m, "source", "ORIGINAL"));
        });
    }

    public static io.versaera.domain.market.MarketCatalog market(Map<String, Object> root, String file) {
        Map<String, io.versaera.domain.market.MarketCatalog.Market> markets = new LinkedHashMap<>();
        for (var m : each(root, "markets", file, (id, m) -> new io.versaera.domain.market.MarketCatalog.Market(id, req(m, "name"), req(m, "region"),
                d(m, "tax", 0.05), new LinkedHashSet<>(list(m, "cheap")), new LinkedHashSet<>(list(m, "dear"))))) markets.put(m.id(), m);
        Map<String, Long> prices = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : section(root, "prices", file).entrySet()) {
            long v = ((Number) e.getValue()).longValue();
            if (v <= 0) throw new ContentException(file + " / prices / " + e.getKey() + ": 시세는 1 이상");
            prices.put(e.getKey(), v);
        }
        Map<String, io.versaera.domain.market.MarketCatalog.Shop> shops = new LinkedHashMap<>();
        for (var sh : each(root, "shops", file, (id, m) -> {
            List<io.versaera.domain.market.MarketCatalog.Offer> sells = new ArrayList<>();
            for (String o : list(m, "sells")) {
                String[] p = o.split(":");
                if (p.length != 2) throw new IllegalArgumentException("sells 는 종류:품질 — " + o);
                sells.add(new io.versaera.domain.market.MarketCatalog.Offer(p[0], Integer.parseInt(p[1])));
            }
            String market = req(m, "market");
            if (!markets.containsKey(market)) throw new IllegalArgumentException("없는 시장: " + market);
            return new io.versaera.domain.market.MarketCatalog.Shop(id, market, sells, new LinkedHashSet<>(list(m, "buys")));
        })) shops.put(sh.npcId(), sh);
        return new io.versaera.domain.market.MarketCatalog(markets, prices, shops);
    }

    /** npc → 장소 키 → 좌표 */
    public static Map<String, Map<String, io.versaera.domain.npc.NpcSchedule.Point>> places(Map<String, Object> root, String file) {
        Map<String, Map<String, io.versaera.domain.npc.NpcSchedule.Point>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : section(root, "places", file).entrySet()) {
            Map<String, io.versaera.domain.npc.NpcSchedule.Point> m = new LinkedHashMap<>();
            for (Map.Entry<String, Object> p : map(e.getValue()).entrySet()) {
                if (!(p.getValue() instanceof List<?> l) || l.size() != 2) throw new ContentException(file + " / " + e.getKey() + "." + p.getKey() + ": [x, z]");
                m.put(p.getKey(), new io.versaera.domain.npc.NpcSchedule.Point(((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue()));
            }
            out.put(e.getKey(), m);
        }
        return out;
    }

    public static List<io.versaera.domain.dungeon.DungeonDefinition> dungeons(Map<String, Object> root, String file) {
        return each(root, "dungeons", file, (id, m) -> {
            List<String> party = list(m, "party");
            if (party.size() != 2) throw new IllegalArgumentException("party 는 [최소, 최대]");
            return new io.versaera.domain.dungeon.DungeonDefinition(id, req(m, "name"), req(m, "region"), i(m, "danger", 1), i(m, "rooms", 9),
                    Integer.parseInt(party.get(0)), Integer.parseInt(party.get(1)), l(m, "time_limit_minutes", 30) * 60_000L, i(m, "levers", 4),
                    list(m, "palette"), list(m, "monsters"), i(m, "monsters_per_room", 4), d(m, "monster_health", 1.5), str(m, "boss", null),
                    str(m, "boss_mob", null), d(m, "boss_health", 5), reward(m.get("reward")), reward(m.get("hidden_reward")),
                    l(m, "cooldown_hours", 20) * 3_600_000L, str(m, "source", "ORIGINAL"));
        });
    }

    public static List<io.versaera.domain.worldevent.WorldEventDefinition> worldEvents(Map<String, Object> root, String file) {
        return each(root, "world_events", file, (id, m) -> new io.versaera.domain.worldevent.WorldEventDefinition(id, req(m, "name"), req(m, "region"),
                io.versaera.domain.worldevent.WorldEventDefinition.Kind.valueOf(req(m, "kind")), (long) (d(m, "period_hours", 24) * 3_600_000),
                (long) (d(m, "duration_minutes", 30) * 60_000), (long) (d(m, "jitter_hours", 0) * 3_600_000), (long) (d(m, "forecast_hours", 1) * 3_600_000),
                str(m, "forecaster", null), stringMap(map(m.get("effects"))), str(m, "announce", ""), str(m, "source", "ORIGINAL")));
    }

    // ------------------------------------------------------------------ 캐릭터 만들기 · 신 · 연대기
    public static io.versaera.domain.origin.Origins origins(Map<String, Object> root, String file) {
        try {
            List<io.versaera.domain.origin.Race> races = each(root, "races", file, (id, m) -> {
                Map<String, Double> bonus = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : map(m.get("xp_bonus")).entrySet()) bonus.put(e.getKey(), Double.parseDouble(String.valueOf(e.getValue())));
                return new io.versaera.domain.origin.Race(id, req(m, "name"), str(m, "source", "ORIGINAL"), str(m, "note", ""), bonus, str(m, "perk", "none"));
            });
            List<io.versaera.domain.origin.StartCity> cities = each(root, "cities", file, (id, m) -> new io.versaera.domain.origin.StartCity(id, req(m, "name"),
                    req(m, "region"), str(m, "kingdom", ""), str(m, "source", "ORIGINAL"), str(m, "note", "")));
            return new io.versaera.domain.origin.Origins(races, cities, i(root, "beginner_game_days", 30), list(root, "starting_kit"));
        } catch (RuntimeException e) {
            if (e instanceof ContentException) throw e;
            throw new ContentException(file, e);
        }
    }

    public static List<io.versaera.domain.faith.God> gods(Map<String, Object> root, String file) {
        return each(root, "gods", file, (id, m) -> new io.versaera.domain.faith.God(id, req(m, "name"), str(m, "domain", ""), b(m, "evil", false),
                str(m, "blessing", null), str(m, "source", "CANON")));
    }

    public static List<io.versaera.domain.faith.Temple> temples(Map<String, Object> root, String file) {
        return each(root, "temples", file, (id, m) -> new io.versaera.domain.faith.Temple(id, req(m, "god"), req(m, "region"), str(m, "source", "ORIGINAL"),
                str(m, "note", "")));
    }

    public static List<io.versaera.domain.faith.Era> eras(Map<String, Object> root, String file) {
        return each(root, "eras", file, (id, m) -> new io.versaera.domain.faith.Era(id, req(m, "name"), req(m, "when"), req(m, "summary"),
                str(m, "source", "ORIGINAL")));
    }

    public static List<io.versaera.domain.art.SecretArt> arts(Map<String, Object> root, String file) {
        return each(root, "arts", file, (id, m) -> new io.versaera.domain.art.SecretArt(id, req(m, "name"), req(m, "job"), req(m, "discipline"),
                i(m, "min_level", 25), str(m, "relic", null), m.containsKey("discover") ? condition(m.get("discover")) : null, req(m, "effect"),
                l(m, "cooldown_minutes", 30) * 60_000L, b(m, "final", false), str(m, "description", ""), str(m, "source", "CANON")));
    }

    public static List<io.versaera.domain.world.Gate> gates(Map<String, Object> root, String file) {
        return each(root, "gates", file, (id, m) -> {
            List<String> to = list(m, "to");
            if (to.size() != 3) throw new IllegalArgumentException("to 는 [세계, x, z]");
            return new io.versaera.domain.world.Gate(id, req(m, "name"), req(m, "region"), to.get(0), Integer.parseInt(to.get(1)), Integer.parseInt(to.get(2)),
                    i(m, "min_exploration", 1), str(m, "source", "ORIGINAL"), str(m, "note", ""));
        });
    }

    // ------------------------------------------------------------------ 히든 규칙 (봉인을 연 뒤의 YAML)
    public static List<HiddenRule> hidden(Map<String, Object> root, String file) {
        return each(root, "hidden", file, (id, m) -> new HiddenRule(id, req(m, "title"), condition(m.get("when")), str(m, "rumor", ""),
                new LinkedHashMap<>(stringMap(map(m.get("reward"))))));
    }

    private static Map<String, String> stringMap(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.put(e.getKey(), String.valueOf(e.getValue()));
        return out;
    }

    /** when: {all: [...]} · {any: [...]} · {counter: key, at_least: n} · {mastery: d, level: n} · {affinity: npc, at_least: n}
     *  · {region: id} · {hours: [from, to]} · {discovered: "kind:ref"} */
    // ------------------------------------------------------------------ 모험 확장 (V7)
    public static Expansion expansion(java.util.function.Function<String, Map<String, Object>> read, List<Region> regions) {
        Map<String, Object> ach = read.apply("achievements.yml");
        Map<String, io.versaera.domain.achievement.Title> titles = new LinkedHashMap<>();
        for (var t : each(ach, "titles", "achievements.yml", (id, m) -> new io.versaera.domain.achievement.Title(id, req(m, "name"), str(m, "color", "&f"), str(m, "desc", ""))))
            titles.put(t.id(), t);
        var achievements = each(ach, "achievements", "achievements.yml", (id, m) -> {
            Map<String, Object> r = map(m.get("reward"));
            String title = str(r, "title", null);
            if (title != null && !titles.containsKey(title)) throw new IllegalArgumentException("없는 칭호: " + title);
            return new io.versaera.domain.achievement.Achievement(id, req(m, "name"), str(m, "category", "기타"), str(m, "desc", ""), condition(m.get("when")),
                    l(r, "money", 0), i(r, "fame", 0), title, b(m, "hidden", false), i(r, "points", 10));
        });
        var species = each(read.apply("pets.yml"), "species", "pets.yml", (id, m) -> {
            Map<String, Object> t = map(m.get("tame"));
            Map<Integer, String> skills = new TreeMap<>();
            for (var e : map(m.get("skills")).entrySet()) skills.put(Integer.parseInt(String.valueOf(e.getKey())), String.valueOf(e.getValue()));
            return new io.versaera.domain.pet.Species(id, req(m, "name"), req(m, "entity"), i(t, "level", 0), new LinkedHashSet<>(list(t, "food")),
                    d(t, "chance", 0.3), new LinkedHashSet<>(list(m, "habitat")), i(m, "health", 10), i(m, "attack", 1), d(m, "health_per_level", 1),
                    d(m, "attack_per_level", 0.2), skills);
        });
        Map<String, Object> tr = read.apply("travel.yml");
        var mounts = each(tr, "mounts", "travel.yml", (id, m) -> new io.versaera.domain.travel.MountKind(id, req(m, "name"), req(m, "entity"),
                d(m, "speed", 0.22), d(m, "jump", 0.6), i(m, "health", 20), l(m, "price", 1000), i(m, "riding", 0), str(m, "color", "NONE")));
        var network = io.versaera.domain.travel.TravelNetwork.build(regions, new io.versaera.domain.travel.TravelNetwork.Rules(
                mode(section(tr, "carriage", "travel.yml")), mode(section(tr, "ship", "travel.yml")), i(tr, "port_range", 900)));
        Map<String, Object> w = read.apply("weather.yml");
        var kinds = each(w, "kinds", "weather.yml", (id, m) -> new io.versaera.domain.weather.WeatherKind(id, req(m, "name"), doubleMap(map(m.get("gather"))),
                d(m, "melee", 1), d(m, "ranged", 1), d(m, "spell", 1), b(m, "indoor", false), b(m, "downfall", false), str(m, "particle", null), str(m, "desc", "")));
        List<io.versaera.domain.weather.Climate> climates = new ArrayList<>();
        if (!(w.get("climates") instanceof List<?> cl)) throw new ContentException("weather.yml / climates 가 목록이 아닙니다");
        for (Object o : cl) {
            Map<String, Object> m = map(o);
            climates.add(new io.versaera.domain.weather.Climate(req(m, "id"), new LinkedHashSet<>(list(m, "tags")), intMap(m, "weights")));
        }
        var raids = each(read.apply("raids.yml"), "raids", "raids.yml", (id, m) -> {
            List<String> pl = list(m, "players");
            Map<String, Object> r = map(m.get("reward"));
            String title = str(r, "title", null);
            if (title != null && !titles.containsKey(title)) throw new IllegalArgumentException("없는 칭호: " + title);
            return new io.versaera.domain.raid.RaidDefinition(id, req(m, "name"), req(m, "region"), req(m, "boss"), Integer.parseInt(pl.get(0)),
                    Integer.parseInt(pl.get(1)), i(m, "mastery", 0), l(m, "time_limit_minutes", 15) * 60_000L, l(r, "money", 0), list(r, "items"), title,
                    i(r, "fame", 0), str(m, "desc", ""));
        });
        var arts = each(read.apply("artworks.yml"), "kinds", "artworks.yml", (id, m) -> {
            List<io.versaera.domain.art.ArtworkKind.Part> parts = new ArrayList<>();
            if (m.get("parts") instanceof List<?> pl) for (Object o : pl) {
                Map<String, Object> p = map(o);
                parts.add(new io.versaera.domain.art.ArtworkKind.Part(req(p, "slot"), list(p, "tags"), i(p, "amount", 1)));
            }
            return new io.versaera.domain.art.ArtworkKind(id, req(m, "name"), d(m, "scale", 1), i(m, "level", 0), parts, i(m, "fame", 0), str(m, "desc", ""));
        });
        Map<String, Object> gq = read.apply("guild_quests.yml");
        var guildQuests = each(gq, "quests", "guild_quests.yml", (id, m) -> new io.versaera.domain.guild.GuildQuestDef(id, req(m, "name"), req(m, "counter"),
                l(m, "target", 1), l(m, "money", 0), l(m, "activity", 0), b(m, "deposit", false), str(m, "desc", "")));
        Map<String, Object> st = section(gq, "storage", "guild_quests.yml");
        return new Expansion(achievements, titles, species, mounts, network, kinds, climates, l(w, "window_minutes", 40) * 60_000L, i(w, "cell", 3000),
                raids, arts, guildQuests, intMap(st, "daily_withdraw"), i(st, "max_kinds", 120));
    }

    private static io.versaera.domain.travel.TravelNetwork.Mode mode(Map<String, Object> m) {
        return new io.versaera.domain.travel.TravelNetwork.Mode(i(m, "links", 3), i(m, "max_distance", 4000), l(m, "base", 20), d(m, "per_block", 0.04),
                d(m, "blocks_per_second", 30));
    }

    static Condition condition(Object o) {
        Map<String, Object> m = map(o);
        if (m.isEmpty()) throw new IllegalArgumentException("조건이 비었습니다");
        if (m.containsKey("all")) return new Condition.All(parts(m.get("all")));
        if (m.containsKey("any")) return new Condition.Any(parts(m.get("any")));
        if (m.containsKey("counter")) return new Condition.Counter(req(m, "counter"), l(m, "at_least", 1));
        if (m.containsKey("mastery")) return new Condition.MasteryAtLeast(req(m, "mastery"), i(m, "level", 1));
        if (m.containsKey("affinity")) return new Condition.AffinityAtLeast(req(m, "affinity"), i(m, "at_least", 0));
        if (m.containsKey("region")) return new Condition.InRegion(req(m, "region"));
        if (m.containsKey("hours")) {
            List<String> h = list(m, "hours");
            return new Condition.Hours(Integer.parseInt(h.get(0)), Integer.parseInt(h.get(1)));
        }
        if (m.containsKey("discovered")) {
            String v = req(m, "discovered");
            int c = v.indexOf(':');
            if (c <= 0) throw new IllegalArgumentException("discovered 는 kind:ref");
            return new Condition.Discovered(v.substring(0, c), v.substring(c + 1));
        }
        throw new IllegalArgumentException("알 수 없는 조건: " + m.keySet());
    }

    private static List<Condition> parts(Object o) {
        if (!(o instanceof List<?> l) || l.isEmpty()) throw new IllegalArgumentException("all / any 는 비지 않은 목록이어야 합니다");
        List<Condition> out = new ArrayList<>();
        for (Object x : l) out.add(condition(x));
        return out;
    }
}
