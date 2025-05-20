package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class TableInputParametersTest {

    @Test
    public void builder_allFieldsSet_shouldCreateObjectCorrectly() {
        String tableName = "TestTable";
        String baseId = "base123";
        String tableId = "table456";

        TableInputParameters params = TableInputParameters.builder()
                .larkTableName(tableName)
                .larkBaseId(baseId)
                .larkTableId(tableId)
                .build();

        assertEquals(tableName, params.getLarkTableName());
        assertEquals(baseId, params.getLarkBaseId());
        assertEquals(tableId, params.getLarkTableId());
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkTableName_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkBaseId("base123")
                .larkTableId("table456")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkBaseId_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkTableName("TestTable")
                .larkTableId("table456")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkTableId_shouldThrowNullPointerException() {
        TableInputParameters.builder()
                .larkTableName("TestTable")
                .larkBaseId("base123")
                .build();
    }
}