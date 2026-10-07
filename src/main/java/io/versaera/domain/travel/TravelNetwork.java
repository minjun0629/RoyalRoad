package io.versaera.domain.travel;

import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.world.Region;

import java.util.*;

/**
 * 마차 · 배 노선망 (TRV-02, 순수 계산). 지역에서 결정적으로 만든다 — 지역이 같으면 노선도 같다.
 * <ul>
 *   <li>마차: 도시마다 가까운 도시 links 곳 (maxDistance 안, 같은 세계). 노선은 양방향</li>
 *   <li>배: 바다(sea) 지역에서 portRange 안에 있거나 coast 태그인 도시 = 항구. 항구마다 가까운 항구 links 곳</li>
 * </ul>
 * 요금 = base + 거리 × perBlock, 걸리는 시간 = 거리 ÷ 초당 블록 (실제 시간).
 */
public final class TravelNetwork {
    public record Mode(int links, int maxDistance, long base, double perBlock, double blocksPerSecond) {}

    public record Rules(Mode carriage, Mode ship, int portRange) {}

    private final Map<String, Route> routes;
    private final Map<String, List<Route>> from = new HashMap<>();
    private final Set<String> ports;
    private final Set<String> stations;

    private TravelNetwork(Map<String, Route> routes, Set<String> ports, Set<String> stations) {
        this.routes = Map.copyOf(routes);
        this.ports = Set.copyOf(ports);
        this.stations = Set.copyOf(stations);
        for (Route r : routes.values()) from.computeIfAbsent(r.from(), k -> new ArrayList<>()).add(r);
        for (List<Route> l : from.values()) l.sort(Comparator.comparing((Route r) -> r.kind()).thenComparingInt(Route::distance));
    }

    /** 항구: coast 태그거나 바다(sea)에서 range 안의 도시 (port 태그가 이미 붙어 있어도) */
    public static Set<String> ports(Collection<Region> regions, int range) {
        List<Region> seas = regions.stream().filter(r -> r.tags().contains("sea")).toList();
        Set<String> ports = new TreeSet<>();
        for (Region t : regions) {
            if (!SettlementPlanner.isTown(t)) continue;
            if (t.tags().contains("coast") || t.tags().contains("port")) ports.add(t.id());
            else for (Region s : seas) if (s.world().equals(t.world()) && gap(t, s) <= range) { ports.add(t.id()); break; }
        }
        return ports;
    }

    public static TravelNetwork build(Collection<Region> regions, Rules rules) {
        List<Region> towns = regions.stream().filter(SettlementPlanner::isTown).sorted(Comparator.comparing(Region::id)).toList();
        Set<String> ports = ports(regions, rules.portRange());
        Map<String, Route> routes = new TreeMap<>();
        link(towns, rules.carriage(), Route.Kind.CARRIAGE, routes);
        link(towns.stream().filter(t -> ports.contains(t.id())).toList(), rules.ship(), Route.Kind.SHIP, routes);
        Set<String> stations = new TreeSet<>();
        for (Route r : routes.values()) if (r.kind() == Route.Kind.CARRIAGE) stations.add(r.from());
        return new TravelNetwork(routes, ports, stations);
    }

    private static void link(List<Region> nodes, Mode m, Route.Kind kind, Map<String, Route> out) {
        for (Region a : nodes) {
            List<Region> near = nodes.stream().filter(b -> b != a && b.world().equals(a.world()) && dist(a, b) <= m.maxDistance())
                    .sorted(Comparator.comparingDouble((Region b) -> dist(a, b)).thenComparing(Region::id)).limit(m.links()).toList();
            for (Region b : near) {
                add(a, b, m, kind, out);
                add(b, a, m, kind, out);
            }
        }
    }

    private static void add(Region a, Region b, Mode m, Route.Kind kind, Map<String, Route> out) {
        int d = (int) Math.round(dist(a, b));
        String id = Route.id(kind, a.id(), b.id());
        out.putIfAbsent(id, new Route(id, kind, a.id(), b.id(), d, m.base() + Math.round(d * m.perBlock()),
                Math.max(5_000L, Math.round(d / m.blocksPerSecond() * 1000))));
    }

    static double dist(Region a, Region b) {
        return Math.hypot((a.minX() + a.maxX()) / 2.0 - (b.minX() + b.maxX()) / 2.0, (a.minZ() + a.maxZ()) / 2.0 - (b.minZ() + b.maxZ()) / 2.0);
    }

    /** 두 지역 상자 사이의 빈 거리 (겹치면 0) */
    static double gap(Region a, Region b) {
        double dx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
        double dz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));
        return Math.hypot(dx, dz);
    }

    public Optional<Route> route(String id) {
        return Optional.ofNullable(routes.get(id));
    }

    public Collection<Route> all() {
        return routes.values();
    }

    /** 이 도시에서 떠나는 노선 (마차 먼저, 가까운 순) */
    public List<Route> from(String region) {
        return from.getOrDefault(region, List.of());
    }

    public boolean port(String region) {
        return ports.contains(region);
    }

    public boolean station(String region) {
        return stations.contains(region);
    }

    public Set<String> ports() {
        return ports;
    }
}
