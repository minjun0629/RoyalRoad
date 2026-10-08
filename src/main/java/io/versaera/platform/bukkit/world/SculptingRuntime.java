package io.versaera.platform.bukkit.world;

import io.versaera.application.ArtworkService;
import io.versaera.application.GameServices;
import io.versaera.application.port.AdventureRepository.Artwork;
import io.versaera.domain.art.ArtGrade;
import io.versaera.domain.art.ArtMaterials;
import io.versaera.domain.art.ArtworkKind;
import io.versaera.domain.craft.WorkTime;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.pack.PackIds;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 손으로 깎는 조각 (ART-03). 조각칼을 든 채 웅크리고 땅(블록 윗면)을 우클릭 → 무엇을 깎을지 · 자리마다 재료를 고른다 → 시간을 들여 깎는다.
 * <ul>
 *   <li>깎는 동안 재료 부스러기 · 정 소리 · 진행 막대. 받침 → 몸체 → 장식 순으로 모습이 드러난다</li>
 *   <li>조각칼을 놓거나 자리를 떠나면 멈추고 재료는 돌려받는다</li>
 *   <li>다 깎으면 등급(졸작 · 평작 · 수작 · 명작 · 대작). 명작은 큰 제목, 대작은 서버 전체에 알려지고 하늘이 응답한다</li>
 *   <li>밤 · 맑은 하늘 아래서 깎으면 달빛이 손을 고르게 한다 (손 떨림이 줄어든다)</li>
 *   <li>다 깎은 뒤 30초 안에 채팅으로 작품 이름을 짓는다</li>
 * </ul>
 */
public final class SculptingRuntime implements Listener {
    private static final String[] SLOT_ORDER = {"pedestal", "body", "accent"};

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final ArtworkRuntime art;
    private final Function<UUID, String> regionOf;
    private final Consumer<Player> deliver;

    /** 고르는 중: 종류 · 자리마다 고른 {종류 id, 품질} */
    private record Draft(ArtworkKind kind, Location base, int yaw, int level, Map<String, String[]> picks) {}

    private static final class Session {
        ArtworkKind kind;
        Location base, anchor;
        int yaw;
        List<ArtworkService.Pick> picks;
        List<List<MaterialInput>> taken;
        Map<String, String> looks;
        String owner;
        int ticks, total;
        final Map<String, ItemDisplay> shown = new HashMap<>();
        BlockData dust;
    }

    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, String> naming = new java.util.concurrent.ConcurrentHashMap<>();   // 채팅은 다른 스레드
    private final Map<UUID, Long> namingUntil = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random rng = new Random();
    private PetRuntime pets;

    public void pets(PetRuntime r) {
        this.pets = r;
    }

    public SculptingRuntime(Plugin plugin, GameServices s, Async async, ItemCodec codec, ArtworkRuntime art, Function<UUID, String> regionOf, Consumer<Player> deliver) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.art = art;
        this.regionOf = regionOf;
        this.deliver = deliver;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private boolean holdingKnife(Player p) {
        String t = codec.typeId(p.getInventory().getItemInMainHand());
        return t != null && codec.types().get(t).hasTag("tool_carving");
    }

