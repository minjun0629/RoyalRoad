package io.versaera.platform.bukkit.combat;

import io.versaera.application.GameServices;
import io.versaera.domain.boss.Vec;
import io.versaera.domain.combat.CombatState;
import io.versaera.domain.combat.SkillDefinition;
import io.versaera.domain.combat.StatusEffect;
import io.versaera.domain.combat.StatusTracker;
import io.versaera.domain.common.DomainException;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.boss.BossRuntime;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 스킬 · 콤보 · 회피 · 상태 이상 (CMB-02). 모든 판단(쿨다운 · 기력 · 마나 · 무적 시간)은 서버의 CombatState 가 한다.
 * <pre>
 *   F                  = 스킬 1        웅크리고 우클릭 = 스킬 2
 *   웅크리고 F         = 회피 (짧은 무적)
 *   좌클릭 / 웅크리고 좌클릭 = 약 / 강 입력 → 맞는 순서면 콤보 마무리
 * </pre>
 * 스킬 목록은 무기를 바꿀 때 DB 스레드에서 다시 읽어 메인 스레드 캐시에 둔다.
 */
public final class SkillListener implements Listener {
    private static final String PROJ_KEY = "versa_skill";

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final BossRuntime bosses;
    private final Map<UUID, CombatState> states = new HashMap<>();
    private final Map<UUID, List<SkillDefinition>> loadouts = new HashMap<>();
    private final Map<UUID, Set<String>> finishers = new HashMap<>();
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final StatusTracker status = new StatusTracker();
    private final Set<UUID> insight = new HashSet<>();

