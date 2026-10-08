package io.versaera.domain.economy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 돈 단위 (원작 「로열 로드」식): 1 골드 = 100 실버 = 10 000 쿠퍼. 지갑 · 장부 · 가격은 모두 <b>쿠퍼</b> 정수로 다룬다.
 * 콘텐츠 파일(yml)의 돈은 <b>실버</b>로 적는다 (소수 가능: 0.3 = 30 쿠퍼) — 로더가 {@link #silver(double)} 로 쿠퍼로 바꾼다.
 * 원작의 환율은 출처로 확인하지 못해 1:100:100 으로 정했다 (docs/01_RESEARCH.md).
 */
public final class Money {
    private Money() {
    }

    public static final long COPPER = 1, SILVER = 100, GOLD = 10_000;

    /** 실버 → 쿠퍼 (반올림) */
    public static long silver(double silver) {
        return Math.round(silver * SILVER);
    }

    public static long gold(double gold) {
        return Math.round(gold * GOLD);
    }

    /** "3골드 20실버 5쿠퍼" — 0 인 단위는 뺀다, 0 은 "0쿠퍼" */
    public static String format(long copper) {
        if (copper == 0) return "0쿠퍼";
        StringBuilder sb = new StringBuilder(copper < 0 ? "-" : "");
        long v = Math.abs(copper), g = v / GOLD, s = v % GOLD / SILVER, c = v % SILVER;
        if (g > 0) sb.append(String.format("%,d", g)).append("골드");
        if (s > 0) sb.append(sb.length() > (copper < 0 ? 1 : 0) ? " " : "").append(s).append("실버");
        if (c > 0) sb.append(sb.length() > (copper < 0 ? 1 : 0) ? " " : "").append(c).append("쿠퍼");
        return sb.toString();
    }

    /** 짧게: "3.2골드" · "45실버" · "30쿠퍼" (메뉴 한 줄용, 가장 큰 단위로 반올림) */
    public static String brief(long copper) {
        long v = Math.abs(copper);
        String sign = copper < 0 ? "-" : "";
        if (v >= GOLD) return sign + trim(v / (double) GOLD) + "골드";
        if (v >= SILVER) return sign + trim(v / (double) SILVER) + "실버";
        return sign + v + "쿠퍼";
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", d).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(골드|골|g|실버|실|s|쿠퍼|쿠|c)?", Pattern.CASE_INSENSITIVE);

    /**
     * 사람이 친 금액 → 쿠퍼. "3골드20실버" · "3g 20s" · "50쿠퍼" · "1.5골드". 단위 없는 숫자는 <b>실버</b>.
     * @return 못 읽으면 -1
     */
    public static long parse(String text) {
        if (text == null) return -1;
        String t = text.replace(",", "").replace("_", "").trim();
        if (t.isEmpty()) return -1;
        Matcher m = PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end && !t.substring(end, m.start()).isBlank()) return -1;
            if (m.group().isBlank()) break;
            double n = Double.parseDouble(m.group(1));
            String u = m.group(2) == null ? "실버" : m.group(2).toLowerCase(java.util.Locale.ROOT);
            long unit = switch (u) { case "골드", "골", "g" -> GOLD; case "쿠퍼", "쿠", "c" -> COPPER; default -> SILVER; };
            total += Math.round(n * unit);
            end = m.end();
        }
        return end == t.length() ? total : -1;
    }
}
