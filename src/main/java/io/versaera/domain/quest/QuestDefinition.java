package io.versaera.domain.quest;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.hidden.Condition;

import java.util.List;
import java.util.Map;

/**
 * 퀘스트 (content/quests.yml) — ORIGINAL. 원작 퀘스트 난이도 등급(F ~ S 등)은 자료로 확인하지 못해(RESEARCH_REQUIRED)
 * 이 게임만의 단계 일상 · 숙련 · 험로 · 전설을 쓴다.
 *
 * @param hidden  true 면 목록에 보이지 않고, 히든 해금 · 발견 기록("quest:<id>")이 생겨야 받을 수 있다
 * @param daily   하루 한 번 다시 받을 수 있음
 */
public record QuestDefinition(String id, String title, String giver, Grade grade, Condition requires, List<String> after, boolean hidden,
                              boolean daily, List<Objective> objectives, Reward reward, List<Choice> choices, String source) {
    public enum Grade { DAILY("일상"), SKILLED("숙련"), PERILOUS("험로"), LEGEND("전설");
        public final String label;

        Grade(String label) { this.label = label; }
    }

    public enum Type { KILL, GATHER, CRAFT, DISCOVER, TALK, DELIVER, AFFINITY, DUNGEON, BOSS }

    /**
     * @param target  KILL: 엔티티 종류(ZOMBIE …) 또는 "any" · GATHER: 아이템 종류 · CRAFT: 레시피 id 또는 "discipline:<분야>"
     *                · DISCOVER: "kind:ref" · TALK/AFFINITY: NPC · DELIVER: 아이템 종류 · DUNGEON/BOSS: id
     */
    public record Objective(Type type, String target, int amount, int minQuality, String label) {
        public Objective {
            DomainException.require(amount >= 1, "quest.bad_amount", "목표 수가 잘못되었습니다");
        }

        public boolean matches(Type t, String what) {
            if (t != type) return false;
            if (type == Type.KILL) return target.equals("any") || target.equalsIgnoreCase(what);
            if (type == Type.CRAFT && target.startsWith("discipline:")) return what.startsWith("discipline:") ? what.equals(target) : false;
            return target.equals(what);
        }
    }

    /** items: "type:quality:amount" · xp: 분야 → 경험치 · affinity: NPC → 호감 · reputation: 세력 → 평판 */
    public record Reward(long money, List<String> items, Map<String, Integer> xp, Map<String, Integer> affinity, Map<String, Integer> reputation,
                         int fame, List<String> unlocks) {
        public Reward {
            DomainException.require(money >= 0 && fame >= 0, "quest.bad_reward", "보상이 잘못되었습니다");
            items = List.copyOf(items == null ? List.of() : items);
            xp = Map.copyOf(xp == null ? Map.of() : xp);
            affinity = Map.copyOf(affinity == null ? Map.of() : affinity);
            reputation = Map.copyOf(reputation == null ? Map.of() : reputation);
            unlocks = List.copyOf(unlocks == null ? List.of() : unlocks);
        }

        public static final Reward NONE = new Reward(0, null, null, null, null, 0, null);
    }

    /** 끝낼 때 고르는 선택지 — 보상과 다음에 열리는 퀘스트가 달라진다 */
    public record Choice(String id, String label, Reward reward) {}

    public QuestDefinition {
        DomainException.require(id != null && id.matches("[a-z0-9_.]+"), "quest.bad_id", "퀘스트 id 형식이 잘못되었습니다: " + id);
        DomainException.require(objectives != null && !objectives.isEmpty(), "quest.no_objectives", "목표가 없습니다: " + id);
        after = List.copyOf(after == null ? List.of() : after);
        objectives = List.copyOf(objectives);
        choices = List.copyOf(choices == null ? List.of() : choices);
        reward = reward == null ? Reward.NONE : reward;
    }
}
