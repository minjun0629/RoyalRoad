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
 * 플레이어 명령: /직업 · /의뢰 · /길드 · /경매 · /던전. 창은 아이콘 + 이름 + 숫자만.
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
                m.set(slot, Menu.icon(slotName.equals("COMBAT") ? Material.IRON_SWORD : Material.ANVIL, name,
                        List.of("&7" + (slotName.equals("COMBAT") ? "전투" : "생활"))), null);
                slot += 2;
            }
            slot = 9;
            for (JobDefinition j : avail) {
                if (slot > 26) break;
                m.set(slot++, Menu.icon(Material.ENCHANTED_BOOK, "&a" + j.name(), List.of("&7" + (j.slot().equals("COMBAT") ? "전투" : "생활") + " " + j.tier())),
                        e -> async.run("job-advance", () -> s.jobs.advance(id, j.id(), f), d -> {
                            p.closeInventory();
                            p.sendTitle(Ui.c("&6" + d.name()), "", 5, 40, 10);
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
                m.set(slot++, Menu.icon(Material.WRITABLE_BOOK, "&f" + a.def().title(), lines), e -> {
                    if (!e.isShiftClick()) return;
                    async.run("quest-abandon", () -> { s.quests.abandon(id, a.def().id()); return null; }, v -> quests(p), p);
                });
            }
            slot = 27;
            for (var e : rep.entrySet()) {
                if (slot > 35) break;
                m.set(slot++, Menu.icon(e.getValue() >= 0 ? Material.LIME_BANNER : Material.RED_BANNER, "&f" + e.getKey(), List.of("&7" + e.getValue())), null);
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
                    t.sendMessage(Ui.info("[" + g.tag() + "] " + g.name() + " 초대 · /길드 수락 " + g.tag()));
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
            case "입금", "deposit" -> amount(p, a, n -> async.run("guild-dep", () -> s.guilds.deposit(id, n, req), b -> p.sendMessage(Ui.info("금고 " + b)), p));
            case "출금", "withdraw" -> amount(p, a, n -> async.run("guild-wd", () -> s.guilds.withdraw(id, n, req), b -> p.sendMessage(Ui.info("금고 " + b)), p));
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
                m.set(4, Menu.icon(Material.WHITE_BANNER, "&f" + g.name(), List.of("&7레벨 " + g.level(), "&7" + mem.size() + "/" + GuildRules.maxMembers(g.level()),
                        "&e" + r[2])), null);
                int slot = 9;
                for (var x : mem) {
                    if (slot > 35) break;
                    var off = Bukkit.getOfflinePlayer(UUID.fromString(x.uuid()));
                    m.set(slot++, Menu.icon(Material.PLAYER_HEAD, "&f" + (off.getName() == null ? "?" : off.getName()),
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
            long n = Long.parseLong(a[1]);
            if (n <= 0) throw new NumberFormatException();
            then.accept(n);
        } catch (RuntimeException e) {
            p.sendMessage(Ui.error("금액을 숫자로 적으세요"));
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
        String market = marketAt(p);
        if (market == null) { p.sendMessage(Ui.error("시장이 있는 도시에서만 쓸 수 있습니다")); return; }
        String id = p.getUniqueId().toString();
        if (a.length >= 2 && (a[0].equals("등록") || a[0].equals("sell"))) {
            long price;
            try { price = Long.parseLong(a[1]); } catch (NumberFormatException e) { p.sendMessage(Ui.error("/경매 등록 <가격>")); return; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            String unique = codec.instanceId(hand);
            if (unique != null) {
                p.getInventory().setItemInMainHand(null);
                async.run("auction-list", () -> s.auctions.listUnique(id, market, unique, price), l -> p.sendMessage(Ui.info("등록 · " + price)),
                        err -> p.getInventory().addItem(hand), p);   // 실패: 서버에선 그대로 내 것 → 돌려줌
                return;
            }
            String type = codec.typeId(hand);
            if (type == null) { p.sendMessage(Ui.error("올릴 물건을 손에 드세요")); return; }
            int q = codec.bulkQuality(hand), n = hand.getAmount();
            List<MaterialInput> taken = InventoryOps.take(p, codec, type, n, q);
            if (taken == null) return;
            async.run("auction-list", () -> s.auctions.listBulk(id, market, type, q, n, price), l -> p.sendMessage(Ui.info("등록 · " + price)),
                    err -> deliver.accept(p), p);   // 실패하면 서비스가 배달함으로 돌려준다
            return;
        }
        String filter = a.length >= 1 ? a[0] : null;
        async.run("auction-browse", () -> new Object[]{s.auctions.browse(market, filter != null && codec.types().has(filter) ? filter : null, 45),
                s.auctions.mine(id)}, r -> {
            @SuppressWarnings("unchecked") List<MarketRepository.Listing> list = (List<MarketRepository.Listing>) r[0];
            @SuppressWarnings("unchecked") List<MarketRepository.Listing> mine = (List<MarketRepository.Listing>) r[1];
            Menu m = new Menu(6, "&8" + s.market.catalog().market(market).name() + " 경매");
            int slot = 0;
            for (var l : list) {
                if (slot > 44) break;
                ItemStack icon = codec.bulk(l.typeId(), l.quality(), Math.min(64, l.amount()));
                var meta = icon.getItemMeta();
                meta.setLore(List.of(Ui.c("&e" + l.price()), Ui.c("&7x" + l.amount()), Ui.c(l.seller().equals(id) ? "&8내 물건 · 클릭: 내리기" : "&8클릭: 사기")));
                icon.setItemMeta(meta);
                m.set(slot++, icon, e -> {
                    if (l.seller().equals(id)) async.run("auction-cancel", () -> { s.auctions.cancel(id, l.id()); return null; }, v -> { deliver.accept(p); auction(p, a); }, p);
                    else async.run("auction-buy", () -> s.auctions.buy(id, l.id()), v -> { p.sendMessage(Ui.info("-" + l.price())); deliver.accept(p); auction(p, a); }, p);
                });
            }
            m.set(49, Menu.icon(Material.CHEST, "&f내 매물 " + mine.size() + "/" + io.versaera.application.AuctionService.MAX_OPEN, List.of("&8/경매 등록 <가격>")), null);
            m.open(p);
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
