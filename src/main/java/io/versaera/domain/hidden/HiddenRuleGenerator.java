package io.versaera.domain.hidden;

import java.util.*;

/**
 * 서버마다 다른 히든 조건을 만든다 (HID-02, ORIGINAL). 조건 조합은 서버 비밀 시드로 정해져서
 * 다른 서버의 공략이나 이 저장소의 코드를 읽어도 이 서버의 조건을 알 수 없다 (봉인 파일로만 저장).
 * <ul>
 *   <li>규칙마다 서로 다른 종류의 조건 3개를 AND 로 묶는다: 행동 기록 · 장소 · 시간대/관계/숙련 중에서</li>
 *   <li>모든 조건은 실제로 쌓이는 기록 · 있는 지역 · 있는 NPC 만 쓴다 (불가능한 조건 금지)</li>
 *   <li>확률 조건은 없다. 소문은 조건 하나의 흐릿한 실마리만 담는다</li>
 * </ul>
 */
public final class HiddenRuleGenerator {
    /** @param counters 실제로 쌓이는 행동 기록 키 → 사람이 읽는 실마리 (예: "gather.fishing" → "물가에서 오래 낚싯대를 드리운") */
    public record Pools(Map<String, String> counters, Map<String, String> regions, Map<String, String> npcs, List<String> disciplines,
                        List<Map<String, String>> rewards) {}

    private HiddenRuleGenerator() {
    }

    /** 생성된 규칙을 hidden YAML 문자열로 (ContentLoader.hidden 이 읽는 형식) */
    public static String generate(long seed, int count, Pools pools) {
        if (pools.counters().size() < 3 || pools.regions().size() < 3 || pools.npcs().isEmpty() || pools.rewards().isEmpty())
            throw new IllegalArgumentException("재료가 모자랍니다");
        SplittableRandom r = new SplittableRandom(seed);
        List<String> counters = new ArrayList<>(new TreeSet<>(pools.counters().keySet()));
        List<String> regions = new ArrayList<>(new TreeSet<>(pools.regions().keySet()));
        List<String> npcs = new ArrayList<>(new TreeSet<>(pools.npcs().keySet()));
        StringBuilder y = new StringBuilder("# 서버 생성 히든 규칙 — 봉인 전용. 평문을 서버에 남기지 마세요.\nhidden:\n");
        Set<String> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String counter = counters.get(r.nextInt(counters.size()));
            String region = regions.get(r.nextInt(regions.size()));
            String sig = counter + "@" + region;
            if (!used.add(sig)) { i--; if (used.size() >= counters.size() * regions.size()) break; continue; }
            long amount = 20L * (1 + r.nextInt(10));   // 20 ~ 200 — 하루 이틀 꾸준히 하면 닿는 양
            List<String> parts = new ArrayList<>();
            parts.add("{counter: " + counter + ", at_least: " + amount + "}");
            parts.add("{region: " + region + "}");
            String third;
            int kind = r.nextInt(3);
            String hintThird;
            if (kind == 0) {
                int from = r.nextInt(24), to = (from + 3) % 24;
                third = "{hours: [" + from + ", " + to + "]}";
                hintThird = from >= 19 || from < 5 ? "밤" : from < 11 ? "아침" : "낮";
            } else if (kind == 1) {
                String npc = npcs.get(r.nextInt(npcs.size()));
                third = "{affinity: " + npc + ", at_least: " + (100 + 50 * r.nextInt(5)) + "}";
                hintThird = pools.npcs().get(npc) + "의 믿음";
            } else {
                String d = pools.disciplines().get(r.nextInt(pools.disciplines().size()));
                third = "{mastery: " + d + ", level: " + (5 + r.nextInt(16)) + "}";
                hintThird = "손에 익은 기술";
            }
            parts.add(third);
            Map<String, String> reward = pools.rewards().get(i % pools.rewards().size());
            String id = String.format("gen.r%02d_%04x", i, r.nextInt(0x10000));
            // 소문: 세 조건 중 하나만, 흐릿하게
            String rumor = switch (r.nextInt(3)) {
                case 0 -> pools.counters().get(counter) + " 사람이 무언가를 알아냈다고 한다.";
                case 1 -> pools.regions().get(region) + " 어딘가에서 이상한 일이 있었다고 한다.";
                default -> hintThird + "에 관한 이야기가 돈다.";
            };
            y.append("  ").append(id).append(":\n");
            y.append("    title: \"").append(reward.getOrDefault("label", "숨겨진 발견")).append("\"\n");
            y.append("    when: {all: [").append(String.join(", ", parts)).append("]}\n");
            y.append("    rumor: \"").append(rumor.replace("\"", "'")).append("\"\n");
            y.append("    reward: {");
            boolean first = true;
            for (Map.Entry<String, String> e : new TreeMap<>(reward).entrySet()) {
                if (e.getKey().equals("label")) continue;
                if (!first) y.append(", ");
                y.append(e.getKey()).append(": \"").append(e.getValue()).append('"');
                first = false;
            }
            y.append("}\n");
        }
        return y.toString();
    }
}
