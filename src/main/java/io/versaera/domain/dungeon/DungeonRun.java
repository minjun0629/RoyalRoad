package io.versaera.domain.dungeon;

import io.versaera.domain.common.DomainException;

import java.util.*;

/**
 * 던전 한 판의 상태 기계 (순수). 서버가 판단한다:
 * 전투방은 몬스터를 다 잡아야 문이 열리고, 보스 문은 길 위의 방을 모두 정리하고 퍼즐을 풀어야 열린다.
 * 모두 죽거나 시간이 다 되면 실패.
 */
public final class DungeonRun {
    public enum State { ACTIVE, CLEARED, FAILED }

    private final DungeonLayout layout;
    private final LeverPuzzle puzzle;
    private final Set<Integer> cleared = new HashSet<>();
    private final Set<String> alive;
    private final long deadline;
    private boolean hiddenFound;
    private State state = State.ACTIVE;

    public DungeonRun(DungeonLayout layout, Collection<String> members, long now, long timeLimitMs, int levers) {
        DomainException.require(!members.isEmpty(), "dungeon.empty", "인원이 없습니다");
        this.layout = layout;
        this.puzzle = new LeverPuzzle(layout.seed(), levers);
        this.alive = new LinkedHashSet<>(members);
        this.deadline = now + timeLimitMs;
        cleared.add(layout.room(DungeonLayout.Kind.START).id());
    }

    public DungeonLayout layout() { return layout; }
    public LeverPuzzle puzzle() { return puzzle; }
    public State state() { return state; }
    public boolean hiddenFound() { return hiddenFound; }
    public long deadline() { return deadline; }

    public boolean isCleared(int room) {
        return cleared.contains(room);
    }

    private void active() {
        DomainException.require(state == State.ACTIVE, "dungeon.over", "이미 끝난 던전입니다");
    }

    /** 방의 문이 열려 있나: 이웃 방 중 하나가 정리되어 있어야 들어갈 수 있다 */
    public boolean canEnter(int room) {
        if (cleared.contains(room)) return true;
        DungeonLayout.Room r = layout.rooms().get(room);
        if (r.kind() == DungeonLayout.Kind.BOSS && !bossUnlocked()) return false;
        if (r.kind() == DungeonLayout.Kind.HIDDEN) return hiddenFound;
        for (int n : layout.neighbours(room, false)) if (cleared.contains(n)) return true;
        return false;
    }

    public void roomCleared(int room, long now) {
        tick(now);
        active();
        DungeonLayout.Room r = layout.rooms().get(room);
        DomainException.require(r.kind() != DungeonLayout.Kind.BOSS, "dungeon.boss", "보스방은 보스를 쓰러뜨려야 합니다");
        DomainException.require(canEnter(room), "dungeon.locked", "아직 들어갈 수 없는 방입니다");
        if (r.kind() == DungeonLayout.Kind.PUZZLE) DomainException.require(puzzle.solved(), "dungeon.puzzle", "퍼즐을 먼저 풀어야 합니다");
        cleared.add(room);
    }

    public LeverPuzzle.Result pull(int lever, long now) {
        tick(now);
        active();
        return puzzle.pull(lever);
    }

    /** 숨은 벽을 부숨 — 숨은 방과 이어진 방이 정리되어 있어야 한다 */
    public boolean revealHidden(int fromRoom, long now) {
        tick(now);
        active();
        DungeonLayout.Room h = layout.room(DungeonLayout.Kind.HIDDEN);
        if (!cleared.contains(fromRoom) || !layout.neighbours(h.id(), true).contains(fromRoom)) return false;
        hiddenFound = true;
        return true;
    }

    public boolean bossUnlocked() {
        for (int id : layout.mainPath()) {
            DungeonLayout.Kind k = layout.rooms().get(id).kind();
            if (k != DungeonLayout.Kind.BOSS && !cleared.contains(id)) return false;
        }
        return puzzle.solved();
    }

    public void bossDefeated(long now) {
        tick(now);
        active();
        DomainException.require(bossUnlocked(), "dungeon.locked", "보스방이 아직 잠겨 있습니다");
        cleared.add(layout.room(DungeonLayout.Kind.BOSS).id());
        state = State.CLEARED;
    }

    public void memberDown(String uuid, long now) {
        alive.remove(uuid);
        tick(now);
        if (alive.isEmpty() && state == State.ACTIVE) state = State.FAILED;
    }

    public void tick(long now) {
        if (state == State.ACTIVE && now > deadline) state = State.FAILED;
    }
}
