package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.QuestRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.quest.QuestProgress;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/**
 * 퀘스트. 진행은 행동(처치 · 채집 · 제작 · 발견 · 대화 · 던전 · 보스)이 들어올 때마다 맞는 목표에 쌓이고,
 * 완료 보상은 idempotency key("quest:<id>:<uuid>:<횟수>")로 한 번만 지급된다.
 * 납품(DELIVER)은 플랫폼이 인벤토리에서 먼저 빼서 넘기고, 완료가 실패하면 배달함으로 돌려준다.
 */
public final class QuestService {
    public record Active(QuestDefinition def, QuestProgress progress) {}

    private final TxRunner tx;
    private final QuestRepository repo;
    private final ProgressRepository progressRepo;
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private final GameServices s;
    private final EventBus bus;
    private final GameClock clock;
    private final ZoneId zone;

    QuestService(TxRunner tx, QuestRepository repo, ProgressRepository progressRepo, Collection<QuestDefinition> defs, GameServices s, EventBus bus,
                 GameClock clock, ZoneId zone) {
        this.tx = tx;
        this.repo = repo;
        this.progressRepo = progressRepo;
        for (QuestDefinition q : defs) if (quests.putIfAbsent(q.id(), q) != null) throw new IllegalArgumentException("퀘스트 id 중복: " + q.id());
        for (QuestDefinition q : defs) {
            for (String a : q.after()) if (!quests.containsKey(a)) throw new IllegalArgumentException(q.id() + ": 없는 선행 퀘스트 " + a);
            if (q.giver() != null) s.relations.npc(q.giver());
            for (QuestDefinition.Objective o : q.objectives()) if (o.type() == QuestDefinition.Type.DELIVER || o.type() == QuestDefinition.Type.GATHER)
                s.items.types().get(o.target());
            checkReward(q.reward(), q.id(), s);
            for (QuestDefinition.Choice c : q.choices()) checkReward(c.reward(), q.id(), s);
        }
        this.s = s;
        this.bus = bus;
        this.clock = clock;
        this.zone = zone;
    }

    static void checkReward(QuestDefinition.Reward r, String id, GameServices s) {
        for (String it : r.items()) s.items.types().get(it.split(":")[0]);
        for (String d : r.xp().keySet()) s.growth.discipline(d);
        for (String n : r.affinity().keySet()) s.relations.npc(n);
    }

    public QuestDefinition quest(String id) {
        QuestDefinition q = quests.get(id);
        if (q == null) throw DomainException.of("quest.unknown", "없는 퀘스트: " + id);
        return q;
    }

    public Collection<QuestDefinition> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    private long day(long ms) {
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay();
    }

    private boolean completed(String uuid, String id) {
        return repo.find(uuid, id).map(r -> r.times() > 0).orElse(false);
    }

    /** 지금 받을 수 있는 퀘스트 (giver 가 null 이 아니면 그 NPC 의 것만) */
    public List<QuestDefinition> available(String uuid, String giver, PlayerFacts f) {
        List<QuestDefinition> out = new ArrayList<>();
        for (QuestDefinition q : quests.values()) {
            if (giver != null && !giver.equals(q.giver())) continue;
            if (canAccept(uuid, q, f) == null) out.add(q);
        }
        return out;
    }

    /** 받을 수 없는 이유 (받을 수 있으면 null) */
    private String canAccept(String uuid, QuestDefinition q, PlayerFacts f) {
        if (q.hidden() && !progressRepo.discovered(uuid, "quest", q.id())) return "아직 알지 못하는 일입니다";
        Optional<QuestRepository.Row> row = repo.find(uuid, q.id());
        if (row.isPresent()) {
            QuestRepository.Row r = row.get();
            if (r.state().equals("ACTIVE")) return "이미 진행 중입니다";
            if (!q.daily() && r.times() > 0) return "이미 끝낸 일입니다";
            if (r.completedAt() != null && day(r.completedAt()) == day(clock.nowMillis())) return "오늘은 이미 했습니다";
        }
        for (String a : q.after()) if (!completed(uuid, a)) return "먼저 해야 할 일이 있습니다";
        if (q.requires() != null && !q.requires().test(f)) return "아직 맡을 수 없습니다";
        return null;
    }

