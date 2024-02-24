package org.sbot.services.dao;

import java.util.Map;

@FunctionalInterface
public interface BatchEntry {

    String ID_FIELD = "id";

    default void batchId(long id) {
        batch(Map.of(ID_FIELD, id));
    }

    void batch(Map<String, Object> ids);

    static Long longId(Map<String, Object> ids) {
        return (Long) ids.get(ID_FIELD);
    }
}
