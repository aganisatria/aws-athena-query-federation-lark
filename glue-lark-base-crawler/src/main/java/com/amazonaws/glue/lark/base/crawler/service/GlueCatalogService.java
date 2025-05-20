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

import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Collections;

import static java.util.Objects.requireNonNull;

/**
 * Service for Glue Catalog
 */
public class GlueCatalogService {
    private final GlueClient glueClient;
    private final String catalogId;

    public GlueCatalogService(GlueClient glueClient, String catalogId) {
        requireNonNull(glueClient);
        this.glueClient = glueClient;
        this.catalogId = catalogId;
    }

    /**
     * Get all databases
     * @return The list of databases
     */
    public List<Database> getDatabases() {
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
     * @param databaseNames The list of database names
     */
    public void batchDeleteDatabase(List<String> databaseNames) {
        for (String databaseName : databaseNames) {
            glueClient.deleteDatabase(DeleteDatabaseRequest.builder()
                    .name(databaseName)
                    .catalogId(catalogId)
                    .build());
        }
    }

    /**
     * Batch create database.
     * There is no batch create database in Glue, so we need to create one by one.
     * @param databaseNamesWithLocationUri The map of database names and location uris
     */
    public void batchCreateDatabase(Map<String, String> databaseNamesWithLocationUri) {
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

            glueClient.createDatabase(request);
        }
    }


    /**
     * Batch update database.
     * There is no batch update database in Glue, so we need to update one by one.
     * @param databaseNamesWithLocationUri The map of database names and location uris
     */
    public void batchUpdateDatabase(Map<String, String> databaseNamesWithLocationUri) {
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

            glueClient.updateDatabase(UpdateDatabaseRequest.builder()
                    .name(databaseNameWithLocationUri.getKey())
                    .databaseInput(databaseInput)
                    .catalogId(catalogId)
                    .build());
        }
    }

    /**
     * Get all tables
     * @param larkBaseDataSourceId The Lark Base Data Source ID
     * @return The list of tables
     */
    public List<Table> getTables(String larkBaseDataSourceId) {
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
     * Batch delete table.
     * There is no batch delete table in Glue, so we need to delete one by one.
     * @param databaseNameAndTables The map of database names and tables
     */
    public void batchDeleteTable(Map<String, List<Table>> databaseNameAndTables) {
        for (Map.Entry<String, List<Table>> dbEntry : databaseNameAndTables.entrySet()) {
            glueClient.batchDeleteTable(
                    BatchDeleteTableRequest.builder()
                            .databaseName(dbEntry.getKey())
                            .tablesToDelete(dbEntry.getValue().stream()
                                    .map(Table::name)
                                    .collect(Collectors.toList()))
                            .catalogId(catalogId)
                            .build());
        }
    }

    /**
     * Batch create table.
     * There is no batch create table in Glue, so we need to create one by one.
     * @param databaseNameAndTableInputs The map of database names and table inputs
     */
    public void batchCreateTable(Map<String, List<TableInput>> databaseNameAndTableInputs) {
        for (Map.Entry<String, List<TableInput>> databaseNameAndTableInput : databaseNameAndTableInputs.entrySet()) {
            for (TableInput tableInput : databaseNameAndTableInput.getValue()) {
                glueClient.createTable(
                        CreateTableRequest.builder()
                            .databaseName(databaseNameAndTableInput.getKey())
                            .tableInput(tableInput)
                            .catalogId(catalogId)
                            .build());
            }
        }
    }

    /**
     * Batch update table.
     * There is no batch update table in Glue, so we need to update one by one.
     * @param databaseNameAndTableInputs The map of database names and table inputs
     */
    public void batchUpdateTable(Map<String, List<TableInput>> databaseNameAndTableInputs) {
        for (Map.Entry<String, List<TableInput>> databaseNameAndTableInput : databaseNameAndTableInputs.entrySet()) {
            for (TableInput tableInput : databaseNameAndTableInput.getValue()) {
                glueClient.updateTable(
                        UpdateTableRequest.builder()
                            .databaseName(databaseNameAndTableInput.getKey())
                            .tableInput(tableInput)
                            .catalogId(catalogId)
                            .build());
            }
        }
    }
}