    public void accept(String uuid, String questId, PlayerFacts f) {
        QuestDefinition q = quest(questId);
        tx.inTx(() -> {
            String why = canAccept(uuid, q, f);
            if (why != null) throw DomainException.of("quest.cannot_accept", why);
            int times = repo.find(uuid, q.id()).map(QuestRepository.Row::times).orElse(0);
            repo.save(uuid, new QuestRepository.Row(q.id(), "ACTIVE", new QuestProgress(q.objectives().size()).encode(), null, times, clock.nowMillis(), null));
            return null;
        });
        refreshStatic(uuid, q);
    }

    public List<Active> active(String uuid) {
        List<Active> out = new ArrayList<>();
        for (QuestRepository.Row r : repo.all(uuid)) {
            if (!r.state().equals("ACTIVE") || !quests.containsKey(r.questId())) continue;
            QuestDefinition q = quests.get(r.questId());
            out.add(new Active(q, QuestProgress.decode(r.progress(), q.objectives().size())));
        }
        return out;
    }

    /** 행동 하나가 들어옴 (처치 · 채집 · 제작 · 대화 · 던전 · 보스 · 발견) */
    public void record(String uuid, QuestDefinition.Type type, String target, int amount, int quality) {
        for (Active a : active(uuid)) {
            boolean changed = false;
            for (int i = 0; i < a.def().objectives().size(); i++) {
                QuestDefinition.Objective o = a.def().objectives().get(i);
                if (o.type() == QuestDefinition.Type.DELIVER || o.type() == QuestDefinition.Type.AFFINITY) continue;
                if (!o.matches(type, target) || quality < o.minQuality()) continue;
                if (a.progress().add(i, amount, o.amount())) {
                    changed = true;
                    bus.publish(new GameEvents.QuestProgressed(uuid, a.def().id(), i, a.progress().get(i), o.amount()));
                }
            }
            if (changed) save(uuid, a);
        }
    }

