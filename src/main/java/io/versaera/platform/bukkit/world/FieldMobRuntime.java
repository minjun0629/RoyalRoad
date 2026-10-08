package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.world.FieldMonster;
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
 * 들판의 몬스터 (WLD-05). 바닐라 생물 무리는 바이옴을 따라 나와서 지역에 맞지 않는다 → content/monsters.yml 의 몬스터를 지역에 맞춰 직접 내보낸다.
 * <ul>
 *   <li>원작 서식지가 있는 몬스터는 그 지역에서 (절망의 평원 = 오크 · 미노타우로스, 유로키나 산맥 = 다크엘프, 니플하임 · 죽음의 계곡 = 불사의 군단,
 *       바란 마을 둘레 = 리자드맨 …), 나머지는 지역 태그 · 위험도로 (초보 땅 = 토끼 · 여우 · 너구리 · 사슴)</li>
 *   <li>사람 둘레 18 ~ 32 블록, 도시 성벽 밖에만. 사람마다 근처에 몇 마리까지만, 멀어지면 사라진다 (저장하지 않음)</li>
 *   <li>hostile 몬스터는 12 블록 안의 사람에게 먼저 덤비고, 순한 짐승(토끼 · 여우 · 사슴 · 곰 …)은 맞으면 때린 사람을 쫓아가 문다 (24 블록 넘게 멀어지면 포기)</li>
 *   <li>이름표에 레벨, 체력 · 공격력은 몬스터마다, 낮에도 타지 않는다. 전리품은 게임 재료 (품질은 레벨만큼) — 고유 무기는 잡은 사람에게 배달</li>
 *   <li>도시 성벽 안에는 밤 몬스터가 자연히 생기지 않는다</li>
 * </ul>
 */
public final class FieldMobRuntime implements Listener {
    static final String TAG = "versa_field";
    /** 몸에 근접 공격 행동이 없어서 직접 쫓아가 무는 몸 */
    private static final Set<EntityType> BITERS = EnumSet.of(EntityType.RABBIT, EntityType.FOX, EntityType.GOAT);

    private final GameServices s;
    private final io.versaera.platform.bukkit.binding.ItemCodec codec;
    private final io.versaera.platform.bukkit.Async async;
    private final java.util.function.Consumer<Player> deliver;
    private final Random rng = new Random();
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Map<UUID, FieldMonster> kindOf = new HashMap<>();
    private final Set<UUID> ours = new HashSet<>();
    /** 화난 몬스터 → 쫓는 사람 */
    private final Map<UUID, UUID> foes = new HashMap<>();
    private final Map<UUID, Long> bitAt = new HashMap<>();
    private final Map<String, Map<FieldMonster, Integer>> tables = new HashMap<>();

