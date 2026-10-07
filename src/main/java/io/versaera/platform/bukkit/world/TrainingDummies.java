package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 수련관 허수아비 (CANON: 위드처럼 허수아비를 쳐서 스탯을 쌓는다 — 허수아비가 스탯을 주는 게 아니라 반복 행동이 스탯을 만든다).
 * 수련관 지역 가운데에 허수아비(갑옷 거치대) 셋. 칠 때마다 행동 기록 hit.training +1 → 힘 스탯 (action_stats.yml).
 * 허수아비는 저장하지 않는다 (청크를 읽을 때 세우고, 내릴 때 함께 사라짐) — 겹쳐 생기지 않게.
 */
public final class TrainingDummies implements Listener {
    public static final String TAG = "versa_dummy";
    private static final List<String> HALLS = List.of("basic_training_hall", "novice_training_hall");
    private final GameServices s;
    private final Async async;
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();
    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    public TrainingDummies(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 200L, 200L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String h : HALLS) {
                Region r = s.regions.byId(h);
                World w = r == null ? null : Bukkit.getWorld(r.world());
                if (w != null && w.isChunkLoaded(((r.minX() + r.maxX()) / 2) >> 4, ((r.minZ() + r.maxZ()) / 2) >> 4)) place(w, r);
            }
        });
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        for (String h : HALLS) {
            Region r = s.regions.byId(h);
            if (r == null || !r.world().equals(e.getWorld().getName())) continue;
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            if (e.getChunk().getX() == cx >> 4 && e.getChunk().getZ() == cz >> 4) place(e.getWorld(), r);
        }
    }

    private void place(World w, Region r) {
        int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
        for (Entity e : w.getChunkAt(cx >> 4, cz >> 4).getEntities()) if (e.getScoreboardTags().contains(TAG)) return;
        for (int[] o : new int[][]{{-3, 0}, {0, -3}, {3, 0}}) {
            int x = cx + o[0], z = cz + o[1];
            Location l = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
            ArmorStand a = w.spawn(l, ArmorStand.class);
            a.setPersistent(false);
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setCustomName("허수아비");
            a.setCustomNameVisible(true);
            a.addScoreboardTag(TAG);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        Long last = lastHit.get(p.getUniqueId());
        if (last != null && now - last < 400) return;   // 연타 매크로 대비: 0.4초에 한 번만 센다
        lastHit.put(p.getUniqueId(), now);
        pending.merge(p.getUniqueId().toString(), 1L, Long::sum);
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    private void flush() {
        if (pending.isEmpty()) return;
        Map<String, Long> batch = new HashMap<>(pending);
        batch.forEach((k, v) -> pending.merge(k, -v, (a, b) -> a + b == 0 ? null : a + b));
        async.fire("training", () -> {
            batch.forEach((u, n) -> s.growth.record(u, "hit.training", n));
            return null;
        });
    }
}
