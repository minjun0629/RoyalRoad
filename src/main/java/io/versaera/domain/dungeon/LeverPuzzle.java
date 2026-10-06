package io.versaera.domain.dungeon;

import java.util.SplittableRandom;

/**
 * 순서 퍼즐 (ORIGINAL). 레버 n 개를 맞는 순서로 당겨야 한다. 순서는 시드로 정해지고,
 * 방 벽에 새겨진 문양(단서)이 순서를 알려 준다 → 운이 아니라 관찰로 푸는 퍼즐. 틀리면 처음부터.
 */
public final class LeverPuzzle {
    private final int[] order;
    private int progress;

    public LeverPuzzle(long seed, int levers) {
        if (levers < 3 || levers > 8) throw new IllegalArgumentException("레버 3 ~ 8개");
        order = new int[levers];
        for (int i = 0; i < levers; i++) order[i] = i;
        SplittableRandom r = new SplittableRandom(seed ^ 0x5EEDL);
        for (int i = levers - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
    }

    /** 벽에 새길 단서: k 번째로 당길 레버 번호 */
    public int clue(int step) {
        return order[step];
    }

    public int size() {
        return order.length;
    }

    public enum Result { PROGRESS, WRONG, SOLVED, ALREADY }

    public Result pull(int lever) {
        if (progress == order.length) return Result.ALREADY;
        if (order[progress] == lever) {
            progress++;
            return progress == order.length ? Result.SOLVED : Result.PROGRESS;
        }
        progress = 0;
        return Result.WRONG;
    }

    public boolean solved() {
        return progress == order.length;
    }
}
