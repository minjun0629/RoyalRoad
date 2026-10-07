package io.versaera.domain.npc;

import io.versaera.domain.market.MarketCatalog;
import io.versaera.domain.npc.NpcSchedule.Point;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.world.Region;

import java.util.*;

/**
 * 주민 생성 (NPC-03, 순수 계산 · 결정적). 지역 성격(태그)에 맞는 직업의 주민을 만들고 — 이름 · 성격 · 레벨 · 일과 장소 · 가족 · NPC 끼리의 관계 ·
 * 아는 소문 · 상점 · 의뢰까지 — 떠돌이 행상 · 음유시인 · 용병의 순회 경로와, 정해진 때에만 나타나는 희귀 NPC 를 만든다.
 * <ul>
 *   <li>도시(SettlementPlanner.isTown)의 자리는 모두 길 · 광장 가장자리 위 → 이미 만들어진 세계에서도 건물 안에 서지 않는다</li>
 *   <li>도시가 아닌 곳은 지역 가운데 둘레의 고리 위 (가운데 랜드마크를 피함)</li>
 *   <li>같은 콘텐츠 = 같은 결과 (지역 id 로 난수 씨앗) → 다시 켜도 같은 사람이 같은 집에 산다</li>
 * </ul>
 */
public final class NpcPopulation {
    public record Wander(int count, int stops, double speed) {}

    public record RareSpec(String id, String name, String archetype, String region, int hourFrom, int hourTo, int everyDays, int level, String line,
                           String trains) {}

    public record Rules(Map<String, Archetype> archetypes, Map<String, Map<String, Integer>> cultures, int maxPerRegion, Map<String, Wander> wanderers,
                        List<RareSpec> rare) {
        public static final Rules NONE = new Rules(Map.of(), Map.of(), 0, Map.of(), List.of());
    }

    public record Result(List<NpcDefinition> npcs, Map<String, Map<String, Point>> places, List<NpcProfile> profiles,
                         Map<String, MarketCatalog.Shop> shops, List<QuestDefinition> quests) {}

    private static final Set<String> SKIP = Set.of("sea", "sky", "divine", "portal");   // 바다 · 하늘 · 신계 · 문 (깊은 산 도시는 사람이 산다)
    private static final Set<String> RUIN_TAGS = Set.of("ruins", "dungeon_site", "landmark", "sealed", "hole", "mist", "crater");

    private record Born(String id, String given, Archetype arch, int level) {}

    private record Resident(NpcDefinition def, Born born, Region region, String family, String household, List<NpcProfile.Link> links, SplittableRandom rng) {}

    private NpcPopulation() {
    }

