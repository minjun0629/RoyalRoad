package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;

/**
 * 원작의 현실 쪽 규칙을 서버 규칙으로 (ACC-01). 나무위키 「로열 로드」 §2:
 * <ul>
 *   <li>캡슐: 접속하려면 실행기 캡슐이 필요하다 → 서버에 '캡슐'을 등록한 사람만 들어온다 (config access.capsule_required, 관리자 /va capsule)</li>
 *   <li>이용료: 매달 이용료 → 게임 골드로 30일마다 자동 결제. 처음 30일은 무료. 못 내면 접속 불가 (config access.subscription_fee, 0 = 끔)</li>
 *   <li>연령 제한: 어린 아이는 사냥 · 전투 모험을 할 수 없다 → 관리자가 '미성년' 표시한 사람은 공격 · 던전 · 다른 차원 · 시련 불가 (/va minor)</li>
 * </ul>
 * 진짜 돈 결제는 없다 — 모두 게임 안 골드와 서버 표시로만. 값은 행동 기록(counter)에: access.capsule · access.paid_until · access.minor
 */
public final class AccessService {
    public record Rules(boolean capsuleRequired, long subscriptionFee, int subscriptionDays) {
        public static final Rules OFF = new Rules(false, 0, 30);

        public Rules {
            DomainException.require(subscriptionFee >= 0 && subscriptionDays >= 1, "access.bad_rules", "이용료 설정이 잘못되었습니다");
        }
    }

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final EconomyService economy;
    private final GameClock clock;
    private volatile Rules rules = Rules.OFF;

    public AccessService(TxRunner tx, ProgressRepository progress, EconomyService economy, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.economy = economy;
        this.clock = clock;
    }

    public void rules(Rules r) {
        rules = r;
    }

    public Rules rules() {
        return rules;
    }

    /** 들어와도 되는가 (들어와도 되면 null, 아니면 이유). 이용료는 이때 자동으로 낸다 */
    public String admit(String uuid) {
        Rules r = rules;
        if (r.capsuleRequired() && progress.counter(uuid, "access.capsule") <= 0)
            return "캡슐이 등록되지 않았습니다 — 서버 관리자에게 캡슐 등록을 요청하세요";
        if (r.subscriptionFee() <= 0) return null;
        long now = clock.nowMillis(), period = r.subscriptionDays() * 86_400_000L;
        long paid = progress.counter(uuid, "access.paid_until");
        if (paid == 0) {   // 처음 들어온 사람: 첫 기간은 무료
            tx.inTx(() -> progress.addCounter(uuid, "access.paid_until", now + period));
            return null;
        }
        if (paid > now) return null;
        long periodNo = (now - paid) / period;
        String key = "subscription:" + uuid + ":" + paid + ":" + periodNo;
        try {
            economy.withdraw(uuid, r.subscriptionFee(), "subscription", key);
        } catch (DomainException e) {
            return "이용료 " + r.subscriptionFee() + " 골드를 낼 돈이 없습니다 — 다른 사람에게 송금받거나 관리자에게 문의하세요";
        }
        tx.inTx(() -> progress.addCounter(uuid, "access.paid_until", (now + period) - paid));
        return null;
    }

    public long paidUntil(String uuid) {
        return progress.counter(uuid, "access.paid_until");
    }

    public void registerCapsule(String uuid) {
        tx.inTx(() -> progress.counter(uuid, "access.capsule") > 0 ? 0 : progress.addCounter(uuid, "access.capsule", 1));
    }

    public boolean minor(String uuid) {
        return progress.counter(uuid, "access.minor") > 0;
    }

    public void setMinor(String uuid, boolean on) {
        tx.inTx(() -> {
            long cur = progress.counter(uuid, "access.minor");
            long want = on ? 1 : 0;
            return cur == want ? cur : progress.addCounter(uuid, "access.minor", want - cur);
        });
    }
}
