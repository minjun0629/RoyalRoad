package io.versaera.application;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.ItemSet;
import io.versaera.domain.item.ItemType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemTypeRegistry {
    private final Map<String, ItemType> types = new LinkedHashMap<>();

    private final Map<String, ItemSet> sets = new LinkedHashMap<>();

    public ItemTypeRegistry(Collection<ItemType> list) {
        this(list, java.util.List.of());
    }

    public ItemTypeRegistry(Collection<ItemType> list, Collection<ItemSet> setList) {
        for (ItemType t : list) if (types.putIfAbsent(t.id(), t) != null) throw new IllegalArgumentException("아이템 종류 id 중복: " + t.id());
        for (ItemSet st : setList) if (sets.putIfAbsent(st.id(), st) != null) throw new IllegalArgumentException("세트 id 중복: " + st.id());
        for (ItemType t : list)
            if (t.set() != null && !sets.containsKey(t.set())) throw new IllegalArgumentException("없는 세트: " + t.set() + " (" + t.id() + ")");
    }

    /** 세트 id → 세트 */
    public Map<String, ItemSet> sets() {
        return Collections.unmodifiableMap(sets);
    }

    public ItemType get(String id) {
        ItemType t = types.get(id);
        if (t == null) throw DomainException.of("item.unknown_type", "없는 아이템 종류: " + id);
        return t;
    }

    public boolean has(String id) {
        return types.containsKey(id);
    }

    public Collection<ItemType> all() {
        return Collections.unmodifiableCollection(types.values());
    }
}
