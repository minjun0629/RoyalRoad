package io.versaera.platform.bukkit.listener;

import io.versaera.domain.item.ItemType;
import io.versaera.domain.pack.PackIds;
import io.versaera.pack.ResourcePackBuilder;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 손에 든 장비의 모습 (리소스팩). 1.20.1 은 한 모델로 '인벤토리 칸'과 '손'을 가를 수 없어서, 카드 모델(인벤토리)과
 * 입체 모델(손 · 다른 사람 눈)을 따로 두고 손에 든 칸(주 손 · 왼손)의 아이템만 CustomModelData 를 손 모델 번호로 바꾼다.
 * 손을 바꾸면 바로, 그 밖에는 0.25 초마다 맞춘다 (인벤토리 정리 · 줍기 · 버리기 뒤에도).
 */
public final class HandModels implements Listener {
    private final Plugin plugin;
    private final ItemCodec codec;
    private final Map<String, Boolean> hasHand = new ConcurrentHashMap<>();
    private int beat;

    public HandModels(Plugin plugin, ItemCodec codec) {
        this.plugin = plugin;
        this.codec = codec;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
    }

    private boolean hand(String typeId) {
        return hasHand.computeIfAbsent(typeId, id -> {
            ItemType t = codec.types().get(id);
            return t != null && ResourcePackBuilder.hasHandModel(t);
        });
    }

    private void tick() {
        boolean all = ++beat % 8 == 0;   // 2 초마다 가방 전체, 그 사이엔 단축 칸 · 왼손만
        for (Player p : Bukkit.getOnlinePlayers()) fix(p, all);
    }

    private void fix(Player p, boolean all) {
        PlayerInventory inv = p.getInventory();
        int held = inv.getHeldItemSlot();
        for (int slot = 0; slot < (all ? 36 : 9); slot++) set(inv.getItem(slot), slot == held, inv, slot);
        set(inv.getItemInOffHand(), true, inv, 40);
    }

    private void set(ItemStack it, boolean inHand, PlayerInventory inv, int slot) {
        if (it == null || it.getType().isAir()) return;
        String t = codec.typeId(it);
        if (t == null || !hand(t)) return;
        int want = inHand ? PackIds.itemHand(t) : PackIds.item(t);
        ItemMeta m = it.getItemMeta();
        if (m.hasCustomModelData() && m.getCustomModelData() == want) return;
        m.setCustomModelData(want);
        it.setItemMeta(m);
        inv.setItem(slot, it);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) fix(p, false); });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) fix(p, false); });
    }
}
