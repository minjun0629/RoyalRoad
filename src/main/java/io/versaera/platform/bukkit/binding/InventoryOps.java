package io.versaera.platform.bukkit.binding;

import io.versaera.domain.crafting.MaterialInput;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 인벤토리에서 묶음 재료를 꺼내기 (납품 · NPC 판매 · 경매 등록). 메인 스레드 전용.
 * 먼저 충분한지 세고, 충분할 때만 뺀다 (반만 빠지는 일 없음). 높은 품질부터 쓰지 않고 낮은 품질부터 쓴다.
 */
public final class InventoryOps {
    private InventoryOps() {
    }

    public static int count(Player p, ItemCodec codec, String typeId, int minQuality) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents())
            if (typeId.equals(codec.typeId(it)) && codec.instanceId(it) == null && codec.bulkQuality(it) >= minQuality) n += it.getAmount();
        return n;
    }

    /** @return 뺀 재료 (품질별), 모자라면 null (아무것도 빼지 않음) */
    public static List<MaterialInput> take(Player p, ItemCodec codec, String typeId, int amount, int minQuality) {
        if (amount <= 0 || count(p, codec, typeId, minQuality) < amount) return null;
        ItemStack[] inv = p.getInventory().getStorageContents();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.length; i++)
            if (typeId.equals(codec.typeId(inv[i])) && codec.instanceId(inv[i]) == null && codec.bulkQuality(inv[i]) >= minQuality) slots.add(i);
        slots.sort((a, b) -> Integer.compare(codec.bulkQuality(inv[a]), codec.bulkQuality(inv[b])));
        List<MaterialInput> out = new ArrayList<>();
        int left = amount;
        for (int i : slots) {
            if (left == 0) break;
            ItemStack it = inv[i];
            int n = Math.min(left, it.getAmount());
            out.add(new MaterialInput(typeId, codec.types().get(typeId).tags(), codec.bulkQuality(it), n));
            if (n == it.getAmount()) inv[i] = null;
            else it.setAmount(it.getAmount() - n);
            left -= n;
        }
        p.getInventory().setStorageContents(inv);
        return out;
    }

    /** 실패했을 때 돌려주기 (넘치면 발밑에) */
    public static void give(Player p, ItemCodec codec, List<MaterialInput> list) {
        for (MaterialInput m : list)
            for (ItemStack over : p.getInventory().addItem(codec.bulk(m.typeId(), m.quality(), m.count())).values())
                p.getWorld().dropItemNaturally(p.getLocation(), over);
    }
}
