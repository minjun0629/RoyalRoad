package io.versaera.application.port;

/** 플레이어 한 명의 진행을 지운다 (관리자 초기화) */
public interface ResetRepository {
    /** 길드장인 길드 id (없으면 null) */
    String guildLedBy(String uuid);

    /** @return 지운 행 수 */
    int wipe(String uuid);
}
