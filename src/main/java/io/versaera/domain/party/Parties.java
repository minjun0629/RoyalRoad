package io.versaera.domain.party;

import io.versaera.domain.common.DomainException;

import java.util.*;

/**
 * 파티 (CANON 개념: 로열 로드의 던전은 대부분 파티로 돈다). 접속 중에만 있는 모임이라 DB 에 두지 않는다.
 * 한 사람은 한 파티에만. 최대 8명 (ORIGINAL). 파티원끼리는 서로 해치지 않고, 던전에 같이 들어간다.
 * 스레드: 메인 스레드에서만 부른다.
 */
public final class Parties {
    public static final int MAX = 8;
    /** 공격대: 파티 최대 5개 (40명) */
    public static final int RAID_PARTIES = 5;

    /** 전리품 나누기 (PTY-02) */
    public enum Loot {
        FREE("자유 — 줍는 사람이 임자"), ROUND_ROBIN("차례 — 돌아가며 한 명"), RANDOM("무작위 — 근처 파티원 중 한 명");
        public final String label;

        Loot(String label) { this.label = label; }
    }

    private final Map<String, Loot> loot = new HashMap<>();                  // 파티장 → 방식
    private final Map<String, Integer> turn = new HashMap<>();               // 파티장 → 차례 순번
    private final Map<String, LinkedHashSet<String>> raids = new HashMap<>(); // 공격대장(파티장) → 묶인 파티장들 (자기 포함)
    private final Map<String, String> raidOf = new HashMap<>();              // 파티장 → 공격대장
    private final Map<String, String> raidInvites = new HashMap<>();         // 초대받은 파티장 → 공격대장
    private final Map<String, String> leaderOf = new HashMap<>();          // 사람 → 파티장
    private final Map<String, LinkedHashSet<String>> members = new HashMap<>();   // 파티장 → 파티원 (파티장 포함, 들어온 순서)
    private final Map<String, String> invites = new HashMap<>();           // 초대받은 사람 → 파티장

    public Optional<String> leader(String who) {
        return Optional.ofNullable(leaderOf.get(who));
    }

    public List<String> members(String who) {
        String l = leaderOf.get(who);
        return l == null ? List.of(who) : List.copyOf(members.get(l));
    }

    public boolean together(String a, String b) {
        String l = leaderOf.get(a);
        return l != null && l.equals(leaderOf.get(b));
    }

    public void invite(String from, String to) {
        DomainException.require(!from.equals(to), "party.self", "자기 자신은 초대할 수 없습니다");
        DomainException.require(!leaderOf.containsKey(to), "party.busy", "이미 다른 파티에 있습니다");
        String l = leaderOf.getOrDefault(from, from);
        DomainException.require(l.equals(from), "party.not_leader", "파티장만 초대할 수 있습니다");
        DomainException.require(members(from).size() < MAX, "party.full", "파티가 가득 찼습니다 (" + MAX + "명)");
        invites.put(to, from);
    }

    /** @return 들어간 파티의 파티장 */
    public String accept(String who) {
        String l = invites.remove(who);
        DomainException.require(l != null, "party.no_invite", "받은 초대가 없습니다");
        DomainException.require(!leaderOf.containsKey(who), "party.busy", "이미 다른 파티에 있습니다");
        DomainException.require(leaderOf.getOrDefault(l, l).equals(l), "party.gone", "그 파티는 없어졌습니다");
        LinkedHashSet<String> m = members.computeIfAbsent(l, k -> new LinkedHashSet<>(List.of(l)));
        DomainException.require(m.size() < MAX, "party.full", "파티가 가득 찼습니다");
        leaderOf.put(l, l);
        m.add(who);
        leaderOf.put(who, l);
        return l;
    }

    /** 나가기. 파티장이 나가면 다음 사람이 파티장, 혼자 남으면 해산 */
    public void leave(String who) {
        invites.remove(who);
        String l = leaderOf.remove(who);
        if (l == null) return;
        LinkedHashSet<String> m = members.remove(l);
        m.remove(who);
        Loot mode = loot.remove(l);
        Integer t = turn.remove(l);
        if (m.size() <= 1) {
            for (String x : m) leaderOf.remove(x);
            leaveRaid(l);
            return;
        }
        String nl = l.equals(who) ? m.iterator().next() : l;
        members.put(nl, m);
        for (String x : m) leaderOf.put(x, nl);
        if (mode != null) loot.put(nl, mode);
        if (t != null) turn.put(nl, t);
        if (!nl.equals(l)) renameInRaid(l, nl);
    }

    // ------------------------------------------------------------------ 전리품 · 경험치
    public Loot loot(String who) {
        return loot.getOrDefault(leaderOf.getOrDefault(who, who), Loot.FREE);
    }

    public void loot(String leader, Loot mode) {
        DomainException.require(leaderOf.getOrDefault(leader, "").equals(leader), "party.not_leader", "파티장만 정할 수 있습니다");
        loot.put(leader, mode);
    }

