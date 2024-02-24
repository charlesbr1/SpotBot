package org.sbot.services.dao;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchEntryTest {

    @Test
    void batchId() {
        var result = new HashMap<>();
        BatchEntry batchEntry = result::putAll;
        batchEntry.batchId(123L);
        assertEquals(Map.of(BatchEntry.ID_FIELD, 123L), result);
    }

    @Test
    void longId() {
        assertEquals(321L, BatchEntry.longId(Map.of(BatchEntry.ID_FIELD, 321L)));
        var idMap = new HashMap<String, Object>();
        BatchEntry batchEntry = idMap::putAll;
        batchEntry.batchId(123L);
        assertEquals(idMap.get(BatchEntry.ID_FIELD), BatchEntry.longId(idMap));
    }
}