package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.gathering.ResourceNode;
import io.versaera.domain.quest.QuestDefinition;
import io.versaera.domain.skill.StatEffects;

import java.util.*;

/**
 * 채집 · 채광 · 벌목 · 낚시의 결과 (GTH-01). 플랫폼은 블록 · 낚시 이벤트만 넘기고 계산은 여기서 한다:
 * 숙련 → 기본 결과, 생활 직업(gather_bonus.&lt;분야&gt;) · 월드 이벤트 → 추가 개수, 항해감 스탯 → 낚시 숙련 보정.
 */
public final class GatheringService {
    public record Result(String typeId, int amount, int quality, int bonus) {}

    private final GameServices s;
    private final Map<String, ResourceNode> nodes = new LinkedHashMap<>();

    GatheringService(GameServices s, Collection<ResourceNode> list) {
        this.s = s;
        for (ResourceNode n : list) nodes.put(n.id(), n);
    }

    public ResourceNode node(String id) {
        ResourceNode n = nodes.get(id);
        if (n == null) throw DomainException.of("gather.unknown", "없는 자원: " + id);
        return n;
    }

    public Collection<ResourceNode> all() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /** @return 숙련이 모자라면 null */
    public Result gather(String uuid, String nodeId, String region, Set<String> regionTags, long seed) {
        ResourceNode n = node(nodeId);
        int level = s.growth.level(uuid, n.discipline());
        if (n.discipline().equals("fishing")) level += StatEffects.of(s.growth.statPoints(uuid)).fishingBonusLevels();
        if (level < n.minLevel()) return null;
        ResourceNode.Gather g = n.gather(Math.min(level, io.versaera.domain.skill.Mastery.MAX_LEVEL), regionTags, new SplittableRandom(seed));
        int bonus = (int) Math.round(s.jobs.perks(uuid).getOrDefault("gather_bonus." + n.discipline(), 0.0)) + s.worldEvents.gatherBonus(region, n.discipline());
        int amount = g.amount() + Math.max(0, bonus);
        s.items.deliverBulk(uuid, n.yield(), g.quality(), amount, "gather:" + n.id());
        s.growth.addXp(uuid, n.discipline(), n.xp(), n.actionLevel());
        s.growth.record(uuid, "gather." + n.discipline(), 1);
        s.quests.record(uuid, QuestDefinition.Type.GATHER, n.yield(), amount, g.quality());
        return new Result(n.yield(), amount, g.quality(), Math.max(0, bonus));
    }
}
