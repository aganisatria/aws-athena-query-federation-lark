package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class UpdateDatabaseProcessResultTest {

    @Test
    public void recordCreation_shouldHoldValues() {
        TableInput createTable1 = TableInput.builder().name("newTable1").build();
        TableInput updateTable1 = TableInput.builder().name("updateTable1").build();
        Table deleteTable1 = Table.builder().name("deleteTable1").build();

        List<TableInput> tablesToCreate = Collections.singletonList(createTable1);
        List<TableInput> tablesToUpdate = Collections.singletonList(updateTable1);
        List<Table> tablesToDelete = Collections.singletonList(deleteTable1);

        UpdateDatabaseProcessResult result = new UpdateDatabaseProcessResult(
                tablesToCreate, tablesToUpdate, tablesToDelete
        );

        assertEquals(tablesToCreate, result.tablesToCreate());
        assertEquals(tablesToUpdate, result.tablesToUpdate());
        assertEquals(tablesToDelete, result.tablesToDelete());
    }

    @Test
    public void recordCreation_withEmptyLists_shouldHoldEmptyLists() {
        List<TableInput> emptyTableInputs = Collections.emptyList();
        List<Table> emptyTables = Collections.emptyList();

        UpdateDatabaseProcessResult result = new UpdateDatabaseProcessResult(
                emptyTableInputs, emptyTableInputs, emptyTables
        );

        assertTrue(result.tablesToCreate().isEmpty());
        assertTrue(result.tablesToUpdate().isEmpty());
        assertTrue(result.tablesToDelete().isEmpty());
    }


    @Test
    public void equalsAndHashCode_shouldBehaveAsExpectedForRecords() {
        TableInput t1 = TableInput.builder().name("t1").build();
        Table t2 = Table.builder().name("t2").build();

        UpdateDatabaseProcessResult r1a = new UpdateDatabaseProcessResult(List.of(t1), List.of(), List.of(t2));
        UpdateDatabaseProcessResult r1b = new UpdateDatabaseProcessResult(List.of(t1), List.of(), List.of(t2));
        UpdateDatabaseProcessResult r2 = new UpdateDatabaseProcessResult(List.of(), List.of(t1), List.of(t2));

        assertEquals(r1a, r1b);
        assertNotEquals(r1a, r2);
        assertEquals(r1a.hashCode(), r1b.hashCode());
    }
}