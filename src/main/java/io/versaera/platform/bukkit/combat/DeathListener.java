package io.versaera.platform.bukkit.combat;

import io.versaera.application.GameServices;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 사망 (DTH-01). 아이템은 떨어뜨리지 않는다 (복제 · 줍기 분쟁 방지) — 대신 숙련 진행도 감소 · 장비 마모 · 쇠약.
 * 계산은 DeathService (DeathPenalty). 던전 안이면 그 판에서 쓰러진 것으로 기록.
 */
public final class DeathListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<UUID, Integer> weakOnRespawn = new HashMap<>();

    public DeathListener(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        Location l = p.getLocation();
        Region r = s.regions.at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        String region = r == null ? null : r.id();
        List<String> equipped = new ArrayList<>();
        List<ItemStack> worn = new ArrayList<>(Arrays.asList(p.getInventory().getArmorContents()));
        worn.add(p.getInventory().getItemInMainHand());
        worn.add(p.getInventory().getItemInOffHand());
        for (ItemStack it : worn) {
            String id = codec.instanceId(it);
            if (id != null) equipped.add(id);
        }
        String id = p.getUniqueId().toString();
        async.run("death", () -> {
            int danger = r == null ? 0 : r.danger() + s.worldEvents.dangerBonus(region);
            var res = s.deaths.die(id, region, danger, equipped);
            s.dungeons.runOf(id).ifPresent(h -> s.dungeons.memberDown(h.runId(), id));
            return res;
        }, res -> {
            weakOnRespawn.put(p.getUniqueId(), res.weakSeconds());
            if (p.isOnline()) p.sendMessage(Ui.c("&7숙련 진행도 &c-" + res.totalXpLoss() + " &7· 장비 마모 &c-" + res.wear() + (res.heavyWear() ? " &7· 최대 내구도 &c-1" : "")));
        }, p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Integer secs = weakOnRespawn.remove(p.getUniqueId());
            if (secs == null || !p.isOnline()) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, secs * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Math.min(secs, 30) * 20, 0));
        }, 2L);
    }
}
