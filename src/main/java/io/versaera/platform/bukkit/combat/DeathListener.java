package io.versaera.platform.bukkit.combat;

import io.versaera.application.GameServices;
import io.versaera.application.ReputationService;
import io.versaera.domain.death.DeathPenalty;
import io.versaera.domain.reputation.Reputation;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 사망 (DTH-01 · DTH-02). 계산은 DeathService.
 * <p>원작식(canon): 서버 난수로 고른 칸의 아이템이 그 자리에 떨어지고, 숙련이 레벨까지 떨어지며, 현실 24시간 동안 접속할 수 없다 (곧바로 내보냄).
 * 떨어진 고유 아이템은 DB 에서 '땅(ground)'이 되어 아무의 것도 아니고, 처음 주운 사람이 서버 판정으로 임자가 된다 — 복제 · 동시 줍기 불가.
 * 땅에서 사라지면(5분) 그 아이템도 사라진다. 초보 기간에는 아무 페널티도 없다.
 * <p>완화판(soft): 드롭 · 접속 제한 없음 — 진행도 감소 · 장비 마모 · 쇠약만.
 */
public final class DeathListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<UUID, Integer> weakOnRespawn = new HashMap<>();
    private final SplittableRandom rng = new SplittableRandom();
    private Predicate<UUID> beginner = u -> false;
    private Function<UUID, ReputationService.Standing> standing = u -> new ReputationService.Standing(0, 0, 0, false);

    public DeathListener(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
    }

    /** 메인 스레드 캐시 (OriginListener · ReputationListener) */
    public void caches(Predicate<UUID> beginner, Function<UUID, ReputationService.Standing> standing) {
        this.beginner = beginner;
        this.standing = standing;
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
        int baseDanger = r == null ? 0 : r.danger();
        List<String> equipped = new ArrayList<>();
        List<ItemStack> worn = new ArrayList<>(Arrays.asList(p.getInventory().getArmorContents()));
        worn.add(p.getInventory().getItemInMainHand());
        worn.add(p.getInventory().getItemInOffHand());
        for (ItemStack it : worn) {
            String id = codec.instanceId(it);
            if (id != null) equipped.add(id);
        }
        String id = p.getUniqueId().toString();
        // 원작식 드롭: 서버가 칸을 고르고 지금 바로 떨어뜨린다 (초보 · 완화판 · 던전 안은 없음)
        List<String> droppedUnique = new ArrayList<>();
        boolean canon = s.rules().canonDeath() && !beginner.test(p.getUniqueId()) && s.dungeons.runOf(id).isEmpty();
        if (canon) {
            ReputationService.Standing st = standing.apply(p.getUniqueId());
            int n = DeathPenalty.canonDrops(baseDanger + s.worldEvents.dangerBonus(region), Reputation.deathMult(st.notoriety(), st.murderer()));
            PlayerInventory inv = p.getInventory();
            List<Integer> filled = new ArrayList<>();
            for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) != null && inv.getItem(i).getType().isItem() && inv.getItem(i).getAmount() > 0) filled.add(i);
            for (int k = 0; k < n && !filled.isEmpty(); k++) {
                int slot = filled.remove(rng.nextInt(filled.size()));
                ItemStack it = inv.getItem(slot);
                String iid = codec.instanceId(it);
                if (iid != null) droppedUnique.add(iid);
                e.getDrops().add(it.clone());
                inv.setItem(slot, null);
            }
        }
        String dropId = UUID.randomUUID().toString();
        async.run("death", () -> {
            for (String iid : droppedUnique) s.items.dropToGround(iid, id, dropId);   // 줍기 요청보다 먼저 (DB 스레드는 한 줄)
            int danger = r == null ? 0 : r.danger() + s.worldEvents.dangerBonus(region);
            var res = s.deaths.die(id, region, danger, equipped);
            s.dungeons.runOf(id).ifPresent(h -> s.dungeons.memberDown(h.runId(), id));
            return res;
        }, res -> {
            weakOnRespawn.put(p.getUniqueId(), res.weakSeconds());
            if (!p.isOnline()) return;
            if (res.beginner()) {
                p.sendMessage(Ui.c("&7초보 기간이라 사망 페널티가 없습니다"));
                return;
            }
            p.sendMessage(Ui.c("&7숙련 &c-" + res.totalXpLoss() + " &7· 장비 마모 &c-" + res.wear() + (res.heavyWear() ? " &7· 최대 내구도 &c-1" : "")
                    + (e.getDrops().isEmpty() ? "" : " &7· 떨어뜨린 물건 &c" + e.getDrops().size() + "개")));
            if (res.lockUntil() > 0) {
                long h = (res.lockUntil() - System.currentTimeMillis() + 59_999) / 3_600_000L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && !p.hasPermission("versaera.admin"))
                        p.kickPlayer(Ui.c("&c사망했습니다\n&7원작처럼 현실 " + Math.max(1, h) + "시간 동안 접속할 수 없습니다"));
                }, 60L);
            }
        }, p);
    }

    /** 땅의 고유 아이템: 줍기를 막고 서버에 '먼저 주운 사람'을 묻는다 (남의 아이템은 서버가 허락할 때만) */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        Item entity = e.getItem();
        String iid = codec.instanceId(entity.getItemStack());
        if (iid == null) return;
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p)) return;
        if (entity.getPickupDelay() > 0) return;
        entity.setPickupDelay(40);   // 결과가 올 때까지 다른 사람도 못 줍게
        String who = p.getUniqueId().toString();
        // 땅의 아이템이면 먼저 온 사람이 임자, 원래 자기 것(가방이 넘쳐 떨어진 것)이면 그냥 줍는다
        async.run("pickup", () -> s.items.claimFromGround(iid, who) || s.items.find(iid).map(it -> it.custody().ownedBy(who)).orElse(false), ok -> {
            if (!ok || !entity.isValid()) return;
            ItemStack stack = entity.getItemStack();
            entity.remove();
            Map<Integer, ItemStack> over = p.isOnline() ? p.getInventory().addItem(stack) : Map.of(0, stack);
            if (!over.isEmpty()) {
                // 가방이 가득: 주운 사람의 배달함으로 (이미 그 사람 것)
                async.fire("pickup-overflow", () -> {
                    s.items.redeliver(iid, who);
                    return null;
                });
            }
        }, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent e) {
        if (codec.instanceId(e.getItem().getItemStack()) != null) e.setCancelled(true);
    }

    /** 땅에서 사라짐 (5분) → 그 고유 아이템도 사라진다 */
    @EventHandler
    public void onDespawn(ItemDespawnEvent e) {
        String iid = codec.instanceId(e.getEntity().getItemStack());
        if (iid == null) return;
        async.fire("ground-lost", () -> {
            if (s.items.onGround(iid)) s.items.destroy(iid, "server", "땅에서 사라짐", "ground-lost:" + iid);
            return null;
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Integer secs = weakOnRespawn.remove(p.getUniqueId());
            if (secs == null || secs <= 0 || !p.isOnline()) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, secs * 20, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Math.min(secs, 30) * 20, 0));
        }, 2L);
    }
}
