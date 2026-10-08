package io.versaera.domain.guild;

import io.versaera.domain.common.DomainException;

import java.util.regex.Pattern;

/**
 * 길드 규칙 (GLD-01, 수치 ORIGINAL). 원작의 길드는 이름만 가져오고(SOURCE-BASED), 성 · 영지 · 하우징은 만들지 않는다.
 * 길드 레벨은 금고에 넣은 돈과 함께 한 활동(던전 · 보스 · 퀘스트)으로 오르고, 오르면 인원 상한만 늘어난다 (능력치 보너스 없음).
 */
public final class GuildRules {
    public enum Rank {
        LEADER, OFFICER, MEMBER;

        public boolean atLeast(Rank r) {
            return ordinal() <= r.ordinal();
        }
    }

    public static final long CREATE_COST = 5_000 * io.versaera.domain.economy.Money.SILVER;   // 50 골드
    public static final int MAX_LEVEL = 10;
    public static final long INVITE_TTL_MS = 5 * 60_000;
    private static final Pattern NAME = Pattern.compile("[가-힣A-Za-z0-9 ]{2,16}");
    private static final Pattern TAG = Pattern.compile("[가-힣A-Z0-9]{2,5}");

    private GuildRules() {
    }

    public static void validate(String name, String tag) {
        DomainException.require(name != null && NAME.matcher(name).matches() && !name.isBlank() && name.equals(name.strip()),
                "guild.bad_name", "길드 이름은 한글 · 영문 · 숫자 2~16자입니다");
        DomainException.require(tag != null && TAG.matcher(tag).matches(), "guild.bad_tag", "태그는 한글 · 대문자 · 숫자 2~5자입니다");
    }

    /** 레벨 lv → lv+1 에 필요한 경험치 */
    public static long need(int lv) {
        return 1_000L * lv * lv;
    }

    public static int levelOf(long xp) {
        int lv = 1;
        long acc = 0;
        while (lv < MAX_LEVEL && xp >= acc + need(lv)) {
            acc += need(lv);
            lv++;
        }
        return lv;
    }

    public static int maxMembers(int level) {
        return 10 + (level - 1) * 5;
    }

    public static int maxOfficers(int level) {
        return 2 + level / 3;
    }
}
