package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.listener.CombatListener;
import io.versaera.platform.bukkit.world.FieldBossRuntime;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * 생활 스킬 · 감정 · 필드 보스 (SKL-05 · ITM-02 · BOS-02).
 * /감정 · /붕대 · /검갈기 · /닦기 · /다림질 · /사자후 · /조각파괴 · /필드보스. 도축은 동물을 잡을 때 저절로.
 */
public final class LifeCommands implements CommandExecutor, Listener {
    private static final Set<String> HIDE_ANIMALS = Set.of("COW", "MOOSHROOM", "RABBIT", "GOAT", "HOGLIN", "HORSE", "LLAMA"),
            MEAT_ANIMALS = Set.of("PIG", "SHEEP", "CHICKEN");
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final CombatListener combat;
    private final FieldBossRuntime bosses;
    private final Consumer<Player> deliver;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHurt = new ConcurrentHashMap<>();
    private final RandomGenerator rng = new java.util.Random();   // getDefault() 는 Paper 의 플러그인 환경에서 구현(jdk.random)을 못 찾는다

    public LifeCommands(GameServices s, Async async, ItemCodec codec, CombatListener combat, FieldBossRuntime bosses, Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.combat = combat;
        this.bosses = bosses;
        this.deliver = deliver;
    }

    private boolean ready(Player p, String what, long ms) {
        String k = p.getUniqueId() + ":" + what;
        long now = System.currentTimeMillis();
        Long until = cooldown.get(k);
        if (until != null && until > now) {
            Ui.bar(p, "&c" + ((until - now) / 1000 + 1) + "초 뒤에 다시");
            return false;
        }
        cooldown.put(k, now + ms);
        return true;
    }