    public SkillListener(Plugin plugin, GameServices s, Async async, ItemCodec codec, BossRuntime bosses) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.bosses = bosses;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private CombatState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new CombatState(100, 60, System.currentTimeMillis()));
    }

    /** 물약 · 음료: 기력 · 마나 채우기 (메인 스레드) */
    public void refill(Player p, double stamina, double mana) {
        state(p).restore(stamina, mana, System.currentTimeMillis());
        hud(p);
    }

    private String weaponTag(Player p) {
        String t = codec.typeId(p.getInventory().getItemInMainHand());
        if (t == null) return null;
        for (String tag : List.of("sword", "dagger", "bow", "staff", "spear", "axe", "mace", "scythe", "whip", "fan", "harp")) if (codec.types().get(t).hasTag(tag)) return tag;
        return null;
    }

    /** 종족 특성 같은 추가 최대 체력 (메인 스레드 캐시) */
    private java.util.function.ToDoubleFunction<UUID> extraHealth = u -> 0;

    public void extraHealth(java.util.function.ToDoubleFunction<UUID> f) {
        extraHealth = f;
    }

    /** 무기 · 직업이 바뀌면 다시 읽는다 */
    public void reload(Player p) {
        String id = p.getUniqueId().toString(), weapon = weaponTag(p);
        async.run("loadout", () -> {
            List<SkillDefinition> l = s.skills.loadout(id, weapon);
            Set<String> fin = new HashSet<>();
            for (CombatState.Combo c : s.content.combos()) if (s.skills.canFinish(id, c.finisher())) fin.add(c.finisher());
            int lv = weapon == null ? 1 : Math.max(1, s.growth.level(id, l.isEmpty() ? "swordsmanship" : l.get(0).discipline()));
            var fx = io.versaera.domain.skill.StatEffects.of(s.growth.statPoints(id));
            return new Object[]{l, fin, lv, fx};
        }, r -> {
            @SuppressWarnings("unchecked") List<SkillDefinition> l = (List<SkillDefinition>) r[0];
            @SuppressWarnings("unchecked") Set<String> fin = (Set<String>) r[1];
            loadouts.put(p.getUniqueId(), l);
            finishers.put(p.getUniqueId(), fin);
            levels.put(p.getUniqueId(), (int) r[2]);
            var fx = (io.versaera.domain.skill.StatEffects) r[3];
            // 인내 → 최대 체력 (SKL-03). 기본 20 에 비율만 더한다
            var hp = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) hp.setBaseValue(20 * (1 + fx.maxHealthPct()) + extraHealth.applyAsDouble(p.getUniqueId()));
            if (fx.seesHints()) insight.add(p.getUniqueId());
            else insight.remove(p.getUniqueId());
        }, null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        reload(e.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> reload(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        states.remove(u);
        loadouts.remove(u);
        finishers.remove(u);
        levels.remove(u);
        status.forget(u);
        insight.remove(u);
    }

    /** 통찰 스탯이 있는가 (숨은 벽 · 기록을 알아봄) — 메인 스레드 */
    public boolean seesHints(Player p) {
        return insight.contains(p.getUniqueId());
    }

    // ------------------------------------------------------------------ 입력
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        e.setCancelled(true);
        if (p.isSneaking()) dodge(p);
        else cast(p, 0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        Action a = e.getAction();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && p.isSneaking() && weaponTag(p) != null) {
            cast(p, 1);
            e.setCancelled(true);
        } else if ((a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) && weaponTag(p) != null) {
            input(p, p.isSneaking() ? CombatState.Input.HEAVY : CombatState.Input.LIGHT);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && weaponTag(p) != null && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
            input(p, p.isSneaking() ? CombatState.Input.HEAVY : CombatState.Input.LIGHT);
    }

    private void input(Player p, CombatState.Input in) {
        long now = System.currentTimeMillis();
        state(p).input(in, now, s.content.combos()).ifPresent(c -> {
            if (!finishers.getOrDefault(p.getUniqueId(), Set.of()).contains(c.finisher())) return;
            SkillDefinition d = s.skills.skill(c.finisher());
            if (d != null) execute(p, d, true);
        });
    }

    private void cast(Player p, int slot) {
        List<SkillDefinition> l = loadouts.getOrDefault(p.getUniqueId(), List.of());
        if (slot >= l.size()) {
            Ui.bar(p, "&8스킬 없음");
            return;
        }
        execute(p, l.get(slot), false);
    }

    private void dodge(Player p) {
        try {
            state(p).dodge(System.currentTimeMillis());
        } catch (DomainException ex) {
            Ui.bar(p, "&c" + ex.getMessage());
            return;
        }
        Vector dir = p.getVelocity().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = p.getLocation().getDirection().setY(0).multiply(-1);   // 서 있으면 뒤로
        p.setVelocity(dir.normalize().multiply(1.1).setY(0.2));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
        hud(p);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        CombatState c = states.get(p.getUniqueId());
        if (c != null && c.invulnerable(System.currentTimeMillis())) e.setCancelled(true);   // 회피 무적
        else if (status.has(p.getUniqueId(), StatusEffect.GUARD, System.currentTimeMillis())) e.setDamage(e.getDamage() * 0.6);
    }

    // ------------------------------------------------------------------ 실행
    private void execute(Player p, SkillDefinition d, boolean combo) {
        long now = System.currentTimeMillis();
        if (status.disabled(p.getUniqueId(), now)) {
            Ui.bar(p, "&c움직일 수 없습니다");
            return;
        }
        try {
            state(p).use(d, now, 1);
        } catch (DomainException ex) {
            Ui.bar(p, "&c" + ex.getMessage());
            return;
        }
        double power = (4 + levels.getOrDefault(p.getUniqueId(), 1) * 0.35) * d.damageMult();
        Location at = p.getLocation();
        switch (d.kind()) {
            case AREA -> area(p, d, at, power);
            case DASH -> {
                p.setVelocity(at.getDirection().setY(0).normalize().multiply(1.6).setY(0.15));
                Bukkit.getScheduler().runTaskLater(plugin, () -> area(p, d, p.getLocation(), power), 6L);
            }
            case PROJECTILE -> {
                Projectile pr = d.resource() == SkillDefinition.Resource.MANA ? p.launchProjectile(Snowball.class) : p.launchProjectile(Arrow.class);
                pr.setVelocity(at.getDirection().multiply(Math.min(3.0, d.radius() / 8)));
                pr.setMetadata(PROJ_KEY, new FixedMetadataValue(plugin, d.id() + ":" + power));
                if (pr instanceof Arrow ar) ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
            case SELF -> {
                if (d.effect() != null) status.apply(p.getUniqueId(), d.effect(), d.effectSeconds(), 1, now);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.02);
                // 주변 아군 보호 → 보스 전투 지원 기여
                for (Player o : p.getWorld().getPlayers())
                    if (o != p && o.getLocation().distance(p.getLocation()) <= 6 && d.effect() != null) {
                        status.apply(o.getUniqueId(), d.effect(), d.effectSeconds(), 1, now);
                        bosses.support(p, 200);
                    }
            }
        }
        p.getWorld().playSound(at, combo ? Sound.ENTITY_PLAYER_ATTACK_SWEEP : Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, combo ? 1.3f : 1f);
        Ui.bar(p, (combo ? "&6" : "&f") + d.name());
        String uid = p.getUniqueId().toString();   // 의뢰 (기술 쓰기 목표)
        async.fire("skill-quest", () -> { s.quests.record(uid, io.versaera.domain.quest.QuestDefinition.Type.SKILL, d.id(), 1, 0); return null; });
        hud(p);
    }

    private void area(Player p, SkillDefinition d, Location at, double power) {
        Vec o = new Vec(at.getX(), at.getY(), at.getZ());
        double yaw = at.getYaw();
        for (Entity e : p.getWorld().getNearbyEntities(at, d.radius() + 1, 3, d.radius() + 1)) {
            if (!(e instanceof LivingEntity le) || e == p || e instanceof Player || e instanceof ArmorStand) continue;
            Vec t = new Vec(e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ());
            if (d.shape() != null && !d.shape().hits(o, yaw, t, d.radius(), 0, d.widthOrAngle(), 3)) continue;
            hit(p, le, d, power);
        }
        if (d.shape() != null) for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(yaw + 90) + (i - 8) * 0.12;
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.getX() + Math.cos(a) * d.radius() * 0.7, at.getY() + 1, at.getZ() + Math.sin(a) * d.radius() * 0.7, 1);
        }
    }

    private void hit(Player p, LivingEntity target, SkillDefinition d, double power) {
        // 무기를 들었으면 무기 한 방 × 스킬 배율 (CombatListener) — 맨손이면 숙련만으로 정한 피해
        if (weaponTag(p) != null) io.versaera.platform.bukkit.listener.CombatListener.skillDamage(target, p, d.damageMult());
        else target.damage(power, p);
        if (d.effect() != null && d.effect().harmful()) status.apply(target.getUniqueId(), d.effect(), d.effectSeconds(), 1, System.currentTimeMillis());
    }

    @EventHandler
    public void onProjectile(ProjectileHitEvent e) {
        if (!e.getEntity().hasMetadata(PROJ_KEY) || !(e.getEntity().getShooter() instanceof Player p)) return;
        String[] v = e.getEntity().getMetadata(PROJ_KEY).get(0).asString().split(":");
        SkillDefinition d = s.skills.skill(v[0]);
        e.getEntity().remove();
        if (d == null || !(e.getHitEntity() instanceof LivingEntity le) || le instanceof Player) return;
        hit(p, le, d, Double.parseDouble(v[1]));
    }

    // ------------------------------------------------------------------ 상태 이상 · HUD
    private void tick() {
        long now = System.currentTimeMillis();
        for (StatusTracker.Tick t : status.tick(now)) {
            Entity e = Bukkit.getEntity(t.target());
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            double max = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null ? 20
                    : le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            le.damage(Math.max(0.5, max * t.damagePct()));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID u = p.getUniqueId();
            for (StatusTracker.Applied a : status.of(u)) {
                if (a.until() <= now) continue;
                int ticks = (int) Math.min(200, (a.until() - now) / 50);
                if (a.effect().slowLevel > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, a.effect().slowLevel - 1, false, false));
                if (a.effect().disables) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 6, false, false));
                if (a.effect() == StatusEffect.HASTE) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false));
            }
            if (states.containsKey(u) && weaponTag(p) != null) hud(p);
        }
        for (UUID u : status.targets()) {
            if (!status.disabled(u, now) || !(Bukkit.getEntity(u) instanceof LivingEntity le) || le instanceof Player) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 12, 6, false, false));
        }
    }

    private void hud(Player p) {
        CombatState c = state(p);
        c.regen(System.currentTimeMillis());
        Ui.bar(p, "&e기력 " + c.stamina() + "  &b마나 " + c.mana());
    }
}
