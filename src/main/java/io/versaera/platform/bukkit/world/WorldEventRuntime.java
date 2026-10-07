package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.WorldEventService;
import io.versaera.domain.world.Region;
import io.versaera.domain.worldevent.WorldEventDefinition;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 월드 이벤트 실행부 (EVT-01). 1분마다 시간표를 보고, 시작 · 끝을 알린다 (DB 에 기록해 한 번씩만).
 * 희귀 생물 이벤트는 그 지역 가운데 근처에 생물을 몇 마리 세우고, 끝나면 거둔다.
 * 효과(채집 추가 · 위험도 · 할인 · 입구 드러남)는 서비스가 시각만 보고 계산하므로 여기서 따로 켜고 끌 것이 없다.
 */
public final class WorldEventRuntime {
    private final GameServices s;
    private final Async async;
    private final Map<String, List<UUID>> spawned = new HashMap<>();

    public WorldEventRuntime(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 1200L);
    }

    private void tick() {
        async.run("world-events", s.worldEvents::tick, changes -> {
            for (WorldEventService.Change c : changes) {
                WorldEventDefinition d = c.def();
                Region r = s.regions.byId(d.region());
                if (c.started()) {
                    Bukkit.broadcastMessage(Ui.info(d.name() + " · " + (r == null ? "" : r.name())));
                    if (r != null) for (Player p : Bukkit.getOnlinePlayers())
                        if (inside(p, r)) p.sendTitle(Ui.c("&6" + d.name()), Ui.c("&7" + d.announce()), 10, 70, 20);
                    if (d.effects().containsKey("spawn") && r != null) spawn(d, r);
                } else {
                    Bukkit.broadcastMessage(Ui.c("&7" + d.name() + " 끝"));
                    despawn(d);
                }
            }
        }, null);
    }

    private static boolean inside(Player p, Region r) {
        Location l = p.getLocation();
        return r.contains(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private void spawn(WorldEventDefinition d, Region r) {
        World w = Bukkit.getWorld(r.world());
        if (w == null) return;
        EntityType type;
        try {
            type = EntityType.valueOf(d.effects().get("spawn"));
        } catch (IllegalArgumentException e) {
            return;
        }
        int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
        if (!w.isChunkLoaded(cx >> 4, cz >> 4)) w.getChunkAt(cx >> 4, cz >> 4);
        List<UUID> ids = new ArrayList<>();
        Random rng = new Random(d.id().hashCode());
        for (int i = 0; i < 4; i++) {
            int x = cx + rng.nextInt(41) - 20, z = cz + rng.nextInt(41) - 20;
            Entity e = w.spawnEntity(new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5), type);
            e.setGlowing(true);
            e.setPersistent(false);
            if (e instanceof LivingEntity le) le.setRemoveWhenFarAway(false);
            ids.add(e.getUniqueId());
        }
        spawned.put(d.id(), ids);
    }

    private void despawn(WorldEventDefinition d) {
        for (UUID u : spawned.getOrDefault(d.id(), List.of())) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        spawned.remove(d.id());
    }

    public void stop() {
        for (List<UUID> l : spawned.values()) for (UUID u : l) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        spawned.clear();
    }
}
