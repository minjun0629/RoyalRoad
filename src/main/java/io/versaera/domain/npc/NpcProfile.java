package io.versaera.domain.npc;

import java.util.List;

/**
 * 주민 한 명의 세계 속 자리 (NPC-03): 직업 틀 · 레벨 · 가족 · 다른 NPC 와의 관계 · 아는 소문 · 떠돌이 경로 · 희귀 출현 조건 · 숨은 의뢰.
 * 손으로 만든 NPC(npcs.yml)는 프로필이 없을 수 있다 — 그때는 이름 · 직업 · 일과만 쓴다.
 *
 * @param family    성 (가족이 있으면) — 같은 성 + 같은 household = 한 집
 * @param links     다른 NPC 와의 관계 (SPOUSE · PARENT · CHILD · SIBLING · PARTNER · MASTER · APPRENTICE · RIVAL · LORD · VASSAL)
 * @param rumors    이 NPC 가 아는 소문: 지역 id (관계가 '관심' 이상이면 들려준다)
 * @param route     떠돌이: 차례로 도는 도시 지역 id (비면 한 자리에 산다)
 * @param rare      희귀 출현 조건 (null = 늘 있음)
 */
public record NpcProfile(String id, String archetype, int level, String family, String household, List<Link> links, List<String> rumors,
                         List<String> route, double speed, Rare rare, List<String> hiddenQuests, String trains, String line) {
    public record Link(String npc, String type) {}

    /** 게임 시각 hourFrom ~ hourTo, everyDays 일마다 (offset 날) 에만 나타난다 */
    public record Rare(int hourFrom, int hourTo, int everyDays, int offset) {
        public boolean present(long epochDay, int hour) {
            if (Math.floorMod(epochDay, everyDays) != offset) return false;
            return hourFrom <= hourTo ? hour >= hourFrom && hour < hourTo : hour >= hourFrom || hour < hourTo;
        }
    }

    public NpcProfile {
        links = List.copyOf(links == null ? List.of() : links);
        rumors = List.copyOf(rumors == null ? List.of() : rumors);
        route = List.copyOf(route == null ? List.of() : route);
        hiddenQuests = List.copyOf(hiddenQuests == null ? List.of() : hiddenQuests);
    }

    public boolean wanderer() {
        return route.size() >= 2;
    }
}
