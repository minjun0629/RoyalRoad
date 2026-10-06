package io.versaera.application.port;

import java.util.Map;

public interface JobRepository {
    record Held(String jobId, long since) {}

    /** slot → 직업 */
    Map<String, Held> jobs(String uuid);

    void set(String uuid, String slot, String jobId, long since);

    void deathLog(String uuid, String region, int danger, long xpLost, long at);

    int deaths(String uuid);
}
