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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.AlreadyExistsException;
import software.amazon.awssdk.services.glue.model.BatchDeleteTableRequest;
import software.amazon.awssdk.services.glue.model.BatchDeleteTableResponse;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.DataLakePrincipal;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.DeleteDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetDatabasesRequest;
import software.amazon.awssdk.services.glue.model.GetDatabasesResponse;
import software.amazon.awssdk.services.glue.model.GetTablesRequest;
import software.amazon.awssdk.services.glue.model.GetTablesResponse;
import software.amazon.awssdk.services.glue.model.Permission;
import software.amazon.awssdk.services.glue.model.PrincipalPermissions;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.glue.model.UpdateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.UpdateTableRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Service for Glue Catalog
 */
public class GlueCatalogService
{
    private static final Logger logger = LoggerFactory.getLogger(GlueCatalogService.class);

    // AWS Glue's BatchDeleteTable enforces a max of 100 table names per call.
    private static final int GLUE_BATCH_DELETE_TABLE_LIMIT = 100;

    private final GlueClient glueClient;
    private final String catalogId;

    public GlueCatalogService(GlueClient glueClient, String catalogId)
    {
        requireNonNull(glueClient);
        this.glueClient = glueClient;
        this.catalogId = catalogId;
    }

    /**
     * Get all databases
     *
     * @return The list of databases
     */
    public List<Database> getDatabases()
    {
        List<Database> allDatabases = new ArrayList<>();
        String nextToken = null;

        do {
            GetDatabasesRequest request = GetDatabasesRequest.builder()
                    .catalogId(catalogId)
                    .nextToken(nextToken)
                    .build();

            GetDatabasesResponse response = glueClient.getDatabases(request);
            allDatabases.addAll(response.databaseList());
            nextToken = response.nextToken();
        } while (nextToken != null);

        return allDatabases;
    }

    /**
     * Batch delete database.
     * There is no batch delete database in Glue, so we need to delete one by one.
     *
     * @param databaseNames The list of database names
     */
    public void batchDeleteDatabase(List<String> databaseNames)
    {
        for (String databaseName : databaseNames) {
            // Isolate one database's delete failure (e.g. a concurrent invocation already deleted it)
            // instead of aborting deletion of the rest of the batch.
            try {
                glueClient.deleteDatabase(DeleteDatabaseRequest.builder()
                        .name(databaseName)
                        .catalogId(catalogId)
                        .build());
            }
            catch (Exception e) {
                logger.error("Failed to delete database {}: {}", databaseName, e.getMessage(), e);
            }
        }
    }

