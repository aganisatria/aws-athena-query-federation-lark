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
package com.amazonaws.glue.lark.base.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
class GlueCatalogServiceTest {

    private GlueClient glueClient;
    private GlueCatalogService glueCatalogService;

    @BeforeEach
    void setUp() {
        glueClient = mock(GlueClient.class);
        glueCatalogService = new GlueCatalogService(glueClient, "test-catalog-id");
    }

    @Test
    void testGetDatabases() {
        Database database = Database.builder().name("testDatabase").build();
        GetDatabasesResponse response = GetDatabasesResponse.builder()
                .databaseList(Collections.singletonList(database))
                .nextToken(null)
                .build();

        when(glueClient.getDatabases(any(GetDatabasesRequest.class))).thenReturn(response);

        List<Database> databases = glueCatalogService.getDatabases();
        assertEquals(1, databases.size());
        assertEquals("testDatabase", databases.get(0).name());
    }

    @Test
    void testBatchCreateDatabase() {
        Map<String, String> databaseNamesWithLocationUri = Map.of(
                "testDatabase", "s3://test-location/"
        );

        glueCatalogService.batchCreateDatabase(databaseNamesWithLocationUri);

        verify(glueClient, times(1)).createDatabase(any(CreateDatabaseRequest.class));
    }

    @Test
    void testBatchUpdateDatabase() {
        Map<String, String> databaseNamesWithLocationUri = Map.of(
                "testDatabase1", "s3://test-location-1/",
                "testDatabase2", "s3://test-location-2/"
        );

        glueCatalogService.batchUpdateDatabase(databaseNamesWithLocationUri);

        ArgumentCaptor<UpdateDatabaseRequest> captor = ArgumentCaptor.forClass(UpdateDatabaseRequest.class);
        verify(glueClient, times(2)).updateDatabase(captor.capture());

        List<UpdateDatabaseRequest> capturedRequests = captor.getAllValues();
        for (String databaseName : databaseNamesWithLocationUri.keySet()) {
            UpdateDatabaseRequest request = capturedRequests.remove(0); // Ambil request yang ditangkap
            assertEquals(databaseName, request.name());
            assertEquals(databaseNamesWithLocationUri.get(databaseName), request.databaseInput().locationUri());
        }
    }

    @Test
    void testBatchDeleteDatabase() {
        List<String> databaseNames = List.of("testDatabase");

        glueCatalogService.batchDeleteDatabase(databaseNames);

        verify(glueClient, times(1)).deleteDatabase(any(DeleteDatabaseRequest.class));
    }

    @Test
    void testGetTables() {
        Table table = Table.builder().name("testTable").build();
        GetTablesResponse response = GetTablesResponse.builder()
                .tableList(Collections.singletonList(table))
                .nextToken(null)
                .build();

        when(glueClient.getTables(any(GetTablesRequest.class))).thenReturn(response);

        List<Table> tables = glueCatalogService.getTables("testDatabase");
        assertEquals(1, tables.size());
        assertEquals("testTable", tables.get(0).name());
    }

    @Test
    void testBatchDeleteTable() {
        Map<String, List<Table>> databaseNameAndTables = Map.of(
                "testDatabase", List.of(Table.builder().name("testTable").build())
        );

        glueCatalogService.batchDeleteTable(databaseNameAndTables);

        verify(glueClient, times(1)).batchDeleteTable(any(BatchDeleteTableRequest.class));
    }

    @Test
    void testBatchCreateTable() {
        Map<String, List<TableInput>> databaseNameAndTableInputs = Map.of(
                "testDatabase", List.of(TableInput.builder().name("testTable").build())
        );

        glueCatalogService.batchCreateTable(databaseNameAndTableInputs);

        verify(glueClient, times(1)).createTable(any(CreateTableRequest.class));
    }

    @Test
    void testBatchUpdateTable() {
        Map<String, List<TableInput>> databaseNameAndTableInputs = Map.of(
                "testDatabase", List.of(TableInput.builder().name("testTable").build())
        );

        glueCatalogService.batchUpdateTable(databaseNameAndTableInputs);

        verify(glueClient, times(1)).updateTable(any(UpdateTableRequest.class));
    }
}