    public static Result generate(Collection<Region> allRegions, Rules rules, Collection<MarketCatalog.Market> markets, Set<String> takenIds) {
        List<Region> regions = new ArrayList<>(allRegions);
        regions.sort(Comparator.comparing(Region::id));
        Map<String, Region> byId = new HashMap<>();
        for (Region r : regions) byId.put(r.id(), r);
        List<NpcDefinition> npcs = new ArrayList<>();
        Map<String, Map<String, Point>> places = new LinkedHashMap<>();
        List<NpcProfile> profiles = new ArrayList<>();
        Map<String, MarketCatalog.Shop> shops = new LinkedHashMap<>();
        List<QuestDefinition> quests = new ArrayList<>();
        Set<String> used = new HashSet<>(takenIds);
        List<Resident> residents = new ArrayList<>();

        for (Region r : regions) {
            if (r.maxY() < 64 || !Collections.disjoint(r.tags(), SKIP)) continue;
            LinkedHashMap<String, Integer> want = new LinkedHashMap<>();
            for (var c : rules.cultures().entrySet())
                if (r.tags().contains(c.getKey())) c.getValue().forEach((a, n) -> want.merge(a, n, (x, y) -> Math.min(3, x + y)));
            if (want.isEmpty()) continue;
            SplittableRandom rng = new SplittableRandom(r.id().hashCode() * 0x9E3779B97F4A7C15L + 17);
            Spots spots = new Spots(r);
            List<Born> born = new ArrayList<>();
            Set<String> names = new HashSet<>();
            int total = 0;
            for (var w : want.entrySet()) {
                Archetype a = rules.archetypes().get(w.getKey());
                if (a == null) throw new IllegalArgumentException("없는 직업 틀: " + w.getKey());
                for (int i = 0; i < w.getValue() && total < rules.maxPerRegion(); i++, total++) {
                    String id = unique(r.id() + "_" + a.id(), used);
                    String given;
                    do given = NpcNames.given(rng); while (!names.add(given));
                    born.add(new Born(id, given, a, a.minLevel() + rng.nextInt(a.maxLevel() - a.minLevel() + 1)));
                }
            }
            // 가족: 1 ~ 3 명씩 한 집 (같은 성 · 같은 집)
            List<List<Integer>> households = new ArrayList<>();
            for (int i = 0; i < born.size(); ) {
                int size = Math.min(born.size() - i, 1 + rng.nextInt(3));
                List<Integer> h = new ArrayList<>();
                for (int k = 0; k < size; k++) h.add(i + k);
                households.add(h);
                i += size;
            }
            Map<Integer, String> familyOf = new HashMap<>(), homeOf = new HashMap<>();
            Map<Integer, List<NpcProfile.Link>> links = new HashMap<>();
            for (int i = 0; i < born.size(); i++) links.put(i, new ArrayList<>());
            for (List<Integer> h : households) {
                String family = NpcNames.family(rng);
                String home = "h" + h.get(0);
                for (int idx : h) { familyOf.put(idx, family); homeOf.put(idx, home); }
                if (h.size() >= 2) {
                    boolean married = rng.nextInt(3) != 0;
                    link(links, born, h.get(0), h.get(1), married ? "SPOUSE" : "SIBLING", married ? "SPOUSE" : "SIBLING");
                }
                if (h.size() == 3) {
                    link(links, born, h.get(0), h.get(2), "PARENT", "CHILD");
                    if (links.get(h.get(1)).stream().anyMatch(l -> l.type().equals("SPOUSE"))) link(links, born, h.get(1), h.get(2), "PARENT", "CHILD");
                    else link(links, born, h.get(1), h.get(2), "SIBLING", "SIBLING");
                }
            }
            // 일 관계: 생산자 → 소비자(거래처) · 같은 직업 둘이면 스승과 제자 · 같은 상업 직업이면 경쟁 · 기사와 병사
            for (int i = 0; i < born.size(); i++)
                for (int j = 0; j < born.size(); j++) {
                    if (i == j) continue;
                    Archetype a = born.get(i).arch(), b = born.get(j).arch();
                    if (!Collections.disjoint(a.produces(), b.consumes()) && links.get(i).stream().noneMatch(l -> l.type().equals("PARTNER")))
                        link(links, born, i, j, "PARTNER", "PARTNER");
                    if (i < j && a.id().equals(b.id())) {
                        if (a.category().equals("COMMERCE")) link(links, born, i, j, "RIVAL", "RIVAL");
                        else link(links, born, i, j, "MASTER", "APPRENTICE");
                    }
                    if (a.id().equals("knight") && (b.id().equals("soldier") || b.id().equals("guard"))) link(links, born, i, j, "LORD", "VASSAL");
                }
            // 정의 · 자리 (프로필 · 의뢰는 지역을 넘는 거래처까지 이은 다음에)
            int workIdx = 0;
            Map<String, Point> homes = new HashMap<>();
            for (int i = 0; i < born.size(); i++) {
                Born b = born.get(i);
                Archetype a = b.arch();
                String id = b.id(), given = b.given(), family = familyOf.get(i);
                String personality = a.personalities().get(rng.nextInt(a.personalities().size()));
                NpcDefinition def = new NpcDefinition(id, given + " " + family, a.job(), personality, null, r.id(), a.likes(), a.dislikes(),
                        a.schedule().isEmpty() ? List.of("0-24:work") : a.schedule(), "ORIGINAL", a.evil());
                npcs.add(def);
                Map<String, Point> pl = new LinkedHashMap<>();
                Point home = homes.computeIfAbsent(homeOf.get(i), k -> spots.home());
                for (String sc : def.schedule()) {
                    String key = sc.substring(sc.indexOf(':') + 1);
                    if (pl.containsKey(key)) continue;
                    pl.put(key, switch (key) {
                        case "home" -> home;
                        case "work" -> spots.work(workIdx++);
                        default -> spots.shared(key, i);
                    });
                }
                places.put(id, pl);
                residents.add(new Resident(def, b, r, family, homeOf.get(i), links.get(i), new SplittableRandom(id.hashCode())));
            }
        }
        // 지역을 넘는 거래처: 거래처가 없는 생산자(농부 · 광부 · 어부 …)는 그 물건을 쓰는 가장 가까운 지역의 주민과 잇는다
        for (Resident p : residents) {
            if (p.born().arch().produces().isEmpty() || p.links().stream().anyMatch(l -> l.type().equals("PARTNER"))) continue;
            Resident best = null;
            double bd = 4000;
            for (Resident c : residents) {
                if (c == p || c.region() == p.region() || !c.region().world().equals(p.region().world())
                        || Collections.disjoint(p.born().arch().produces(), c.born().arch().consumes())) continue;
                double d = dist(p.region(), c.region());
                if (d < bd) { bd = d; best = c; }
            }
            if (best != null) {
                p.links().add(new NpcProfile.Link(best.def().id(), "PARTNER"));
                best.links().add(new NpcProfile.Link(p.def().id(), "PARTNER"));
            }
        }
        Map<String, String> givenOf = new HashMap<>();
        for (Resident x : residents) givenOf.put(x.def().id(), x.born().given());
        for (Resident x : residents) {
            Archetype a = x.born().arch();
            List<String> rumors = (a.offers("RUMOR") || a.offers("LORE")) ? rumorsFor(x.region(), regions, x.rng(), 1 + x.rng().nextInt(2)) : List.of();
            List<String> hidden = new ArrayList<>();
            int qn = 0;
            for (Archetype.QuestTemplate qt : a.quests()) {
                QuestDefinition q = quest(x.def().id() + "." + (++qn), x.def(), x.born().level(), qt, x.links(), givenOf, x.region(), regions);
                if (q == null) continue;
                quests.add(q);
                if (q.hidden()) hidden.add(q.id());
            }
            profiles.add(new NpcProfile(x.def().id(), a.id(), x.born().level(), x.family(), x.household(), x.links(), rumors, List.of(), 0, null, hidden,
                    a.trains(), null));
            if (a.offers("SHOP")) shops.put(x.def().id(), shop(x.def().id(), a, x.region(), markets, byId));
        }
        wanderers(regions, rules, markets, byId, used, npcs, profiles, shops);
        rare(byId, rules, used, npcs, places, profiles);
        return new Result(List.copyOf(npcs), places, List.copyOf(profiles), shops, List.copyOf(quests));
    }

