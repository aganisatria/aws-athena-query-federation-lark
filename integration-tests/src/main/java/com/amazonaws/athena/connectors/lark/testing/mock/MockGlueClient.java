/*-
 * #%L
 * Integration Tests
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
package com.amazonaws.athena.connectors.lark.testing.mock;

import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateDatabaseResponse;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.CreateTableResponse;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.DeleteDatabaseRequest;
import software.amazon.awssdk.services.glue.model.DeleteDatabaseResponse;
import software.amazon.awssdk.services.glue.model.DeleteTableRequest;
import software.amazon.awssdk.services.glue.model.DeleteTableResponse;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetDatabaseResponse;
import software.amazon.awssdk.services.glue.model.GetDatabasesRequest;
import software.amazon.awssdk.services.glue.model.GetDatabasesResponse;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.glue.model.GetTablesResponse;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.glue.model.UpdateTableRequest;
import software.amazon.awssdk.services.glue.model.UpdateTableResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AWS Glue Client for testing.
 * Provides in-memory storage for databases and tables.
 *
 * <p>This mock client:
 * <ul>
 *   <li>Stores databases and tables in memory</li>
 *   <li>Supports basic CRUD operations</li>
 *   <li>Throws appropriate exceptions for missing resources</li>
 *   <li>Thread-safe using ConcurrentHashMap</li>
 * </ul>
 */
public class MockGlueClient implements GlueClient
{
    private final Map<String, Database> databases = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Table>> tables = new ConcurrentHashMap<>();

    @Override
    public String serviceName()
    {
        return "glue";
    }

    @Override
    public void close()
    {
        databases.clear();
        tables.clear();
    }

    // ========================================================================
    // Database Operations
    // ========================================================================

    @Override
    public GetDatabaseResponse getDatabase(GetDatabaseRequest request)
    {
        String dbName = request.name();
        Database db = databases.get(dbName);

        if (db == null) {
            throw EntityNotFoundException.builder()
                    .message("Database not found: " + dbName)
                    .build();
        }

        return GetDatabaseResponse.builder()
                .database(db)
                .build();
    }

    @Override
    public GetDatabasesResponse getDatabases(GetDatabasesRequest request)
    {
        List<Database> dbList = new ArrayList<>(databases.values());
        return GetDatabasesResponse.builder()
                .databaseList(dbList)
                .build();
    }

    @Override
    public CreateDatabaseResponse createDatabase(CreateDatabaseRequest request)
    {
        DatabaseInput input = request.databaseInput();
        String dbName = input.name();

        Database database = Database.builder()
                .name(dbName)
                .description(input.description())
                .locationUri(input.locationUri())
                .parameters(input.parameters())
                .createTime(Instant.now())
                .build();

        databases.put(dbName, database);
        tables.put(dbName, new ConcurrentHashMap<>());

        return CreateDatabaseResponse.builder().build();
    }

    @Override
    public DeleteDatabaseResponse deleteDatabase(DeleteDatabaseRequest request)
    {
        String dbName = request.name();

        if (!databases.containsKey(dbName)) {
            throw EntityNotFoundException.builder()
                    .message("Database not found: " + dbName)
                    .build();
        }

        databases.remove(dbName);
        tables.remove(dbName);

        return DeleteDatabaseResponse.builder().build();
    }

    // ========================================================================
    // Table Operations
    // ========================================================================

    @Override
    public GetTableResponse getTable(GetTableRequest request)
    {
        String dbName = request.databaseName();
        String tableName = request.name();

        validateDatabaseExists(dbName);

        Map<String, Table> dbTables = tables.get(dbName);
        Table table = dbTables.get(tableName);

        if (table == null) {
            throw EntityNotFoundException.builder()
                    .message("Table not found: " + dbName + "." + tableName)
                    .build();
        }

        return GetTableResponse.builder()
                .table(table)
                .build();
    }

