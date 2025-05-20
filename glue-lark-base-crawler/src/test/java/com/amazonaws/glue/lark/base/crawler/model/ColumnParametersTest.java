package com.amazonaws.glue.lark.base.crawler.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class ColumnParametersTest {

    @Test
    public void builder_allFieldsSet_shouldCreateObjectCorrectly() {
        String originalColumnName = "Test Column Name";
        String sanitizedColumnName = "test_column_name";
        String columnType = "string";
        String larkBaseFieldId = "field123";
        String larkBaseColumnType = "TEXT";
        String larkBaseId = "base456";
        String larkBaseTableId = "table789";

        ColumnParameters params = ColumnParameters.builder()
                .columnName(originalColumnName)
                .columnType(columnType)
                .larkBaseFieldId(larkBaseFieldId)
                .larkBaseColumnType(larkBaseColumnType)
                .larkBaseId(larkBaseId)
                .larkBaseTableId(larkBaseTableId)
                .build();

        assertEquals(sanitizedColumnName, params.getColumnName());
        assertEquals(originalColumnName, params.getOriginalColumnName());
        assertEquals(columnType, params.getColumnType());
        assertEquals(larkBaseFieldId, params.getLarkBaseRecordId());
        assertEquals(larkBaseColumnType, params.getLarkBaseColumnType());
        assertEquals(larkBaseId, params.getLarkBaseId());
        assertEquals(larkBaseTableId, params.getLarkBaseTableId());
    }

    @Test
    public void columnName_sanitization_shouldWorkAsExpected() {
        ColumnParameters params = ColumnParameters.builder()
                .columnName("My Column@Name-1")
                .columnType("string")
                .larkBaseFieldId("f1")
                .larkBaseColumnType("TEXT")
                .larkBaseId("b1")
                .larkBaseTableId("t1")
                .build();
        assertEquals("my_column_name_1", params.getColumnName());
        assertEquals("My Column@Name-1", params.getOriginalColumnName());
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingColumnName_shouldThrowNullPointerException() {
        ColumnParameters.builder()
                .columnType("string")
                .larkBaseFieldId("field123")
                .larkBaseColumnType("TEXT")
                .larkBaseId("base456")
                .larkBaseTableId("table789")
                .build();
    }

    @Test(expected = NullPointerException.class)
    public void builder_missingLarkBaseFieldId_shouldThrowNullPointerException() {
        ColumnParameters.builder()
                .columnName("Test Column")
                .columnType("string")
                .larkBaseColumnType("TEXT")
                .larkBaseId("base456")
                .larkBaseTableId("table789")
                .build();
    }
}