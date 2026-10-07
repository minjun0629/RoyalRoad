package io.versaera.domain.npc;

import io.versaera.domain.common.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 주민 직업 틀 (content/npc_population.yml, NPC-03). 직업마다 하는 일 · 일과 · 파는 물건 · 사는 물건 · 지역 경제에 더하는 공급/수요 ·
 * 관계 단계별 대사 · 맡기는 의뢰가 다르다.
 *
 * @param services SHOP · REPAIR · INN · HEAL · TRAIN · RUMOR · SONG · AUCTION · LORE · QUEST
 * @param lines    [낯섦, 보통, 가까움] 한 줄 대사
 */
public record Archetype(String id, String job, String category, Set<String> services, Set<String> likes, Set<String> dislikes,
                        List<String> personalities, List<String> schedule, List<String> stock, Set<String> buys, Set<String> produces,
                        Set<String> consumes, List<String> lines, int minLevel, int maxLevel, boolean evil, String trains,
                        List<QuestTemplate> quests, List<String> gifts) {
    public static final Set<String> SERVICES = Set.of("SHOP", "REPAIR", "INN", "HEAL", "TRAIN", "RUMOR", "SONG", "AUCTION", "LORE", "QUEST", "STABLE", "CARRIAGE", "SHIP", "GIFT");
    public static final Set<String> PLACES = Set.of("work", "home", "market", "tavern", "square", "temple", "gate", "yard");

    /**
     * 의뢰 틀. target: "item:&lt;id&gt;" · "kill:&lt;엔티티|any&gt;" · "partner" · "ruin" · "craft:&lt;분야&gt;" · "train:&lt;dummy|target&gt;"(허수아비 치기 · 과녁 맞히기)
     */
    public record QuestTemplate(String target, int amount, long money, Map<String, Integer> xp, int affinity, boolean daily, boolean hidden,
                                String title, String label) {
        public QuestTemplate {
            DomainException.require(target != null && (target.startsWith("item:") || target.startsWith("kill:") || target.equals("partner")
                    || target.equals("ruin") || target.startsWith("craft:") || target.startsWith("train:")), "arch.bad_quest", "의뢰 대상 형식: " + target);
            DomainException.require(amount >= 1 && money >= 0, "arch.bad_quest", "의뢰 수치: " + title);
            xp = Map.copyOf(xp == null ? Map.of() : xp);
        }
    }

    public Archetype {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "arch.bad_id", "직업 틀 id: " + id);
        services = Set.copyOf(services == null ? Set.of() : services);
        for (String sv : services) DomainException.require(SERVICES.contains(sv), "arch.bad_service", "없는 일: " + sv + " (" + id + ")");
        DomainException.require(!services.contains("SHOP") || (stock != null && !stock.isEmpty()), "arch.no_stock", "상점인데 파는 물건이 없음: " + id);
        DomainException.require(!services.contains("TRAIN") || trains != null, "arch.no_train", "지도하는데 분야가 없음: " + id);
        gifts = List.copyOf(gifts == null ? List.of() : gifts);
        DomainException.require(!services.contains("GIFT") || !gifts.isEmpty(), "arch.no_gift", "주는데 줄 물건이 없음: " + id);
        for (String g : gifts) DomainException.require(g.split(":").length == 3, "arch.bad_gift", "선물 형식 \"아이템:품질:개수\" 또는 \"minecraft:아이템:개수\": " + g);
        likes = Set.copyOf(likes == null ? Set.of() : likes);
        dislikes = Set.copyOf(dislikes == null ? Set.of() : dislikes);
        personalities = List.copyOf(personalities == null || personalities.isEmpty() ? List.of("말수가 적음") : personalities);
        schedule = List.copyOf(schedule == null ? List.of() : schedule);
        for (String sc : schedule) {
            String place = sc.substring(sc.indexOf(':') + 1);
            DomainException.require(PLACES.contains(place), "arch.bad_place", "없는 장소 키: " + place + " (" + id + ")");
        }
        stock = List.copyOf(stock == null ? List.of() : stock);
        buys = Set.copyOf(buys == null ? Set.of() : buys);
        produces = Set.copyOf(produces == null ? Set.of() : produces);
        consumes = Set.copyOf(consumes == null ? Set.of() : consumes);
        lines = List.copyOf(lines == null || lines.size() < 3 ? List.of("...", "...", "...") : lines);
        DomainException.require(minLevel >= 1 && maxLevel >= minLevel, "arch.bad_level", "레벨 범위: " + id);
        quests = List.copyOf(quests == null ? List.of() : quests);
    }

    public boolean offers(String service) {
        return services.contains(service);
    }
}