    @Override
    public GetTablesResponse getTables(GetTablesRequest request)
    {
        String dbName = request.databaseName();

        validateDatabaseExists(dbName);

        Map<String, Table> dbTables = tables.get(dbName);
        List<Table> tableList = new ArrayList<>(dbTables.values());

        return GetTablesResponse.builder()
                .tableList(tableList)
                .build();
    }

    @Override
    public CreateTableResponse createTable(CreateTableRequest request)
    {
        String dbName = request.databaseName();
        TableInput input = request.tableInput();

        validateDatabaseExists(dbName);

        Table table = Table.builder()
                .name(input.name())
                .databaseName(dbName)
                .description(input.description())
                .owner(input.owner())
                .createTime(Instant.now())
                .updateTime(Instant.now())
                .lastAccessTime(input.lastAccessTime())
                .retention(input.retention())
                .storageDescriptor(input.storageDescriptor())
                .partitionKeys(input.partitionKeys())
                .viewOriginalText(input.viewOriginalText())
                .viewExpandedText(input.viewExpandedText())
                .tableType(input.tableType())
                .parameters(input.parameters())
                .build();

        tables.get(dbName).put(input.name(), table);

        return CreateTableResponse.builder().build();
    }

    @Override
    public UpdateTableResponse updateTable(UpdateTableRequest request)
    {
        String dbName = request.databaseName();
        TableInput input = request.tableInput();

        validateDatabaseExists(dbName);

        Map<String, Table> dbTables = tables.get(dbName);
        if (!dbTables.containsKey(input.name())) {
            throw EntityNotFoundException.builder()
                    .message("Table not found: " + dbName + "." + input.name())
                    .build();
        }

        Table table = Table.builder()
                .name(input.name())
                .databaseName(dbName)
                .description(input.description())
                .owner(input.owner())
                .createTime(dbTables.get(input.name()).createTime()) // Keep original create time
                .updateTime(Instant.now())
                .lastAccessTime(input.lastAccessTime())
                .retention(input.retention())
                .storageDescriptor(input.storageDescriptor())
                .partitionKeys(input.partitionKeys())
                .viewOriginalText(input.viewOriginalText())
                .viewExpandedText(input.viewExpandedText())
                .tableType(input.tableType())
                .parameters(input.parameters())
                .build();

        dbTables.put(input.name(), table);

        return UpdateTableResponse.builder().build();
    }

    @Override
    public DeleteTableResponse deleteTable(DeleteTableRequest request)
    {
        String dbName = request.databaseName();
        String tableName = request.name();

        validateDatabaseExists(dbName);

        Map<String, Table> dbTables = tables.get(dbName);
        if (dbTables.remove(tableName) == null) {
            throw EntityNotFoundException.builder()
                    .message("Table not found: " + dbName + "." + tableName)
                    .build();
        }

        return DeleteTableResponse.builder().build();
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private void validateDatabaseExists(String dbName)
    {
        if (!databases.containsKey(dbName)) {
            throw EntityNotFoundException.builder()
                    .message("Database not found: " + dbName)
                    .build();
        }
    }

    /**
     * Helper method to create a simple table for testing.
     */
    public void createSimpleTable(String dbName, String tableName, List<Column> columns, Map<String, String> parameters)
    {
        TableInput tableInput = TableInput.builder()
                .name(tableName)
                .storageDescriptor(StorageDescriptor.builder()
                        .columns(columns)
                        .build())
                .parameters(parameters)
                .build();

        createTable(CreateTableRequest.builder()
                .databaseName(dbName)
                .tableInput(tableInput)
                .build());
    }

    /**
     * Get all databases (for testing/debugging).
     */
    public Map<String, Database> getAllDatabases()
    {
        return new ConcurrentHashMap<>(databases);
    }

    /**
     * Get all tables in a database (for testing/debugging).
     */
    public Map<String, Table> getAllTablesInDatabase(String dbName)
    {
        return new ConcurrentHashMap<>(tables.getOrDefault(dbName, new ConcurrentHashMap<>()));
    }

    /**
     * Clear all data (for cleanup between tests).
     */
    public void clearAll()
    {
        databases.clear();
        tables.clear();
    }
}
