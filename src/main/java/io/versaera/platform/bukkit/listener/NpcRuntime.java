package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcSchedule;
import io.versaera.domain.npc.NpcSchedule.Point;
import io.versaera.domain.npc.Wandering;
import io.versaera.domain.world.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.IntSupplier;

/**
 * NPC 일과 · 떠돌이 이동 (NPC-02 · NPC-04). 1초마다, 900 명이 넘는 주민 중 <b>플레이어 근처 칸에 자리가 있는 NPC 만</b> 본다:
 * <ul>
 *   <li>공간 색인: 세계 → 128 블록 칸 → 그 칸에 일과 장소가 있는 NPC. 플레이어마다 주변 3×3 칸만 꺼낸다 (전체 순회 없음)</li>
 *   <li>떠돌이(수십 명)는 시각으로 자리를 계산하고, 160 블록 안에 누가 있을 때만 세운다</li>
 *   <li>희귀 NPC 는 정해진 날 · 시각에만, 쇠퇴한 지역의 귀한 물건 상인은 떠나 있다 (NpcWorldService.absent — DB 스레드가 갈아 끼움)</li>
 *   <li>폭풍 · 눈보라 · 모래폭풍에는 주점이나 집에 들어가 있다 (WeatherService.indoor)</li>
 *   <li>근처(64 블록)에 아무도 없으면 엔티티를 치우고, 누가 다가오면 그 시각의 자리에 다시 세운다</li>
 * </ul>
 * 엔티티는 저장하지 않는다(persistent=false) → 재시작해도 겹치지 않는다. 관리자가 직접 세운 NPC(/va npc spawn)는 건드리지 않는다.
 */
public final class NpcRuntime {
    static final int CELL = 128;
    private static final double WANDER_SEEN = 160;

    private final Plugin plugin;
    private final GameServices s;
    private final NpcListener npcs;
    private final IntSupplier hour;
    private final Map<String, Villager> live = new HashMap<>();
    private final Map<String, Point> pos = new HashMap<>();
    private final Map<String, Map<Long, List<String>>> index = new HashMap<>();
    private final Map<String, String> worldOf = new HashMap<>();
    private final Set<String> wanderers = new LinkedHashSet<>();
    /** 집에 들어가는 중: NPC → 문 (아래 칸) · 집 안에 있음 */
    private final Map<String, Location> entering = new HashMap<>();
    private final Map<String, Location> inside = new HashMap<>();

