package io.versaera.platform.bukkit.world;

import io.versaera.application.ArtworkService;
import io.versaera.application.GameServices;
import io.versaera.application.port.AdventureRepository.Artwork;
import io.versaera.domain.art.ArtworkKind;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.pack.PackIds;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 대형 조각 작품 보이기 (ART-02). 작품은 DB 에, 모습은 근처에 사람이 있을 때만 엔티티(받침 · 몸체 · 장식 ItemDisplay + 이름표 + 클릭 판)로 세운다.
 * 64 블록 칸 색인으로 플레이어 주변 칸의 작품만 본다. 재료마다 팩 모델(artwork/종류/자리/모양)이 달라 같은 종류라도 재료 조합이 보인다.
 */
public final class ArtworkRuntime implements Listener {
    private static final int CELL = 64;
    private static final double SHOW = 48, HIDE = 72;

    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Function<UUID, String> regionOf;
    private final Consumer<Player> deliver;
    private final NamespacedKey key;
    private final Map<String, Map<Long, List<Artwork>>> index = new HashMap<>();
    private final Map<String, List<Entity>> live = new HashMap<>();
    private final Map<String, Artwork> liveArt = new HashMap<>();

    public ArtworkRuntime(Plugin plugin, GameServices s, Async async, ItemCodec codec, Function<UUID, String> regionOf, Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.regionOf = regionOf;
        this.deliver = deliver;
        this.key = new NamespacedKey(plugin, "artwork");
        async.run("art-load", s.artworks::load, list -> {
            for (Artwork a : list) add(a);
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        }, null);
    }

    private static long cell(int x, int z) {
        return ((long) Math.floorDiv(x, CELL) << 32) ^ (Math.floorDiv(z, CELL) & 0xffffffffL);
    }

    private void add(Artwork a) {
        index.computeIfAbsent(a.world(), w -> new HashMap<>()).computeIfAbsent(cell(a.x(), a.z()), c -> new ArrayList<>()).add(a);
    }

    private void drop(Artwork a) {
        var cells = index.get(a.world());
        if (cells != null) {
            var l = cells.get(cell(a.x(), a.z()));
            if (l != null) l.removeIf(x -> x.id().equals(a.id()));
        }
        hide(a.id());
    }

    /** 새로 세운 작품을 바로 보인다 */
    public void placed(Artwork a) {
        add(a);
        show(a);
    }

    /** 허문 작품을 치운다 */
    public void removed(Artwork a) {
        drop(a);
    }

    /** 이름이 바뀐 작품을 다시 그린다 */
    public void replaced(Artwork a) {
        drop(a);
        add(a);
        show(a);
    }

