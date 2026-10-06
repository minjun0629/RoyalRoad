package io.versaera.domain.combat;

import java.util.*;

/**
 * 엔티티별 상태 이상 (순수 계산). 플랫폼이 0.5초마다 tick 을 불러 지속 피해 · 만료를 처리한다.
 * 기절 · 빙결은 같은 대상에게 연달아 걸면 점점 짧아진다 (무한 기절 방지: 10초 안에 다시 걸리면 절반).
 */
public final class StatusTracker {
    public record Applied(StatusEffect effect, long until, double power) {}

    public record Tick(UUID target, StatusEffect effect, double damagePct) {}

    private final Map<UUID, EnumMap<StatusEffect, Applied>> active = new HashMap<>();
    private final Map<UUID, Long> lastControl = new HashMap<>();
    private final Map<UUID, Long> lastTick = new HashMap<>();

    public long apply(UUID target, StatusEffect e, int seconds, double power, long now) {
        long dur = seconds * 1000L;
        if (e.disables || e == StatusEffect.FREEZE) {
            Long last = lastControl.get(target);
            if (last != null && now - last < 10_000) dur /= 2;
            lastControl.put(target, now);
        }
        EnumMap<StatusEffect, Applied> m = active.computeIfAbsent(target, k -> new EnumMap<>(StatusEffect.class));
        Applied old = m.get(e);
        long until = Math.max(old == null ? 0 : old.until(), now + dur);
        m.put(e, new Applied(e, until, Math.max(power, old == null ? 0 : old.power())));
        return dur;
    }

    public boolean has(UUID target, StatusEffect e, long now) {
        Applied a = active.getOrDefault(target, new EnumMap<>(StatusEffect.class)).get(e);
        return a != null && a.until() > now;
    }

    public boolean disabled(UUID target, long now) {
        return has(target, StatusEffect.STUN, now) || has(target, StatusEffect.FREEZE, now);
    }

    public Collection<Applied> of(UUID target) {
        return active.getOrDefault(target, new EnumMap<>(StatusEffect.class)).values();
    }

    /** 지속 피해 계산 + 만료 정리. 같은 대상은 0.5초에 한 번만 피해 */
    public List<Tick> tick(long now) {
        List<Tick> out = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, EnumMap<StatusEffect, Applied>>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, EnumMap<StatusEffect, Applied>> en = it.next();
            long prev = lastTick.getOrDefault(en.getKey(), now - 500);
            double secs = Math.min(1.0, Math.max(0, (now - prev) / 1000.0));
            lastTick.put(en.getKey(), now);
            for (Iterator<Applied> ai = en.getValue().values().iterator(); ai.hasNext(); ) {
                Applied a = ai.next();
                if (a.until() <= now) { ai.remove(); continue; }
                if (a.effect().tickPct > 0 && secs > 0) out.add(new Tick(en.getKey(), a.effect(), a.effect().tickPct * a.power() * secs));
            }
            if (en.getValue().isEmpty()) {
                it.remove();
                lastTick.remove(en.getKey());
            }
        }
        lastControl.values().removeIf(t -> now - t > 60_000);
        return out;
    }

    public void forget(UUID target) {
        active.remove(target);
        lastTick.remove(target);
        lastControl.remove(target);
    }
}
