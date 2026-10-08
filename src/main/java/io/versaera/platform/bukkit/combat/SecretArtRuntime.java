package io.versaera.platform.bukkit.combat;

import io.versaera.application.GameServices;
import io.versaera.application.SecretArtService;
import io.versaera.domain.art.SecretArt;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.listener.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 비기 쓰기 (ART-01). 판정(배웠나 · 직업 · 단계)은 SecretArtService, 효과는 여기서. 다시 쓰기까지의 시간은 메모리.
 * <ul>
 *   <li>조각 생명술: 손에 든 조각품을 바쳐 철 골렘 '조각 생명체' (품질에 따라 3 ~ 13분)</li>
 *   <li>조각 변신술: 5분 힘 · 속도 · 도약, 이름표 숨김</li>
 *   <li>조각 부활술: 10분 동안 나와 파티원에게 부활의 가호 (죽을 피해 한 번 버팀)</li>
 *   <li>정령창조 조각술: 정령 셋 (길들인 늑대, 10분)</li>
 *   <li>시간 조각술: 초급 가속 · 중급 정지 · 고급 여행</li>
 *   <li>천상의 맛: 잔치 — 주변 모두 포만 · 재생 · 흡수 + 하루 한 번 영구 인내 기록</li>
 *   <li>조각 검술 · 대재앙의 자연조각술 · 광휘의 검술 · 분검술 · 명예로운 약속 · 다른 하나의 검</li>
 * </ul>
 */
public final class SecretArtRuntime implements Listener {
    private static final String DISGUISE = "versa_disguise";
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> ward = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Location>> trail = new ConcurrentHashMap<>();
    private CombatListener combat;

    /** 다른 하나의 검 (TWIN) 은 전투 계산에 붙는다 */
    public void combat(CombatListener c) {
        combat = c;
    }

    static final String COOLDOWN = "artcd";

    private void saveCooldowns(UUID u) {
        long now = System.currentTimeMillis(), last = 0;
        Map<String, String> d = new java.util.LinkedHashMap<>();
        String prefix = u + ":";
        for (var e : cooldown.entrySet()) {
            if (!e.getKey().startsWith(prefix) || e.getValue() <= now) continue;
            d.put(e.getKey().substring(prefix.length()), Long.toString(e.getValue()));
            last = Math.max(last, e.getValue());
        }
        long until = last;
        async.fire("art-cooldown-save", () -> { s.state.save(COOLDOWN, u.toString(), d, until); return null; });
    }

