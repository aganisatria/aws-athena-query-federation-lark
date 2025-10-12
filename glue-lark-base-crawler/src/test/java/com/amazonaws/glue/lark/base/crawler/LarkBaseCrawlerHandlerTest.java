/*-
 * #%L
 * glue-lark-base-crawler
 * %%
 * Copyright (C) 2019 - 2025 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.amazonaws.glue.lark.base.crawler;

import com.amazonaws.glue.lark.base.crawler.model.LarkDatabaseRecord;
import com.amazonaws.glue.lark.base.crawler.model.enums.UITypeEnum;
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllTableResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListFieldResponse;
import com.amazonaws.glue.lark.base.crawler.service.GlueCatalogService;
import com.amazonaws.glue.lark.base.crawler.service.LarkBaseService;
import com.amazonaws.glue.lark.base.crawler.service.LarkDriveService;
import com.amazonaws.glue.lark.base.crawler.service.STSService;
import com.amazonaws.glue.lark.base.crawler.util.Util;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.utils.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LarkBaseCrawlerHandlerTest {

    @Mock
    private GlueCatalogService mockGlueCatalogService;
    @Mock
    private LarkBaseService mockLarkBaseService;
    @Mock
    private LarkDriveService mockLarkDriveService; // Meskipun tidak digunakan langsung, diperlukan untuk konstruktor
    @Mock
    private STSService mockStsService;
    @Mock
    private Context mockContext;

    private LarkBaseCrawlerHandler handler;

    // Payload input yang akan digunakan di banyak tes
    private final Map<String, Object> payload = new HashMap<>() {{
        put("larkBaseDataSourceId", "baseDs123");
        put("larkTableDataSourceId", "tableDs456");
    }};

    @Before
    public void setUp() {
        // Menggunakan konstruktor yang memungkinkan dependency injection
        handler = new LarkBaseCrawlerHandler(
                mockGlueCatalogService,
                mockLarkBaseService,
                mockLarkDriveService,
                mockStsService
        );
    }

    // Awal dari tes yang sudah ada
    @Test
    public void handleRequest_shouldProcessPayloadAndSucceed_whenNoChanges() {
        // Siapkan data Lark dan Glue yang identik
        LarkDatabaseRecord larkDb = new LarkDatabaseRecord("dbId1", "db_name");
        Database glueDb = Database.builder().name("db_name")
                .locationUri("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1").build();
        ListAllTableResponse.BaseItem larkTable = ListAllTableResponse.BaseItem.builder().tableId("tableId1").name("table_name").build();
        Table glueTable = Table.builder().name("table_name").databaseName("db_name")
                .storageDescriptor(StorageDescriptor.builder()
                        .location("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1/Table=tableId1")
                        .columns(Collections.emptyList())
                        .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                        .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                        .build())
                .parameters(Map.of(
                        "crawlingMethod", "LarkBase",
                        "larkTableId", "tableId1",
                        "larkBaseId", "dbId1",
                        "classification", "lark-base-flag",
                        "larkBaseDataSourceId", "baseDs123",
                        "larkTableDataSourceId", "tableDs456"
                ))
                .tableType("LARK_BASE_TABLE")
                .build();

        // Mock pemanggilan service
        when(mockLarkBaseService.getTableRecords("baseDs123", "tableDs456")).thenReturn(Collections.singletonList(larkDb));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.singletonList(glueDb));
        when(mockGlueCatalogService.getTables("db_name")).thenReturn(Collections.singletonList(glueTable));
        when(mockLarkBaseService.listTables("dbId1")).thenReturn(Collections.singletonList(larkTable));
        when(mockLarkBaseService.getTableFields(anyString(), anyString())).thenReturn(Collections.emptyList());

        // Jalankan metode utama
        String result = handler.handleRequest(payload, mockContext);

        // Verifikasi hasil
        assertEquals("Success", result);
        // Pastikan tidak ada operasi tulis yang dipanggil
        verify(mockGlueCatalogService, never()).batchDeleteDatabase(any());
        verify(mockGlueCatalogService, never()).batchCreateDatabase(any());
        verify(mockGlueCatalogService, never()).batchUpdateDatabase(any());
        verify(mockGlueCatalogService, never()).batchDeleteTable(any());
        verify(mockGlueCatalogService, never()).batchCreateTable(any());
        verify(mockGlueCatalogService, never()).batchUpdateTable(any());
    }

    @Test
    public void getCrawlingMethod_shouldReturnLarkBase() {
        assertEquals("LarkBase", handler.getCrawlingMethod());
    }



    @Test
    public void handleRequest_shouldSucceed_whenNoChanges() {
        // Data Lark dan Glue identik
        LarkDatabaseRecord larkDb = new LarkDatabaseRecord("dbId1", "db_name");
        Database glueDb = Database.builder().name("db_name")
                .locationUri("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1").build();
        ListAllTableResponse.BaseItem larkTable = ListAllTableResponse.BaseItem.builder().tableId("tableId1").name("table_name").build();

        List<Column> columns = Collections.singletonList(Column.builder().name("col1").type("string").comment("LarkBaseId=dbId1/LarkBaseTableId=tableId1/LarkBaseFieldId=f1/LarkBaseFieldName=col1/LarkBaseFieldType=Text").build());
        StorageDescriptor sd = StorageDescriptor.builder()
                .location("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1/Table=tableId1")
                .columns(columns)
                .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                .build();
        Map<String, String> params = Map.of(
                "crawlingMethod", "LarkBase",
                "larkTableId", "tableId1",
                "larkBaseId", "dbId1",
                "classification", "lark-base-flag",
                "larkBaseDataSourceId", "baseDs123",
                "larkTableDataSourceId", "tableDs456"
        );
        Table glueTable = Table.builder().name("table_name").databaseName("db_name").storageDescriptor(sd).parameters(params).tableType("LARK_BASE_TABLE").build();

        ListFieldResponse.FieldItem larkField = ListFieldResponse.FieldItem.builder().fieldName("col1").uiType("Text").fieldId("f1").build();

        // Mock pemanggilan service
        when(mockLarkBaseService.getTableRecords("baseDs123", "tableDs456")).thenReturn(Collections.singletonList(larkDb));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.singletonList(glueDb));
        when(mockGlueCatalogService.getTables("db_name")).thenReturn(Collections.singletonList(glueTable));
        when(mockLarkBaseService.listTables("dbId1")).thenReturn(Collections.singletonList(larkTable));
        when(mockLarkBaseService.getTableFields("dbId1", "tableId1")).thenReturn(Collections.singletonList(larkField));

        // Jalankan metode utama
        String result = handler.handleRequest(payload, mockContext);

        assertEquals("Success", result);
        // Verifikasi tidak ada operasi tulis yang dipanggil
        verify(mockGlueCatalogService, never()).batchUpdateTable(any());
        verify(mockGlueCatalogService, never()).batchDeleteDatabase(any());
        verify(mockGlueCatalogService, never()).batchCreateDatabase(any());
    }

    @Test
    public void testGetCrawlingSource() {
        handler.handleRequest(payload, mockContext); // Panggil untuk set properti
        assertEquals("baseDs123:tableDs456", handler.getCrawlingSource());
    }

    @Test
    public void testHandleRequest_withDatabaseAndTableCreation() {
        // Lark memiliki 1 DB & 1 Tabel, Glue kosong
        LarkDatabaseRecord larkDb = new LarkDatabaseRecord("dbId1", "new_db");
        ListAllTableResponse.BaseItem larkTable = ListAllTableResponse.BaseItem.builder().tableId("tableId1").name("new_table").build();

        when(mockLarkBaseService.getTableRecords("baseDs123", "tableDs456")).thenReturn(Collections.singletonList(larkDb));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList()); // Glue kosong
        when(mockLarkBaseService.listTables("dbId1")).thenReturn(Collections.singletonList(larkTable));
        when(mockLarkBaseService.getTableFields("dbId1", "tableId1")).thenReturn(Collections.emptyList());

        String result = handler.handleRequest(payload, mockContext);
        assertEquals("Success", result);

        verify(mockGlueCatalogService, times(1)).batchCreateDatabase(any());
        verify(mockGlueCatalogService, times(1)).batchCreateTable(any());
        verify(mockGlueCatalogService, never()).batchUpdateDatabase(any());
        verify(mockGlueCatalogService, never()).batchDeleteTable(any());
    }

    @Test
    public void testHandleRequest_withDatabaseDeletion() {
        // Glue memiliki 1 DB, Lark kosong
        Database glueDb = Database.builder().name("old_db")
                .locationUri("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1").build();

        when(mockLarkBaseService.getTableRecords("baseDs123", "tableDs456")).thenReturn(Collections.emptyList());
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.singletonList(glueDb));

        String result = handler.handleRequest(payload, mockContext);
        assertEquals("Success", result);

        verify(mockGlueCatalogService, times(1)).batchDeleteDatabase(Collections.singletonList("old_db"));
    }

    @Test
    public void testHandleRequest_withTableDeletion() {
        LarkDatabaseRecord larkDb = new LarkDatabaseRecord("dbId1", "db_name");
        Database glueDb = Database.builder().name("db_name").locationUri("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1").build();
        Table glueTable = Table.builder().name("table_to_delete").databaseName("db_name").build();

        when(mockLarkBaseService.getTableRecords(anyString(), anyString())).thenReturn(Collections.singletonList(larkDb));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.singletonList(glueDb));
        when(mockGlueCatalogService.getTables("db_name")).thenReturn(Collections.singletonList(glueTable));
        when(mockLarkBaseService.listTables("dbId1")).thenReturn(Collections.emptyList()); // Lark tidak punya tabel

        String result = handler.handleRequest(payload, mockContext);
        assertEquals("Success", result);

        verify(mockGlueCatalogService, times(1)).batchDeleteTable(any());
    }

    @Test
    public void testHandleRequest_withTableUpdate() {
        LarkDatabaseRecord larkDb = new LarkDatabaseRecord("dbId1", "db_name");
        Database glueDb = Database.builder().name("db_name").locationUri("lark-base-flag/CrawlingMethod=LarkBase/DataSource=baseDs123:tableDs456/Base=dbId1").build();
        ListAllTableResponse.BaseItem larkTable = ListAllTableResponse.BaseItem.builder().tableId("tableId1").name("table_name").build();

        // Buat tabel Glue dengan kolom yang berbeda
        Column oldColumn = Column.builder().name("old_col").type("string").build();
        Table glueTable = Table.builder().name("table_name").databaseName("db_name")
                .storageDescriptor(StorageDescriptor.builder().columns(oldColumn).build())
                .parameters(Map.of("crawlingMethod", "LarkBase", "larkTableId", "tableId1", "larkBaseId", "dbId1"))
                .build();

        // Buat field Lark yang baru
        ListFieldResponse.FieldItem newField = ListFieldResponse.FieldItem.builder().fieldName("new_col").uiType("Text").fieldId("field1").build();

        when(mockLarkBaseService.getTableRecords(anyString(), anyString())).thenReturn(Collections.singletonList(larkDb));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.singletonList(glueDb));
        when(mockGlueCatalogService.getTables("db_name")).thenReturn(Collections.singletonList(glueTable));
        when(mockLarkBaseService.listTables("dbId1")).thenReturn(Collections.singletonList(larkTable));
        when(mockLarkBaseService.getTableFields("dbId1", "tableId1")).thenReturn(Collections.singletonList(newField));

        String result = handler.handleRequest(payload, mockContext);
        assertEquals("Success", result);

        verify(mockGlueCatalogService, times(1)).batchUpdateTable(any());
    }

    @Test
    public void testGetFormulaOrLookupFieldType_recursiveLookup() throws Exception {
        java.lang.reflect.Method method = BaseLarkBaseCrawlerHandler.class.getDeclaredMethod("getFormulaOrLookupFieldType", ListFieldResponse.FieldItem.class, String.class);
        method.setAccessible(true);

        // Gunakan builder, bukan mock, untuk kelas final
        ListFieldResponse.FieldItem initialLookup = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(Map.of("target_field", "field2", "filter_info", Map.of("target_table", "table2"))).build();
        ListFieldResponse.FieldItem intermediateLookup = ListFieldResponse.FieldItem.builder().uiType("Lookup").fieldId("field2").property(Map.of("target_field", "field3", "filter_info", Map.of("target_table", "table3"))).build();
        ListFieldResponse.FieldItem finalText = ListFieldResponse.FieldItem.builder().uiType("Text").fieldId("field3").build();

        when(mockLarkBaseService.getTableFields("baseId", "table2")).thenReturn(Collections.singletonList(intermediateLookup));
        when(mockLarkBaseService.getTableFields("baseId", "table3")).thenReturn(Collections.singletonList(finalText));

        String result = (String) method.invoke(handler, initialLookup, "baseId");

        assertEquals("string", result);
    }
}