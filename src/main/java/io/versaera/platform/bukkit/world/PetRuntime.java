package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.PetService;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Function;

/**
 * 펫 (PET-01 · PET-02): 길들이기(쉬프트 + 먹이 들고 우클릭) · 부르기 · 따라다니기 · 싸우기 · 스킬 · 쓰러짐.
 * 펫 엔티티는 저장하지 않는다(persistent=false) — 펫의 기록(레벨 · 충성 · 이름)은 DB 에, 엔티티는 부를 때마다 새로.
 * 1초마다 불러낸 펫만 본다 (접속자 수만큼, 전체 엔티티를 훑지 않는다).
 */
public final class PetRuntime implements Listener {
    private static final double FOLLOW = 5, LEASH = 24, BITE_RANGE = 3.2;

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Function<UUID, String> regionOf;
    private final Function<UUID, Set<String>> tagsOf;
    private final NamespacedKey key;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    private static final class Active {
        final PetService.Stats stats;
        final LivingEntity entity;
        LivingEntity target;
        long lastBite, lastHowl, lastMend, lastSpot;

        Active(PetService.Stats stats, LivingEntity entity) {
            this.stats = stats;
            this.entity = entity;
        }

        boolean has(String skill) {
            return stats.skills().contains(skill);
        }
    }

    public PetRuntime(Plugin plugin, GameServices s, Async async, ItemCodec codec, Function<UUID, String> regionOf, Function<UUID, Set<String>> tagsOf) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.regionOf = regionOf;
        this.tagsOf = tagsOf;
        this.key = new NamespacedKey(plugin, "pet");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** 곁에 이 스킬을 가진 펫이 나와 있는가 (채집 SCAVENGE · 방어 GUARD) */
    public boolean hasSkill(UUID owner, String skill) {
        Active a = active.get(owner);
        return a != null && a.entity.isValid() && a.has(skill);
    }

    public Optional<String> activePet(UUID owner) {
        Active a = active.get(owner);
        return a == null ? Optional.empty() : Optional.of(a.stats.pet().id());
    }

