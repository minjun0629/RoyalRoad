package io.versaera.platform.bukkit.listener;

import io.versaera.domain.item.ItemType;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 입체 투구 쓰기 (RP-01). 입체 투구는 바닐라 투구 재질이 아니라서(머리에 쓰면 리소스팩 3D 모델이 그대로 보이게) 바닐라가 투구 칸에 넣어 주지 않는다.
 * 그래서 서버가 대신 씌운다: 손에 들고 우클릭 · 커서로 투구 칸에 놓기 · 쉬프트 클릭(투구 칸이 비었을 때).
 * 방어력 · 세트는 CombatListener 가 투구 칸을 그대로 읽는다.
 */
public final class HeadGear implements Listener {
    private static final int HELMET_SLOT = 39;
    private final ItemCodec codec;

    public HeadGear(ItemCodec codec) {
        this.codec = codec;
    }

    /** 서버가 씌워 줘야 하는 입체 투구인가 */
    private boolean headItem(ItemStack s) {
        if (s == null || s.getAmount() != 1) return false;
        String id = codec.typeId(s);
        if (id == null) return false;
        ItemType t = codec.types().get(id);
        return (t.hasTag("helmet") || t.hasTag("crown")) && !t.material().endsWith("_HELMET");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!headItem(hand)) return;
        e.setCancelled(true);
        ItemStack old = p.getInventory().getHelmet();
        p.getInventory().setHelmet(hand);
        p.getInventory().setItemInMainHand(old);
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != p.getInventory()) return;
        if (e.getSlotType() == InventoryType.SlotType.ARMOR && e.getSlot() == HELMET_SLOT && headItem(e.getCursor())) {   // 커서로 투구 칸에
            e.setCancelled(true);
            ItemStack old = p.getInventory().getHelmet();
            p.getInventory().setHelmet(e.getCursor().clone());
            p.setItemOnCursor(old);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        } else if (e.isShiftClick() && e.getSlotType() != InventoryType.SlotType.ARMOR && headItem(e.getCurrentItem())
                && (p.getInventory().getHelmet() == null || p.getInventory().getHelmet().getType().isAir())) {   // 쉬프트 클릭
            e.setCancelled(true);
            p.getInventory().setHelmet(e.getCurrentItem().clone());
            p.getInventory().setItem(e.getSlot(), null);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f);
        }
    }
}