    /**
     * 떨어진 전리품의 주인. FREE 면 null (누구나). eligible = 근처에 있는 파티원 (처치한 사람 포함, 파티 순서대로).
     * ROUND_ROBIN 은 파티 차례를 돌며 근처에 있는 다음 사람, RANDOM 은 roll(0~1) 로 고른다.
     */
    public String looter(String killer, List<String> eligible, double roll) {
        if (eligible.isEmpty()) return null;
        Loot mode = loot(killer);
        if (mode == Loot.FREE || !leaderOf.containsKey(killer)) return null;
        if (mode == Loot.RANDOM) return eligible.get(Math.min(eligible.size() - 1, (int) Math.floor(roll * eligible.size())));
        String l = leaderOf.get(killer);
        List<String> order = List.copyOf(members.get(l));
        int start = turn.getOrDefault(l, 0);
        for (int i = 0; i < order.size(); i++) {
            String c = order.get((start + i) % order.size());
            if (eligible.contains(c)) {
                turn.put(l, (start + i + 1) % order.size());
                return c;
            }
        }
        return null;
    }

    /**
     * 파티 경험치 나누기: 근처 n 명이 함께 싸우면 전체가 (1 + 0.15·(n−1)) 배가 되어 n 명이 나눈다 — 혼자보다 손해 보지 않게 (n=2 → 각자 57.5%).
     * @return 한 사람 몫 (최소 1)
     */
    public static long share(long base, int n) {
        if (n <= 1) return base;
        return Math.max(1, Math.round(base * (100.0 + 15.0 * (n - 1)) / (100.0 * n)));
    }

    // ------------------------------------------------------------------ 공격대 (RAID-02)
    /** 공격대 초대: 공격대장(=파티장)이 다른 파티의 파티장을 */
    public void raidInvite(String from, String toLeader) {
        DomainException.require(leaderOf.getOrDefault(from, "").equals(from), "raid.not_leader", "파티장만 공격대를 꾸릴 수 있습니다");
        DomainException.require(leaderOf.getOrDefault(toLeader, "").equals(toLeader), "raid.not_party", "상대가 파티장이 아닙니다");
        DomainException.require(!together(from, toLeader), "raid.same", "같은 파티입니다");
        String head = raidOf.getOrDefault(from, from);
        DomainException.require(head.equals(from), "raid.not_head", "공격대장만 초대할 수 있습니다");
        DomainException.require(!raidOf.containsKey(toLeader), "raid.busy", "상대 파티는 이미 공격대에 있습니다");
        DomainException.require(raids.getOrDefault(from, new LinkedHashSet<>(List.of(from))).size() < RAID_PARTIES, "raid.full", "공격대가 가득 찼습니다");
        raidInvites.put(toLeader, from);
    }

    /** @return 공격대장 */
    public String raidAccept(String leader) {
        String head = raidInvites.remove(leader);
        DomainException.require(head != null, "raid.no_invite", "받은 공격대 초대가 없습니다");
        DomainException.require(leaderOf.getOrDefault(leader, "").equals(leader), "raid.not_leader", "파티장만 받을 수 있습니다");
        DomainException.require(leaderOf.getOrDefault(head, "").equals(head), "raid.gone", "그 공격대는 없어졌습니다");
        LinkedHashSet<String> r = raids.computeIfAbsent(head, k -> new LinkedHashSet<>(List.of(head)));
        DomainException.require(r.size() < RAID_PARTIES, "raid.full", "공격대가 가득 찼습니다");
        r.add(leader);
        raidOf.put(head, head);
        raidOf.put(leader, head);
        return head;
    }

    /** 파티(파티장)가 공격대를 떠난다. 공격대장이 떠나면 다음 파티장이 공격대장 */
    public void leaveRaid(String leader) {
        raidInvites.remove(leader);
        String head = raidOf.remove(leader);
        if (head == null) return;
        LinkedHashSet<String> r = raids.remove(head);
        r.remove(leader);
        if (r.size() <= 1) {
            for (String x : r) raidOf.remove(x);
            return;
        }
        String nh = head.equals(leader) ? r.iterator().next() : head;
        raids.put(nh, r);
        for (String x : r) raidOf.put(x, nh);
    }

    private void renameInRaid(String oldLeader, String newLeader) {
        String head = raidOf.remove(oldLeader);
        if (head == null) return;
        LinkedHashSet<String> r = raids.remove(head);
        LinkedHashSet<String> nr = new LinkedHashSet<>();
        for (String x : r) nr.add(x.equals(oldLeader) ? newLeader : x);
        String nh = head.equals(oldLeader) ? newLeader : head;
        raids.put(nh, nr);
        for (String x : nr) raidOf.put(x, nh);
    }

    /** 공격대장 (공격대가 없으면 파티장, 파티도 없으면 자신) */
    public String raidHead(String who) {
        String l = leaderOf.getOrDefault(who, who);
        return raidOf.getOrDefault(l, l);
    }

    /** 공격대 전원 (공격대가 없으면 파티원) */
    public List<String> raidMembers(String who) {
        String l = leaderOf.getOrDefault(who, who);
        String head = raidOf.get(l);
        if (head == null) return members(who);
        List<String> out = new ArrayList<>();
        for (String pl : raids.get(head)) out.addAll(members(pl));
        return out;
    }
}
