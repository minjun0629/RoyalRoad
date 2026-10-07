package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 들판의 몬스터 (WLD-05). 바닐라 생물 무리는 바이옴을 따라 나와서 도시 둘레에 토끼 · 여우가 없다 → 지역 위험도에 맞춰 직접 내보낸다.
 * <ul>
 *   <li>위험도 0: 토끼 · 여우 (초보 사냥감 — 원작에서 처음 사냥하던 짐승) · 1: + 늑대 · 2: 늑대 무리 · 거미 · 고블린 · 3 이상: 해골 병사 · 약탈자 …
 *       사막은 미라, 얼음 땅은 서리 해골</li>
 *   <li>사람 둘레 18 ~ 32 블록, 도시 성벽 밖에만. 사람마다 근처에 몇 마리까지만, 멀어지면 사라진다 (저장하지 않음)</li>
 *   <li>체력은 위험도만큼 강해지고, 낮에도 타지 않는다. 도시 성벽 안에는 밤 몬스터가 자연히 생기지 않는다</li>
 * </ul>
 */
public final class FieldMobRuntime implements Listener {
    static final String TAG = "versa_field";

    private record Kind(EntityType type, String name, double health, boolean angry) {}

    private final GameServices s;
    private final Random rng = new Random();
    private final Set<UUID> ours = new HashSet<>();

    public FieldMobRuntime(Plugin plugin, GameServices s) {
        this.s = s;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 80L);
    }

    private static List<Kind> table(int danger, Set<String> tags) {
        List<Kind> l = new ArrayList<>();
        if (danger <= 1) {
            for (int i = 0; i < 3; i++) l.add(new Kind(EntityType.RABBIT, "토끼", 6, false));
            for (int i = 0; i < 2; i++) l.add(new Kind(EntityType.FOX, "여우", 10, false));
            if (danger == 1) l.add(new Kind(EntityType.WOLF, "늑대", 16, true));
            return l;
        }
        boolean desert = tags.contains("desert"), frozen = tags.contains("frozen");
        l.add(new Kind(EntityType.WOLF, "늑대", 16, true));
        l.add(new Kind(EntityType.WOLF, "회색 늑대", 20, true));
        l.add(new Kind(EntityType.SPIDER, "독거미", 18, false));
        l.add(new Kind(desert ? EntityType.HUSK : frozen ? EntityType.STRAY : EntityType.ZOMBIE, desert ? "사막 미라" : frozen ? "서리 해골" : "고블린", 20, false));
        if (danger >= 3) {
            l.add(new Kind(frozen ? EntityType.STRAY : EntityType.SKELETON, "해골 병사", 20, false));
            l.add(new Kind(EntityType.HUSK, "오크 척후병", 26, false));
        }
        if (danger >= 4) {
            l.add(new Kind(EntityType.VINDICATOR, "약탈자", 28, false));
            l.add(new Kind(EntityType.CAVE_SPIDER, "독안개 거미", 14, false));
        }
        return l;
    }

    /** 도시 성벽(+ 4 블록) 안인가 */
    private boolean inTown(String world, int x, int z, Region r) {
        for (Region t = r; t != null; t = t.parent() == null ? null : s.regions.byId(t.parent())) {
            if (!SettlementPlanner.isTown(t)) continue;
            int[] g = SettlementPlanner.townGrid(t);
            if (Math.max(Math.abs(x - g[0]), Math.abs(z - g[1])) <= g[2] + 8) return true;
        }
        return false;
    }

    private void tick() {
        // 사람과 멀어진 것 · 죽은 것 정리
        for (Iterator<UUID> it = ours.iterator(); it.hasNext(); ) {
            Entity e = Bukkit.getEntity(it.next());
            if (e == null || !e.isValid()) { it.remove(); continue; }
            boolean near = false;
            for (Player p : e.getWorld().getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < 80 * 80) { near = true; break; }
            if (!near) { e.remove(); it.remove(); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location at = p.getLocation();
            World w = at.getWorld();
            Region r = s.regions.at(w.getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (r == null) continue;
            int danger = r.danger(), cap = danger <= 1 ? 6 : 8, near = 0;
            for (UUID u : ours) {
                Entity e = Bukkit.getEntity(u);
                if (e != null && e.getWorld().equals(w) && e.getLocation().distanceSquared(at) < 40 * 40) near++;
            }
            if (near >= cap) continue;
            Location spot = spot(at, r);
            if (spot == null) continue;
            Region sr = s.regions.at(w.getName(), spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (sr == null) continue;
            List<Kind> kinds = table(sr.danger(), sr.tags());
            Kind k = kinds.get(rng.nextInt(kinds.size()));
            int pack = k.type() == EntityType.WOLF || k.type() == EntityType.RABBIT ? 1 + rng.nextInt(2) : 1;
            for (int i = 0; i < pack && near + i < cap; i++) spawn(k, spot.clone().add(rng.nextInt(3) - 1, 0, rng.nextInt(3) - 1), sr.danger());
        }
    }

    private Location spot(Location around, Region r) {
        World w = around.getWorld();
        for (int tries = 0; tries < 6; tries++) {
            double a = rng.nextDouble() * Math.PI * 2, d = 18 + rng.nextDouble() * 14;
            int x = around.getBlockX() + (int) Math.round(Math.cos(a) * d), z = around.getBlockZ() + (int) Math.round(Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Region here = s.regions.at(w.getName(), x, around.getBlockY(), z);
            if (here == null || inTown(w.getName(), x, z, here)) continue;
            int y = w.getHighestBlockYAt(x, z);
            Material ground = w.getBlockAt(x, y, z).getType();
            if (!ground.isSolid() || ground.name().endsWith("LEAVES") || Math.abs(y - around.getBlockY()) > 12) continue;
            if (!w.getBlockAt(x, y + 1, z).getType().isAir() || !w.getBlockAt(x, y + 2, z).getType().isAir()) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    private void spawn(Kind k, Location at, int danger) {
        Entity e = at.getWorld().spawnEntity(at, k.type());
        if (!(e instanceof LivingEntity le)) { e.remove(); return; }
        le.addScoreboardTag(TAG);
        le.setRemoveWhenFarAway(true);
        le.setCustomName(Ui.c((k.angry() || danger >= 2 ? "&c" : "&f") + k.name()));
        le.setCustomNameVisible(true);
        double hp = k.health() * (1 + Math.max(0, danger - 1) * 0.5);
        AttributeInstance max = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(hp);
            le.setHealth(hp);
        }
        if (k.angry() && le instanceof Wolf wolf) wolf.setAngry(true);
        ours.add(le.getUniqueId());
    }

    /** 들판 몬스터는 낮에도 타지 않는다 */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    /** 도시 성벽 안에는 밤 몬스터가 저절로 생기지 않는다 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof org.bukkit.entity.Monster)) return;
        Location l = e.getLocation();
        Region r = s.regions.at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (r != null && inTown(l.getWorld().getName(), l.getBlockX(), l.getBlockZ(), r)) e.setCancelled(true);
    }

    public void removeAll() {
        for (UUID u : ours) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        ours.clear();
    }
}