    public NpcRuntime(Plugin plugin, GameServices s, NpcListener npcs, IntSupplier hour) {
        this.plugin = plugin;
        this.s = s;
        this.npcs = npcs;
        this.hour = hour;
        for (NpcDefinition n : s.relations.all()) {
            Region region = s.regions.byId(n.region());
            if (region == null) continue;
            worldOf.put(n.id(), region.world());
            if (s.npcWorld.profile(n.id()).map(p -> p.wanderer()).orElse(false)) {
                wanderers.add(n.id());
                continue;
            }
            Map<String, Point> places = s.content.places().get(n.id());
            if (places == null) continue;
            Map<Long, List<String>> cells = index.computeIfAbsent(region.world(), w -> new HashMap<>());
            Set<Long> mine = new HashSet<>();
            for (Point p : places.values()) mine.add(cell(Math.floorDiv((int) Math.floor(p.x()), CELL), Math.floorDiv((int) Math.floor(p.z()), CELL)));
            for (long c : mine) cells.computeIfAbsent(c, k -> new ArrayList<>()).add(n.id());
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private static long cell(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private void tick() {
        int h = hour.getAsInt();
        Set<String> absent = s.npcWorld.absent();
        Map<String, List<Point>> players = new HashMap<>();
        Set<String> candidates = new HashSet<>(live.keySet());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            String w = l.getWorld().getName();
            players.computeIfAbsent(w, k -> new ArrayList<>()).add(new Point(l.getX(), l.getZ()));
            Map<Long, List<String>> cells = index.get(w);
            if (cells == null) continue;
            int cx = Math.floorDiv(l.getBlockX(), CELL), cz = Math.floorDiv(l.getBlockZ(), CELL);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    List<String> ids = cells.get(cell(cx + dx, cz + dz));
                    if (ids != null) candidates.addAll(ids);
                }
        }
        Map<String, Point> wanderAt = new HashMap<>();
        for (String id : wanderers) {
            List<Point> near = players.get(worldOf.get(id));
            if (near == null) continue;
            Wandering.State st = s.npcWorld.wanderer(id);
            for (Point p : near)
                if (p.dist(st.where()) <= WANDER_SEEN) {
                    candidates.add(id);
                    wanderAt.put(id, st.where());
                    break;
                }
        }
        for (String id : candidates) {
            NpcDefinition n = s.relations.npc(id);
            World w = Bukkit.getWorld(worldOf.getOrDefault(id, ""));
            Point target = wanderers.contains(id) ? wanderAt.getOrDefault(id, s.npcWorld.wanderer(id).where()) : target(n, h);
            boolean away = w == null || target == null || absent.contains(id) || !s.npcWorld.rareNow(id, h);
            Villager v = live.get(id);
            List<Point> near = w == null ? List.of() : players.getOrDefault(w.getName(), List.of());
            Point cur = pos.getOrDefault(id, target);
            // 집에 갈 시간: 집 근처 문까지 걸어가 문을 열고 들어간다 (닫힌다). 아침이면 그 문을 열고 나온다
            boolean homeTime = !away && !wanderers.contains(id) && "home".equals(n.placeAt(h));
            if (!homeTime) entering.remove(id);
            if (homeTime && inside.containsKey(id)) away = true;
            else if (homeTime && (cur.dist(target) < 1.5 || entering.get(id) != null) && w != null) {
                Location door = entering.get(id);
                if (door == null && !entering.containsKey(id)) { door = nearestDoor(w, target); entering.put(id, door); }
                if (door == null) { away = true; inside.put(id, null); }
                else {
                    Location front = doorFront(door);
                    Point fp = new Point(front.getX(), front.getZ());
                    if (cur.dist(fp) < 1.2) {
                        swing(door);
                        entering.remove(id);
                        inside.put(id, door);
                        away = true;
                    } else target = fp;
                }
            } else if (!homeTime && inside.containsKey(id)) {
                entering.remove(id);   // 아침: 문을 열고 나온다
                Location door = inside.remove(id);
                if (door != null && w != null && w.isChunkLoaded(door.getBlockX() >> 4, door.getBlockZ() >> 4)) {
                    swing(door);
                    Location front = doorFront(door);
                    pos.put(id, new Point(front.getX(), front.getZ()));
                    cur = pos.get(id);
                }
            }
            if (away || (!NpcSchedule.active(cur, near) && !NpcSchedule.active(target, near))) {
                if (v != null) { v.remove(); live.remove(id); }
                if (target != null) pos.put(id, target);   // 보는 사람이 없으면 바로 그 자리에
                else pos.remove(id);
                continue;
            }
            if (!w.isChunkLoaded((int) Math.floor(cur.x()) >> 4, (int) Math.floor(cur.z()) >> 4)) continue;
            Point next = NpcSchedule.step(cur, target, 1.0);
            pos.put(id, next);
            Location at = ground(w, next, target);
            if (v == null || !v.isValid()) {
                v = npcs.spawn(n, at);
                v.setPersistent(false);
                live.put(id, v);
            } else if (!next.equals(cur)) {
                v.teleport(at);
            }
        }
    }

    private Point target(NpcDefinition n, int h) {
        Map<String, Point> places = s.content.places().get(n.id());
        if (places == null) return null;
        // 날씨 (WTH-01): 폭풍 · 눈보라 · 모래폭풍이면 일하던 사람도 주점이나 집으로 (경비 · 성문지기는 그 자리)
        if (s.weather.indoor(n.region()) && !"gate".equals(n.placeAt(h))) {
            Point in = places.getOrDefault("tavern", places.get("home"));
            if (in != null) return in;
        }
        return spread(n.id(), NpcSchedule.target(n, places, h));
    }

    /**
     * 같은 자리(주점 · 광장 · 가게 앞)를 쓰는 사람들이 한 칸에 겹쳐 서지 않게: 사람마다 정해진 방향 · 거리(1 ~ 2.5 블록, 지붕 위로 올라가지 않을 만큼)만큼 비켜 선다.
     * id 로 정하므로 날마다 같은 자리에 선다.
     */
    static Point spread(String id, Point p) {
        if (p == null) return null;
        int h = id.hashCode();
        double angle = (h & 0xffff) / 65536.0 * Math.PI * 2, r = 1.0 + ((h >>> 16) & 0xff) / 255.0 * 1.5;
        return new Point(p.x() + Math.cos(angle) * r, p.z() + Math.sin(angle) * r);
    }

    /** 집 자리 둘레 6 블록 안에서 가장 가까운 문 (아래 칸) */
    private static Location nearestDoor(World w, Point near) {
        int x0 = (int) Math.floor(near.x()), z0 = (int) Math.floor(near.z());
        if (!w.isChunkLoaded(x0 >> 4, z0 >> 4)) return null;
        int y0 = w.getHighestBlockYAt(x0, z0);
        Location best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                for (int dy = -2; dy <= 3; dy++) {
                    var b = w.getBlockAt(x0 + dx, y0 + dy, z0 + dz);
                    if (!(b.getBlockData() instanceof org.bukkit.block.data.type.Door d) || d.getHalf() != org.bukkit.block.data.Bisected.Half.BOTTOM) continue;
                    double dist = dx * dx + dz * dz;
                    if (dist < bd) { bd = dist; best = b.getLocation(); }
                }
        return best;
    }

    /** 문 바깥쪽 한 칸 (문이 보는 쪽) */
    private static Location doorFront(Location door) {
        if (door.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Door d)
            return door.getBlock().getRelative(d.getFacing()).getLocation().add(0.5, 0, 0.5);
        return door.clone().add(0.5, 0, 0.5);
    }

    /** 문을 열었다가 1초 뒤 닫는다 (소리와 함께) */
    private void swing(Location door) {
        var b = door.getBlock();
        if (!(b.getBlockData() instanceof org.bukkit.block.data.type.Door d)) return;
        d.setOpen(true);
        b.setBlockData(d);
        door.getWorld().playSound(door, org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Door c && c.isOpen()) {
                c.setOpen(false);
                b.setBlockData(c);
                door.getWorld().playSound(door, org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.8f, 1f);
            }
        }, 20L);
    }

    private static Location ground(World w, Point p, Point facing) {
        int x = (int) Math.floor(p.x()), z = (int) Math.floor(p.z());
        float yaw = (float) Math.toDegrees(Math.atan2(-(facing.x() - p.x()), facing.z() - p.z()));
        return new Location(w, p.x(), w.getHighestBlockYAt(x, z) + 1, p.z(), Double.isNaN(yaw) ? 0 : yaw, 0);
    }

    /** 지금 세워 둔 NPC 수 (관리 명령 · 성능 확인용) */
    public int liveCount() {
        return live.size();
    }

    public void removeAll() {
        for (Villager v : live.values()) v.remove();
        live.clear();
    }
}