    // ------------------------------------------------------------------ 보이기
    private void tick() {
        Set<String> wanted = new HashSet<>();
        Map<String, Artwork> found = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            var cells = index.get(l.getWorld().getName());
            if (cells == null) continue;
            int cx = Math.floorDiv(l.getBlockX(), CELL), cz = Math.floorDiv(l.getBlockZ(), CELL);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    var list = cells.get(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL));
                    if (list == null) continue;
                    for (Artwork a : list) {
                        double d = Math.hypot(a.x() - l.getX(), a.z() - l.getZ());
                        if (d <= SHOW || (d <= HIDE && live.containsKey(a.id()))) { wanted.add(a.id()); found.put(a.id(), a); }
                    }
                }
        }
        for (String id : new ArrayList<>(live.keySet())) if (!wanted.contains(id)) hide(id);
        for (String id : wanted) if (!live.containsKey(id)) show(found.get(id));
    }

    private void show(Artwork a) {
        World w = Bukkit.getWorld(a.world());
        if (w == null || !w.isChunkLoaded(a.x() >> 4, a.z() >> 4)) return;
        ArtworkKind k = s.artworks.kind(a.kind());
        float sc = (float) k.scale();
        List<Entity> parts = new ArrayList<>();
        Location base = new Location(w, a.x() + 0.5, a.y() + 0.5 * sc, a.z() + 0.5, a.yaw(), 0);
        ArtworkService.looks(a).forEach((slot, look) -> {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            meta.setCustomModelData(PackIds.modelData("artwork/" + a.kind() + "/" + slot + "/" + look));
            it.setItemMeta(meta);
            boolean moon = ArtworkService.moonlit(a);
            ItemDisplay d = w.spawn(base, ItemDisplay.class, e -> {
                e.setItemStack(it);
                e.setPersistent(false);
                if (moon) {   // 달빛 조각품: 은은한 푸른 빛
                    e.setGlowing(true);
                    e.setGlowColorOverride(Color.fromRGB(0xA8D8FF));
                    e.setBrightness(new Display.Brightness(15, 15));
                }
                e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
                e.setViewRange(1.5f);
            });
            parts.add(d);
        });
        Location top = new Location(w, a.x() + 0.5, a.y() + 2.05 * sc + 0.3, a.z() + 0.5);
        TextDisplay label = w.spawn(top, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.setText(Ui.c("&f" + a.title() + "\n" + Ui.gradeColor(a.quality()) + k.name() + " &7· 감상 " + a.views()));
        });
        parts.add(label);
        Interaction click = w.spawn(new Location(w, a.x() + 0.5, a.y(), a.z() + 0.5), Interaction.class, e -> {
            e.setPersistent(false);
            e.setInteractionWidth(Math.max(1f, sc * 1.1f));
            e.setInteractionHeight(Math.max(1.5f, sc * 2f));
            e.getPersistentDataContainer().set(key, PersistentDataType.STRING, a.id());
        });
        parts.add(click);
        live.put(a.id(), parts);
        liveArt.put(a.id(), a);
    }

    private void hide(String id) {
        List<Entity> l = live.remove(id);
        liveArt.remove(id);
        if (l != null) for (Entity e : l) e.remove();
    }

    public void shutdown() {
        for (String id : new ArrayList<>(live.keySet())) hide(id);
    }

    // ------------------------------------------------------------------ 감상
    @EventHandler(ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction i)) return;
        String id = i.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString(), region = regionOf.apply(p.getUniqueId());
        async.run("art-view", () -> {
            var v = s.artworks.view(uuid, id, region);
            String maker = Optional.ofNullable(Bukkit.getOfflinePlayer(UUID.fromString(v.owner())).getName()).orElse("이름 모를 조각가");
            return new Object[]{v, maker};
        }, r -> {
            var v = (ArtworkService.View) r[0];
            String maker = (String) r[1];
            p.sendMessage(Ui.c("&f「" + v.artwork().title() + "」 &7— " + maker + " · " + s.artworks.kind(v.artwork().kind()).name()
                    + " · 품질 " + Ui.gradeColor(v.artwork().quality()) + v.artwork().quality()));
            if (!v.fresh()) { p.sendMessage(Ui.c("&8오늘은 이미 감상했다")); return; }
            int t = v.buffMinutes() * 60 * 20;
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, t, v.buffLevel() - 1));
            if (v.buffLevel() >= 2) p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, t, 0));
            if (v.buffLevel() >= 3) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 30, 0));
            // 주제마다 다른 감흥: 입상 = 힘 · 짐승상 = 단단함 · 분수 = 재생 · 기념비 = 마을의 영웅 · 흉상 = 행운(위)
            int amp = Math.max(0, v.buffLevel() - 2);
            switch (v.artwork().kind()) {
                case "statue" -> p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, t, amp));
                case "beast" -> p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, t, amp));
                case "fountain" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Math.min(t, 20 * 120), 0));
                case "monument" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, t, amp));
                default -> { }
            }
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.1f);
            Ui.bar(p, "&d감동 &7" + v.buffMinutes() + "분 · 예술 경험 +1");
        }, p);
    }

    // ------------------------------------------------------------------ 만들기 · 허물기
    /** /조각 만들기 <종류> <이름>: 인벤토리에서 자리마다 재료를 골라(품질 높은 것부터) 바라보는 블록 위에 세운다 */
    public void create(Player p, String kindId, String title) {
        ArtworkKind k;
        try {
            k = s.artworks.kind(kindId);
        } catch (RuntimeException ex) {
            p.sendMessage(Ui.error("종류: " + String.join(" · ", s.artworks.kinds().stream().map(x -> x.id() + "(" + x.name() + ")").toList())));
            return;
        }
        Block b = p.getTargetBlockExact(6);
        if (b == null || !b.getType().isSolid()) { p.sendMessage(Ui.error("세울 땅을 바라보세요 (6 블록 안)")); return; }
        // 재료 고르기: 자리마다 태그가 맞는 묶음을 품질 높은 순으로
        List<ArtworkService.Pick> picks = new ArrayList<>();
        List<List<MaterialInput>> taken = new ArrayList<>();
        for (ArtworkKind.Part part : k.parts()) {
            int need = part.amount();
            Map<String, Integer> have = new TreeMap<>();
            for (ItemStack it : p.getInventory().getStorageContents()) {
                String t = codec.typeId(it);
                if (t == null || codec.instanceId(it) != null || codec.types().get(t).tags().stream().noneMatch(part.tags()::contains)) continue;
                have.merge(t, it.getAmount(), Integer::sum);
            }
            String best = have.entrySet().stream().filter(x -> x.getValue() >= need).map(Map.Entry::getKey).findFirst().orElse(null);
            if (best == null) {
                for (var l : taken) InventoryOps.give(p, codec, l);
                p.sendMessage(Ui.error(slotName(part.slot()) + " 재료 " + need + "개 (" + String.join(" · ", part.tags()) + ") 가 모자랍니다"));
                return;
            }
            List<MaterialInput> got = InventoryOps.take(p, codec, best, need, 0);
            if (got == null) {
                for (var l : taken) InventoryOps.give(p, codec, l);
                return;
            }
            taken.add(got);
            for (MaterialInput mi : got) picks.add(new ArtworkService.Pick(part.slot(), mi.typeId(), mi.quality(), mi.count()));
        }
        Location at = b.getLocation().add(0, 1, 0);
        int yaw = Math.round(p.getLocation().getYaw() / 90f) * 90 + 180;
        String uuid = p.getUniqueId().toString(), region = regionOf.apply(p.getUniqueId());
        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        async.run("art-create", () -> s.artworks.create(uuid, k.id(), picks, at.getWorld().getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(), yaw, title, region, roll), a -> {
            add(a);
            show(a);
            p.playSound(at, Sound.BLOCK_ANVIL_USE, 1f, 1.3f);
            p.sendTitle(Ui.c("&f「" + a.title() + "」"), Ui.c(Ui.gradeColor(a.quality()) + k.name() + " · 품질 " + a.quality()), 5, 60, 15);
        }, err -> deliver.accept(p), p);   // 실패 → 서비스가 재료를 배달함으로 돌려줬다
    }

    /** /조각 허물기: 바라보는(4 블록 안) 내 작품 — 재료 절반을 돌려받는다 */
    public void dismantle(Player p, boolean admin) {
        Location l = p.getLocation();
        Artwork near = null;
        for (Artwork a : s.artworks.all())
            if (a.world().equals(l.getWorld().getName()) && Math.hypot(a.x() + 0.5 - l.getX(), a.z() + 0.5 - l.getZ()) <= 4) { near = a; break; }
        if (near == null) { p.sendMessage(Ui.error("가까이(4 블록) 있는 작품이 없습니다")); return; }
        Artwork target = near;
        String uuid = p.getUniqueId().toString();
        async.run("art-remove", () -> s.artworks.remove(uuid, target.id(), admin), a -> {
            drop(a);
            deliver.accept(p);
            p.sendMessage(Ui.info("「" + a.title() + "」을(를) 허물었다 — 재료 절반이 배달함으로"));
        }, p);
    }

    private static String slotName(String slot) {
        return switch (slot) {
            case "pedestal" -> "받침";
            case "body" -> "몸체";
            default -> "장식";
        };
    }
}
