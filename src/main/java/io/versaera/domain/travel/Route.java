package io.versaera.domain.travel;

/** 마차 · 배 노선 하나 (한 방향). id = kind:from>to */
public record Route(String id, Kind kind, String from, String to, int distance, long fare, long durationMs) {
    public enum Kind {
        CARRIAGE("마차"), SHIP("배");
        public final String label;

        Kind(String label) { this.label = label; }
    }

    public static String id(Kind k, String from, String to) {
        return k.name().toLowerCase(java.util.Locale.ROOT) + ":" + from + ">" + to;
    }
}
