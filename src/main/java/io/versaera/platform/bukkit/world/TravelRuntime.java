package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.port.AdventureRepository.Journey;
import io.versaera.domain.terrain.SettlementPlanner;
import io.versaera.domain.travel.Route;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 이동 (TRV-01 · TRV-02).
 * <ul>
 *   <li>탈것: 부르면 안장 얹은 말이 나오고 바로 탄다. 내리면 사라진다 (저장하지 않음). 2초마다 달린 거리를 재어 100 블록마다 승마 숙련</li>
 *   <li>마차 · 배: 표를 사면 여행 중 — 자리에서 움직일 수 없고 다치지 않는다. 진행 막대가 차면 도착 도시 광장 옆 큰길에 내린다.
 *       접속을 끊었다 돌아와도 도착 시각이 지났으면 도착지에서 깨어난다 (DB)</li>
 * </ul>
 */
public final class TravelRuntime implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final NamespacedKey key;
    private final Map<UUID, AbstractHorse> mounts = new HashMap<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Double> ridden = new HashMap<>();
    private final Map<UUID, Trip> trips = new HashMap<>();

    private record Trip(Journey journey, String label, BossBar bar, Location anchor) {}

    public TravelRuntime(Plugin plugin, GameServices s, Async async) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.key = new NamespacedKey(plugin, "mount");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tripTick, 20L, 10L);
    }

    // ------------------------------------------------------------------ 탈것
    public void summonMount(Player p, String mountId) {
        if (trips.containsKey(p.getUniqueId())) { p.sendMessage(Ui.error("여행 중입니다")); return; }
        String uuid = p.getUniqueId().toString();
        async.run("mount-summon", () -> s.travel.summon(uuid, mountId), ride -> {
            dismissMount(p);
            EntityType type;
            try {
                type = EntityType.valueOf(ride.kind().entity());
            } catch (IllegalArgumentException ex) {
                p.sendMessage(Ui.error("이 서버 버전에 없는 탈것입니다: " + ride.kind().entity()));
                return;
            }
            Entity e = p.getWorld().spawnEntity(p.getLocation(), type);
            if (!(e instanceof AbstractHorse h)) { e.remove(); p.sendMessage(Ui.error("탈 수 없는 동물입니다")); return; }
            h.setPersistent(false);
            h.setTamed(true);
            h.setOwner(p);
            h.setAdult();
            h.setCustomName(Ui.c("&6" + ride.mount().name()));
            h.getPersistentDataContainer().set(key, PersistentDataType.STRING, uuid);
            var sp = h.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(ride.speed());
            var hp = h.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(ride.kind().health()); h.setHealth(ride.kind().health()); }
            h.setJumpStrength(ride.kind().jump());
            if (h instanceof Horse horse && !ride.kind().color().equals("NONE")) {
                try {
                    horse.setColor(Horse.Color.valueOf(ride.kind().color()));
                } catch (IllegalArgumentException ignored) {
                    // 모르는 색이면 기본
                }
            }
            h.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            h.addPassenger(p);
            mounts.put(p.getUniqueId(), h);
            lastPos.put(p.getUniqueId(), p.getLocation());
        }, p);
    }

    public void dismissMount(Player p) {
        AbstractHorse h = mounts.remove(p.getUniqueId());
        if (h != null) h.remove();
        flushRide(p.getUniqueId());
    }

    private boolean isMount(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private void tick() {
        for (Iterator<Map.Entry<UUID, AbstractHorse>> it = mounts.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            AbstractHorse h = en.getValue();
            if (p == null || !h.isValid()) {
                h.remove();
                it.remove();
                flushRide(en.getKey());
                continue;
            }
            if (!h.getPassengers().contains(p)) {   // 내렸다 → 돌려보낸다
                h.remove();
                it.remove();
                flushRide(en.getKey());
                continue;
            }
            Location now = p.getLocation(), before = lastPos.put(p.getUniqueId(), now);
            if (before != null && before.getWorld().equals(now.getWorld())) {
                double d = Math.min(60, before.distance(now));   // 순간 이동은 세지 않음
                double sum = ridden.merge(p.getUniqueId(), d, Double::sum);
                if (sum >= 100) {
                    int hundreds = (int) (sum / 100);
                    ridden.put(p.getUniqueId(), sum - hundreds * 100);
                    String uuid = p.getUniqueId().toString();
                    async.fire("ride", () -> { s.travel.rode(uuid, hundreds); return null; });
                }
            }
        }
    }

    private void flushRide(UUID u) {
        lastPos.remove(u);
        ridden.remove(u);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMountClick(PlayerInteractEntityEvent e) {
        if (!isMount(e.getRightClicked())) return;
        String owner = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (!e.getPlayer().getUniqueId().toString().equals(owner)) e.setCancelled(true);   // 남의 말에는 못 탄다
    }

    // ------------------------------------------------------------------ 마차 · 배
    /** 표를 사고 떠난다 (NPC 창에서) */
    public void depart(Player p, String npcId, Route r, String here) {
        dismissMount(p);
        String uuid = p.getUniqueId().toString(), req = UUID.randomUUID().toString();
        async.run("travel-depart", () -> s.travel.depart(uuid, npcId, r.id(), here, req), j -> {
            begin(p, j, r);
            p.sendMessage(Ui.info(r.kind().label + "에 올랐다 → " + s.regions.byId(r.to()).name() + " &7(" + Math.max(1, (j.arriveAt() - j.departAt()) / 1000) + "초 · -" + r.fare() + " 골드)"));
        }, p);
    }

    private void begin(Player p, Journey j, Route r) {
        String to = s.regions.byId(j.dest()) == null ? j.dest() : s.regions.byId(j.dest()).name();
        String label = (r == null ? "여행" : r.kind().label) + " · " + to;
        BossBar bar = Bukkit.createBossBar(Ui.c("&f" + label), r != null && r.kind() == Route.Kind.SHIP ? BarColor.BLUE : BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.addPlayer(p);
        Trip old = trips.put(p.getUniqueId(), new Trip(j, label, bar, p.getLocation()));
        if (old != null) old.bar().removeAll();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 3600, 6, false, false));
        p.playSound(p.getLocation(), r != null && r.kind() == Route.Kind.SHIP ? Sound.ENTITY_BOAT_PADDLE_WATER : Sound.ENTITY_HORSE_GALLOP, 1f, 1f);
    }

    private void tripTick() {
        long now = System.currentTimeMillis();
        for (var en : new ArrayList<>(trips.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            Trip t = en.getValue();
            if (p == null) { t.bar().removeAll(); trips.remove(en.getKey()); continue; }
            Journey j = t.journey();
            double prog = Math.max(0, Math.min(1, (now - j.departAt()) / (double) Math.max(1, j.arriveAt() - j.departAt())));
            t.bar().setProgress(prog);
            t.bar().setTitle(Ui.c("&f" + t.label() + " &7" + Math.max(0, (j.arriveAt() - now) / 1000) + "초"));
            if (now >= j.arriveAt()) arrive(p);
        }
    }

    private void arrive(Player p) {
        Trip t = trips.remove(p.getUniqueId());
        if (t != null) t.bar().removeAll();
        String uuid = p.getUniqueId().toString();
        async.run("travel-arrive", () -> s.travel.arrive(uuid), dest -> {
            p.removePotionEffect(PotionEffectType.SLOW);
            if (dest.isEmpty()) return;
            Region r = s.regions.byId(dest.get());
            World w = r == null ? null : Bukkit.getWorld(r.world());
            if (w == null) { p.sendMessage(Ui.error("도착지 세계가 없습니다: " + dest.get())); return; }
            int[] g = SettlementPlanner.townGrid(r);
            int x = g[0] + 14, z = g[1] + 1;
            Location at = new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5, p.getLocation().getYaw(), 0);
            p.teleport(at);
            p.playSound(at, Sound.BLOCK_BELL_USE, 1f, 1.2f);
            p.sendTitle(Ui.c("&f" + r.name()), Ui.c("&7도착"), 5, 40, 10);
        }, p);
    }

    public boolean traveling(UUID u) {
        return trips.containsKey(u);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Trip t = trips.get(e.getPlayer().getUniqueId());
        if (t == null || e.getTo() == null) return;
        Location to = e.getTo(), a = t.anchor();
        if (to.getWorld().equals(a.getWorld()) && (Math.abs(to.getX() - a.getX()) > 0.01 || Math.abs(to.getZ() - a.getZ()) > 0.01 || Math.abs(to.getY() - a.getY()) > 0.6)) {
            Location back = a.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && trips.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();
        async.run("travel-resume", () -> s.travel.journey(uuid), j -> {
            if (j.isEmpty() || !p.isOnline()) return;
            if (j.get().arriveAt() <= System.currentTimeMillis()) arrive(p);
            else begin(p, j.get(), s.travel.network().route(j.get().route()).orElse(null));
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismissMount(e.getPlayer());
        Trip t = trips.remove(e.getPlayer().getUniqueId());
        if (t != null) t.bar().removeAll();
    }

    public void shutdown() {
        for (AbstractHorse h : mounts.values()) h.remove();
        mounts.clear();
        for (Trip t : trips.values()) t.bar().removeAll();
        trips.clear();
    }
}
