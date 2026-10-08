package io.versaera.platform.bukkit.world;

import io.versaera.application.GameServices;
import io.versaera.application.RealmService;
import io.versaera.application.port.GuildRepository;
import io.versaera.application.port.RealmRepository;
import io.versaera.domain.realm.RealmRules;
import io.versaera.domain.world.Region;
import io.versaera.persistence.DbExecutor;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 땅 보호 · 개인 상점 창 · 공성 진행 · 성 수입 (LND-01 · SHP-01 · CST-01).
 * <ul>
 *   <li>산 땅(청크)에서는 주인과 허가받은 사람만 블록을 놓고 · 부수고 · 상자 · 문을 쓸 수 있다. 폭발도 막는다. 주인 없는 땅은 예전처럼 자유</li>
 *   <li>상점 블록을 우클릭하면 상점 창 — 좌클릭 1개 · 쉬프트 16개 구매, 주인은 클릭해서 내리기</li>
 *   <li>공성: 1초마다 성 한가운데 8 블록 안의 공격 · 수비 길드원을 센다</li>
 * </ul>
 * 캐시(땅 · 상점 자리)는 시작할 때 DB 에서 한 번 읽고, 바뀔 때마다 고친다 — 메인 스레드는 DB 를 기다리지 않는다.
 */
public final class RealmRuntime implements Listener {
    private record PlotInfo(String owner, Set<String> members) {}

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final Map<String, PlotInfo> plots = new ConcurrentHashMap<>();
    private final Map<String, String> shops = new ConcurrentHashMap<>();   // "world:x:y:z" → 상점 id
    private final Map<String, Integer> progress = new ConcurrentHashMap<>();
    private final Map<String, Set<String>[]> siegeSides = new ConcurrentHashMap<>();
    private final Set<String> sidesAsked = ConcurrentHashMap.newKeySet();

    public RealmRuntime(Plugin plugin, GameServices s, Async async, DbExecutor exec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        exec.submit("realm-load", () -> {
            for (RealmRepository.Plot p : s.realm.allPlots())
                plots.put(key(p.world(), p.cx(), p.cz()), new PlotInfo(p.owner(), new HashSet<>(s.realm.members(p.world(), p.cx(), p.cz()))));
            for (RealmRepository.Shop sh : s.realm.allShops()) shops.put(block(sh.world(), sh.x(), sh.y(), sh.z()), sh.id());
            for (RealmService.Siege sg : s.realm.sieges()) {   // 꺼지기 전 점령 진행을 이어 간다
                int held = s.realm.captureProgress(sg.region());
                if (held > 0) progress.put(sg.region(), held);
            }
            return null;
        }).join();
        Bukkit.getScheduler().runTaskTimer(plugin, this::siegeTick, 20L, 20L);
        // 성 수입: 10분마다 하루가 지난 만큼
        Bukkit.getScheduler().runTaskTimer(plugin, () -> async.fire("castle-income", s.realm::collectIncome), 200L, 12_000L);
    }

