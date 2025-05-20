package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import software.amazon.awssdk.services.glue.model.Database;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DatabaseProcessResultTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        Database db1 = Database.builder().name("remainingDb1").build();
        LarkDatabaseRecord larkRecord1 = new LarkDatabaseRecord("larkId1", "remainingLark1");
        Map<String, String> dbsToCreateMap = Map.of("newDb1", "s3://location1");

        List<Database> remainingDatabases = Collections.singletonList(db1);
        List<LarkDatabaseRecord> remainingLarkRecords = Collections.singletonList(larkRecord1);

        DatabaseProcessResult result = new DatabaseProcessResult(
                remainingDatabases, remainingLarkRecords, dbsToCreateMap
        );

        assertEquals(remainingDatabases, result.remainingDatabases());
        assertEquals(remainingLarkRecords, result.remainingLarkRecords());
        assertEquals(dbsToCreateMap, result.databasesToCreate());
    }

    @Test
    public void recordCreation_withEmptyCollections_shouldHoldEmptyCollections() {
        List<Database> emptyDbs = Collections.emptyList();
        List<LarkDatabaseRecord> emptyLarkRecords = Collections.emptyList();
        Map<String, String> emptyMap = Collections.emptyMap();

        DatabaseProcessResult result = new DatabaseProcessResult(
                emptyDbs, emptyLarkRecords, emptyMap
        );

        assertTrue(result.remainingDatabases().isEmpty());
        assertTrue(result.remainingLarkRecords().isEmpty());
        assertTrue(result.databasesToCreate().isEmpty());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        Database db1 = Database.builder().name("db1").build();
        LarkDatabaseRecord lr1 = new LarkDatabaseRecord("id1", "lr1");
        Map<String, String> map1 = Map.of("k1", "v1");

        DatabaseProcessResult r1a = new DatabaseProcessResult(List.of(db1), List.of(lr1), map1);
        DatabaseProcessResult r1b = new DatabaseProcessResult(List.of(db1), List.of(lr1), map1);
        DatabaseProcessResult r2 = new DatabaseProcessResult(List.of(), List.of(lr1), map1);

        assertEquals(r1a, r1b);
        assertNotEquals(r1a, r2);
        assertEquals(r1a.hashCode(), r1b.hashCode());
    }
}