    /**
     * Batch create database.
     * There is no batch create database in Glue, so we need to create one by one.
     *
     * @param databaseNamesWithLocationUri The map of database names and location uris
     */
    public void batchCreateDatabase(Map<String, String> databaseNamesWithLocationUri)
    {
        for (Map.Entry<String, String> databaseNameWithLocationUri : databaseNamesWithLocationUri.entrySet()) {
            DatabaseInput databaseInput = DatabaseInput.builder()
                    .name(databaseNameWithLocationUri.getKey())
                    .locationUri(databaseNameWithLocationUri.getValue())
                    .createTableDefaultPermissions(
                            List.of(
                                    PrincipalPermissions.builder()
                                            .principal(
                                                    DataLakePrincipal.builder()
                                                            .dataLakePrincipalIdentifier("IAM_ALLOWED_PRINCIPALS")
                                                            .build()
                                            )
                                            .permissions(Collections.singletonList(Permission.ALL))
                                            .build()
                            )
                    ).build();

            CreateDatabaseRequest request = CreateDatabaseRequest.builder()
                    .databaseInput(databaseInput)
                    .catalogId(catalogId)
                    .build();

            // Observed in production: a concurrent/overlapping crawler invocation can create the same
            // database first, so this call throws AlreadyExistsException - which is harmless (the
            // database is there either way) but, left uncaught, aborted this whole loop before it ever
            // reached the remaining databases in the batch. Since the caller (createGlueDatabases) goes
            // on to create tables for every database in this batch regardless of which of these calls
            // actually succeeded, isolating one database's failure here is enough to let table creation
            // proceed normally for the rest instead of getting stuck retrying forever with zero tables.
            try {
                glueClient.createDatabase(request);
            }
            catch (AlreadyExistsException e) {
                logger.info("Database {} already exists, skipping creation.", databaseNameWithLocationUri.getKey());
            }
            catch (Exception e) {
                logger.error("Failed to create database {}: {}", databaseNameWithLocationUri.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * Batch update database.
     * There is no batch update database in Glue, so we need to update one by one.
     *
     * @param databaseNamesWithLocationUri The map of database names and location uris
     */
    public void batchUpdateDatabase(Map<String, String> databaseNamesWithLocationUri)
    {
        for (Map.Entry<String, String> databaseNameWithLocationUri : databaseNamesWithLocationUri.entrySet()) {
            DatabaseInput databaseInput = DatabaseInput.builder()
                    .name(databaseNameWithLocationUri.getKey())
                    .locationUri(databaseNameWithLocationUri.getValue())
                    .createTableDefaultPermissions(
                            List.of(
                                    PrincipalPermissions.builder()
                                            .principal(
                                                    DataLakePrincipal.builder()
                                                            .dataLakePrincipalIdentifier("IAM_ALLOWED_PRINCIPALS")
                                                            .build()
                                            )
                                            .permissions(Collections.singletonList(Permission.ALL))
                                            .build()
                            )
                    ).build();

            // Isolate one database's update failure instead of aborting the rest of the batch.
            try {
                glueClient.updateDatabase(UpdateDatabaseRequest.builder()
                        .name(databaseNameWithLocationUri.getKey())
                        .databaseInput(databaseInput)
                        .catalogId(catalogId)
                        .build());
            }
            catch (Exception e) {
                logger.error("Failed to update database {}: {}", databaseNameWithLocationUri.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * Get all tables
     *
     * @param larkBaseDataSourceId The Lark Base Data Source ID
     * @return The list of tables
     */
    public List<Table> getTables(String larkBaseDataSourceId)
    {
        List<Table> allTables = new ArrayList<>();
        String nextToken = null;

        do {
            GetTablesRequest request = GetTablesRequest.builder()
                    .catalogId(catalogId)
                    .databaseName(larkBaseDataSourceId)
                    .nextToken(nextToken)
                    .build();

            GetTablesResponse response = glueClient.getTables(request);
            allTables.addAll(response.tableList());
            nextToken = response.nextToken();
        } while (nextToken != null);

        return allTables;
    }

    /**
     * Batch delete table. Unlike batchCreateTable/batchUpdateTable/the database equivalents (Glue has no
     * real batch create/update API, so those are single-item loops), BatchDeleteTable is a genuine Glue
     * batch API - and can report PER-TABLE failures inside an overall HTTP 200 response
     * ({@link BatchDeleteTableResponse#hasErrors()}/{@link BatchDeleteTableResponse#errors()}), which this
     * method used to silently discard entirely (the response wasn't even captured). A table that fails to
     * delete for a persistent Glue-side reason would then linger in the catalog indefinitely with no
     * operator-visible signal - every future crawl would recompute the same deletion candidate and hit the
     * same silent failure again. Also chunks each database's table list to Glue's documented 100-item
     * BatchDeleteTable limit, since a database needing more than 100 deletions in one crawl would
     * otherwise fail the entire batch.
     *
     * @param databaseNameAndTables The map of database names and tables
     */
    public void batchDeleteTable(Map<String, List<Table>> databaseNameAndTables)
    {
        for (Map.Entry<String, List<Table>> dbEntry : databaseNameAndTables.entrySet()) {
            String databaseName = dbEntry.getKey();
            List<String> tableNames = dbEntry.getValue().stream().map(Table::name).collect(Collectors.toList());

            for (int i = 0; i < tableNames.size(); i += GLUE_BATCH_DELETE_TABLE_LIMIT) {
                List<String> chunk = tableNames.subList(i, Math.min(i + GLUE_BATCH_DELETE_TABLE_LIMIT, tableNames.size()));
                // Isolate one chunk's delete-table batch failure instead of aborting the rest.
                try {
                    BatchDeleteTableResponse response = glueClient.batchDeleteTable(
                            BatchDeleteTableRequest.builder()
                                    .databaseName(databaseName)
                                    .tablesToDelete(chunk)
                                    .catalogId(catalogId)
                                    .build());

                    if (response.hasErrors() && !response.errors().isEmpty()) {
                        response.errors().forEach(tableError ->
                                logger.error("Failed to delete table {}.{}: {} ({})",
                                        databaseName, tableError.tableName(),
                                        tableError.errorDetail().errorMessage(), tableError.errorDetail().errorCode()));
                    }
                }
                catch (Exception e) {
                    logger.error("Failed to delete tables in database {}: {}", databaseName, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Batch create table.
     * There is no batch create table in Glue, so we need to create one by one.
     *
     * @param databaseNameAndTableInputs The map of database names and table inputs
     */
    public void batchCreateTable(Map<String, List<TableInput>> databaseNameAndTableInputs)
    {
        for (Map.Entry<String, List<TableInput>> databaseNameAndTableInput : databaseNameAndTableInputs.entrySet()) {
            for (TableInput tableInput : databaseNameAndTableInput.getValue()) {
                // Isolate one table's create failure (e.g. AlreadyExistsException from a concurrent
                // invocation, same race condition as batchCreateDatabase above) instead of aborting
                // creation of every other table across every other database in this same batch.
                try {
                    glueClient.createTable(
                            CreateTableRequest.builder()
                                    .databaseName(databaseNameAndTableInput.getKey())
                                    .tableInput(tableInput)
                                    .catalogId(catalogId)
                                    .build());
                }
                catch (AlreadyExistsException e) {
                    logger.info("Table {}.{} already exists, skipping creation.", databaseNameAndTableInput.getKey(), tableInput.name());
                }
                catch (Exception e) {
                    logger.error("Failed to create table {}.{}: {}", databaseNameAndTableInput.getKey(), tableInput.name(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Batch update table.
     * There is no batch update table in Glue, so we need to update one by one.
     *
     * @param databaseNameAndTableInputs The map of database names and table inputs
     */
    public void batchUpdateTable(Map<String, List<TableInput>> databaseNameAndTableInputs)
    {
        for (Map.Entry<String, List<TableInput>> databaseNameAndTableInput : databaseNameAndTableInputs.entrySet()) {
            for (TableInput tableInput : databaseNameAndTableInput.getValue()) {
                // Isolate one table's update failure instead of aborting every other table update
                // across every other database in this same batch.
                try {
                    glueClient.updateTable(
                            UpdateTableRequest.builder()
                                    .databaseName(databaseNameAndTableInput.getKey())
                                    .tableInput(tableInput)
                                    .catalogId(catalogId)
                                    .build());
                }
                catch (Exception e) {
                    logger.error("Failed to update table {}.{}: {}", databaseNameAndTableInput.getKey(), tableInput.name(), e.getMessage(), e);
                }
            }
        }
    }
}
