package io.versaera.application.port;

import java.util.List;
import java.util.Optional;

public interface MarketRepository {
    record Supply(long supply, long updatedAt) {}

    record Listing(String id, String seller, String market, String kind, String itemId, String typeId, int quality, int amount, long price,
                   String state, String buyer, long createdAt, long expiresAt, Long closedAt) {}

    Supply supply(String market, String typeId);

    void setSupply(String market, String typeId, long supply, long at);

    void insert(Listing l);

    Optional<Listing> find(String id);

    /** state 가 expected 일 때만 바꾼다 (같은 물건을 두 사람이 동시에 사는 것을 막음). 바꿨으면 true */
    boolean close(String id, String expected, String state, String buyer, long at);

    List<Listing> open(String market, String typeId, int limit);

    List<Listing> bySeller(String seller, String state);

    List<Listing> expired(long now, int limit);
}
