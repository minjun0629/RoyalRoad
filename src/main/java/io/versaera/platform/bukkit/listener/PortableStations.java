package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사람이 놓은 작업대 (베틀 · 모루 · 훈연기 …): 세계 복구가 지우지 않고, 주인이 <b>웅크리고 우클릭</b>하거나 부수면 가방으로 돌아온다.
 * 다른 사람은 쓸 수만 있고 부수거나 가져갈 수 없다. 폭발에도 남는다. 자리는 runtime_state 에 남아 재시작해도 주인을 기억한다.
 */
public final class PortableStations implements Listener {
    static final String SCOPE = "station";

    private final GameServices s;
    private final Async async;
    /** "world,x,y,z" → 주인 uuid */
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    public PortableStations(GameServices s, Async async) {
        this.s = s;
        this.async = async;
        async.fire("stations-load", () -> {
            s.state.loadAll(SCOPE).forEach((k, d) -> { if (d.get("owner") != null) owners.put(k, d.get("owner")); });
            return null;
        });
    }

    private static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    /** 사람이 놓은 작업대인가 (세계 복구 예외) */
    public boolean placed(Block b) {
        return StationListener.STATIONS.containsKey(b.getType()) && owners.containsKey(key(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!StationListener.STATIONS.containsKey(b.getType()) || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        String k = key(b.getLocation()), owner = e.getPlayer().getUniqueId().toString(), type = b.getType().name();
        owners.put(k, owner);
        async.fire("station-save", () -> { s.state.save(SCOPE, k, Map.of("owner", owner, "type", type), 0); return null; });
        Ui.bar(e.getPlayer(), "&7웅크리고 우클릭하면 다시 가방으로");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || !e.getPlayer().isSneaking() || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!placed(b)) return;
        e.setCancelled(true);
        take(e.getPlayer(), b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!placed(e.getBlock())) return;
        e.setCancelled(true);   // 바닐라 드롭 대신 가방으로 (세계 복구도 되돌리지 않게)
        take(e.getPlayer(), e.getBlock());
    }

    private void take(Player p, Block b) {
        String k = key(b.getLocation()), owner = owners.get(k);
        if (owner == null) return;
        if (!owner.equals(p.getUniqueId().toString()) && !p.hasPermission("versaera.admin")) {
            p.sendMessage(Ui.error("다른 사람이 놓은 작업대입니다 — 쓸 수만 있어요"));
            return;
        }
        Material m = b.getType();
        b.setType(Material.AIR, false);
        owners.remove(k);
        async.fire("station-drop", () -> { s.state.delete(SCOPE, k); return null; });
        ItemStack item = new ItemStack(m);
        if (m == Material.LOOM) {   // 튜토리얼에서 준 베틀과 같은 이름
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Ui.c("&f베틀 &7(재봉 작업대)"));
                meta.setLore(List.of(Ui.c("&7땅에 놓고 우클릭 → 리넨 짜기")));
                item.setItemMeta(meta);
            }
        }
        p.getInventory().addItem(item).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
        Ui.bar(p, "&a작업대를 거뒀습니다");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::placed);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::placed);
    }

    /** 자리에 작업대가 없어졌으면 (관리자가 지움 · 세계 초기화) 기록도 지운다 */
    public void prune() {
        for (String k : List.copyOf(owners.keySet())) {
            String[] p = k.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null || !w.isChunkLoaded(Integer.parseInt(p[1]) >> 4, Integer.parseInt(p[3]) >> 4)) continue;
            if (!StationListener.STATIONS.containsKey(w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])).getType())) {
                owners.remove(k);
                async.fire("station-drop", () -> { s.state.delete(SCOPE, k); return null; });
            }
        }
    }
}
