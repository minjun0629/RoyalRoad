package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.TrialService;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 초급 수련관의 시련 (TRN-02): 철인 100명과 차례로 싸운다. 한 번에 셋까지 나오고, 도전자가 쓰러뜨린 철인만 센다.
 * 죽거나 · 나가거나 · 수련관에서 멀어지거나(24 블록) · 30분이 지나면 실패. 철인은 저장하지 않고 아무것도 떨어뜨리지 않는다.
 * 한 수련관에 한 사람씩.
 */
public final class IronMenTrial implements Listener {
    public static final String TAG = "versa_ironman";
    private static final String HALL = "novice_training_hall";
    private static final long LIMIT_MS = 30 * 60_000L;
    private final GameServices s;
    private final Async async;
    private Run run;

    private static final class Run {
        final UUID who;
        final Location center;
        final long started = System.currentTimeMillis();
        int killed, spawned;
        final List<LivingEntity> alive = new ArrayList<>();

        Run(UUID who, Location center) {
            this.who = who;
            this.center = center;
        }
    }

    public IronMenTrial(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void start(Player p) {
        Region hall = s.regions.byId(HALL);
        if (hall == null) return;
        Region here = s.regions.at(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
        boolean inside = false;
        for (Region r = here; r != null; r = r.parent() == null ? null : s.regions.byId(r.parent())) if (r.id().equals(HALL)) inside = true;
        if (!inside) {
            p.sendMessage(Ui.error(hall.name() + " 안에서 시작할 수 있습니다"));
            return;
        }
        if (run != null) {
            p.sendMessage(Ui.error("지금 다른 사람이 시련 중입니다"));
            return;
        }
        String id = p.getUniqueId().toString();
        async.run("trial-start", () -> {
            s.trials.checkStart(id);
            return true;
        }, ok -> {
            if (run != null || !p.isOnline()) return;
            World w = p.getWorld();
            int cx = (hall.minX() + hall.maxX()) / 2, cz = (hall.minZ() + hall.maxZ()) / 2;
            run = new Run(p.getUniqueId(), new Location(w, cx + 0.5, w.getHighestBlockYAt(cx, cz) + 1, cz + 0.5));
            p.sendTitle(Ui.c("&6철인 100명"), Ui.c("&7모두 쓰러뜨려라 — 30분"), 10, 50, 15);
        }, p);
    }

    public void giveUp(Player p) {
        if (run != null && run.who.equals(p.getUniqueId())) fail("포기했다");
    }

    private void tick() {
        Run r = run;
        if (r == null) return;
        Player p = Bukkit.getPlayer(r.who);
        if (p == null || !p.isOnline()) { fail("도전자가 떠났다"); return; }
        if (p.getWorld() != r.center.getWorld() || p.getLocation().distanceSquared(r.center) > 24 * 24) { fail("수련관을 벗어났다"); return; }
        if (System.currentTimeMillis() - r.started > LIMIT_MS) { fail("시간이 다 됐다"); return; }
        r.alive.removeIf(e -> !e.isValid());
        while (r.alive.size() < 3 && r.killed + r.alive.size() < TrialService.IRON_MEN) r.alive.add(spawn(r));
        Ui.bar(p, "&6철인 &f" + r.killed + "&7/" + TrialService.IRON_MEN + " &8· 남은 시간 " + (LIMIT_MS - (System.currentTimeMillis() - r.started)) / 60_000 + "분");
    }

    private LivingEntity spawn(Run r) {
        r.spawned++;
        double a = r.spawned * 2.4;
        Location l = r.center.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
        l.setY(l.getWorld().getHighestBlockYAt(l.getBlockX(), l.getBlockZ()) + 1);
        Vindicator v = l.getWorld().spawn(l, Vindicator.class);
        v.setPersistent(false);
        v.setRemoveWhenFarAway(false);
        v.setCustomName("철인 " + (r.killed + r.alive.size() + 1) + "번째");
        v.setCustomNameVisible(true);
        v.addScoreboardTag(TAG);
        v.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
        v.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        v.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        v.getEquipment().setHelmetDropChance(0);
        v.getEquipment().setChestplateDropChance(0);
        v.getEquipment().setItemInMainHandDropChance(0);
        Player p = Bukkit.getPlayer(r.who);
        if (p != null) v.setTarget(p);
        return v;
    }

    @EventHandler
    public void onIronManDeath(EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains(TAG)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Run r = run;
        if (r == null) return;
        r.alive.remove(e.getEntity());
        Player killer = e.getEntity().getKiller();
        if (killer == null || !killer.getUniqueId().equals(r.who)) return;   // 도전자가 쓰러뜨린 것만 센다
        r.killed++;
        if (r.killed >= TrialService.IRON_MEN) succeed(killer);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (run != null && run.who.equals(e.getEntity().getUniqueId())) fail("쓰러졌다");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (run != null && run.who.equals(e.getPlayer().getUniqueId())) fail("도전자가 떠났다");
    }

    private void succeed(Player p) {
        cleanup();
        String id = p.getUniqueId().toString(), name = p.getName();
        async.run("trial-clear", () -> s.trials.complete(id), first -> {
            p.sendTitle(Ui.c("&6시련 통과"), Ui.c("&7철인 100명을 모두 이겼다"), 10, 70, 20);
            if (first) {
                p.sendMessage(Ui.info("명성 +300 · 인내 · 힘 기록 +300"));
                Bukkit.broadcastMessage(Ui.info(name + " 님이 초급 수련관의 철인 100명을 모두 이겼습니다"));
            } else p.sendMessage(Ui.c("&7이미 통과한 시련이라 보상은 없습니다"));
        }, p);
    }

    private void fail(String why) {
        Run r = run;
        cleanup();
        Player p = r == null ? null : Bukkit.getPlayer(r.who);
        if (p != null) p.sendMessage(Ui.error("시련 실패 — " + why + " (" + r.killed + "/" + TrialService.IRON_MEN + ")"));
    }

    private void cleanup() {
        Run r = run;
        run = null;
        if (r == null) return;
        for (LivingEntity e : r.alive) e.remove();
        for (Entity e : r.center.getWorld().getNearbyEntities(r.center, 32, 16, 32)) if (e.getScoreboardTags().contains(TAG)) e.remove();
    }
}