    public FieldMobRuntime(Plugin plugin, GameServices s, io.versaera.platform.bukkit.binding.ItemCodec codec, io.versaera.platform.bukkit.Async async,
                           java.util.function.Consumer<Player> deliver) {
        this.s = s;
        this.codec = codec;
        this.async = async;
        this.deliver = deliver;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 80L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::chase, 20L, 5L);
    }

    /** 이 지역에 나오는 몬스터 (지역마다 한 번 계산) */
    private Map<FieldMonster, Integer> table(Region r) {
        return tables.computeIfAbsent(r.id(), k -> FieldMonster.table(s.content.expansion().monsters(), r, s.regions));
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
            UUID u = it.next();
            Entity e = Bukkit.getEntity(u);
            if (e == null || !e.isValid()) { it.remove(); levels.remove(u); kindOf.remove(u); foes.remove(u); continue; }
            boolean near = false;
            for (Player p : e.getWorld().getPlayers()) if (p.getLocation().distanceSquared(e.getLocation()) < 80 * 80) { near = true; break; }
            if (!near) { e.remove(); it.remove(); levels.remove(u); kindOf.remove(u); foes.remove(u); }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location at = p.getLocation();
            World w = at.getWorld();
            Region r = s.regions.at(w.getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (r == null) continue;
            int cap = r.danger() <= 1 ? 6 : 8, near = 0;
            for (UUID u : ours) {
                Entity e = Bukkit.getEntity(u);
                if (e != null && e.getWorld().equals(w) && e.getLocation().distanceSquared(at) < 40 * 40) near++;
            }
            if (near >= cap) continue;
            Location spot = spot(at, r);
            if (spot == null) continue;
            Region sr = s.regions.at(w.getName(), spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
            if (sr == null) continue;
            FieldMonster k = FieldMonster.pick(table(sr), rng.nextDouble());
            if (k == null) continue;
            int pack = k.minPack() + rng.nextInt(k.maxPack() - k.minPack() + 1);
            for (int i = 0; i < pack && near + i < cap; i++) spawn(k, spot.clone().add(rng.nextInt(3) - 1, 0, rng.nextInt(3) - 1));
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

    private void spawn(FieldMonster k, Location at) {
        EntityType type;
        try {
            type = EntityType.valueOf(k.entity());
        } catch (IllegalArgumentException e) {
            return;
        }
        Entity e = at.getWorld().spawnEntity(at, type);
        if (!(e instanceof LivingEntity le)) { e.remove(); return; }
        le.addScoreboardTag(TAG);
        for (var kind : k.kinds()) le.addScoreboardTag("versa_kind_" + kind.name());
        le.setRemoveWhenFarAway(true);
        if (k.baby() && le instanceof org.bukkit.entity.Ageable ag) ag.setBaby();
        int lv = k.minLevel() + rng.nextInt(k.maxLevel() - k.minLevel() + 1);
        levels.put(le.getUniqueId(), lv);
        kindOf.put(le.getUniqueId(), k);
        le.setCustomName(Ui.c((k.hostile() ? "&c" : "&f") + k.name() + " &7Lv." + lv));
        le.setCustomNameVisible(true);
        AttributeInstance max = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(k.hp());
            le.setHealth(k.hp());
        }
        AttributeInstance atk = le.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(k.damage());
        if (k.hostile() && le instanceof Wolf wolf) wolf.setAngry(true);
        ours.add(le.getUniqueId());
    }

    /** 맞으면 반격한다: 때린 사람을 쫓는다 (순한 짐승도 사냥감이지만 공짜는 아니다) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        Player p = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (p == null) return;
        if (!foes.containsKey(e.getEntity().getUniqueId())) {
            LivingEntity le = (LivingEntity) e.getEntity();
            String name = le.getCustomName();
            if (name != null) le.setCustomName(name.replace("§f", "§c"));
            if (e.getEntityType() == EntityType.FOX || e.getEntityType() == EntityType.RABBIT)
                le.getWorld().playSound(le.getLocation(), e.getEntityType() == EntityType.FOX ? org.bukkit.Sound.ENTITY_FOX_AGGRO : org.bukkit.Sound.ENTITY_RABBIT_ATTACK, 1f, 1.2f);
        }
        foes.put(e.getEntity().getUniqueId(), p.getUniqueId());
    }

    private static boolean fair(Player p) {
        return !p.isDead() && p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    private void chase() {
        // 먼저 덤비는 몬스터: 12 블록 안의 사람을 찾는다
        for (UUID u : ours) {
            if (foes.containsKey(u)) continue;
            FieldMonster k = kindOf.get(u);
            if (k == null || !k.hostile()) continue;
            Entity e = Bukkit.getEntity(u);
            if (e == null || !e.isValid()) continue;
            Player best = null;
            double bd = 12 * 12;
            for (Player p : e.getWorld().getPlayers()) {
                double d = p.getLocation().distanceSquared(e.getLocation());
                if (d < bd && fair(p)) { bd = d; best = p; }
            }
            if (best != null) foes.put(u, best.getUniqueId());
        }
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, UUID>> it = foes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> f = it.next();
            Entity e = Bukkit.getEntity(f.getKey());
            Player p = Bukkit.getPlayer(f.getValue());
            if (!(e instanceof org.bukkit.entity.Mob m) || !m.isValid() || p == null || !fair(p) || !p.getWorld().equals(m.getWorld())
                    || p.getLocation().distanceSquared(m.getLocation()) > 24 * 24) {
                it.remove();
                bitAt.remove(f.getKey());
                if (e instanceof org.bukkit.entity.Mob m2 && m2.isValid()) m2.setTarget(null);
                continue;
            }
            if (!BITERS.contains(m.getType())) {   // 근접 · 원거리 공격 행동이 있는 몸: 표적만 정해 주면 알아서 싸운다
                if (m.getTarget() != p) m.setTarget(p);
                continue;
            }
            boolean fox = m.getType() == EntityType.FOX;
            if (p.getLocation().distanceSquared(m.getLocation()) <= (fox ? 2.2 * 2.2 : 1.8 * 1.8)) {
                if (now - bitAt.getOrDefault(m.getUniqueId(), 0L) < (fox ? 1000 : 1300)) continue;
                bitAt.put(m.getUniqueId(), now);
                FieldMonster k = kindOf.get(m.getUniqueId());
                int lv = levels.getOrDefault(m.getUniqueId(), 1);
                p.damage((k == null ? 1.0 : k.damage()) + lv * 0.15, m);
                m.getWorld().playSound(m.getLocation(), fox ? org.bukkit.Sound.ENTITY_FOX_BITE
                        : m.getType() == EntityType.GOAT ? org.bukkit.Sound.ENTITY_GOAT_RAM_IMPACT : org.bukkit.Sound.ENTITY_RABBIT_ATTACK, 1f, 1f);
            } else m.getPathfinder().moveTo(p, fox ? 1.5 : 1.8);
        }
    }

    /** 전리품: 바닐라 대신 게임 재료 (품질은 레벨만큼). 고유 아이템(무기 · 방어구)은 잡은 사람에게 배달 */
    @EventHandler
    public void onDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        UUID id = e.getEntity().getUniqueId();
        int lv = levels.getOrDefault(id, 1);
        FieldMonster k = kindOf.remove(id);
        levels.remove(id);
        foes.remove(id);
        ours.remove(id);
        e.getDrops().clear();
        if (k == null) return;
        int q = Math.min(950, 200 + lv * 2 + rng.nextInt(80));
        Player killer = e.getEntity().getKiller();
        for (FieldMonster.Drop d : k.drops()) {
            if (rng.nextDouble() >= d.chance()) continue;
            var type = codec.types().get(d.item());
            if (type == null) continue;
            if (!type.category().unique()) {
                e.getDrops().add(codec.bulk(d.item(), q, d.count()));
                continue;
            }
            if (killer == null) continue;
            String uuid = killer.getUniqueId().toString(), req = UUID.randomUUID().toString();
            async.run("field-drop", () -> s.items.create(d.item(), q, null, k.name(), "drop", Map.of(), uuid, req), it -> {
                Bukkit.broadcastMessage(Ui.c("&6" + killer.getName() + " &f님이 &e" + k.name() + "&f 에게서 &d" + type.name() + "&f 을(를) 얻었습니다!"));
                if (killer.isOnline()) deliver.accept(killer);
            }, null);
        }
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
        kindOf.clear();
        foes.clear();
    }
}
