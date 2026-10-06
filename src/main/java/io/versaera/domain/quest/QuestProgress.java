package io.versaera.domain.quest;

import java.util.Arrays;

/** 목표별 진행 수 (DB 에는 "3,0,1" 처럼 저장) */
public final class QuestProgress {
    private final int[] counts;

    public QuestProgress(int objectives) {
        counts = new int[objectives];
    }

    public static QuestProgress decode(String s, int objectives) {
        QuestProgress p = new QuestProgress(objectives);
        if (s == null || s.isBlank()) return p;
        String[] parts = s.split(",");
        for (int i = 0; i < Math.min(parts.length, objectives); i++) {
            try {
                p.counts[i] = Math.max(0, Integer.parseInt(parts[i].strip()));
            } catch (NumberFormatException e) {
                p.counts[i] = 0;   // 손상된 값은 0 으로 (진행이 줄어들 뿐 보상이 늘지 않는 쪽)
            }
        }
        return p;
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(counts[i]);
        }
        return sb.toString();
    }

    public int get(int i) {
        return counts[i];
    }

    /** @return 실제로 바뀌었으면 true (목표치를 넘지 않음) */
    public boolean add(int i, int amount, int target) {
        int before = counts[i];
        counts[i] = Math.min(target, counts[i] + Math.max(0, amount));
        return counts[i] != before;
    }

    public void set(int i, int value, int target) {
        counts[i] = Math.max(0, Math.min(target, value));
    }

    public boolean done(QuestDefinition q) {
        for (int i = 0; i < counts.length; i++)
            if (q.objectives().get(i).type() != QuestDefinition.Type.DELIVER && counts[i] < q.objectives().get(i).amount()) return false;
        return true;
    }

    @Override
    public String toString() {
        return Arrays.toString(counts);
    }
}
