package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.domain.faith.God;
import io.versaera.domain.reputation.Reputation;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.listener.OriginListener;
import io.versaera.platform.bukkit.listener.ReputationListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * /파티 · /기부 · /신 · /연대기 · /명성 · /비기 · /수련 (PTY-01 · GOD-01 · REP-01 · LORE-01 · ART-01 · TRN-02)
 */
public final class CanonCommands implements CommandExecutor {
    private final GameServices s;
    private final Async async;
    private final OriginListener origins;
    private final ReputationListener reputation;

    private io.versaera.platform.bukkit.combat.SecretArtRuntime arts;
    private io.versaera.platform.bukkit.world.IronMenTrial trial;

    public void attach(io.versaera.platform.bukkit.combat.SecretArtRuntime arts, io.versaera.platform.bukkit.world.IronMenTrial trial) {
        this.arts = arts;
        this.trial = trial;
    }

    public CanonCommands(GameServices s, Async async, OriginListener origins, ReputationListener reputation) {
        this.s = s;
        this.async = async;
        this.origins = origins;
        this.reputation = reputation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        switch (cmd.getName()) {
            case "history" -> {
                sender.sendMessage(Ui.c("&6── 베르사 연대기 ──"));
                for (var e : s.content.eras())
                    sender.sendMessage(Ui.c("&e" + e.name() + " &8(" + e.when() + ")" + "\n&7  " + e.summary()));
                return true;
            }
            case "gods" -> {
                sender.sendMessage(Ui.c("&6── 신 · 교단 ──"));
                for (God g : s.reputation.gods()) {
                    StringBuilder where = new StringBuilder();
                    for (var t : s.content.temples()) if (t.god().equals(g.id())) where.append(where.length() == 0 ? "" : ", ").append(s.regions.byId(t.region()).name());
                    sender.sendMessage(Ui.c((g.evil() ? "&c" : "&e") + g.name() + " &7— " + g.domain() + (where.length() > 0 ? " &8· 신전: " + where : "")));
                }
                sender.sendMessage(Ui.c("&7신전 안에서 &f/기부 <금액>&7: 악명을 씻고, 남은 돈만큼 축복을 받는다"));
                return true;
            }
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("플레이어만");
            return true;
        }
        String id = p.getUniqueId().toString();
        switch (cmd.getName()) {
            case "fame" -> async.run("fame", () -> new Object[]{s.reputation.standing(id), s.origins.character(id)}, r -> {
                var st = (io.versaera.application.ReputationService.Standing) r[0];
                @SuppressWarnings("unchecked") var c = (java.util.Optional<io.versaera.application.OriginService.Character>) r[1];
                c.ifPresent(ch -> p.sendMessage(Ui.c("&6" + p.getName() + " &7— " + ch.race().name() + " · " + ch.gender().label + " · 시작 도시 " + ch.city().name()
                        + (ch.beginner(System.currentTimeMillis()) ? " &e(초보 기간)" : ""))));
                p.sendMessage(Ui.c("&e명성 " + st.fame() + " &7(" + st.fameName() + ") · 의뢰 보상 x" + String.format("%.2f", Reputation.questRewardMult(st.fame(), st.notoriety()))));
                p.sendMessage(Ui.c((st.notoriety() > 0 ? "&c" : "&7") + "악명 " + st.notoriety() + (Reputation.notorious(st.notoriety()) ? " — 보통 NPC 가 상대하지 않는다" : "")));
                if (st.murderer()) p.sendMessage(Ui.c("&c살인자 — 남은 시간 " + (st.murdererUntil() - System.currentTimeMillis()) / 60_000 + "분 (몬스터 사냥 · 신전 기부로 줄어든다)"));
            }, p);
            case "donate" -> {
                if (a.length < 1) {
                    p.sendMessage(Ui.error("/기부 <금액> (예: 50실버 · 2골드)"));
                    return true;
                }
                long amount = io.versaera.domain.economy.Money.parse(String.join("", a));
                if (amount <= 0) {
                    p.sendMessage(Ui.error("금액을 적으세요 (예: 50실버 · 2골드 · 숫자만 쓰면 쿠퍼)"));
                    return true;
                }
                Region r = s.regions.at(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ());
                String region = r == null ? null : r.id(), key = "donate:" + id + ":" + System.currentTimeMillis();
                async.run("donate", () -> s.reputation.donate(id, region, amount, key), d -> {
                    p.sendMessage(Ui.info(d.god().name() + "에게 " + io.versaera.domain.economy.Money.format(d.paid()) + "를 바쳤다" + (d.cleansed() > 0 ? " — 악명 -" + d.cleansed() : "")));
                    PotionEffectType t = PotionEffectType.getByName(d.god().blessing());
                    if (t != null && d.blessingSeconds() > 0) {
                        p.addPotionEffect(new PotionEffect(t, d.blessingSeconds() * 20, 0));
                        p.sendMessage(Ui.c("&e" + d.god().name() + "의 축복 &7(" + d.blessingSeconds() / 60 + "분 " + d.blessingSeconds() % 60 + "초)"));
                    }
                    reputation.refresh(p);
                }, p);
            }
            case "party" -> party(p, id, a);
            case "arts" -> {
                if (a.length == 0) arts.list(p);
                else if (a.length >= 2 && (a[0].equals("배우기") || a[0].equals("learn"))) arts.learn(p, a[1]);
                else if (a.length >= 2 && (a[0].equals("쓰기") || a[0].equals("use"))) arts.cast(p, a[1]);
                else arts.cast(p, a[0]);
            }
            case "trial" -> {
                if (a.length > 0 && (a[0].equals("포기") || a[0].equals("quit"))) trial.giveUp(p);
                else trial.start(p);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void party(Player p, String id, String[] a) {
        String sub = a.length == 0 ? "목록" : a[0];
        try {
            switch (sub) {
                case "초대", "invite" -> {
                    Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                    if (t == null) throw io.versaera.domain.common.DomainException.of("party.no_player", "접속 중인 사람이 아닙니다");
                    s.parties.invite(id, t.getUniqueId().toString());
                    p.sendMessage(Ui.info(t.getName() + " 님을 초대했다"));
                    t.sendMessage(Ui.info(p.getName() + " 님이 파티에 초대했다"));
                }
                case "수락", "accept" -> {
                    String leader = s.parties.accept(id);
                    for (String m : s.parties.members(leader)) {
                        Player o = Bukkit.getPlayer(java.util.UUID.fromString(m));
                        if (o != null) o.sendMessage(Ui.info(p.getName() + " 님이 파티에 들어왔다"));
                    }
                }
                case "나가기", "leave" -> {
                    var before = s.parties.members(id);
                    s.parties.leave(id);
                    for (String m : before) {
                        Player o = Bukkit.getPlayer(java.util.UUID.fromString(m));
                        if (o != null) o.sendMessage(Ui.info(p.getName() + " 님이 파티를 떠났다"));
                    }
                }
                case "분배", "loot" -> {
                    var mode = switch (a.length > 1 ? a[1] : "") {
                        case "자유", "free" -> io.versaera.domain.party.Parties.Loot.FREE;
                        case "차례", "round" -> io.versaera.domain.party.Parties.Loot.ROUND_ROBIN;
                        case "무작위", "random" -> io.versaera.domain.party.Parties.Loot.RANDOM;
                        default -> throw io.versaera.domain.common.DomainException.of("party.loot", "/파티 분배 <자유|차례|무작위> (지금: " + s.parties.loot(id).label + ")");
                    };
                    s.parties.loot(id, mode);
                    for (String m : s.parties.members(id)) {
                        Player o = Bukkit.getPlayer(java.util.UUID.fromString(m));
                        if (o != null) o.sendMessage(Ui.info("전리품 나누기: " + mode.label));
                    }
                }
                case "말", "chat" -> {
                    String msg = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
                    for (String m : s.parties.members(id)) {
                        Player o = Bukkit.getPlayer(java.util.UUID.fromString(m));
                        if (o != null) o.sendMessage(Ui.c("&b[파티] " + p.getName() + ": &f" + msg));
                    }
                }
                default -> {
                    var ms = s.parties.members(id);
                    if (ms.size() <= 1) p.sendMessage(Ui.c("&7파티가 없습니다 — /파티 초대 <이름> · 수락 · 나가기 · 분배 · 말 <내용>"));
                    else {
                        StringBuilder b = new StringBuilder("&b파티 (" + ms.size() + "/" + io.versaera.domain.party.Parties.MAX + "): ");
                        String leader = s.parties.leader(id).orElse(id);
                        for (String m : ms) {
                            Player o = Bukkit.getPlayer(java.util.UUID.fromString(m));
                            b.append(m.equals(leader) ? "&e★" : "&f").append(o == null ? "?" : o.getName()).append(" ");
                        }
                        p.sendMessage(Ui.c(b.toString()));
                    }
                }
            }
        } catch (io.versaera.domain.common.DomainException ex) {
            p.sendMessage(Ui.error(ex.getMessage()));
        }
    }
}
