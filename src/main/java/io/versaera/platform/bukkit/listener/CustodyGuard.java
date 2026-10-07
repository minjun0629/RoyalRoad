package io.versaera.platform.bukkit.listener;

import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 고유 아이템 보관 경계 (ITM-03). DB 가 아는 위치는 "플레이어"(인벤토리 · 엔더 상자) · 거래 · 경매 · 배달함뿐이다.
 * 그래서 고유 아이템은 <b>그 밖으로 나갈 수 없다</b>: 바닥에 버리기 · 상자 · 화로 · 호퍼 · 액자 · 갑옷 거치대에 넣기를 막는다.
 * 남에게 주는 길은 서버가 검증하는 거래 · 경매뿐 → 검사되지 않는 곳에 복제품을 숨겨 둘 수 없다.
 * 아이템을 줍거나 · 손에 들거나 · 손을 바꾸면 그 사람을 곧바로(최대 5초에 한 번) 다시 검사한다 → 검사 주기 사이의 창을 줄인다.
 */
public final class CustodyGuard implements Listener {
    private static final long RECHECK_MS = 5000;

    private final Plugin plugin;
    private final ItemCodec codec;
    private final InventoryGuard guard;
    private final Map<UUID, Long> lastCheck = new HashMap<>();

    public CustodyGuard(Plugin plugin, ItemCodec codec, InventoryGuard guard) {
        this.plugin = plugin;
        this.codec = codec;
        this.guard = guard;
    }

    private boolean unique(ItemStack it) {
        return codec.instanceId(it) != null;
    }

    /** 플레이어 자신 · 엔더 상자 · 우리 메뉴가 아닌 위쪽 창 = 바깥 보관함 */
    private static boolean outside(Inventory top, Player p) {
        if (top == null || top.getHolder() instanceof Menu) return false;
        InventoryType t = top.getType();
        return t != InventoryType.CRAFTING && t != InventoryType.PLAYER && t != InventoryType.CREATIVE && !top.equals(p.getEnderChest());
    }

    private void soon(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastCheck.get(p.getUniqueId());
        if (last != null && now - last < RECHECK_MS) return;
        lastCheck.put(p.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) guard.scan(p); });
    }

    private static void deny(Player p) {
        Ui.bar(p, "&c고유 아이템은 거래 · 경매로만 넘길 수 있습니다");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!unique(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        deny(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() == GameMode.CREATIVE) return;
        Inventory top = e.getView().getTopInventory();
        if (!outside(top, p)) return;
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        ItemStack cursor = e.getCursor(), current = e.getCurrentItem();
        boolean bad =
                // 쉬프트 클릭으로 아래(내 인벤토리)에서 위(상자)로
                (!clickedTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && unique(current))
                // 들고 있던 것을 위 칸에 놓기 · 바꾸기
                || (clickedTop && unique(cursor) && (e.getAction().name().startsWith("PLACE") || e.getAction() == InventoryAction.SWAP_WITH_CURSOR))
                // 숫자 키로 위 칸과 핫바 맞바꾸기
                || (clickedTop && (e.getAction() == InventoryAction.HOTBAR_SWAP || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD)
                    && e.getHotbarButton() >= 0 && unique(p.getInventory().getItem(e.getHotbarButton())))
                // 창 밖으로 던지기
                || (e.getAction().name().startsWith("DROP") && (unique(cursor) || unique(current)));
        if (bad) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() == GameMode.CREATIVE || !unique(e.getOldCursor())) return;
        Inventory top = e.getView().getTopInventory();
        if (!outside(top, p)) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots())
            if (raw < topSize) {
                e.setCancelled(true);
                deny(p);
                return;
            }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame && unique(e.getPlayer().getInventory().getItem(e.getHand()))) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (unique(e.getPlayerItem())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ------------------------------------------------------------------ 곧바로 다시 검사
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && unique(e.getItem().getItemStack())) soon(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        if (unique(e.getPlayer().getInventory().getItem(e.getNewSlot()))) soon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (unique(e.getMainHandItem()) || unique(e.getOffHandItem())) soon(e.getPlayer());
    }
}
