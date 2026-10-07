package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.Relation;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * NPC: 주민 엔티티에 NPC id 를 붙여 둔다. 우클릭 = 대화 → 그날 첫 대화면 호감 +, 관계 단계에 맞는 한 줄 → NPC 창(의뢰 · 상점 · 소식 · 선물).
 * NPC 는 AI 를 끄고 서 있으며, 일과에 따른 이동은 NpcRuntime 이 1초마다 근처에 사람이 있을 때만 한다.
 */
public final class NpcListener implements Listener {
    private final GameServices s;
    private final Async async;
    private final NamespacedKey key;

    private java.util.function.BiConsumer<Player, String> onOpen = (p, id) -> {};

    /** 대화 뒤에 열 창 (NpcMenus) */
    public void onOpen(java.util.function.BiConsumer<Player, String> open) {
        this.onOpen = open;
    }

    public NpcListener(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        this.key = new NamespacedKey(plugin, "npc");
    }

    public Villager spawn(NpcDefinition n, Location at) {
        return at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCustomName(Ui.c("&f" + n.name() + " &7" + n.job()));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id());
        });
    }

    private String npcId(Entity e) {
        return e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTalk(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = npcId(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();
        async.run("talk", () -> {
            var t = s.npcWorld.talk(uuid, id);
            s.quests.record(uuid, io.versaera.domain.quest.QuestDefinition.Type.TALK, id, 1, 0);
            s.exploration.discover(uuid, p.getName(), "npc", id);
            return t;
        }, t -> {
            NpcDefinition n = t.npc();
            p.sendMessage(Ui.c("&f" + n.name() + "&7: " + (t.line() != null ? t.line() : fallback(n, t.stage()))));
            if (t.memoryLine() != null) p.sendMessage(Ui.c("&7  \"" + t.memoryLine() + "\""));
            p.sendMessage(Ui.c("&8" + t.stage().label + " " + t.affinity() + (t.gain() > 0 ? " &a+" + t.gain() : "")
                    + (t.regionTier() != io.versaera.application.NpcWorldService.Tier.NORMAL ? " &8· 마을 " + t.regionTier().label : "")
                    + (t.unlocked().isEmpty() ? "" : " &8· &e" + String.join(" · ", t.unlocked()))));
            onOpen.accept(p, n.id());
        }, p);
    }

    /** 직업 틀이 없는 NPC (손으로 만든 NPC) 의 짧은 한 줄 */
    private static String fallback(NpcDefinition n, Relation.Stage st) {
        return switch (st) {
            case HOSTILE, COLD -> "...볼일 없으면 가 보시오.";
            case STRANGER, KNOWN -> "처음 보는 얼굴이군.";
            case INTEREST, FRIENDLY -> "또 왔군. " + n.job() + " 일은 오늘도 바쁘네.";
            case TRUST, CLOSE -> "자네라면 믿고 맡길 만하지.";
            default -> "자네 덕에 이 동네가 살아났어.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (npcId(e.getEntity()) != null) e.setCancelled(true);
    }
}
