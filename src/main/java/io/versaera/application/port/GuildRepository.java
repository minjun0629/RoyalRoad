package io.versaera.application.port;

import java.util.List;
import java.util.Optional;

public interface GuildRepository {
    record Guild(String id, String name, String tag, String leader, int level, long xp, long createdAt) {}

    record Member(String uuid, String guildId, String rank, long contribution, long joinedAt) {}

    void create(Guild g);

    Optional<Guild> find(String id);

    Optional<Guild> byName(String name);

    boolean nameOrTagTaken(String name, String tag);

    void update(Guild g);

    void delete(String id);

    Optional<Member> member(String uuid);

    List<Member> members(String guildId);

    void addMember(Member m);

    void setRank(String uuid, String rank);

    void addContribution(String uuid, long delta);

    void removeMember(String uuid);
}
