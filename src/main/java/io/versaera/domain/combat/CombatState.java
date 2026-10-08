package io.versaera.domain.combat;

import io.versaera.domain.common.DomainException;

import java.util.*;

/**
 * 플레이어 한 명의 전투 자원 · 쿨다운 · 콤보 입력 · 회피 무적 (순수 계산 — 시각은 밖에서 받는다).
 * <ul>
 *   <li>기력(STAMINA) 초당 12 · 마나(MANA) 초당 4 회복</li>
 *   <li>회피: 기력 25 · 0.4초 무적 · 0.8초 재사용 대기</li>
 *   <li>콤보: 약 · 강 입력 순서가 정의와 맞고 제한 시간 안이면 마무리 스킬</li>
 * </ul>
 */
public final class CombatState {
    public enum Input { LIGHT, HEAVY }

    public record Combo(String id, List<Input> sequence, long windowMs, String finisher) {
        public Combo {
            DomainException.require(sequence.size() >= 2 && windowMs >= 300, "combo.bad", "콤보 정의가 잘못되었습니다: " + id);
            sequence = List.copyOf(sequence);
        }
    }

    public static final int DODGE_COST = 25;
    public static final long DODGE_IFRAMES = 400, DODGE_COOLDOWN = 800;

    private double stamina, mana;
    private final int maxStamina, maxMana;
    private long lastRegen;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Deque<long[]> buffer = new ArrayDeque<>();   // [input ordinal, time]
    private long iframesUntil, dodgeReadyAt;

    public CombatState(int maxStamina, int maxMana, long now) {
        this.maxStamina = Math.max(10, maxStamina);
        this.maxMana = Math.max(0, maxMana);
        this.stamina = this.maxStamina;
        this.mana = this.maxMana;
        this.lastRegen = now;
    }

    public void regen(long now) {
        double s = Math.max(0, (now - lastRegen) / 1000.0);
        lastRegen = now;
        stamina = Math.min(maxStamina, stamina + 12 * s);
        mana = Math.min(maxMana, mana + 4 * s);
    }

    /** 물약 · 음료로 기력 · 마나를 채운다 (최대치까지) */
    public void restore(double addStamina, double addMana, long now) {
        regen(now);
        stamina = Math.min(maxStamina, stamina + Math.max(0, addStamina));
        mana = Math.min(maxMana, mana + Math.max(0, addMana));
    }

    public int stamina() { return (int) stamina; }
    public int mana() { return (int) mana; }
    public int maxStamina() { return maxStamina; }
    public int maxMana() { return maxMana; }

    public long remaining(String skill, long now) {
        return Math.max(0, cooldowns.getOrDefault(skill, 0L) - now);
    }

    /** 스킬 사용 — 쿨다운 · 자원 확인 후 소모 (서버가 판단) */
    public void use(SkillDefinition s, long now, double cooldownMult) {
        regen(now);
        DomainException.require(remaining(s.id(), now) == 0, "skill.cooldown", s.name() + " 재사용 대기 " + (remaining(s.id(), now) / 100) / 10.0 + "초");
        double have = s.resource() == SkillDefinition.Resource.MANA ? mana : stamina;
        DomainException.require(have >= s.cost(), "skill.no_resource", s.resource() == SkillDefinition.Resource.MANA ? "마나가 부족합니다" : "기력이 부족합니다");
        if (s.resource() == SkillDefinition.Resource.MANA) mana -= s.cost();
        else stamina -= s.cost();
        cooldowns.put(s.id(), now + Math.round(s.cooldownMs() * Math.max(0.3, cooldownMult)));
    }

    public void dodge(long now) {
        regen(now);
        DomainException.require(now >= dodgeReadyAt, "dodge.cooldown", "아직 회피할 수 없습니다");
        DomainException.require(stamina >= DODGE_COST, "dodge.no_stamina", "기력이 부족합니다");
        stamina -= DODGE_COST;
        iframesUntil = now + DODGE_IFRAMES;
        dodgeReadyAt = now + DODGE_COOLDOWN;
    }

    public boolean invulnerable(long now) {
        return now < iframesUntil;
    }

    /** 공격 입력을 기록하고, 맞는 콤보가 완성되면 그 콤보를 돌려주고 버퍼를 비운다 */
    public Optional<Combo> input(Input in, long now, List<Combo> combos) {
        buffer.addLast(new long[]{in.ordinal(), now});
        while (buffer.size() > 6) buffer.removeFirst();
        for (Combo c : combos) {
            int n = c.sequence().size();
            if (buffer.size() < n) continue;
            List<long[]> tail = new ArrayList<>(buffer).subList(buffer.size() - n, buffer.size());
            if (now - tail.get(0)[1] > c.windowMs()) continue;
            boolean match = true;
            for (int i = 0; i < n; i++) if (tail.get(i)[0] != c.sequence().get(i).ordinal()) { match = false; break; }
            if (match) {
                buffer.clear();
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