    /** 이미 이룬 목표(발견 · 관계)는 받자마자 채운다 */
    private void refreshStatic(String uuid, QuestDefinition q) {
        for (Active a : active(uuid)) {
            if (!a.def().id().equals(q.id())) continue;
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestDefinition.Objective o = q.objectives().get(i);
                if (o.type() == QuestDefinition.Type.DISCOVER) {
                    int c = o.target().indexOf(':');
                    if (c > 0 && progressRepo.discovered(uuid, o.target().substring(0, c), o.target().substring(c + 1))) a.progress().set(i, o.amount(), o.amount());
                } else if (o.type() == QuestDefinition.Type.AFFINITY) {
                    a.progress().set(i, s.relations.affinity(uuid, o.target()), o.amount());
                }
            }
            save(uuid, a);
        }
    }

    private void save(String uuid, Active a) {
        tx.inTx(() -> {
            QuestRepository.Row r = repo.find(uuid, a.def().id()).orElseThrow();
            repo.save(uuid, new QuestRepository.Row(r.questId(), r.state(), a.progress().encode(), r.choice(), r.times(), r.acceptedAt(), r.completedAt()));
            return null;
        });
    }

    /** 납품 목표가 요구하는 재료 (플랫폼이 인벤토리에서 먼저 뺀다) */
    public List<QuestDefinition.Objective> deliveries(String questId) {
        return quest(questId).objectives().stream().filter(o -> o.type() == QuestDefinition.Type.DELIVER).toList();
    }

    /**
     * 완료. 실패하면 납품한 재료를 배달함으로 돌려주고 예외를 다시 던진다.
     * @param delivered 플랫폼이 이미 인벤토리에서 뺀 납품 재료
     */
    public QuestDefinition.Reward complete(String uuid, String name, String questId, String choiceId, List<MaterialInput> delivered) {
        boolean[] paid = {false};
        try {
            return doComplete(uuid, name, questId, choiceId, delivered, paid);
        } catch (RuntimeException e) {
            if (!paid[0]) for (MaterialInput m : delivered) s.items.deliverBulk(uuid, m.typeId(), m.quality(), m.count(), "quest_refund");
            throw e;
        }
    }

    private QuestDefinition.Reward doComplete(String uuid, String name, String questId, String choiceId, List<MaterialInput> delivered, boolean[] paid) {
        QuestDefinition q = quest(questId);
        refreshStatic(uuid, q);
        QuestDefinition.Choice choice = null;
        if (!q.choices().isEmpty()) {
            for (QuestDefinition.Choice c : q.choices()) if (c.id().equals(choiceId)) choice = c;
            if (choice == null) throw DomainException.of("quest.need_choice", "선택지를 골라야 합니다");
        }
        QuestDefinition.Choice picked = choice;
        AfterCommit after = new AfterCommit();
        tx.inTx(() -> {
            QuestRepository.Row r = repo.find(uuid, questId).orElseThrow(() -> DomainException.of("quest.not_active", "진행 중인 퀘스트가 아닙니다"));
            if (!r.state().equals("ACTIVE")) throw DomainException.of("quest.not_active", "진행 중인 퀘스트가 아닙니다");
            QuestProgress p = QuestProgress.decode(r.progress(), q.objectives().size());
            if (!p.done(q)) throw DomainException.of("quest.not_done", "아직 목표를 다 이루지 못했습니다");
            for (QuestDefinition.Objective o : deliveries(questId)) {
                int have = delivered.stream().filter(m -> m.typeId().equals(o.target()) && m.quality() >= o.minQuality()).mapToInt(MaterialInput::count).sum();
                if (have < o.amount()) throw DomainException.of("quest.missing_delivery", "납품할 물건이 모자랍니다: " + o.label());
            }
            int times = r.times() + 1;
            String key = "quest:" + questId + ":" + uuid + ":" + times;
            pay(uuid, name, q.reward(), key, after);
            if (picked != null) pay(uuid, name, picked.reward(), key + ":" + picked.id(), after);
            repo.save(uuid, new QuestRepository.Row(questId, "COMPLETED", p.encode(), picked == null ? null : picked.id(), times, r.acceptedAt(), clock.nowMillis()));
            s.audit.record("QUEST_COMPLETED", uuid, questId, picked == null ? "" : picked.id(), key);
            return null;
        });
        paid[0] = true;
        after.publish(bus);
        grantAfterCommit(uuid, q.reward());
        if (picked != null) grantAfterCommit(uuid, picked.reward());
        s.growth.record(uuid, "quest.completed", 1);
        bus.publish(new GameEvents.QuestCompleted(uuid, questId, picked == null ? null : picked.id()));
        return q.reward();
    }

    /** 경험치 · 호감 (커밋 뒤 — 각자 트랜잭션) */
    void grantAfterCommit(String uuid, QuestDefinition.Reward r) {
        for (var e : r.xp().entrySet()) s.growth.addXp(uuid, e.getKey(), e.getValue(), 1);
        for (var e : r.affinity().entrySet()) s.relations.adjust(uuid, e.getKey(), e.getValue());
    }

    /** 돈 · 아이템 · 평판 · 명성 · 해금 (트랜잭션 안) — 경험치 · 호감은 커밋 뒤. 던전 · 보스 보상도 이 경로를 쓴다 */
    void pay(String uuid, String name, QuestDefinition.Reward r, String key, AfterCommit after) {
        if (r.money() > 0) s.economy.depositInTx(uuid, r.money(), "quest", key, after);
        int n = 0;
        for (String it : r.items()) {
            String[] p = it.split(":");
            int q = Integer.parseInt(p[1]), amount = Integer.parseInt(p[2]);
            if (s.items.types().get(p[0]).category().unique())
                for (int i = 0; i < amount; i++) s.items.createInTx(p[0], q, null, "의뢰 보상", "quest", Map.of(), uuid, key + ":" + n++, after);
            else s.items.deliverBulk(uuid, p[0], q, amount, "quest");
        }
        for (var e : r.reputation().entrySet()) repo.addReputation(uuid, e.getKey(), e.getValue());
        if (r.fame() > 0) progressRepo.addCounter(uuid, "fame", r.fame());
        for (String u : r.unlocks()) {
            int c = u.indexOf(':');
            if (c > 0) progressRepo.discover(uuid, u.substring(0, c), u.substring(c + 1), clock.nowMillis());
        }
    }

    public Map<String, Integer> reputations(String uuid) {
        return repo.reputations(uuid);
    }

    public void abandon(String uuid, String questId) {
        tx.inTx(() -> {
            QuestRepository.Row r = repo.find(uuid, questId).orElseThrow(() -> DomainException.of("quest.not_active", "진행 중인 퀘스트가 아닙니다"));
            if (!r.state().equals("ACTIVE")) throw DomainException.of("quest.not_active", "진행 중인 퀘스트가 아닙니다");
            if (r.times() == 0) repo.save(uuid, new QuestRepository.Row(questId, "COMPLETED", r.progress(), null, 0, r.acceptedAt(), null));
            else repo.save(uuid, new QuestRepository.Row(questId, "COMPLETED", r.progress(), r.choice(), r.times(), r.acceptedAt(), r.completedAt()));
            return null;
        });
    }
}