    static String key(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    static String block(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public void plotChanged(String world, int cx, int cz) {
        async.run("plot-reload", () -> new Object[]{s.realm.plot(world, cx, cz), s.realm.members(world, cx, cz)}, r -> {
            @SuppressWarnings("unchecked") var p = (Optional<RealmRepository.Plot>) r[0];
            @SuppressWarnings("unchecked") var m = (List<String>) r[1];
            if (p.isEmpty()) plots.remove(key(world, cx, cz));
            else plots.put(key(world, cx, cz), new PlotInfo(p.get().owner(), new HashSet<>(m)));
        }, null);
    }

    public void shopOpened(RealmRepository.Shop sh) {
        shops.put(block(sh.world(), sh.x(), sh.y(), sh.z()), sh.id());
    }

    public void shopClosed(String world, int x, int y, int z) {
        shops.remove(block(world, x, y, z));
    }

    public Optional<String> shopAt(Block b) {
        return Optional.ofNullable(shops.get(block(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())));
    }

    public Optional<String> ownerAt(Block b) {
        PlotInfo p = plots.get(key(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4));
        return Optional.ofNullable(p == null ? null : p.owner());
    }

    private boolean denied(Player p, Block b) {
        if (p.hasPermission("versaera.admin")) return false;
        PlotInfo info = plots.get(key(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4));
        if (info == null) return false;
        String id = p.getUniqueId().toString();
        return !info.owner().equals(id) && !info.members().contains(id);
    }

    // ------------------------------------------------------------------ 땅 보호
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (shops.containsKey(block(e.getBlock().getWorld().getName(), e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()))) {
            e.setCancelled(true);
            Ui.bar(e.getPlayer(), "&7상점 블록");
            return;
        }
        if (denied(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            Ui.bar(e.getPlayer(), "&7남의 땅입니다");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (denied(e.getPlayer(), e.getBlock())) {
            e.setCancelled(true);
            Ui.bar(e.getPlayer(), "&7남의 땅입니다");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (denied(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plots.containsKey(key(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4)));
    }

    /** 상점 블록 우클릭 = 상점 창. 남의 땅의 상자 · 문 · 버튼은 막는다 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        String shop = shops.get(block(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        if (shop != null) {
            e.setCancelled(true);
            openShop(e.getPlayer(), shop);
            return;
        }
        if (b.getType().isInteractable() && denied(e.getPlayer(), b)) {
            e.setCancelled(true);
            Ui.bar(e.getPlayer(), "&7남의 땅입니다");
        }
    }

    // ------------------------------------------------------------------ 상점 창
    public void openShop(Player p, String shopId) {
        String me = p.getUniqueId().toString();
        async.run("shop-open", () -> new Object[]{s.realm.shop(shopId).orElseThrow(), s.realm.stock(shopId)}, r -> {
            var sh = (RealmRepository.Shop) r[0];
            @SuppressWarnings("unchecked") var stock = (List<RealmRepository.Stock>) r[1];
            boolean owner = sh.owner().equals(me);
            Menu m = new Menu(3, "&8" + sh.name() + (owner ? " (내 상점)" : ""));
            for (int i = 0; i < stock.size() && i < 27; i++) {
                RealmRepository.Stock st = stock.get(i);
                var type = s.items.types().get(st.typeId());
                Material mat = Material.matchMaterial(type.material());
                List<String> lore = new ArrayList<>(List.of("&e" + st.price() + " 골드" + (st.itemId() == null ? " / 개" : ""), "&7품질 " + st.quality() + " · 남은 " + st.amount() + "개"));
                lore.add(owner ? "&8클릭: 내리기 (배달함으로)" : st.itemId() == null ? "&8클릭 1개 · 쉬프트 16개" : "&8클릭: 사기");
                m.set(i, Menu.icon(mat == null ? Material.PAPER : mat, "&f" + type.name(), lore), ev -> {
                    if (owner) {
                        async.run("shop-withdraw", () -> { s.realm.withdrawStock(me, st.id()); return true; }, ok -> {
                            p.sendMessage(Ui.info(type.name() + " 을(를) 내렸다 — 배달함"));
                            openShop(p, shopId);
                        }, p);
                    } else {
                        int n = st.itemId() == null && ev.isShiftClick() ? Math.min(16, st.amount()) : 1;
                        String key = "shop-buy:" + me + ":" + st.id() + ":" + System.nanoTime();
                        async.run("shop-buy", () -> s.realm.buy(me, st.id(), n, key), ok -> {
                            p.sendMessage(Ui.info(type.name() + " " + n + "개를 샀다 (" + st.price() * n + " 골드) — 배달함"));
                            openShop(p, shopId);
                        }, p);
                    }
                });
            }
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 공성
    public void siegeStarted(RealmService.Siege sg) {
        progress.put(sg.region(), 0);
        refreshSides(sg);
        Region r = s.regions.byId(sg.region());
        Bukkit.broadcastMessage(Ui.info("공성 선포 — 「" + (r == null ? sg.region() : r.name()) + "」 (30분)"));
    }

    @SuppressWarnings("unchecked")
    private void refreshSides(RealmService.Siege sg) {
        async.run("siege-sides", () -> {
            Set<String> a = new HashSet<>(), d = new HashSet<>();
            for (GuildRepository.Member m : s.guilds.members(sg.attacker())) a.add(m.uuid());
            for (GuildRepository.Member m : s.guilds.members(sg.defender())) d.add(m.uuid());
            return new Set[]{a, d};
        }, sides -> { siegeSides.put(sg.region(), sides); sidesAsked.remove(sg.region()); }, null);
    }

    private void siegeTick() {
        long now = System.currentTimeMillis();
        for (RealmService.Siege sg : new ArrayList<>(s.realm.sieges())) {
            Region r = s.regions.byId(sg.region());
            var world = r == null ? null : Bukkit.getWorld(r.world());
            Set<String>[] sides = siegeSides.get(sg.region());
            if (sides == null && world != null && sidesAsked.add(sg.region())) refreshSides(sg);   // 다시 켠 뒤 이어지는 공성
            if (world == null || sides == null) continue;
            int cx = (r.minX() + r.maxX()) / 2, cz = (r.minZ() + r.maxZ()) / 2;
            Location center = new Location(world, cx + 0.5, world.getHighestBlockYAt(cx, cz) + 1, cz + 0.5);
            int att = 0, def = 0;
            for (Player p : world.getPlayers()) {
                if (p.isDead() || p.getLocation().distanceSquared(center) > RealmRules.CAPTURE_RADIUS * RealmRules.CAPTURE_RADIUS) continue;
                String id = p.getUniqueId().toString();
                if (sides[0].contains(id)) att++;
                else if (sides[1].contains(id)) def++;
            }
            int before = progress.getOrDefault(sg.region(), 0), after = RealmRules.captureTick(before, att, def);
            progress.put(sg.region(), after);
            if (now % 10_000 < 1_000) {
                refreshSides(sg);
                int held = after;
                async.fire("siege-save", () -> { s.realm.saveCaptureProgress(sg.region(), held); return null; });
            }
            for (Player p : world.getPlayers())
                if (p.getLocation().distanceSquared(center) < 128 * 128)
                    Ui.bar(p, "&c공성 &f" + r.name() + " &7점령 " + after + "/" + RealmRules.CAPTURE_SECONDS + "초 · 공격 " + att + " · 수비 " + def
                            + " · 남은 " + (sg.endsAt() - now) / 60_000 + "분");
            if (after >= RealmRules.CAPTURE_SECONDS) {
                progress.remove(sg.region());
                siegeSides.remove(sg.region());
                async.run("siege-capture", () -> new Object[]{s.realm.capture(sg.region()), s.guilds.find(sg.attacker())}, res -> {
                    @SuppressWarnings("unchecked") var g = (Optional<GuildRepository.Guild>) res[1];
                    Bukkit.broadcastMessage(Ui.info("「" + r.name() + "」이(가) " + g.map(GuildRepository.Guild::name).orElse("?") + " 길드에게 넘어갔습니다"));
                    @SuppressWarnings("unchecked") var crown = (Optional<RealmService.Crowning>) res[0];
                    crown.ifPresent(this::announceEmperor);
                }, null);
            }
        }
        // 끝난 공성
        progress.keySet().removeIf(region -> s.realm.sieges().stream().noneMatch(x -> x.region().equals(region)) && announceFail(region));
    }

    private boolean announceFail(String region) {
        Region r = s.regions.byId(region);
        Bukkit.broadcastMessage(Ui.info("「" + (r == null ? region : r.name()) + "」 공성이 끝났습니다 — 성은 지켜졌습니다"));
        siegeSides.remove(region);
        return true;
    }

    public void announceEmperor(RealmService.Crowning c) {
        Bukkit.broadcastMessage(Ui.c("&6&l대륙 통일 — " + c.nation().name() + " (" + c.guild().name() + ") 이(가) 베르사 대륙 최초의 황제국이 되었습니다!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.sendTitle(Ui.c("&6황제 탄생"), Ui.c("&f" + c.nation().name()), 10, 100, 20);
    }
}