    public SecretArtRuntime(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        // 재사용 대기는 서버를 다시 켜도 이어진다 (껐다 켜서 비기를 다시 쓰지 못하게)
        async.fire("art-cooldowns", () -> {
            s.state.loadAll(COOLDOWN).forEach((u, d) -> d.forEach((art, until) -> cooldown.merge(u + ":" + art, Long.parseLong(until), Math::max)));
            return null;
        });
        // 시간 여행용: 5초마다 자리 기록 (1분치)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Deque<Location> d = trail.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
                d.addLast(p.getLocation());
                while (d.size() > 12) d.removeFirst();
            }
        }, 100L, 100L);
    }

    /** /비기: 익힌 비기(이름 · 효과)와 깨달음이 온 비기(이름만)만 보인다 — 모르는 비기는 있다는 것조차 드러나지 않는다 */
    public void list(Player p) {
        String id = p.getUniqueId().toString();
        async.run("arts", () -> s.arts.status(id), st -> {
            List<SecretArtService.Status> known = st.stream().filter(SecretArtService.Status::learned).toList();
            List<SecretArtService.Status> ready = st.stream().filter(x -> !x.learned() && x.missing() == null).toList();
            if (known.isEmpty() && ready.isEmpty()) {
                p.sendMessage(Ui.c("&7익힌 비기가 없다"));
                return;
            }
            for (SecretArtService.Status x : known) p.sendMessage(Ui.c("&6" + x.art().name() + "\n&7  " + x.art().description()));
            for (SecretArtService.Status x : ready) p.sendMessage(Ui.c("&e" + x.art().name() + " &8— 깨달음이 왔다 (" + x.art().id() + ")"));
        }, p);
    }

    public void learn(Player p, String artId) {
        String id = p.getUniqueId().toString(), hand = codec.instanceId(p.getInventory().getItemInMainHand());
        async.run("art-learn", () -> s.arts.learn(id, artId, hand), how -> {
            if ("relic".equals(how) && hand != null && hand.equals(codec.instanceId(p.getInventory().getItemInMainHand())))
                p.getInventory().setItemInMainHand(null);   // 바친 조각상은 DB 에서 사라졌다
            p.sendTitle(Ui.c("&6비기"), Ui.c("&f" + s.arts.art(artId).name() + " &7을(를) 익혔다"), 10, 60, 20);
        }, p);
    }

    private final Map<UUID, String> lastPet = new java.util.concurrent.ConcurrentHashMap<>();
    private io.versaera.platform.bukkit.world.PetRuntime pets;

    /** 조각 생명술로 깨어난 동료를 바로 부르기 위해 */
    public void pets(io.versaera.platform.bukkit.world.PetRuntime r) {
        this.pets = r;
    }

    public void cast(Player p, String artId) {
        String key = p.getUniqueId() + ":" + artId;
        long now = System.currentTimeMillis();
        Long until = cooldown.get(key);
        if (until != null && until > now) {
            p.sendMessage(Ui.error("다시 쓰려면 " + ((until - now) / 60_000 + 1) + "분 기다려야 한다"));
            return;
        }
        String id = p.getUniqueId().toString(), hand = codec.instanceId(p.getInventory().getItemInMainHand());
        async.run("art-cast", () -> {
            int lv = s.arts.castLevel(id, artId);
            int quality = 0;
            String newPet = null;
            if ("COMPANION".equals(s.arts.art(artId).effect())) {   // 조각품을 바쳐 계속 함께하는 조각 생명체로
                var it = hand == null ? null : s.items.find(hand).filter(x -> x.custody().ownedBy(id) && s.items.types().get(x.typeId()).hasTag("sculpture")
                        && !s.items.types().get(x.typeId()).hasTag("relic")).orElse(null);
                if (it == null) throw io.versaera.domain.common.DomainException.of("art.need_sculpture", "생명을 불어넣을 조각품을 손에 들어야 한다");
                quality = it.quality();
                String species = it.typeId().equals("statuette") ? "living_statue" : "living_beast";
                newPet = s.pets.awaken(id, species, s.items.types().get(it.typeId()).name(), quality).id();
                s.items.destroy(hand, id, "조각 생명술", "art-life:" + hand);
            }
            lastPet.put(p.getUniqueId(), newPet == null ? "" : newPet);
            return new int[]{lv, quality, s.growth.statPoints(id, "artistry")};
        }, r -> {
            if (!p.isOnline()) return;
            SecretArt a = s.arts.art(artId);
            cooldown.put(key, System.currentTimeMillis() + a.cooldownMs());
            saveCooldowns(p.getUniqueId());
            switch (a.effect()) {
                case "COMPANION" -> {   // 조각품이 깨어나 펫이 된다 (/펫 으로 부르고 · 이름 짓고 · 함께 자란다)
                    p.getInventory().setItemInMainHand(null);
                    String petId = lastPet.remove(p.getUniqueId());
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 80, 0.8, 1.2, 0.8, 0.05);
                    p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                    if (pets != null && petId != null && !petId.isEmpty()) pets.summon(p, petId);
                }
                case "TRANSFORM" -> {
                    int t = 5 * 60 * 20;
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, t, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, t, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, t, 1));
                    Team team = disguiseTeam();
                    team.addEntry(p.getName());
                    p.setPlayerListName(ChatColor.GRAY + "???");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        disguiseTeam().removeEntry(p.getName());
                        if (p.isOnline()) {
                            p.setPlayerListName(p.getName());
                            p.sendMessage(Ui.c("&7변신이 풀렸다"));
                        }
                    }, t);
                }
                case "REVIVE" -> {
                    long w = System.currentTimeMillis() + 10 * 60_000L;
                    for (String m : s.parties.members(id)) {
                        Player o = Bukkit.getPlayer(UUID.fromString(m));
                        if (o != null && o.getWorld() == p.getWorld() && o.getLocation().distanceSquared(p.getLocation()) <= 16 * 16) {
                            ward.put(o.getUniqueId(), w);
                            o.sendMessage(Ui.info("부활의 가호 (10분)"));
                        }
                    }
                }
                case "SPIRITS" -> {
                    for (int i = 0; i < 3; i++) {
                        Wolf wolf = p.getWorld().spawn(p.getLocation().add(i - 1, 0, 1), Wolf.class);
                        wolf.setOwner(p);
                        wolf.setTamed(true);
                        wolf.setPersistent(false);
                        wolf.setCustomName(new String[]{"바람의 정령", "불의 정령", "대지의 정령"}[i]);
                        wolf.setCustomNameVisible(true);
                        expire(wolf, 600);
                    }
                }
                case "TIME" -> time(p, SecretArt.timeTier(r[0]));
                case "BLADE" -> {   // 조각 검술: 앞쪽 부채꼴
                    double dmg = 12 + r[2] * 0.5;
                    org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0).normalize();
                    for (Entity e : p.getNearbyEntities(7, 3, 7)) {
                        if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof ArmorStand) continue;
                        org.bukkit.util.Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                        if (to.lengthSquared() > 49 || to.lengthSquared() < 1e-4 || to.normalize().dot(dir) < 0.5) continue;
                        CombatListener.rawDamage(le, dmg, p);
                    }
                    for (int i = 0; i < 20; i++) {
                        double ang = Math.toRadians(p.getLocation().getYaw() + 90) + (i - 10) * 0.1;
                        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(Math.cos(ang) * 4, 1, Math.sin(ang) * 4), 1);
                    }
                }
                case "DISASTER" -> {   // 대재앙의 자연조각술: 폭풍 + 벼락 (땅은 부수지 않는 효과 번개)
                    p.getWorld().setStorm(true);
                    p.getWorld().setWeatherDuration(30 * 20);
                    List<LivingEntity> targets = new ArrayList<>();
                    for (Entity e : p.getNearbyEntities(16, 8, 16)) if (e instanceof Monster m) targets.add(m);
                    for (int i = 0; i < 6; i++) {
                        int k = i;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!p.isOnline()) return;
                            Location at = targets.isEmpty() ? p.getLocation().add(Math.cos(k) * 8, 0, Math.sin(k) * 8)
                                    : targets.get(k % targets.size()).getLocation();
                            p.getWorld().strikeLightningEffect(at);
                            for (Entity e : p.getWorld().getNearbyEntities(at, 3, 3, 3))
                                if (e instanceof Monster m) CombatListener.rawDamage(m, 20 + r[2] * 0.3, p);
                        }, 10L + i * 15L);
                    }
                }
                case "RADIANT" -> {   // 광휘의 검술: 앞으로 14 블록 직선
                    org.bukkit.util.Vector dir = p.getLocation().getDirection().normalize();
                    Set<Entity> hit = new HashSet<>();
                    for (double d = 1; d <= 14; d += 0.5) {
                        Location at = p.getEyeLocation().add(dir.clone().multiply(d));
                        p.getWorld().spawnParticle(Particle.END_ROD, at, 2, 0.05, 0.05, 0.05, 0);
                        for (Entity e : p.getWorld().getNearbyEntities(at, 1.2, 1.2, 1.2))
                            if (e instanceof LivingEntity le && !(e instanceof Player) && !(e instanceof ArmorStand) && hit.add(e)) {
                                boolean unholy = CombatListener.kinds(le).contains(io.versaera.domain.item.ItemOptions.Kind.UNDEAD)
                                        || CombatListener.kinds(le).contains(io.versaera.domain.item.ItemOptions.Kind.DEMON);
                                CombatListener.rawDamage(le, (25 + r[0]) * (unholy ? 2 : 1), p);
                            }
                    }
                }
                case "SPLIT" -> {   // 분검술: 주위 6 블록 세 번
                    for (int i = 0; i < 3; i++)
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!p.isOnline()) return;
                            for (Entity e : p.getNearbyEntities(6, 3, 6))
                                if (e instanceof LivingEntity le && !(e instanceof Player) && !(e instanceof ArmorStand)) CombatListener.rawDamage(le, 10 + r[0] * 0.5, p);
                            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 12, 3, 0.5, 3, 0);
                        }, i * 6L);
                }
                case "OATH" -> {   // 명예로운 약속: 파티 힘 · 저항 1분
                    for (String m : s.parties.members(id)) {
                        Player o = Bukkit.getPlayer(UUID.fromString(m));
                        if (o == null || o.getWorld() != p.getWorld() || o.getLocation().distanceSquared(p.getLocation()) > 16 * 16) continue;
                        o.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 0));
                        o.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 0));
                        o.sendMessage(Ui.info(p.getName() + "와(과) 명예로운 약속을 나눴다 (1분)"));
                    }
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1200, 0));
                }
                case "TWIN" -> {
                    if (combat != null) combat.twin(p.getUniqueId(), 30);
                }
                case "FEAST" -> {
                    List<Player> eaters = new ArrayList<>();
                    for (Player o : p.getWorld().getPlayers()) if (o.getLocation().distanceSquared(p.getLocation()) <= 16 * 16) eaters.add(o);
                    for (Player o : eaters) {
                        o.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0));
                        o.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 60 * 20, 0));
                        o.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 5 * 60 * 20, 1));
                        String oid = o.getUniqueId().toString();
                        async.run("feast", () -> s.arts.feast(oid), first -> {
                            if (o.isOnline()) o.sendMessage(Ui.info("천상의 맛" + (first ? " — 몸이 단단해진다 (인내 기록 +200)" : "")));
                        }, null);
                    }
                }
                case "BUFF" -> buff(p, id, a);
                case "STRIKE" -> strike(p, a, r[0], r[2]);
                default -> { }
            }
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.02);
            p.sendMessage(Ui.info(a.name()));
        }, p);
    }

    /** 범용 버프 비기: potions "효과:세기:초" 를 나 · 파티(16 블록) · 근처 모두(16 블록)에게 */
    private void buff(Player p, String id, SecretArt a) {
        List<Player> who = new ArrayList<>();
        String target = a.params().getOrDefault("target", "self");
        if (target.equals("near")) {
            for (Player o : p.getWorld().getPlayers()) if (o.getLocation().distanceSquared(p.getLocation()) <= 16 * 16) who.add(o);
        } else if (target.equals("party")) {
            for (String m : s.parties.members(id)) {
                Player o = Bukkit.getPlayer(UUID.fromString(m));
                if (o != null && o.getWorld() == p.getWorld() && o.getLocation().distanceSquared(p.getLocation()) <= 16 * 16) who.add(o);
            }
        }
        if (!who.contains(p)) who.add(p);
        for (String spec : a.params().get("potions").split(",")) {
            String[] x = spec.trim().split(":");
            PotionEffectType type = PotionEffectType.getByName(x[0]);
            if (type == null) continue;
            for (Player o : who) o.addPotionEffect(new PotionEffect(type, Integer.parseInt(x[2]) * 20, Integer.parseInt(x[1])));
        }
        for (Player o : who) if (o != p) o.sendMessage(Ui.info(p.getName() + " — " + a.name()));
        for (Player o : who) fx(o.getLocation(), a, 1.2);
        sound(p.getLocation(), a, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME);
    }

    /**
     * 범용 공격 비기: shape cone(앞쪽 부채꼴) · line(앞으로 곧게) · circle(둘레), range, damage(+ level_bonus × 숙련, + art_bonus × 예술),
     * hits(몇 번) · interval(틱), slow(맞은 적 느리게 초)
     */
    private void strike(Player p, SecretArt a, int level, int artistry) {
        String shape = a.params().get("shape");
        double range = a.param("range", 6), dmg = a.param("damage", 10) + a.param("level_bonus", 0) * level + a.param("art_bonus", 0) * artistry;
        int hits = (int) a.param("hits", 1), every = (int) a.param("interval", 6), slow = (int) a.param("slow", 0);
        for (int i = 0; i < hits; i++)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0).normalize();
                for (Entity e : p.getNearbyEntities(range, 4, range)) {
                    if (!(e instanceof LivingEntity le) || e instanceof Player || e instanceof ArmorStand) continue;
                    org.bukkit.util.Vector to = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    double d = to.length();
                    if (d > range || d < 1e-3) continue;
                    boolean in = switch (shape) {
                        case "cone" -> to.clone().normalize().dot(dir) >= 0.5;
                        case "line" -> to.dot(dir) > 0 && to.clone().subtract(dir.clone().multiply(to.dot(dir))).length() <= 1.4;
                        default -> true;
                    };
                    if (!in) continue;
                    CombatListener.rawDamage(le, dmg, p);
                    if (slow > 0) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slow * 20, 1));
                }
                Location c = p.getLocation().add(0, 1, 0);
                switch (shape) {
                    case "line" -> { for (double d = 1; d <= range; d += 0.7) p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(dir.clone().multiply(d)), 1); }
                    case "cone" -> { for (int k = -4; k <= 4; k++) { double ang = Math.atan2(dir.getZ(), dir.getX()) + k * 0.13;
                        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(ang) * range * 0.6, 0, Math.sin(ang) * range * 0.6), 1); } }
                    default -> p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c, 14, range / 2, 0.4, range / 2, 0);
                }
                if (slow > 0) p.getWorld().spawnParticle(Particle.SNOWFLAKE, c, 30, range / 2, 0.5, range / 2, 0.02);
                if ("trail".equals(a.params().get("pattern")) || "line".equals(shape)) {   // 직선: 날아가는 기운
                    Particle pt = particle(a);
                    for (double d = 1; d <= range; d += 0.5) p.getWorld().spawnParticle(pt, c.clone().add(dir.clone().multiply(d)), 2, 0.1, 0.1, 0.1, 0.01);
                } else fx(p.getLocation(), a, "circle".equals(shape) ? range : range * 0.6);
                sound(c, a, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP);
            }, (long) i * every);
    }

    // ------------------------------------------------------------------ 비기마다 다른 모습 (params: fx 입자 · pattern 모양 · sound 소리)
    private static Particle particle(SecretArt a) {
        try {
            return Particle.valueOf(a.params().getOrDefault("fx", "END_ROD"));
        } catch (IllegalArgumentException e) {
            return Particle.END_ROD;
        }
    }

    private static void sound(Location at, SecretArt a, org.bukkit.Sound fallback) {
        org.bukkit.Sound snd = fallback;
        try {
            if (a.params().containsKey("sound")) snd = org.bukkit.Sound.valueOf(a.params().get("sound"));
        } catch (IllegalArgumentException ignored) {
        }
        at.getWorld().playSound(at, snd, 1f, 1f);
    }

    /** ring 고리 · spiral 감아 오르는 나선 · pillar 솟는 기둥 · burst 터짐 · rain 위에서 쏟아짐 · trail(직선 비기는 strike 가 그림) */
    private void fx(Location base, SecretArt a, double radius) {
        Particle pt = particle(a);
        org.bukkit.World w = base.getWorld();
        String pattern = a.params().getOrDefault("pattern", "burst");
        double r = Math.max(1, radius);
        switch (pattern) {
            case "ring" -> {
                for (int i = 0; i < 36; i++) {
                    double ang = Math.PI * 2 * i / 36;
                    w.spawnParticle(pt, base.clone().add(Math.cos(ang) * r, 0.2, Math.sin(ang) * r), 1, 0, 0.05, 0, 0);
                }
            }
            case "spiral" -> {
                for (int i = 0; i < 48; i++) {
                    double ang = i * 0.4, h = i * 0.05;
                    w.spawnParticle(pt, base.clone().add(Math.cos(ang) * r * 0.8, h, Math.sin(ang) * r * 0.8), 1, 0, 0, 0, 0);
                }
            }
            case "pillar" -> {
                for (double h = 0; h < 3.5; h += 0.15) w.spawnParticle(pt, base.clone().add(0, h, 0), 2, 0.25, 0, 0.25, 0);
            }
            case "rain" -> {
                for (int k = 0; k < 6; k++) {
                    int tick = k;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> w.spawnParticle(pt, base.clone().add(0, 4, 0), 25, r, 0.3, r, 0.2), tick * 4L);
                }
            }
            default -> w.spawnParticle(pt, base.clone().add(0, 1, 0), 40, r * 0.4, 0.6, r * 0.4, 0.08);
        }
    }

    private void time(Player p, int tier) {
        switch (tier) {
            case 1 -> {   // 시간 가속
                for (String m : s.parties.members(p.getUniqueId().toString())) {
                    Player o = Bukkit.getPlayer(UUID.fromString(m));
                    if (o == null || o.getWorld() != p.getWorld() || o.getLocation().distanceSquared(p.getLocation()) > 16 * 16) continue;
                    o.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2));
                    o.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 1200, 2));
                }
                p.sendMessage(Ui.c("&e시간 가속 (초급)"));
            }
            case 2 -> {   // 시간 정지
                List<Mob> frozen = new ArrayList<>();
                for (Entity e : p.getNearbyEntities(16, 8, 16)) if (e instanceof Monster m && m.hasAI()) { m.setAI(false); frozen.add(m); }
                Bukkit.getScheduler().runTaskLater(plugin, () -> { for (Mob m : frozen) if (m.isValid()) m.setAI(true); }, 160L);
                p.sendMessage(Ui.c("&e시간 정지 (중급) — 몬스터 " + frozen.size() + "마리"));
            }
            default -> {   // 시간 여행
                Deque<Location> d = trail.get(p.getUniqueId());
                Location back = d == null || d.isEmpty() ? null : d.peekFirst();
                if (back == null) {
                    p.sendMessage(Ui.error("돌아갈 과거가 아직 없다"));
                    return;
                }
                p.teleport(back);
                p.sendMessage(Ui.c("&e시간 여행 (고급) — 1분 전의 자리로"));
            }
        }
    }

    private Team disguiseTeam() {
        var b = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = b.getTeam(DISGUISE);
        if (t == null) {
            t = b.registerNewTeam(DISGUISE);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return t;
    }

    private void expire(Entity e, int seconds) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.isValid()) e.remove(); }, seconds * 20L);
    }

    /** 부활의 가호: 죽을 피해를 한 번 버틴다 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long w = ward.get(p.getUniqueId());
        if (w == null || w < System.currentTimeMillis() || e.getFinalDamage() < p.getHealth()) return;
        ward.remove(p.getUniqueId());
        e.setCancelled(true);
        var max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        p.setHealth(Math.max(1, (max == null ? 20 : max.getValue()) / 2));
        p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.2);
        p.sendMessage(Ui.info("조각 부활술 — 쓰러지지 않았다"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        trail.remove(e.getPlayer().getUniqueId());
        ward.remove(e.getPlayer().getUniqueId());
    }
}
