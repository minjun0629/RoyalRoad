package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.potion.PotionRule;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 물약 (POT-01, CANON 개념): 즉시 회복이 아니라 회복력을 잠시 올린다 (재생 효과). 효과가 남아 있으면 다음 물약을 마실 수 없고,
 * 높은 숙련일수록 같은 물약이 덜 듣는다 (PotionRule).
 */
public final class PotionListener implements Listener {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();

    public PotionListener(GameServices s, Async async, ItemCodec codec) {
        this.s = s;
        this.async = async;
        this.codec = codec;
    }

    private static int tier(ItemType t) {
        for (int i = 5; i >= 1; i--) if (t.hasTag("potion_t" + i)) return i;
        return 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        String typeId = codec.typeId(e.getItem());
        if (typeId == null) return;
        ItemType t = codec.types().get(typeId);
        if (!t.hasTag("potion")) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (!PotionRule.canDrink(now, activeUntil.getOrDefault(p.getUniqueId(), 0L))) {
            e.setCancelled(true);
            p.sendMessage(Ui.c("&7앞의 물약 기운이 남아 있어 더 마셔도 소용없다"));
            return;
        }
        activeUntil.put(p.getUniqueId(), now + 5_000);   // 결과가 올 때까지 이어 마시기 막기
        String id = p.getUniqueId().toString();
        int tier = tier(t);
        async.run("potion", () -> {
            int lv = 1;
            for (String d : List.of("swordsmanship", "spearmanship", "archery", "spellcraft")) lv = Math.max(lv, s.growth.level(id, d));
            return PotionRule.effect(tier, lv);
        }, fx -> {
            if (!p.isOnline()) return;
            if (fx.none()) {
                activeUntil.remove(p.getUniqueId());
                p.sendMessage(Ui.c("&7이 물약은 이제 몸에 듣지 않는다 (숙련이 너무 높다)"));
                return;
            }
            activeUntil.put(p.getUniqueId(), System.currentTimeMillis() + fx.seconds() * 1000L);
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, fx.seconds() * 20, fx.amplifier()));
            p.sendMessage(Ui.c("&a회복력이 오른다 &7(" + fx.seconds() + "초)"));
        }, p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        activeUntil.remove(e.getPlayer().getUniqueId());
    }
}