    /** 인벤토리에서 묶음 아이템 하나를 뺀다. @return 뺀 것의 품질 (없으면 -1) */
    private int take(Player p, String typeId) {
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || codec.instanceId(c[i]) != null || !typeId.equals(codec.typeId(c[i]))) continue;
            int q = codec.bulkQuality(c[i]);
            c[i].setAmount(c[i].getAmount() - 1);
            if (c[i].getAmount() <= 0) c[i] = null;
            p.getInventory().setStorageContents(c);
            return q;
        }
        return -1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (cmd.getName().equals("fieldboss")) {
            sender.sendMessage(Ui.c("&6── 필드 보스 ──"));
            for (String line : bosses.status()) sender.sendMessage(Ui.c(line));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("플레이어만");
            return true;
        }
        String id = p.getUniqueId().toString();
        UUID u = p.getUniqueId();
        String hand = codec.instanceId(p.getInventory().getItemInMainHand());
        switch (cmd.getName()) {
            case "appraise" -> {
                if (hand == null) { p.sendMessage(Ui.error("감정할 고유 아이템을 손에 드세요")); return true; }
                if (!ready(p, "appraise", 3000)) return true;
                async.run("appraise", () -> {
                    var r = s.gear.appraise(id, hand, rng);
                    return new Object[]{r, s.items.find(hand).orElseThrow()};
                }, r -> {
                    var res = (io.versaera.application.GearService.Appraisal) r[0];
                    if (!res.success()) {
                        p.sendMessage(Ui.c("&7감정에 실패했다 — " + res.type().name() + "은(는) 아직 알아보기 어렵다 &8(난이도 " + res.difficulty() + ")"));
                        return;
                    }
                    if (hand.equals(codec.instanceId(p.getInventory().getItemInMainHand())))
                        p.getInventory().setItemInMainHand(codec.unique((io.versaera.domain.item.ItemInstance) r[1]));
                    p.sendMessage(Ui.info("감정 성공: " + res.type().name() + (res.firstOfKind() ? " &e(처음 보는 물건)" : "")));
                    if (res.type().lore() != null) p.sendMessage(Ui.c("&o&7" + res.type().lore()));
                }, p);
            }
            case "bandage" -> {
                if (!ready(p, "bandage", 5000)) return true;
                if (take(p, "bandage") < 0) { cooldown.remove(id + ":bandage"); p.sendMessage(Ui.error("붕대가 없습니다 (리넨 천으로 만든다)")); return true; }
                Long hurt = lastHurt.get(u);
                boolean fighting = hurt != null && System.currentTimeMillis() - hurt < 8000;
                async.run("bandage", () -> s.life.bandage(id, fighting), heal -> {
                    if (!p.isOnline() || p.isDead()) return;
                    var max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + heal));
                    p.removePotionEffect(PotionEffectType.POISON);
                    Ui.bar(p, "&a붕대 감기 +" + String.format("%.1f", heal / 2) + "♥" + (fighting ? " &7(전투 중)" : ""));
                }, p);
            }
            case "whet", "polish", "iron" -> {
                String disc = switch (cmd.getName()) { case "whet" -> "whetting"; case "polish" -> "polishing"; default -> "ironing"; };
                if (hand == null) { p.sendMessage(Ui.error("손질할 고유 장비를 손에 드세요")); return true; }
                if (!ready(p, disc, 10_000)) return true;
                int stone = disc.equals("whetting") ? take(p, "whetstone") : 0;
                if (stone < 0) { cooldown.remove(id + ":" + disc); p.sendMessage(Ui.error("숫돌이 없습니다")); return true; }
                async.run("care", () -> s.life.care(id, disc, hand), c -> {
                    if (disc.equals("whetting")) combat.buffWeapon(u, c.pct(), c.minutes());
                    else combat.buffArmor(u, c.pct(), c.minutes());
                    p.getWorld().playSound(p.getLocation(), disc.equals("whetting") ? Sound.BLOCK_GRINDSTONE_USE : Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1.2f);
                    p.sendMessage(Ui.info(s.growth.discipline(disc).name() + " — " + (disc.equals("whetting") ? "공격력" : "방어력") + " +"
                            + String.format("%.0f", c.pct()) + "% (" + c.minutes() + "분)"));
                }, err -> {   // 손질 못 함 → 숫돌을 돌려준다
                    if (disc.equals("whetting") && p.isOnline()) p.getInventory().addItem(codec.bulk("whetstone", stone, 1));
                }, p);
            }
            case "roar" -> {
                if (!ready(p, "roar", 60_000)) return true;
                async.run("roar", () -> s.life.roar(id), r -> {
                    double rad = r[0];
                    int sec = (int) r[1];
                    int n = 0;
                    for (Entity e : p.getNearbyEntities(rad, 4, rad)) {
                        if (e instanceof Monster m) {
                            m.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, sec * 20, 2));
                            m.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, sec * 20, 1));
                            m.setTarget(null);
                            n++;
                        } else if (e instanceof Player o && s.parties.members(id).contains(o.getUniqueId().toString())) {
                            o.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 400, 0));
                            o.sendMessage(Ui.info(p.getName() + "의 사자후 — 힘이 솟는다"));
                        }
                    }
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.6f, 0.8f);
                    p.getWorld().spawnParticle(Particle.SONIC_BOOM, p.getLocation().add(0, 1, 0), 1);
                    p.sendMessage(Ui.info("사자후! 몬스터 " + n + "마리가 " + sec + "초 동안 겁에 질렸다"));
                }, e -> cooldown.remove(id + ":roar"), p);
            }
            case "shatter" -> {
                if (hand == null) { p.sendMessage(Ui.error("부술 조각품을 손에 드세요")); return true; }
                async.run("shatter", () -> s.life.destroySculpture(id, hand), r -> {
                    if (hand.equals(codec.instanceId(p.getInventory().getItemInMainHand()))) p.getInventory().setItemInMainHand(null);
                    combat.buffDestruction(u, r.pct(), r.minutes());
                    p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, org.bukkit.Material.CALCITE.createBlockData());
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
                    p.sendMessage(Ui.info("조각 파괴술 — 예술 " + r.artistry() + " → 근접 피해 +" + String.format("%.0f", r.pct()) + "% (" + r.minutes() + "분)"));
                }, p);
            }
            default -> { return false; }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) lastHurt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** 도축: 동물을 잡으면 고기 · 가죽을 더 얻는다 (배달함으로) */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        String type = e.getEntityType().name();
        boolean hide = HIDE_ANIMALS.contains(type);
        if (!hide && !MEAT_ANIMALS.contains(type)) return;
        String id = k.getUniqueId().toString();
        boolean takeHide = hide && rng.nextBoolean();
        long seed = rng.nextLong();
        async.run("butcher", () -> s.life.butcher(id, takeHide, new SplittableRandom(seed)), n -> {
            if (n > 0 && k.isOnline()) {
                Ui.bar(k, "&6도축 — " + (takeHide ? "가죽" : "고기") + " +" + n);
                deliver.accept(k);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastHurt.remove(e.getPlayer().getUniqueId());
        String prefix = e.getPlayer().getUniqueId() + ":";
        cooldown.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
