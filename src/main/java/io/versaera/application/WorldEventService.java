package io.versaera.application;

import io.versaera.application.port.TxRunner;
import io.versaera.application.port.WorldEventRepository;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.worldevent.WorldEventClock;
import io.versaera.domain.worldevent.WorldEventDefinition;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.*;

/**
 * 월드 이벤트 (EVT-01). 시간표는 WorldEventClock (서버 시드), 시작 · 끝 알림은 world_event_state 로 한 번씩만.
 * 효과는 지역(과 그 하위 지역)에만: 채집 추가량 · 위험도 · 입구 드러남 · 상점 할인 · 희귀 생물.
 */
public final class WorldEventService {
    public record Change(WorldEventDefinition def, boolean started) {}

    private final TxRunner tx;
    private final WorldEventRepository repo;
    private final Map<String, WorldEventDefinition> defs = new LinkedHashMap<>();
    private final RegionIndex regions;
    private final EventBus bus;
    private final GameClock clock;
    private volatile WorldEventClock schedule;

    public WorldEventService(TxRunner tx, WorldEventRepository repo, Collection<WorldEventDefinition> list, RegionIndex regions, EventBus bus,
                             GameClock clock, long seed) {
        this.tx = tx;
        this.repo = repo;
        this.regions = regions;
        this.bus = bus;
        this.clock = clock;
        for (WorldEventDefinition d : list) {
            if (defs.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("이벤트 id 중복: " + d.id());
            if (regions.byId(d.region()) == null) throw new IllegalArgumentException(d.id() + ": 없는 지역 " + d.region());
        }
        reseed(seed);
    }

    /** 서버 비밀 시드 (플러그인이 secret.key 에서 만든다) — 다른 서버의 시간표로 미리 계산할 수 없게 */
    public void reseed(long seed) {
        this.schedule = new WorldEventClock(seed, 0);
    }

    public Collection<WorldEventDefinition> all() {
        return Collections.unmodifiableCollection(defs.values());
    }

    public WorldEventClock clock() {
        return schedule;
    }

    public boolean active(String eventId) {
        WorldEventDefinition d = defs.get(eventId);
        return d != null && schedule.activeAt(d, clock.nowMillis()) != null;
    }

    public List<WorldEventDefinition> activeNow() {
        long now = clock.nowMillis();
        return defs.values().stream().filter(d -> schedule.activeAt(d, now) != null).toList();
    }

    /** region 이 이벤트 지역이거나 그 하위 지역인가 */
    private boolean within(String region, String eventRegion) {
        for (Region r = region == null ? null : regions.byId(region); r != null; r = r.parent() == null ? null : regions.byId(r.parent()))
            if (r.id().equals(eventRegion)) return true;
        return false;
    }

    private List<WorldEventDefinition> activeIn(String region) {
        return activeNow().stream().filter(d -> within(region, d.region())).toList();
    }

    public int gatherBonus(String region, String discipline) {
        int sum = 0;
        for (WorldEventDefinition d : activeIn(region)) sum += Integer.parseInt(d.effects().getOrDefault("gather_bonus." + discipline, "0"));
        return sum;
    }

    public int dangerBonus(String region) {
        int sum = 0;
        for (WorldEventDefinition d : activeIn(region)) sum += Integer.parseInt(d.effects().getOrDefault("danger", "0"));
        return sum;
    }

    public double shopDiscount(String region) {
        double sum = 0;
        for (WorldEventDefinition d : activeIn(region)) sum += Double.parseDouble(d.effects().getOrDefault("shop_discount", "0"));
        return sum;
    }

    /** 지금 드러나 있는 것 ("region:buried_city" 등) */
    public Set<String> revealed() {
        Set<String> out = new HashSet<>();
        for (WorldEventDefinition d : activeNow()) if (d.effects().containsKey("reveal")) out.add(d.effects().get("reveal"));
        return out;
    }

    /** NPC 예보: 그 NPC 가 알려 줄 수 있는 다음 이벤트와 시작 시각 */
    public Map<WorldEventDefinition, Long> forecastBy(String npcId) {
        Map<WorldEventDefinition, Long> out = new LinkedHashMap<>();
        long now = clock.nowMillis();
        for (WorldEventDefinition d : defs.values()) {
            if (!npcId.equals(d.forecaster())) continue;
            long t = schedule.forecast(d, now);
            if (t > 0) out.put(d, t);
        }
        return out;
    }

    /** 주기적으로 (1분 정도) — 시작 · 끝이 바뀐 이벤트를 한 번씩 알린다 */
    public List<Change> tick() {
        long now = clock.nowMillis();
        List<Change> changes = new ArrayList<>();
        tx.inTx(() -> {
            Map<String, Boolean> known = repo.states();
            for (WorldEventDefinition d : defs.values()) {
                WorldEventClock.Window w = schedule.activeAt(d, now);
                boolean on = w != null;
                if (known.getOrDefault(d.id(), false) == on) continue;
                repo.set(d.id(), on, on ? w.start() : 0, schedule.next(d, now).start());
                changes.add(new Change(d, on));
            }
            return null;
        });
        for (Change c : changes) bus.publish(new GameEvents.WorldEventChanged(c.def().id(), c.started()));
        return changes;
    }
}
