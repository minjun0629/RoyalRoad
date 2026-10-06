package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.NpcSchedule;
import io.versaera.domain.npc.NpcSchedule.Point;
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
 * NPC 일과 이동 (NPC-02). 1초마다:
 * <ul>
 *   <li>근처(64 블록)에 플레이어가 있는 NPC 만 걷는다 (초당 3.5 블록, 높이는 지형에서)</li>
 *   <li>아무도 없으면 엔티티를 두지 않는다 → 누군가 다가오면 그 시각의 자리에 다시 세운다</li>
 * </ul>
 * 엔티티는 저장하지 않는다(persistent=false) → 재시작해도 겹치지 않는다. 관리자가 직접 세운 NPC(/va npc spawn)는 건드리지 않는다.
 */
public final class NpcRuntime {
    private final GameServices s;
    private final NpcListener npcs;
    private final IntSupplier hour;
    private final Map<String, Villager> live = new HashMap<>();
    private final Map<String, Point> pos = new HashMap<>();

    public NpcRuntime(Plugin plugin, GameServices s, NpcListener npcs, IntSupplier hour) {
        this.s = s;
        this.npcs = npcs;
        this.hour = hour;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private void tick() {
        int h = hour.getAsInt();
        for (NpcDefinition n : s.relations.all()) {
            Map<String, Point> places = s.content.places().get(n.id());
            if (places == null) continue;
            Point target = NpcSchedule.target(n, places, h);
            if (target == null) continue;
            Region region = s.regions.byId(n.region());
            World w = region == null ? null : Bukkit.getWorld(region.world());
            if (w == null) continue;
            Point cur = pos.getOrDefault(n.id(), target);
            List<Point> players = new ArrayList<>();
            for (Player p : w.getPlayers()) players.add(new Point(p.getLocation().getX(), p.getLocation().getZ()));
            Villager v = live.get(n.id());
            if (!NpcSchedule.active(cur, players) && !NpcSchedule.active(target, players)) {
                if (v != null) { v.remove(); live.remove(n.id()); }
                pos.put(n.id(), target);   // 보는 사람이 없으면 바로 그 자리에
                continue;
            }
            if (!w.isChunkLoaded((int) Math.floor(cur.x()) >> 4, (int) Math.floor(cur.z()) >> 4)) continue;
            Point next = NpcSchedule.step(cur, target, 1.0);
            pos.put(n.id(), next);
            Location at = ground(w, next, target);
            if (v == null || !v.isValid()) {
                v = npcs.spawn(n, at);
                v.setPersistent(false);
                live.put(n.id(), v);
            } else if (!next.equals(cur)) {
                v.teleport(at);
            }
        }
    }

    private static Location ground(World w, Point p, Point facing) {
        int x = (int) Math.floor(p.x()), z = (int) Math.floor(p.z());
        float yaw = (float) Math.toDegrees(Math.atan2(-(facing.x() - p.x()), facing.z() - p.z()));
        return new Location(w, p.x(), w.getHighestBlockYAt(x, z) + 1, p.z(), Double.isNaN(yaw) ? 0 : yaw, 0);
    }

    public void removeAll() {
        for (Villager v : live.values()) v.remove();
        live.clear();
    }
}