    // ------------------------------------------------------------------ 내 작품: 조각칼로 허공 우클릭
    @EventHandler(priority = EventPriority.HIGH)
    public void onAir(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_AIR || !holdingKnife(e.getPlayer())) return;
        mine(e.getPlayer());
    }

    /** 내 작품 목록: 클릭 = 이름 바꾸기 (채팅) · 쉬프트 클릭 = 허물기 (재료 절반이 배달함으로) */
    private void mine(Player p) {
        String uuid = p.getUniqueId().toString();
        List<Artwork> list = s.artworks.mine(uuid);
        Menu m = new Menu(Math.max(1, Math.min(6, (list.size() + 8) / 9)), "&8내 작품 " + list.size());
        int slot = 0;
        for (Artwork a : list) {
            if (slot >= 54) break;
            ArtworkKind k = s.artworks.kind(a.kind());
            List<String> lore = List.of(Ui.gradeColor(a.quality()) + ArtGrade.name(a.quality()) + " &7" + a.quality() / 10, "&7" + k.name() + " · 감상 " + a.views(),
                    "&8" + a.world() + " " + a.x() + ", " + a.y() + ", " + a.z() + (ArtworkService.moonlit(a) ? " &b☾" : ""), ArtworkService.livingSpecies(a.kind()) != null ? "&8클릭: 이름 · 쉬프트: 허물기 · 우클릭: 생명 불어넣기" : "&8클릭: 이름 · 쉬프트: 허물기");
            m.set(slot++, Menu.icon(icon(a.kind()), "&f「" + a.title() + "」", lore), ev -> {
                p.closeInventory();
                if (ev.isRightClick() && !ev.isShiftClick() && ArtworkService.livingSpecies(a.kind()) != null) {   // 조각 생명술: 작품이 깨어나 동료로
                    async.run("art-awaken", () -> s.artworks.awaken(uuid, a.id()), pet -> {
                        art.removed(a);
                        World w = Bukkit.getWorld(a.world());
                        if (w != null) {
                            Location at = new Location(w, a.x() + 0.5, a.y() + 1, a.z() + 0.5);
                            w.spawnParticle(Particle.END_ROD, at, 120, 0.8, 1.5, 0.8, 0.06);
                            w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                        }
                        p.sendTitle(Ui.c("&b「" + pet.name() + "」"), Ui.c("&7깨어났다 · Lv." + pet.level()), 5, 60, 15);
                        if (pets != null) pets.summon(p, pet.id());
                    }, p);
                } else if (ev.isShiftClick()) {
                    async.run("art-remove", () -> s.artworks.remove(uuid, a.id(), false), gone -> {
                        art.removed(gone);
                        deliver.accept(p);
                        Ui.bar(p, "&7「" + gone.title() + "」을(를) 허물었다");
                    }, p);
                } else {
                    naming.put(p.getUniqueId(), a.id());
                    namingUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
                    Ui.bar(p, "&e채팅으로 새 이름 (30초)");
                }
            });
        }
        m.open(p);
    }

    // ------------------------------------------------------------------ 시작: 웅크리고 땅 우클릭
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (!p.isSneaking() || !holdingKnife(p)) return;
        e.setCancelled(true);
        if (e.getBlockFace() != BlockFace.UP) { Ui.bar(p, "&c땅 위에 세웁니다"); return; }
        var above = e.getClickedBlock().getRelative(BlockFace.UP);
        if (!above.isPassable() || !above.getRelative(BlockFace.UP).isPassable()) { Ui.bar(p, "&c자리가 비어 있지 않습니다"); return; }
        if (sessions.containsKey(p.getUniqueId())) { Ui.bar(p, "&c이미 깎는 중"); return; }
        int yaw = Math.round(p.getLocation().getYaw() / 90f) * 90 + 180;
        chooseKind(p, above.getLocation(), yaw);
    }

    private void chooseKind(Player p, Location base, int yaw) {
        String id = p.getUniqueId().toString();
        async.run("sculpt-level", () -> s.growth.level(id, "sculpting"), lv -> {
            Menu m = new Menu(3, "&8무엇을 깎을까");
            int slot = 10;
            for (ArtworkKind k : s.artworks.kinds()) {
                boolean ok = lv >= k.level();
                List<String> lore = new ArrayList<>();
                lore.add("&7" + k.desc());
                for (ArtworkKind.Part part : k.parts()) lore.add("&8· " + slotName(part.slot()) + " ×" + part.amount());
                lore.add(ok ? "&7조각 " + k.level() : "&c조각 " + k.level());
                m.set(slot++, Menu.icon(ok ? icon(k.id()) : Material.GRAY_DYE, (ok ? "&f" : "&7") + k.name(), lore),
                        ok ? ev -> { drafts.put(p.getUniqueId(), new Draft(k, base, yaw, lv, new LinkedHashMap<>())); chooseMaterials(p); } : null);
            }
            m.open(p);
        }, p);
    }

    private static Material icon(String kind) {
        return switch (kind) {
            case "bust" -> Material.ARMOR_STAND;
            case "statue" -> Material.TOTEM_OF_UNDYING;
            case "beast" -> Material.WOLF_SPAWN_EGG;
            case "monument" -> Material.LODESTONE;
            case "fountain" -> Material.WATER_BUCKET;
            default -> Material.STONE;
        };
    }

    /** 자리마다 한 줄: 가진 재료 중 그 자리에 맞는 것 (품질 높은 순). 다 고르면 '깎기 시작' */
    private void chooseMaterials(Player p) {
        Draft d = drafts.get(p.getUniqueId());
        if (d == null) return;
        Menu m = new Menu(Math.min(6, d.kind().parts().size() + 1), "&8재료 · " + d.kind().name());
        int row = 0;
        boolean ready = true;
        for (ArtworkKind.Part part : d.kind().parts()) {
            String[] chosen = d.picks().get(part.slot());
            m.set(row * 9, Menu.icon(Material.NAME_TAG, "&f" + slotName(part.slot()), List.of("&7×" + part.amount(), chosen == null ? "&c고르세요" : "&a✔")), null);
            Map<String, Integer> have = new TreeMap<>(Comparator.reverseOrder());
            for (ItemStack it : p.getInventory().getStorageContents()) {
                String t = codec.typeId(it);
                if (t == null || codec.instanceId(it) != null || codec.types().get(t).tags().stream().noneMatch(part.tags()::contains)) continue;
                have.merge(String.format("%04d", codec.bulkQuality(it)) + ":" + t, it.getAmount(), Integer::sum);
            }
            int col = 1;
            for (var en : have.entrySet()) {
                if (col > 8) break;
                String[] qt = en.getKey().split(":");
                int q = Integer.parseInt(qt[0]);
                String type = qt[1];
                boolean enough = en.getValue() >= part.amount();
                boolean sel = chosen != null && chosen[0].equals(type) && Integer.parseInt(chosen[1]) == q;
                ItemStack ic = codec.bulk(type, q, Math.max(1, Math.min(64, en.getValue())));
                ItemMeta meta = ic.getItemMeta();
                meta.setLore(List.of(Ui.c("&7" + en.getValue() + " / " + part.amount()), Ui.c(Ui.gradeColor(q) + "품질 " + q / 10), Ui.c(sel ? "&a✔" : enough ? "&8클릭" : "&c모자람")));
                if (sel) meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                ic.setItemMeta(meta);
                m.set(row * 9 + col++, ic, enough ? ev -> { d.picks().put(part.slot(), new String[]{type, String.valueOf(q)}); chooseMaterials(p); } : null);
            }
            if (chosen == null) ready = false;
            row++;
        }
        int last = m.getInventory().getSize() - 1;
        boolean go = ready;
        m.set(last, Menu.icon(go ? Material.SHEARS : Material.BARRIER, go ? "&a깎기 시작" : "&7재료를 모두 고르세요", List.of()), go ? ev -> { p.closeInventory(); begin(p); } : null);
        m.open(p);
    }

    // ------------------------------------------------------------------ 깎기
    private void begin(Player p) {
        Draft d = drafts.remove(p.getUniqueId());
        if (d == null || sessions.containsKey(p.getUniqueId())) return;
        List<List<MaterialInput>> taken = new ArrayList<>();
        List<ArtworkService.Pick> picks = new ArrayList<>();
        Map<String, String> looks = new LinkedHashMap<>();
        for (ArtworkKind.Part part : d.kind().parts()) {
            String[] c = d.picks().get(part.slot());
            int q = Integer.parseInt(c[1]);
            List<MaterialInput> got = InventoryOps.take(p, codec, c[0], part.amount(), q);
            if (got == null) {
                for (var l : taken) InventoryOps.give(p, codec, l);
                p.sendMessage(Ui.error(slotName(part.slot()) + " 재료가 모자랍니다"));
                return;
            }
            taken.add(got);
            for (MaterialInput mi : got) picks.add(new ArtworkService.Pick(part.slot(), mi.typeId(), mi.quality(), mi.count()));
            looks.put(part.slot(), ArtMaterials.look(codec.types().get(c[0])));
        }
        Session ss = new Session();
        ss.owner = p.getUniqueId().toString();
        ss.kind = d.kind();
        ss.base = d.base();
        ss.anchor = p.getLocation();
        ss.yaw = d.yaw();
        ss.picks = picks;
        ss.taken = taken;
        ss.looks = looks;
        ss.total = (int) Math.round(WorkTime.artwork(d.kind().level(), d.level()) * 20);
        ss.dust = dustOf(looks.getOrDefault("body", "stone")).createBlockData();
        sessions.put(p.getUniqueId(), ss);
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.8f);
    }

    private static Material dustOf(String look) {
        return switch (look) {
            case "sandstone" -> Material.SANDSTONE;
            case "marble" -> Material.QUARTZ_BLOCK;
            case "iron" -> Material.IRON_BLOCK;
            case "silver" -> Material.DIORITE;
            case "skymetal" -> Material.LAPIS_BLOCK;
            case "amethyst" -> Material.AMETHYST_BLOCK;
            case "frost" -> Material.PACKED_ICE;
            case "oak" -> Material.OAK_PLANKS;
            case "spruce" -> Material.SPRUCE_PLANKS;
            default -> Material.STONE;
        };
    }

    private void tick() {
        long now = System.currentTimeMillis();
        namingUntil.entrySet().removeIf(en -> { if (en.getValue() < now) { naming.remove(en.getKey()); return true; } return false; });
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Session ss = en.getValue();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead()) { it.remove(); cancel(ss, null); continue; }
            if (!holdingKnife(p) || !p.getWorld().equals(ss.base.getWorld()) || p.getLocation().distanceSquared(ss.base) > 36
                    || p.getLocation().distanceSquared(ss.anchor) > 9) {
                it.remove();
                cancel(ss, p);
                Ui.bar(p, "&c손을 멈췄다 — 재료는 돌려받았다");
                continue;
            }
            ss.ticks += 10;
            double f = Math.min(1, ss.ticks / (double) ss.total);
            Location fx = ss.base.clone().add(0.5, 0.3 + f * 1.6 * ss.kind.scale(), 0.5);
            World w = ss.base.getWorld();
            w.spawnParticle(Particle.BLOCK_CRACK, fx, 10, 0.35 * ss.kind.scale(), 0.3, 0.35 * ss.kind.scale(), ss.dust);
            w.playSound(fx, rng.nextInt(3) == 0 ? Sound.BLOCK_STONE_BREAK : Sound.BLOCK_STONE_HIT, 0.7f, 0.85f + rng.nextFloat() * 0.4f);
            // 받침 → 몸체 → 장식이 차례로 드러난다
            int parts = ss.kind.parts().size();
            for (int i = 0; i < parts; i++) {
                String slot = ss.kind.parts().get(i).slot();
                if (f >= (i + 0.5) / parts && !ss.shown.containsKey(slot)) ss.shown.put(slot, preview(ss, slot));
            }
            Ui.bar(p, "&d" + ss.kind.name() + " " + bar(f) + " &f" + (int) (f * 100) + "%");
            if (ss.ticks >= ss.total) {
                it.remove();
                finish(p, ss);
            }
        }
    }

    private static String bar(double f) {
        int n = (int) Math.round(f * 20);
        return "&d" + "|".repeat(n) + "&8" + "|".repeat(20 - n);
    }

    private ItemDisplay preview(Session ss, String slot) {
        World w = ss.base.getWorld();
        float sc = (float) ss.kind.scale();
        Location at = new Location(w, ss.base.getBlockX() + 0.5, ss.base.getBlockY() + 0.5 * sc, ss.base.getBlockZ() + 0.5, ss.yaw, 0);
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(PackIds.modelData("artwork/" + ss.kind.id() + "/" + slot + "/" + ss.looks.getOrDefault(slot, "stone")));
        it.setItemMeta(meta);
        w.spawnParticle(Particle.CLOUD, at, 8, 0.3 * sc, 0.3 * sc, 0.3 * sc, 0.01);
        return w.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(it);
            e.setPersistent(false);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
        });
    }

    private void clearPreview(Session ss) {
        for (ItemDisplay d : ss.shown.values()) d.remove();
        ss.shown.clear();
    }

    /** 멈춤: 재료를 돌려준다 (접속 중이면 가방으로, 아니면 배달함으로) */
    private void cancel(Session ss, Player p) {
        clearPreview(ss);
        if (p != null && p.isOnline()) for (var l : ss.taken) InventoryOps.give(p, codec, l);
        else for (var pk : ss.picks) async.fire("sculpt-refund", () -> { s.items.deliverBulk(ss.owner, pk.typeId(), pk.quality(), pk.amount(), "art_refund"); return null; });
    }

    /** 달빛: 밤 · 비 없음 · 하늘이 보임 */
    private static boolean moonlit(Location l) {
        World w = l.getWorld();
        long t = w.getTime();
        return w.getEnvironment() == World.Environment.NORMAL && !w.hasStorm() && t >= 13000 && t <= 23000 && l.getBlock().getLightFromSky() >= 13;
    }

    private void finish(Player p, Session ss) {
        String uuid = p.getUniqueId().toString(), region = regionOf.apply(p.getUniqueId());
        boolean moon = moonlit(ss.base);
        double r0 = rng.nextDouble();
        Location at = ss.base;
        String title = ss.kind.name();
        async.run("art-create", () -> {
            // 달빛은 손 떨림을 줄인다 — 달빛 조각사는 더
            boolean master = s.jobs.held(uuid).values().stream().anyMatch(h -> h.jobId().equals("moonlight_sculptor"));
            double roll = moon ? (master ? 0.75 + r0 * 0.25 : 0.5 + r0 * 0.5) : r0;
            Artwork made = s.artworks.create(uuid, ss.kind.id(), ss.picks, at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), ss.yaw,
                    title, region, roll);
            return moon ? s.artworks.markMoonlit(uuid, made.id()) : made;   // 달빛 조각품은 계속 은은하게 빛난다
        }, a -> {
            clearPreview(ss);
            art.placed(a);
            reveal(p, ss, a, moon);
            naming.put(p.getUniqueId(), a.id());
            namingUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
            Ui.bar(p, "&e채팅으로 작품 이름 (30초)");
        }, err -> {
            clearPreview(ss);
            deliver.accept(p);   // 실패 → 서비스가 재료를 배달함으로 돌려줬다
        }, p);
    }

    /** 등급마다 다르게 드러낸다 */
    private void reveal(Player p, Session ss, Artwork a, boolean moon) {
        int g = ArtGrade.of(a.quality());
        World w = ss.base.getWorld();
        Location top = ss.base.clone().add(0.5, 2.1 * ss.kind.scale(), 0.5);
        w.spawnParticle(Particle.BLOCK_CRACK, ss.base.clone().add(0.5, 0.8, 0.5), 50, 0.6, 0.6, 0.6, ss.dust);
        String head = gradeColor(g) + ArtGrade.NAMES[g];
        switch (g) {
            case 0 -> { w.playSound(top, Sound.BLOCK_STONE_BREAK, 1f, 0.6f); p.sendTitle(Ui.c(head), Ui.c("&7손이 미끄러졌다"), 5, 40, 10); }
            case 1 -> { w.playSound(top, Sound.BLOCK_STONE_PLACE, 1f, 1f); p.sendTitle(Ui.c(head), Ui.c("&7" + ss.kind.name()), 5, 40, 10); }
            case 2 -> { w.playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f); p.sendTitle(Ui.c(head), Ui.c("&f" + ss.kind.name()), 5, 50, 10); }
            case 3 -> {
                w.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
                w.spawnParticle(Particle.FIREWORKS_SPARK, top, 60, 0.6, 0.6, 0.6, 0.08);
                p.sendTitle(Ui.c("&d&l명작!"), Ui.c("&f" + ss.kind.name()), 5, 70, 15);
            }
            default -> {
                w.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.7f);
                w.strikeLightningEffect(ss.base.clone().add(3, 0, 3));
                w.spawnParticle(Particle.END_ROD, top, 200, 1.2, 1.5, 1.2, 0.1);
                p.sendTitle(Ui.c("&6&l대작!"), Ui.c("&f" + ss.kind.name()), 5, 90, 20);
                Bukkit.broadcastMessage(Ui.c("&6[대작] &f" + p.getName() + " &7— " + ss.kind.name()));
                for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) o.playSound(o.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.6f);
            }
        }
        if (moon) w.spawnParticle(Particle.END_ROD, top, 30, 0.5, 0.8, 0.5, 0.02);
    }

    private static String gradeColor(int g) {
        return new String[]{"&8", "&7", "&a", "&d", "&6&l"}[g];
    }

    // ------------------------------------------------------------------ 이름 짓기
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        String id = naming.get(u);
        Long until = namingUntil.get(u);
        if (id == null || until == null || until < System.currentTimeMillis()) return;
        e.setCancelled(true);
        String name = ChatColor.stripColor(e.getMessage()).replace("&", "").trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            naming.remove(u);
            namingUntil.remove(u);
            Player p = e.getPlayer();
            if (name.isEmpty()) return;
            String uuid = u.toString();
            String t = name.length() > 24 ? name.substring(0, 24) : name;
            async.run("art-name", () -> s.artworks.rename(uuid, id, t), a -> {
                art.replaced(a);
                p.sendMessage(Ui.c("&f「" + a.title() + "」"));
            }, p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        drafts.remove(u);
        naming.remove(u);
        namingUntil.remove(u);
        Session ss = sessions.remove(u);
        if (ss != null) cancel(ss, null);
    }

    public void shutdown() {
        for (var en : sessions.entrySet()) cancel(en.getValue(), Bukkit.getPlayer(en.getKey()));
        sessions.clear();
    }

    private static String slotName(String slot) {
        return switch (slot) {
            case "pedestal" -> "받침";
            case "body" -> "몸체";
            default -> "장식";
        };
    }
}
