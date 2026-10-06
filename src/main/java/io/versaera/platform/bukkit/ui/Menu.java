package io.versaera.platform.bukkit.ui;

import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 메뉴 (상자 창 기반). 리소스팩을 쓰는 서버는 제목에 배경 글자(U+E000~)를 깔아 전용 배경을 보여 준다 (UI-01).
 * 팩을 끈 서버에서는 배경 글자가 네모로 보이므로 쓰지 않는다 (background 가 false).
 * 규칙: 아이콘 + 짧은 이름 + 숫자. 설명 문장은 쓰지 않는다. 모든 클릭은 취소되고, 정해진 칸만 동작한다.
 */
public class Menu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    /** 리소스팩 배경 사용 여부 (플러그인이 팩 서버를 켰을 때만 true) */
    public static volatile boolean background = false;

    public Menu(int rows, String title) {
        inv = Bukkit.createInventory(this, rows * 9, Ui.c(background ? bg(rows) + title : title));
    }

    /** 왼쪽으로 8 당김 + 배경 + 다시 169 당겨 제목 자리로 */
    private static String bg(int rows) {
        char glyph = rows >= 6 ? io.versaera.pack.ResourcePackBuilder.MENU_BG_6 : rows >= 3 ? io.versaera.pack.ResourcePackBuilder.MENU_BG_3 : 0;
        if (glyph == 0) return "";
        return "&f" + io.versaera.pack.ResourcePackBuilder.SHIFT_LEFT_8 + glyph + io.versaera.pack.ResourcePackBuilder.SHIFT_LEFT_169;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static ItemStack icon(Material m, String name, List<String> lines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Ui.c(name));
        meta.setLore(lines.stream().map(Ui::c).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    public void set(int slot, ItemStack icon, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, icon);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** MenuListener 가 부른다 */
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    public void closed(Player p) {
    }
}
