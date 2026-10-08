package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.application.QuestService;
import io.versaera.application.port.GuildRepository;
import io.versaera.application.port.MarketRepository;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.guild.GuildRules;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.job.JobDefinition;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.InventoryOps;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 플레이어 명령: /직업 · /의뢰 · /길드 · /경매 · /던전 · /배달함. 창은 아이콘 + 이름 + 숫자만.
 */
public final class GameCommands implements CommandExecutor {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Consumer<Player> deliver;
    private final Function<Player, PlayerFacts> facts;
    private final DungeonStarter dungeons;

    /** 던전 입장 (DungeonRuntime) */
    public interface DungeonStarter {
        void enter(Player leader, String dungeonId);
    }

    public GameCommands(GameServices s, Async async, ItemCodec codec, Consumer<Player> deliver, Function<Player, PlayerFacts> facts, DungeonStarter dungeons) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.deliver = deliver;
        this.facts = facts;
        this.dungeons = dungeons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) return true;
        switch (cmd.getName()) {
            case "job" -> jobs(p);
            case "quest" -> quests(p);
            case "guild" -> guild(p, a);
            case "auction" -> auction(p, a);
            case "dungeon" -> dungeon(p, a);
            case "mailbox" -> mailbox(p);
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------ 직업
    private void jobs(Player p) {
        String id = p.getUniqueId().toString();
        PlayerFacts f = facts.apply(p);
        async.run("jobs", () -> new Object[]{s.jobs.held(id), s.jobs.available(id, f)}, r -> {
            @SuppressWarnings("unchecked") var held = (Map<String, io.versaera.application.port.JobRepository.Held>) r[0];
            @SuppressWarnings("unchecked") var avail = (List<JobDefinition>) r[1];
            Menu m = new Menu(3, "&8직업");
            int slot = 0;
            for (String slotName : List.of("COMBAT", "LIFE")) {
                var h = held.get(slotName);
                String name = h == null ? "&8없음" : "&f" + s.jobs.job(h.jobId()).name();
                m.set(slot, Menu.ui(slotName.equals("COMBAT") ? "combat" : "life", slotName.equals("COMBAT") ? Material.IRON_SWORD : Material.ANVIL, name,
                        List.of("&7" + (slotName.equals("COMBAT") ? "전투" : "생활"))), null);
                slot += 2;
            }
            slot = 9;
            for (JobDefinition j : avail) {
                if (slot > 26) break;
                m.set(slot++, Menu.icon(Material.ENCHANTED_BOOK, "&a" + j.name(), List.of("&7" + (j.slot().equals("COMBAT") ? "전투" : "생활") + " " + j.tier())),
                        e -> async.run("job-advance", () -> s.jobs.advance(id, j.id(), f), d -> {
                            p.closeInventory();
                            if (d.id().equals("moonlight_sculptor")) {   // 히든 직업: 서버 전체가 안다
                                p.sendTitle(Ui.c("&b&l☾ 달빛 조각사"), Ui.c("&f달빛이 조각칼에 깃들었다"), 10, 80, 20);
                                p.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, p.getLocation().add(0, 1, 0), 150, 0.8, 1.5, 0.8, 0.05);
                                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                                Bukkit.broadcastMessage(Ui.c("&b[히든 직업] &f" + p.getName() + " &7— 달빛 조각사"));
                            } else p.sendTitle(Ui.c("&6" + d.name()), "", 5, 40, 10);
                        }, p));
            }
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 의뢰
    private void quests(Player p) {
        String id = p.getUniqueId().toString();
        async.run("quests", () -> new Object[]{s.quests.active(id), s.quests.reputations(id)}, r -> {
            @SuppressWarnings("unchecked") List<QuestService.Active> act = (List<QuestService.Active>) r[0];
            @SuppressWarnings("unchecked") Map<String, Integer> rep = (Map<String, Integer>) r[1];
            Menu m = new Menu(4, "&8의뢰");
            int slot = 0;
            for (QuestService.Active a : act) {
                if (slot > 26) break;
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < a.def().objectives().size(); i++) {
                    var o = a.def().objectives().get(i);
                    lines.add("&8· &7" + o.label() + (o.type() == io.versaera.domain.quest.QuestDefinition.Type.DELIVER ? ""
                            : " &f" + Math.min(o.amount(), a.progress().get(i)) + "/" + o.amount()));
                }
                if (a.def().giver() != null) lines.add("&8" + s.relations.npc(a.def().giver()).name());
                lines.add("&8쉬프트 클릭: 포기");
                m.set(slot++, Menu.ui("quest_active", Material.WRITABLE_BOOK, "&f" + a.def().title(), lines), e -> {
                    if (!e.isShiftClick()) return;
                    async.run("quest-abandon", () -> { s.quests.abandon(id, a.def().id()); return null; }, v -> quests(p), p);
                });
            }
            slot = 27;
            for (var e : rep.entrySet()) {
                if (slot > 35) break;
                m.set(slot++, Menu.ui("reputation", e.getValue() >= 0 ? Material.LIME_BANNER : Material.RED_BANNER, "&f" + e.getKey(), List.of("&7" + e.getValue())), null);
            }
            m.open(p);
        }, p);
    }

    // ------------------------------------------------------------------ 길드
    private void guild(Player p, String[] a) {
        String id = p.getUniqueId().toString();
        String sub = a.length == 0 ? "" : a[0];
        String req = UUID.randomUUID().toString();
        switch (sub) {
            case "창설", "create" -> {
                if (a.length < 3) { p.sendMessage(Ui.error("/길드 창설 <태그> <이름>")); return; }
                String tag = a[1], name = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                async.run("guild-create", () -> s.guilds.create(id, name, tag, req), g -> Bukkit.broadcastMessage(Ui.info("길드 [" + g.tag() + "] " + g.name() + " 창설")), p);
            }
            case "초대", "invite" -> {
                Player t = a.length < 2 ? null : Bukkit.getPlayerExact(a[1]);
                if (t == null) { p.sendMessage(Ui.error("상대를 찾을 수 없습니다")); return; }
                String tid = t.getUniqueId().toString();
                async.run("guild-invite", () -> { s.guilds.invite(id, tid); return s.guilds.guildOf(id).orElseThrow(); }, g -> {
                    p.sendMessage(Ui.info(t.getName() + " 초대"));
                    t.sendMessage(Ui.info("[" + g.tag() + "] " + g.name() + " 길드 초대"));
                }, p);
            }
            case "수락", "accept" -> async.run("guild-accept", () -> {
                for (String gid : s.guilds.invitesFor(id)) {
                    var g = s.guilds.find(gid).orElse(null);
                    if (g != null && (a.length < 2 || g.tag().equalsIgnoreCase(a[1]))) return s.guilds.accept(id, gid);
                }
                throw io.versaera.domain.common.DomainException.of("guild.no_invite", "초대가 없습니다");
            }, g -> p.sendMessage(Ui.info("[" + g.tag() + "] " + g.name() + " 가입")), p);
            case "탈퇴", "leave" -> async.run("guild-leave", () -> { s.guilds.leave(id); return null; }, v -> p.sendMessage(Ui.info("탈퇴")), p);
            case "추방", "kick" -> target(p, a, tid -> async.run("guild-kick", () -> { s.guilds.kick(id, tid); return null; }, v -> p.sendMessage(Ui.info("추방")), p));
            case "부길드장", "officer" -> target(p, a, tid -> async.run("guild-rank", () -> { s.guilds.setRank(id, tid, GuildRules.Rank.OFFICER); return null; },
                    v -> p.sendMessage(Ui.info("부길드장 임명")), p));
            case "강등", "demote" -> target(p, a, tid -> async.run("guild-rank", () -> { s.guilds.setRank(id, tid, GuildRules.Rank.MEMBER); return null; },
                    v -> p.sendMessage(Ui.info("길드원으로")), p));
            case "위임", "leader" -> target(p, a, tid -> async.run("guild-leader", () -> { s.guilds.transferLeader(id, tid); return null; },
                    v -> p.sendMessage(Ui.info("길드장 위임")), p));
            case "해산", "disband" -> async.run("guild-disband", () -> { s.guilds.disband(id); return null; }, v -> p.sendMessage(Ui.info("해산")), p);
            case "입금", "deposit" -> amount(p, a, n -> async.run("guild-dep", () -> s.guilds.deposit(id, n, req), b -> p.sendMessage(Ui.info("금고 " + io.versaera.domain.economy.Money.format(b))), p));
            case "출금", "withdraw" -> amount(p, a, n -> async.run("guild-wd", () -> s.guilds.withdraw(id, n, req), b -> p.sendMessage(Ui.info("금고 " + io.versaera.domain.economy.Money.format(b))), p));
            case "채팅", "c" -> {
                String msg = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                if (msg.isBlank()) return;
                async.run("guild-chat", () -> {
                    var m = s.guilds.membership(id).orElseThrow(() -> io.versaera.domain.common.DomainException.of("guild.none", "길드가 없습니다"));
                    return s.guilds.members(m.guildId()).stream().map(GuildRepository.Member::uuid).toList();
                }, members -> {
                    String line = Ui.c("&a[길드] &f" + p.getName() + "&7: ") + msg;   // 플레이어 글에는 색 코드를 적용하지 않음
                    for (String u : members) { Player o = Bukkit.getPlayer(UUID.fromString(u)); if (o != null) o.sendMessage(line); }
                }, p);
            }
            default -> async.run("guild-info", () -> {
                var g = s.guilds.guildOf(id).orElse(null);
                if (g == null) return null;
                return new Object[]{g, s.guilds.members(g.id()), s.guilds.treasury(g.id())};
            }, r -> {
                if (r == null) { p.sendMessage(Ui.info("/길드 창설 <태그> <이름> · /길드 수락")); return; }
                var g = (GuildRepository.Guild) r[0];
                @SuppressWarnings("unchecked") var mem = (List<GuildRepository.Member>) r[1];
                Menu m = new Menu(4, "&8[" + g.tag() + "] " + g.name());
                m.set(4, Menu.ui("guild", Material.WHITE_BANNER, "&f" + g.name(), List.of("&7레벨 " + g.level(), "&7" + mem.size() + "/" + GuildRules.maxMembers(g.level()),
                        "&e" + r[2])), null);
                int slot = 9;
                for (var x : mem) {
                    if (slot > 35) break;
                    var off = Bukkit.getOfflinePlayer(UUID.fromString(x.uuid()));
                    m.set(slot++, Menu.ui("member", Material.PLAYER_HEAD, "&f" + (off.getName() == null ? "?" : off.getName()),
                            List.of("&7" + switch (x.rank()) { case "LEADER" -> "길드장"; case "OFFICER" -> "부길드장"; default -> "길드원"; }, "&8공헌 " + x.contribution())), null);
                }
                m.open(p);
            }, p);
        }
    }

    private static void target(Player p, String[] a, Consumer<String> then) {
        var t = a.length < 2 ? null : Bukkit.getOfflinePlayer(a[1]);
        if (t == null || (!t.hasPlayedBefore() && !t.isOnline())) { p.sendMessage(Ui.error("상대를 찾을 수 없습니다")); return; }
        then.accept(t.getUniqueId().toString());
    }

    private static void amount(Player p, String[] a, Consumer<Long> then) {
        try {
            long n = io.versaera.domain.economy.Money.parse(a[1]);
            if (n <= 0) throw new NumberFormatException();
            then.accept(n);
        } catch (RuntimeException e) {
            p.sendMessage(Ui.error("금액: 3골드20실버 · 50실버 · 30쿠퍼 (숫자만 쓰면 실버)"));
        }
    }

    // ------------------------------------------------------------------ 경매
    private String marketAt(Player p) {
        Location l = p.getLocation();
        Region r = s.regions.at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        for (; r != null; r = r.parent() == null ? null : s.regions.byId(r.parent()))
            for (var m : s.market.catalog().markets().values()) if (m.region().equals(r.id())) return m.id();
        return null;
    }

    private void auction(Player p, String[] a) {
        String id = p.getUniqueId().toString();
        // 어디서나: 내 매물 (모든 시장) · 모두 내리기
        if (a.length >= 1 && (a[0].equals("내매물") || a[0].equals("mine"))) { myListings(p); return; }
        if (a.length >= 1 && (a[0].equals("모두내리기") || a[0].equals("cancelall"))) {
            async.run("auction-cancel-all", () -> s.auctions.cancelAll(id), n -> { p.sendMessage(Ui.info("내림 " + n)); deliver.accept(p); }, p);
            return;
        }
        String market = marketAt(p);
        if (market == null) { p.sendMessage(Ui.error("시장이 있는 도시에서만 쓸 수 있습니다")); return; }
        if (a.length >= 2 && (a[0].equals("등록") || a[0].equals("sell"))) {
            long price;
            price = io.versaera.domain.economy.Money.parse(a[1]);
            if (price <= 0) { p.sendMessage(Ui.error("/경매 등록 <가격> (예: 3골드20실버 · 50실버 · 숫자만 쓰면 실버)")); return; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            String unique = codec.instanceId(hand);
            if (unique != null) {
                p.getInventory().setItemInMainHand(null);
                async.run("auction-list", () -> s.auctions.listUnique(id, market, unique, price), l -> p.sendMessage(Ui.info("등록 · " + io.versaera.domain.economy.Money.format(price))),
                        err -> p.getInventory().addItem(hand), p);   // 실패: 서버에선 그대로 내 것 → 돌려줌
                return;
            }
            String type = codec.typeId(hand);
            if (type == null) { p.sendMessage(Ui.error("올릴 물건을 손에 드세요")); return; }
            int q = codec.bulkQuality(hand), n = hand.getAmount();
            List<MaterialInput> taken = InventoryOps.take(p, codec, type, n, q);
            if (taken == null) return;
            async.run("auction-list", () -> s.auctions.listBulk(id, market, type, q, n, price), l -> p.sendMessage(Ui.info("등록 · " + io.versaera.domain.economy.Money.format(price))),
                    err -> deliver.accept(p), p);   // 실패하면 서비스가 배달함으로 돌려준다
            return;
        }
        // 찾기: id 또는 이름 일부 (예: /경매 철) → 맞는 종류만
        String query = a.length >= 1 ? String.join(" ", a).trim() : null;
        Set<String> types = query == null ? null : new HashSet<>();
        if (query != null)
            for (var t : codec.types().all())
                if (t.id().equalsIgnoreCase(query) || t.name().contains(query)) types.add(t.id());
        if (types != null && types.isEmpty()) { p.sendMessage(Ui.error("그런 물건이 없습니다: " + query)); return; }
        String only = types != null && types.size() == 1 ? types.iterator().next() : null;
        async.run("auction-browse", () -> new Object[]{s.auctions.browse(market, only, 100), s.auctions.mine(id), s.economy.balance(id)}, r -> {
            @SuppressWarnings("unchecked") List<MarketRepository.Listing> list = new ArrayList<>((List<MarketRepository.Listing>) r[0]);
            @SuppressWarnings("unchecked") List<MarketRepository.Listing> mine = (List<MarketRepository.Listing>) r[1];
            if (types != null) list.removeIf(l -> !types.contains(l.typeId()));
            browse(p, a, market, list, mine.size(), (Long) r[2], 0, false);
        }, p);
    }

    /** 경매 목록 한 쪽 (45칸). 아래 줄: 이전 · 정렬 · 내 매물 · 배달함 · 다음 */
    private void browse(Player p, String[] a, String market, List<MarketRepository.Listing> all, int mineCount, long balance, int page, boolean newest) {
        String id = p.getUniqueId().toString();
        List<MarketRepository.Listing> list = new ArrayList<>(all);
        if (newest) list.sort(Comparator.comparingLong(MarketRepository.Listing::createdAt).reversed());
        else list.sort(Comparator.comparingDouble((MarketRepository.Listing l) -> (double) l.price() / Math.max(1, l.amount())).thenComparingLong(MarketRepository.Listing::createdAt));
        int pages = Math.max(1, (list.size() + 44) / 45), pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, "&8" + s.market.catalog().market(market).name() + " 경매 " + (pg + 1) + "/" + pages);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 45 && pg * 45 + i < list.size(); i++) {
            var l = list.get(pg * 45 + i);
            boolean own = l.seller().equals(id);
            ItemStack icon = listingIcon(l, List.of("&e" + io.versaera.domain.economy.Money.format(l.price()) + " &7(개당 " + io.versaera.domain.economy.Money.format(Math.max(1, l.price() / Math.max(1, l.amount()))) + ")", "&7x" + l.amount(),
                    "&8" + left(l.expiresAt() - now), own ? "&8내 물건 · 클릭: 내리기" : "&8클릭: 사기"));
            m.set(i, icon, e -> {
                if (own) async.run("auction-cancel", () -> { s.auctions.cancel(id, l.id()); return null; }, v -> { deliver.accept(p); auction(p, a); }, p);
                else confirmBuy(p, a, l, balance);
            });
        }
        if (pg > 0) m.set(45, Menu.icon(Material.ARROW, "&f이전", List.of()), e -> browse(p, a, market, all, mineCount, balance, pg - 1, newest));
        m.set(47, Menu.icon(Material.HOPPER, newest ? "&f최신순" : "&f싼 순", List.of("&8클릭: 바꾸기")), e -> browse(p, a, market, all, mineCount, balance, 0, !newest));
        m.set(49, Menu.ui("auction", Material.CHEST, "&f내 매물 " + mineCount + "/" + io.versaera.application.AuctionService.MAX_OPEN, List.of("&8클릭: 보기 · 내리기")), e -> myListings(p));
        m.set(51, Menu.icon(Material.ENDER_CHEST, "&f배달함 받기", List.of()), e -> { deliver.accept(p); p.closeInventory(); });
        if (pg < pages - 1) m.set(53, Menu.icon(Material.ARROW, "&f다음", List.of()), e -> browse(p, a, market, all, mineCount, balance, pg + 1, newest));
        m.open(p);
    }

    private ItemStack listingIcon(MarketRepository.Listing l, List<String> lore) {
        ItemStack icon = codec.bulk(l.typeId(), l.quality(), Math.max(1, Math.min(64, l.amount())));
        var meta = icon.getItemMeta();
        List<String> lines = new ArrayList<>();
        for (String x : lore) lines.add(Ui.c(x));
        meta.setLore(lines);
        icon.setItemMeta(meta);
        return icon;
    }

    private static String left(long ms) {
        if (ms <= 0) return "곧 끝남";
        long h = ms / 3_600_000L, mi = ms / 60_000L % 60;
        return h > 0 ? h + "시간 " + mi + "분 남음" : mi + "분 남음";
    }

    /** 사기 전에 한 번 더: 값 · 남는 돈 */
    private void confirmBuy(Player p, String[] a, MarketRepository.Listing l, long balance) {
        String id = p.getUniqueId().toString();
        Menu m = new Menu(3, "&8사시겠습니까?");
        m.set(13, listingIcon(l, List.of("&e" + io.versaera.domain.economy.Money.format(l.price()), "&7x" + l.amount(), "&7남는 돈 " + io.versaera.domain.economy.Money.format(balance - l.price()))), null);
        m.set(11, Menu.icon(Material.LIME_WOOL, "&a사기", List.of("&e-" + io.versaera.domain.economy.Money.format(l.price()))),
                e -> async.run("auction-buy", () -> s.auctions.buy(id, l.id()), v -> { p.sendMessage(Ui.info("-" + io.versaera.domain.economy.Money.format(l.price()))); deliver.accept(p); auction(p, a); }, p));
        m.set(15, Menu.icon(Material.RED_WOOL, "&c그만두기", List.of()), e -> auction(p, a));
        m.open(p);
    }

    /** 내 매물 (모든 시장): 시장 · 값 · 남은 시간, 클릭 = 내리기 (배달함으로) */
    private void myListings(Player p) {
        String id = p.getUniqueId().toString();
        async.run("auction-mine", () -> s.auctions.mine(id), mine -> {
            Menu m = new Menu(3, "&8내 매물 " + mine.size() + "/" + io.versaera.application.AuctionService.MAX_OPEN);
            long now = System.currentTimeMillis();
            int slot = 0;
            for (var l : mine) {
                if (slot > 17) break;
                String mk = s.market.catalog().markets().containsKey(l.market()) ? s.market.catalog().market(l.market()).name() : l.market();
                m.set(slot++, listingIcon(l, List.of("&e" + io.versaera.domain.economy.Money.format(l.price()), "&7x" + l.amount(), "&7" + mk, "&8" + left(l.expiresAt() - now), "&8클릭: 내리기")),
                        e -> async.run("auction-cancel", () -> { s.auctions.cancel(id, l.id()); return null; }, v -> { deliver.accept(p); myListings(p); }, p));
            }
            if (!mine.isEmpty())
                m.set(22, Menu.icon(Material.BARRIER, "&c모두 내리기", List.of("&8물건은 배달함으로")),
                        e -> async.run("auction-cancel-all", () -> s.auctions.cancelAll(id), n -> { p.sendMessage(Ui.info("내림 " + n)); deliver.accept(p); myListings(p); }, p));
            m.set(26, Menu.icon(Material.ENDER_CHEST, "&f배달함 받기", List.of()), e -> { deliver.accept(p); p.closeInventory(); });
            m.open(p);
        }, p);
    }

    /** /배달함 — 가득 차서 못 받은 물건 받기 */
    private void mailbox(Player p) {
        String id = p.getUniqueId().toString();
        async.run("mailbox", () -> s.items.pendingDeliveries(id).size() + s.items.pendingBulk(id).size(), n -> {
            if (n == 0) { p.sendMessage(Ui.info("배달함이 비어 있습니다")); return; }
            if (p.getInventory().firstEmpty() < 0) p.sendMessage(Ui.error("가방이 가득 찼습니다 — " + n + "개 대기"));
            deliver.accept(p);
        }, p);
    }

    // ------------------------------------------------------------------ 던전
    private void dungeon(Player p, String[] a) {
        if (a.length == 0) {
            StringBuilder sb = new StringBuilder();
            for (var d : s.dungeons.all()) sb.append(d.id()).append(' ');
            p.sendMessage(Ui.info("/던전 <" + sb.toString().trim() + ">"));
            return;
        }
        dungeons.enter(p, a[0]);
    }
}