    private static void link(Map<Integer, List<NpcProfile.Link>> links, List<Born> born, int a, int b, String typeAB, String typeBA) {
        String ida = born.get(a).id(), idb = born.get(b).id();
        if (links.get(a).stream().anyMatch(l -> l.npc().equals(idb))) return;
        links.get(a).add(new NpcProfile.Link(idb, typeAB));
        links.get(b).add(new NpcProfile.Link(ida, typeBA));
    }

    private static String unique(String base, Set<String> used) {
        String id = base.replaceAll("[^a-z0-9_]", "_");
        if (used.add(id)) return id;
        for (int n = 2; ; n++) if (used.add(id + "_" + n)) return id + "_" + n;
    }

    private static double dist(Region a, Region b) {
        return Math.hypot((a.minX() + a.maxX()) / 2.0 - (b.minX() + b.maxX()) / 2.0, (a.minZ() + a.maxZ()) / 2.0 - (b.minZ() + b.maxZ()) / 2.0);
    }

    /** 가까운 유적 · 던전 · 랜드마크 (3500 블록 안, 자기 지역 제외) */
    private static List<String> rumorsFor(Region self, List<Region> regions, SplittableRandom rng, int n) {
        List<Region> near = new ArrayList<>();
        for (Region o : regions)
            if (o != self && o.world().equals(self.world()) && !Collections.disjoint(o.tags(), RUIN_TAGS) && dist(self, o) < 3500) near.add(o);
        near.sort(Comparator.comparingDouble(o -> dist(self, o)));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < near.size() && out.size() < n; i++) if (i < 2 || rng.nextInt(3) == 0) out.add(near.get(i).id());
        return out;
    }

    private static QuestDefinition quest(String qid, NpcDefinition npc, int level, Archetype.QuestTemplate t, List<NpcProfile.Link> links,
                                         Map<String, String> givenOf, Region r, List<Region> regions) {
        String label = t.label() == null ? "" : t.label(), title = t.title().replace("{npc}", npc.name().split(" ")[0]);
        Map<String, Integer> aff = new LinkedHashMap<>();
        aff.put(npc.id(), t.affinity());
        QuestDefinition.Objective obj;
        if (t.target().equals("partner")) {
            NpcProfile.Link partner = links.stream().filter(l -> l.type().equals("PARTNER")).findFirst()
                    .orElse(links.stream().filter(l -> !l.type().equals("RIVAL")).findFirst().orElse(null));
            if (partner == null) return null;
            String pname = givenOf.getOrDefault(partner.npc(), partner.npc());
            obj = new QuestDefinition.Objective(QuestDefinition.Type.TALK, partner.npc(), 1, 0, label.replace("{partner}", pname));
            aff.put(partner.npc(), Math.max(1, t.affinity() / 2));
        } else if (t.target().equals("ruin")) {
            Region ruin = null;
            double best = Double.MAX_VALUE;
            for (Region o : regions)
                if (o != r && o.world().equals(r.world()) && !Collections.disjoint(o.tags(), RUIN_TAGS) && dist(r, o) < best
                        && (!t.hidden() || dist(r, o) > 1200)) { best = dist(r, o); ruin = o; }
            if (ruin == null) return null;
            obj = new QuestDefinition.Objective(QuestDefinition.Type.DISCOVER, "region:" + ruin.id(), 1, 0, label.replace("{ruin}", ruin.name()));
        } else if (t.target().startsWith("item:")) {
            obj = new QuestDefinition.Objective(QuestDefinition.Type.DELIVER, t.target().substring(5), t.amount(), 0, label);
        } else if (t.target().startsWith("kill:")) {
            obj = new QuestDefinition.Objective(QuestDefinition.Type.KILL, t.target().substring(5), t.amount(), 0, label);
        } else {
            obj = new QuestDefinition.Objective(QuestDefinition.Type.CRAFT, "discipline:" + t.target().substring(6), t.amount(), t.hidden() ? 600 : 0, label);
        }
        long money = Math.round(t.money() * (1 + level / 100.0));
        QuestDefinition.Grade grade = t.hidden() ? QuestDefinition.Grade.PERILOUS : t.daily() ? QuestDefinition.Grade.DAILY : QuestDefinition.Grade.SKILLED;
        return new QuestDefinition(qid, title, npc.id(), grade, null, List.of(), t.hidden(), t.daily(), List.of(obj),
                new QuestDefinition.Reward(money, null, t.xp(), aff, null, t.hidden() ? 30 : 0, null), List.of(), "ORIGINAL");
    }

    private static MarketCatalog.Shop shop(String id, Archetype a, Region r, Collection<MarketCatalog.Market> markets, Map<String, Region> byId) {
        List<MarketCatalog.Offer> offers = new ArrayList<>();
        for (String s : a.stock()) {
            String[] p = s.split(":");
            offers.add(new MarketCatalog.Offer(p[0], p.length > 1 ? Integer.parseInt(p[1]) : 450));
        }
        return new MarketCatalog.Shop(id, nearestMarket(r, markets, byId), offers, a.buys());
    }

    /** 가장 가까운 시장 (같은 세계 우선) — 상점 값은 그 시장의 싼 · 비싼 태그를 따른다 */
    static String nearestMarket(Region r, Collection<MarketCatalog.Market> markets, Map<String, Region> byId) {
        return markets.stream().min(Comparator.comparingDouble(m -> {
            Region mr = byId.get(m.region());
            if (mr == null) return Double.MAX_VALUE;
            return dist(r, mr) + (mr.world().equals(r.world()) ? 0 : 1e9);   // 다른 세계의 시장은 맨 뒤
        })).map(MarketCatalog.Market::id).orElseThrow(() -> new IllegalArgumentException("시장이 하나도 없음"));
    }

    // ------------------------------------------------------------------ 떠돌이 · 희귀
    private static void wanderers(List<Region> regions, Rules rules, Collection<MarketCatalog.Market> markets, Map<String, Region> byId, Set<String> used,
                                  List<NpcDefinition> npcs,
                                  List<NpcProfile> profiles, Map<String, MarketCatalog.Shop> shops) {
        List<Region> towns = regions.stream().filter(r -> r.world().equals("world") && SettlementPlanner.isTown(r) && r.tags().contains("city")).toList();
        if (towns.size() < 2) return;
        SplittableRandom rng = new SplittableRandom(0xC0FFEEL);
        for (var w : rules.wanderers().entrySet()) {
            Archetype a = rules.archetypes().get(w.getKey());
            if (a == null) throw new IllegalArgumentException("없는 직업 틀: " + w.getKey());
            for (int n = 0; n < w.getValue().count(); n++) {
                Region start = towns.get(rng.nextInt(towns.size()));
                List<String> route = new ArrayList<>(List.of(start.id()));
                Region cur = start;
                while (route.size() < Math.min(w.getValue().stops(), towns.size())) {
                    Region from = cur;
                    Region next = towns.stream().filter(t -> !route.contains(t.id())).min(Comparator.comparingDouble(t -> dist(from, t))).orElse(null);
                    if (next == null) break;
                    route.add(next.id());
                    cur = next;
                }
                String id = unique("wander_" + a.id(), used);
                String name = NpcNames.given(rng) + " " + NpcNames.family(rng);
                npcs.add(new NpcDefinition(id, name, a.job(), a.personalities().get(rng.nextInt(a.personalities().size())), null, start.id(),
                        a.likes(), a.dislikes(), List.of(), "ORIGINAL", a.evil()));
                List<String> rumors = new ArrayList<>(route.subList(1, Math.min(3, route.size())));
                profiles.add(new NpcProfile(id, a.id(), a.minLevel() + rng.nextInt(a.maxLevel() - a.minLevel() + 1), null, null, List.of(), rumors, route,
                        w.getValue().speed(), null, List.of(), a.trains(), null));
                if (a.offers("SHOP")) shops.put(id, shop(id, a, start, markets, byId));
            }
        }
    }

    private static void rare(Map<String, Region> byId, Rules rules, Set<String> used, List<NpcDefinition> npcs, Map<String, Map<String, Point>> places,
                             List<NpcProfile> profiles) {
        for (RareSpec rs : rules.rare()) {
            Archetype a = rules.archetypes().get(rs.archetype());
            Region r = byId.get(rs.region());
            if (a == null || r == null) throw new IllegalArgumentException("희귀 NPC 설정 오류: " + rs.id());
            if (!used.add(rs.id())) throw new IllegalArgumentException("희귀 NPC id 중복: " + rs.id());
            npcs.add(new NpcDefinition(rs.id(), rs.name(), a.job(), "알 수 없음", null, r.id(), a.likes(), a.dislikes(), List.of("0-24:work"), "ORIGINAL", false));
            places.put(rs.id(), Map.of("work", new Spots(r).shared("rare", Math.floorMod(rs.id().hashCode(), 7))));
            int offset = Math.floorMod(rs.id().hashCode(), rs.everyDays());
            profiles.add(new NpcProfile(rs.id(), a.id(), rs.level(), null, null, List.of(), List.of(), List.of(), 0,
                    new NpcProfile.Rare(rs.hourFrom(), rs.hourTo(), rs.everyDays(), offset), List.of(), rs.trains() != null ? rs.trains() : a.trains(), rs.line()));
        }
    }

    // ------------------------------------------------------------------ 자리
    /** 지역 안의 자리: 도시는 길 · 광장 가장자리, 아니면 가운데 둘레 고리 */
    static final class Spots {
        private final Region r;
        private final boolean town;
        private final int cx, cz, radius;
        private final List<Point> inner = new ArrayList<>(), outer = new ArrayList<>();
        private int homeNext;

        Spots(Region r) {
            this.r = r;
            this.town = SettlementPlanner.isTown(r);
            int[] g = SettlementPlanner.townGrid(r);
            cx = g[0];
            cz = g[1];
            radius = town ? g[2] : Math.max(16, Math.min(34, Math.min(r.maxX() - r.minX(), r.maxZ() - r.minZ()) / 2 - 4));
            List<Point> all = new ArrayList<>();
            if (town) {
                int k = radius / 32;
                for (int i = -k; i <= k; i++)
                    for (int j = -radius + 6; j <= radius - 6; j += 6) {
                        add(all, cx + i * 32 + 0.5, cz + j + 0.5);       // 세로 길
                        add(all, cx + j + 0.5, cz + i * 32 + 0.5);       // 가로 길
                    }
            } else {
                for (int i = 0; i < 48; i++) {
                    double a = Math.PI * 2 * i / 48, rr = radius * (0.55 + 0.45 * ((i * 7) % 5) / 4.0);
                    add(all, cx + 0.5 + Math.cos(a) * rr, cz + 0.5 + Math.sin(a) * rr);
                }
            }
            all.sort(Comparator.comparingDouble(p -> Math.hypot(p.x() - cx, p.z() - cz)));
            int half = all.size() / 2;
            inner.addAll(all.subList(0, Math.max(1, half)));
            outer.addAll(all.subList(Math.max(0, half), all.size()));
            if (outer.isEmpty()) outer.addAll(inner);
            if (inner.isEmpty()) inner.add(new Point(cx + 0.5, cz + 14.5));
        }

        private void add(List<Point> all, double x, double z) {
            if (x < r.minX() + 1 || x > r.maxX() - 1 || z < r.minZ() + 1 || z > r.maxZ() - 1) return;
            double dx = x - cx, dz = z - cz;
            if (Math.abs(dx) <= 12 && Math.abs(dz) <= 12) return;               // 광장 · 우물
            if (dx >= 6 && dx <= 26 && dz >= -26 && dz <= -6) return;           // 랜드마크 (cx+16, cz-16)
            if (!town && Math.hypot(dx, dz) < 12) return;
            for (Point p : all) if (Math.abs(p.x() - x) < 1 && Math.abs(p.z() - z) < 1) return;
            all.add(new Point(x, z));
        }

        Point work(int i) {
            return inner.get(Math.floorMod(i * 5 + 1, inner.size()));
        }

        Point home() {
            return outer.get(Math.floorMod(outer.size() - 1 - (homeNext++ * 3), outer.size()));
        }

        /** 여럿이 같이 쓰는 자리 (광장 · 시장 · 주점 · 신전 · 성문) — 사람마다 조금씩 비켜 선다 */
        Point shared(String key, int i) {
            int off = (i % 5) * 2;
            Point p = switch (key) {
                case "square" -> new Point(cx + 13.5 + off, cz + 0.5);
                case "market" -> new Point(cx - 13.5 - off, cz + 0.5);
                case "tavern" -> town && radius >= 32 ? new Point(cx + 32.5, cz + 5.5 + off) : new Point(cx + 0.5, cz + 16.5 + off);
                case "temple" -> town && radius >= 32 ? new Point(cx - 31.5, cz - 4.5 - off) : new Point(cx + 0.5, cz - 16.5 - off);
                case "gate" -> new Point(cx + 0.5, (i % 2 == 0 ? cz + radius - 3.5 - off : cz - radius + 3.5 + off));
                default -> new Point(cx + 0.5 + 15 + off, cz + 15.5);
            };
            if (!town) {   // 도시가 아니면 고리 위로
                double a = (key.hashCode() & 0xff) / 255.0 * Math.PI * 2 + i * 0.3;
                p = new Point(cx + 0.5 + Math.cos(a) * radius * 0.7, cz + 0.5 + Math.sin(a) * radius * 0.7);
            }
            double x = Math.max(r.minX() + 1, Math.min(r.maxX() - 1, p.x())), z = Math.max(r.minZ() + 1, Math.min(r.maxZ() - 1, p.z()));
            return new Point(x, z);
        }
    }
}
