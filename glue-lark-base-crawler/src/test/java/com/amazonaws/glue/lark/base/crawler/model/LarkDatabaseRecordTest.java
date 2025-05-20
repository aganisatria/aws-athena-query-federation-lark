package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class LarkDatabaseRecordTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        String id = "dbId123";
        String name = "MyDatabase";
        LarkDatabaseRecord record = new LarkDatabaseRecord(id, name);

        assertEquals(id, record.id());
        assertEquals(name, record.name());
    }

    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        LarkDatabaseRecord record1a = new LarkDatabaseRecord("id1", "name1");
        LarkDatabaseRecord record1b = new LarkDatabaseRecord("id1", "name1");
        LarkDatabaseRecord record2 = new LarkDatabaseRecord("id2", "name2");

        assertEquals(record1a, record1b);
        assertNotEquals(record1a, record2);
        assertEquals(record1a.hashCode(), record1b.hashCode());
    }
}