package io.versaera.platform.bukkit.listener;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;

/**
 * 몬스터 머리 위 체력바: 이름 뒤에 10칸 막대와 남은 체력 / 최대 체력. 맞거나 회복할 때 다음 틱에 고친다.
 * 원래 이름은 표시 앞부분 그대로 두고 막대만 갈아 끼운다 (SEP 뒤를 바꾼다) — 이름을 바꾸는 다른 코드(레벨 · 어그로 색)와 함께 쓴다.
 */
public final class HealthBars implements Listener {
    /** 이름과 막대 사이 (§r 로 시작해 다른 코드의 색 바꾸기에 걸리지 않는다) */
    static final String SEP = "§r §8|§r ";
    private static final int CELLS = 10;

    private final Plugin plugin;

    public HealthBars(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        later(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        later(e.getEntity());
    }

    private void later(Entity e) {
        if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof ArmorStand) return;
        Bukkit.getScheduler().runTask(plugin, () -> update(le));
    }

    private static void update(LivingEntity le) {
        if (!le.isValid() || le.isDead()) return;
        AttributeInstance a = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = a == null ? le.getHealth() : a.getValue(), hp = Math.max(0, le.getHealth());
        if (max <= 0) return;
        String base = baseName(le);
        le.setCustomName((base == null || base.isEmpty() ? "" : base + SEP) + bar(hp, max));
        le.setCustomNameVisible(true);
    }

    /** 체력바를 뺀 진짜 이름 (없으면 null) — 막대만 붙은 이름 없는 몬스터는 이름이 없는 것으로 본다 */
    public static String baseName(Entity e) {
        String name = e.getCustomName();
        if (name == null) return null;
        if (name.contains(SEP)) return name.substring(0, name.indexOf(SEP));
        return name.contains("▮") && name.contains("/") ? null : name;
    }

    /** "§c▮▮▮▮▮§8▮▮▮▮▮ §7123/456" */
    static String bar(double hp, double max) {
        int full = (int) Math.ceil(Math.min(1, hp / max) * CELLS);
        if (hp > 0) full = Math.max(1, full);
        String color = hp / max > 0.5 ? "§a" : hp / max > 0.25 ? "§e" : "§c";
        return color + "▮".repeat(full) + "§8" + "▮".repeat(CELLS - full) + " §7" + (long) Math.ceil(hp) + "/" + (long) Math.ceil(max);
    }
}