    // ------------------------------------------------------------------ 부르기 · 돌려보내기
    public void summon(Player p, String petId) {
        String uuid = p.getUniqueId().toString();
        async.run("pet-summon", () -> s.pets.summon(uuid, petId), st -> {
            dismiss(p, false);
            EntityType type;
            try {
                type = EntityType.valueOf(st.species().entity());
            } catch (IllegalArgumentException ex) {
                p.sendMessage(Ui.error("이 서버 버전에 없는 동물입니다: " + st.species().entity()));
                return;
            }
            Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5));
            Entity e = p.getWorld().spawnEntity(at, type);
            if (!(e instanceof LivingEntity le)) { e.remove(); return; }
            le.setPersistent(false);
            le.setRemoveWhenFarAway(false);
            le.setCustomName(Ui.c("&a" + st.pet().name() + " &7Lv." + st.pet().level()));
            le.setCustomNameVisible(true);
            le.getPersistentDataContainer().set(key, PersistentDataType.STRING, uuid + "|" + st.pet().id());
            var hp = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) hp.setBaseValue(st.maxHealth());
            le.setHealth(st.maxHealth());
            if (le instanceof Tameable t) { t.setTamed(true); t.setOwner(p); }
            if (le instanceof Ageable ag) ag.setAdult();
            active.put(p.getUniqueId(), new Active(st, le));
            p.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
            p.sendMessage(Ui.info(st.pet().name() + " 이(가) 곁에 왔다 &7(충성 " + st.loyalty() + " · " + String.join(" · ", st.skills()) + ")"));
        }, p);
    }

    public void dismiss(Player p, boolean tell) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        a.entity.remove();
        if (tell) p.sendMessage(Ui.info(a.stats.pet().name() + " 을(를) 돌려보냈다"));
    }

    public void dismissAll() {
        for (Active a : active.values()) a.entity.remove();
        active.clear();
    }

    private Active ownerOf(Entity e) {
        String v = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (v == null) return null;
        try {
            Active a = active.get(UUID.fromString(v.substring(0, v.indexOf('|'))));
            return a != null && a.entity.equals(e) ? a : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isPet(Entity e) {
        return e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ 1초마다
    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Active a = en.getValue();
            if (p == null || !a.entity.isValid()) {
                a.entity.remove();
                it.remove();
                continue;
            }
            Location pl = p.getLocation(), el = a.entity.getLocation();
            boolean sameWorld = pl.getWorld().equals(el.getWorld());
            double d = sameWorld ? pl.distance(el) : Double.MAX_VALUE;
            if (d > LEASH) a.entity.teleport(pl.clone().add(pl.getDirection().setY(0).normalize().multiply(-1.5)));
            // 싸움: 주인의 적을 문다 (BITE)
            if (a.target != null && (!a.target.isValid() || a.target.isDead() || !a.target.getWorld().equals(el.getWorld()) || a.target.getLocation().distance(el) > 16))
                a.target = null;
            if (a.target != null) {
                if (a.entity instanceof Mob m) m.setTarget(a.target);
                if (a.target.getLocation().distance(el) > BITE_RANGE) step(a.entity, a.target.getLocation());
                else if (a.has("BITE") && now - a.lastBite >= 1200 && a.stats.attack() > 0) {
                    a.lastBite = now;
                    a.target.damage(a.stats.attack(), a.entity);
                    el.getWorld().spawnParticle(Particle.SWEEP_ATTACK, a.target.getLocation().add(0, 1, 0), 1);
                }
                if (a.has("HOWL") && now - a.lastHowl >= 12_000) {
                    a.lastHowl = now;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 6, 0));
                    el.getWorld().playSound(el, Sound.ENTITY_WOLF_HOWL, 0.7f, 1.1f);
                }
            } else if (d > FOLLOW && d <= LEASH) {
                step(a.entity, pl);
            }
            // 치유 (MEND): 5초마다 주인 체력 +1
            if (a.has("MEND") && now - a.lastMend >= 5000) {
                a.lastMend = now;
                var max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max != null && p.getHealth() < max.getValue()) {
                    p.setHealth(Math.min(max.getValue(), p.getHealth() + 1));
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 1);
                }
            }
            // 눈치 (SPOT): 6초마다 12 블록 안의 적을 3초 빛나게 (주인에게만은 아니고 모두에게 — 펫이 짖어 알린 셈)
            if (a.has("SPOT") && now - a.lastSpot >= 6000) {
                a.lastSpot = now;
                for (Entity e : p.getNearbyEntities(12, 6, 12))
                    if (e instanceof Monster mo) mo.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
            }
        }
    }

    private static void step(LivingEntity pet, Location to) {
        if (pet instanceof Mob m) {
            m.getPathfinder().moveTo(to, 1.2);
        } else {
            Location l = pet.getLocation();
            org.bukkit.util.Vector v = to.toVector().subtract(l.toVector()).setY(0);
            if (v.lengthSquared() > 0.01) pet.teleport(l.add(v.normalize().multiply(0.8)));
        }
    }

    // ------------------------------------------------------------------ 싸움 · 보호
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFight(EntityDamageByEntityEvent e) {
        // 주인이 친 상대 → 펫의 표적
        Player attacker = e.getDamager() instanceof Player pl ? pl
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (attacker != null && e.getEntity() instanceof LivingEntity le && !(le instanceof Player) && !isPet(le)) {
            Active a = active.get(attacker.getUniqueId());
            if (a != null) a.target = le;
        }
        // 주인을 친 상대 → 펫의 표적
        if (e.getEntity() instanceof Player victim && e.getDamager() instanceof LivingEntity src && !(src instanceof Player)) {
            Active a = active.get(victim.getUniqueId());
            if (a != null && !src.equals(a.entity)) a.target = src;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent e) {
        Active a = ownerOf(e.getEntity());
        if (a != null) {
            // 주인 · 파티원은 펫을 해치지 않는다
            Player src = e.getDamager() instanceof Player pl ? pl
                    : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
            if (src != null && (src.getUniqueId().toString().equals(a.stats.pet().owner()) || s.parties.together(src.getUniqueId().toString(), a.stats.pet().owner())))
                e.setCancelled(true);
            return;
        }
        // 지킴 (GUARD): 주인이 받는 피해 -8%
        if (e.getEntity() instanceof Player p && hasSkill(p.getUniqueId(), "GUARD")) e.setDamage(e.getDamage() * 0.92);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPetTarget(EntityTargetEvent e) {
        Active a = ownerOf(e.getEntity());
        if (a != null && e.getTarget() instanceof Player t && (t.getUniqueId().toString().equals(a.stats.pet().owner())
                || s.parties.together(t.getUniqueId().toString(), a.stats.pet().owner())))
            e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Active a = ownerOf(e.getEntity());
        if (a != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            String owner = a.stats.pet().owner(), id = a.stats.pet().id();
            UUID ou = UUID.fromString(owner);
            active.remove(ou);
            Player p = Bukkit.getPlayer(ou);
            if (p != null) p.sendMessage(Ui.error(a.stats.pet().name() + " 이(가) 쓰러졌다 — 5분 쉬어야 한다"));
            async.fire("pet-faint", () -> { s.pets.faint(owner, id); return null; });
            return;
        }
        // 주인이 쓰러뜨린 몬스터 → 곁의 펫이 경험을 얻는다
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        Active mine = active.get(k.getUniqueId());
        if (mine == null || !mine.entity.getWorld().equals(e.getEntity().getWorld()) || mine.entity.getLocation().distance(e.getEntity().getLocation()) > 24) return;
        var max = e.getEntity().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        long xp = Math.max(2, Math.round((max == null ? 10 : max.getValue()) / 2));
        String owner = k.getUniqueId().toString(), id = mine.stats.pet().id();
        async.run("pet-xp", () -> s.pets.gainXp(owner, id, xp), pet -> {
            Active cur = active.get(k.getUniqueId());
            if (cur != null && pet.level() > cur.stats.pet().level()) {
                k.sendMessage(Ui.info(pet.name() + " 레벨 " + pet.level() + "!"));
                cur.entity.setCustomName(Ui.c("&a" + pet.name() + " &7Lv." + pet.level()));
                summon(k, id);   // 새 능력치 · 스킬로 다시 부른다
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismiss(e.getPlayer(), false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Active a = active.get(e.getPlayer().getUniqueId());
        if (a != null) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (a.entity.isValid() && e.getPlayer().isOnline()) a.entity.teleport(e.getPlayer().getLocation());
        }, 5L);
    }

    // ------------------------------------------------------------------ 길들이기
    /** 쉬프트 + 먹이(묶음 재료)를 들고 들의 동물을 우클릭 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTame(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getPlayer().isSneaking()) return;
        Entity target = e.getRightClicked();
        if (!(target instanceof LivingEntity) || target instanceof Player || isPet(target) || target.getCustomName() != null) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String type = codec.typeId(hand);
        if (type == null || codec.instanceId(hand) != null) return;
        Set<String> tags = tagsOf.apply(p.getUniqueId());
        var sp = s.pets.speciesFor(target.getType().name(), tags);
        if (sp.isEmpty()) return;
        e.setCancelled(true);
        if (!busy.add(p.getUniqueId())) return;
        int q = codec.bulkQuality(hand);
        Set<String> food = codec.types().get(type).tags();
        if (food.stream().noneMatch(sp.get().food()::contains)) {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.error(sp.get().name() + " 은(는) " + String.join(" · ", sp.get().food()) + " 을(를) 먹는다"));
            return;
        }
        var taken = InventoryOps.take(p, codec, type, 1, 0);
        if (taken == null) { busy.remove(p.getUniqueId()); return; }
        String uuid = p.getUniqueId().toString();
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        async.run("pet-tame", () -> s.pets.tame(uuid, sp.get().id(), tags, food, q, roll), r -> {
            busy.remove(p.getUniqueId());
            Location at = target.getLocation();
            if (r.success()) {
                target.remove();
                at.getWorld().spawnParticle(Particle.HEART, at.add(0, 1, 0), 8, 0.4, 0.4, 0.4);
                p.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                p.sendTitle(Ui.c("&a" + sp.get().name()), Ui.c("&7길들였다 — /펫"), 5, 40, 10);
            } else {
                at.getWorld().spawnParticle(Particle.SMOKE_NORMAL, at.add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.01);
                p.sendMessage(Ui.c("&7" + sp.get().name() + " 이(가) 먹이만 먹고 물러났다 &8(" + Math.round(r.chance() * 100) + "%)"));
            }
        }, err -> {
            busy.remove(p.getUniqueId());
            InventoryOps.give(p, codec, taken);   // 조건이 안 맞아 거부 → 먹이를 돌려준다
        }, p);
    }
}
