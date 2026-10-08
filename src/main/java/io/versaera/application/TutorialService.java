package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.quest.QuestProgress;

import java.util.Comparator;
import java.util.List;

/**
 * 튜토리얼 (처음 30 ~ 60 분) — content/original/quests.yml 의 "tutorial." 의뢰를 차례로.
 * NPC 없이 저절로 받고, 목표를 다 채우면 저절로 끝내고(보상) 다음 단계를 연다. 시작 도시를 고른 사람에게만.
 * DB 스레드 전용 (플랫폼 TutorialRuntime 이 몇 초마다 부른다).
 */
public final class TutorialService {
    public static final String PREFIX = "tutorial.";

    /**
     * @param index     지금 단계 (0 부터, 끝났으면 steps)
     * @param current   지금 의뢰 (끝났으면 null)
     * @param progress  목표마다 채운 수
     * @param finished  이번에 끝낸 단계 (없으면 null) · started 이번에 새로 연 단계
     */
    public record View(int index, int steps, QuestDefinition current, int[] progress, QuestDefinition finished, QuestDefinition started,
                       QuestDefinition.Reward reward) {
        public boolean done() {
            return current == null;
        }
    }

    private static final PlayerFacts NO_FACTS = new PlayerFacts() {
        public long counter(String key) { return 0; }
        public int mastery(String discipline) { return 1; }
        public int affinity(String npcId) { return 0; }
        public String region() { return null; }
        public boolean discovered(String kind, String ref) { return false; }
        public int hour() { return 12; }
    };

    private final GameServices s;
    private final List<QuestDefinition> steps;

    TutorialService(GameServices s) {
        this.s = s;
        this.steps = s.quests.all().stream().filter(q -> q.id().startsWith(PREFIX)).sorted(Comparator.comparing(QuestDefinition::id)).toList();
    }

    public List<QuestDefinition> steps() {
        return steps;
    }

    /** 건너뛰었나 */
    public boolean skipped(String uuid) {
        return s.growth.counter(uuid, "tutorial.skipped") > 0;
    }

    public void skip(String uuid) {
        DomainException.require(!skipped(uuid), "tutorial.skipped", "이미 건너뛰었습니다");
        s.growth.record(uuid, "tutorial.skipped", 1);
        for (QuestDefinition q : steps) if ("ACTIVE".equals(s.quests.state(uuid, q.id()))) s.quests.abandon(uuid, q.id());
    }

    /**
     * 한 번 살펴본다: 시작 안 했으면 첫 단계를 열고, 지금 단계를 다 채웠으면 끝내고 다음을 연다.
     * @param name 보상 기록에 쓰는 이름
     */
    public View tick(String uuid, String name) {
        if (steps.isEmpty() || skipped(uuid) || s.origins.character(uuid).isEmpty()) return new View(steps.size(), steps.size(), null, new int[0], null, null, null);
        QuestDefinition finished = null, started = null;
        QuestDefinition.Reward reward = null;
        for (int i = 0; i < steps.size(); i++) {
            QuestDefinition q = steps.get(i);
            String st = s.quests.state(uuid, q.id());
            if ("COMPLETED".equals(st)) continue;
            if (st == null) {
                s.quests.unlockAndAccept(uuid, q.id(), NO_FACTS);
                started = q;
            }
            QuestProgress p = progress(uuid, q);
            if (p != null && p.done(q)) {
                reward = s.quests.complete(uuid, name, q.id(), null, List.of());
                finished = q;
                if (i + 1 < steps.size()) {
                    QuestDefinition next = steps.get(i + 1);
                    if (s.quests.state(uuid, next.id()) == null) {
                        s.quests.unlockAndAccept(uuid, next.id(), NO_FACTS);
                        started = next;
                    }
                    QuestProgress np = progress(uuid, next);
                    return new View(i + 1, steps.size(), next, counts(next, np), finished, started, reward);
                }
                return new View(steps.size(), steps.size(), null, new int[0], finished, null, reward);
            }
            return new View(i, steps.size(), q, counts(q, p), null, started, null);
        }
        return new View(steps.size(), steps.size(), null, new int[0], null, null, null);
    }

    private QuestProgress progress(String uuid, QuestDefinition q) {
        for (QuestService.Active a : s.quests.active(uuid)) if (a.def().id().equals(q.id())) return a.progress();
        return null;
    }

    private static int[] counts(QuestDefinition q, QuestProgress p) {
        int[] out = new int[q.objectives().size()];
        for (int i = 0; i < out.length; i++) out[i] = p == null ? 0 : Math.min(q.objectives().get(i).amount(), p.get(i));
        return out;
    }
}
