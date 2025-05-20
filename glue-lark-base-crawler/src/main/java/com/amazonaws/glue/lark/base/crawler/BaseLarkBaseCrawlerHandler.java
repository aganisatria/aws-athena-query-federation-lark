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

import com.amazonaws.glue.lark.base.crawler.model.*;
import com.amazonaws.glue.lark.base.crawler.model.enums.UITypeEnum;
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllTableResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListFieldResponse;
import com.amazonaws.glue.lark.base.crawler.service.GlueCatalogService;
import com.amazonaws.glue.lark.base.crawler.service.LarkBaseService;
import com.amazonaws.glue.lark.base.crawler.service.LarkDriveService;
import com.amazonaws.glue.lark.base.crawler.service.STSService;
import com.amazonaws.glue.lark.base.crawler.util.Util;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.utils.Pair;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for Lark Base Crawler Handler, can be extended by other classes to implement specific logic
 */
abstract class BaseLarkBaseCrawlerHandler implements RequestHandler<Object, String> {

    private static final Logger logger = LoggerFactory.getLogger(BaseLarkBaseCrawlerHandler.class);
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int CONNECT_TIMEOUT = 250;

    private final GlueCatalogService glueCatalogService;
    protected final LarkBaseService larkBaseService;
    protected final LarkDriveService larkDriveService;
    protected final STSService stsService;

    public BaseLarkBaseCrawlerHandler() {
        try {
            this.stsService = new STSService();
            String catalogId = stsService.getAccountId();
            this.glueCatalogService = new GlueCatalogService(GlueClient.builder()
                    .httpClientBuilder(ApacheHttpClient
                            .builder()
                            .connectionTimeout(Duration.ofMillis(CONNECT_TIMEOUT)))
                    .build(), catalogId);

            SecretsManagerClient secretsManager = SecretsManagerClient.create();

            String larkAppSecretManagerEnvVar = System.getenv(LarkBaseCrawlerConstants.LARK_APP_KEY_ENV_VAR);

            if (larkAppSecretManagerEnvVar == null) {
                throw new RuntimeException("Environment variables " + LarkBaseCrawlerConstants.LARK_APP_KEY_ENV_VAR);
            }

            String rawLarkApp = secretsManager.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(larkAppSecretManagerEnvVar)
                    .build()).secretString();

            SecretValue secretMapping = OBJECT_MAPPER.readValue(rawLarkApp, SecretValue.class);
            String larkAppID = secretMapping.larkAppId();
            String larkAppSecret = secretMapping.larkAppSecret();

            this.larkBaseService = new LarkBaseService(larkAppID, larkAppSecret);
            this.larkDriveService = new LarkDriveService(larkAppID, larkAppSecret);
        } catch (Exception e) {
            throw new RuntimeException(e);

        }
    }

    /**
     * Crawling Method
     */
    abstract String getCrawlingMethod();

    /**
     * Get Crawling Source
     * @return The crawling source
     */
    abstract String getCrawlingSource();

    /**
     * Get Lark Databases
     * @return The Lark Databases
     */
    abstract List<LarkDatabaseRecord> getLarkDatabases();

    /**
     * Get Glue Databases
     * @return The Glue Databases
     */
    private List<Database> getGlueDatabases() {
        List<Database> databases = glueCatalogService.getDatabases();

        return databases.stream()
                .filter(database -> database.locationUri() != null &&
                        database.locationUri().contains(Util.constructDatabaseLocationURIPrefix(getCrawlingMethod(), getCrawlingSource())))
                .collect(Collectors.toList());
    }

    /**
     * Get Additional Table Input Parameters when comparing new and old data
     * @return The Table Input
     */
    abstract Map<String, String> getAdditionalTableInputParameter();

    /**
     * Additional comparator of table input changed
     * @param newTableInput New Table Input
     * @param existingTable Existing Table Object
     */
    abstract boolean additionalTableInputChanged(TableInput newTableInput, Table existingTable);


    /**
     * Check if the table input changed
     * @param newTableInput The new table input
     * @param existingTable The existing table
     * @return True if the table input changed, false otherwise
     */
    boolean doesTableInputChanged(TableInput newTableInput, Table existingTable) {
        Map<String, String> newParams = newTableInput.parameters();
        Map<String, String> existingParams = existingTable.parameters();

        // Compare parameters
        if (!newParams.get("crawlingMethod").equals(existingParams.get("crawlingMethod")) ||
                !newParams.get("larkTableId").equals(existingParams.get("larkTableId")) ||
                !newParams.get("larkBaseId").equals(existingParams.get("larkBaseId"))) {
            return true;
        }

        // Compare storageDescriptor
        if (!newTableInput.storageDescriptor().columns()
                .equals(existingTable.storageDescriptor().columns())) {
            return true;
        }

        return additionalTableInputChanged(newTableInput, existingTable);
    }

    /**
     * Remove Non Existent Lark Databases
     * @param databasesFromCatalog The databases from catalog
     * @param databaseFromLark The database from Lark
     * @return The remaining databases
     */
    private List<Database> removeNonExistentLarkDatabases(List<Database> databasesFromCatalog,
                                                          List<LarkDatabaseRecord> databaseFromLark) {
        List<String> databaseNamesToDelete = new ArrayList<>();
        Set<String> larkDatabaseNames = databaseFromLark.stream()
                .map(LarkDatabaseRecord::name)
                .collect(Collectors.toSet());

        List<Database> remainingDatabases = new ArrayList<>();

        for (Database database : databasesFromCatalog) {
            if (!larkDatabaseNames.contains(database.name())) {
                databaseNamesToDelete.add(database.name());
            } else {
                remainingDatabases.add(database);
            }
        }

        if (!databaseNamesToDelete.isEmpty()) {
            glueCatalogService.batchDeleteDatabase(databaseNamesToDelete);
        }

        return remainingDatabases;
    }

    /**
     * Construct New Tables
     * @param databaseId The database ID
     * @param tableId The table ID
     * @param tableName The table name
     * @return The table input
     */
    private TableInput constructNewTables(String databaseId, String tableId, String tableName) {
        List<ListFieldResponse.FieldItem> listFieldResponse = larkBaseService.getTableFields(databaseId, tableId);

        ArrayList<ColumnParameters> columns = listFieldResponse.stream()
                .map(fieldItem -> ColumnParameters.builder()
                        .columnName(fieldItem.getFieldName())
                        .columnType(fieldItem.getUIType().getGlueCatalogType(getFormulaOrLookupFieldType(fieldItem, databaseId)))
                        .larkBaseFieldId(fieldItem.getFieldId())
                        .larkBaseColumnType(getLarkBaseOriginalColumnType(fieldItem, databaseId))
                        .larkBaseId(databaseId)
                        .larkBaseTableId(tableId)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        Collection<Column> fixedColumns = Util.constructColumns(columns);

        return Util.constructTableInput(
                TableInputParameters.builder()
                        .larkTableName(tableName)
                        .larkBaseId(databaseId)
                        .larkTableId(tableId)
                        .build(),
                fixedColumns,
                getCrawlingMethod(),
                getCrawlingSource(),
                getAdditionalTableInputParameter()
        );
    }

    private Optional<ListFieldResponse.FieldItem> getLookupType(ListFieldResponse.FieldItem item, String baseId) {
        Pair<String, String> metadata = item.getLookupSourceFieldAndTableId();
        if (metadata == null) {
            return Optional.empty();
        }

        String fieldId = metadata.left();
        String tableId = metadata.right();

        try {
            return larkBaseService.getTableFields(baseId, tableId)
                    .stream()
                    .filter(fieldItem -> fieldItem.getFieldId().equals(fieldId))
                    .findFirst();
        } catch (Exception e) {
            logger.error("Error getting lookup type for fieldId: {} and tableId: {}", fieldId, tableId, e);
            return Optional.empty();
        }
    }

    private String getLarkBaseOriginalColumnType(ListFieldResponse.FieldItem item, String baseId) {
        if (item.getUIType().equals(UITypeEnum.FORMULA)) {
            return item.getUIType().getUiType() + "<" + item.getFormulaType() + ">";
        } else if (item.getUIType().equals(UITypeEnum.LOOKUP)) {
            Optional<ListFieldResponse.FieldItem> fieldOptional = getLookupType(item, baseId);
            if (fieldOptional.isPresent()) {
                ListFieldResponse.FieldItem field = fieldOptional.get();
                if (field.getUIType() != null) {
                    try {
                        UITypeEnum newUIType = field.getUIType();

                        if (newUIType == UITypeEnum.LOOKUP) {
                            return item.getUIType().getUiType() + "<" + getLarkBaseOriginalColumnType(field, baseId) + ">";
                        } else {
                            return item.getUIType().getUiType() + "<" + newUIType.getUiType() + ">";
                        }

                    } catch (IllegalArgumentException e) {
                        return item.getUIType().getUiType() + "<NULL>";
                    }
                } else {
                    return item.getUIType().getUiType() + "<NULL>";
                }
            }

            return item.getUIType().getUiType() + "<NULL>";
        }

        return item.getUIType().getUiType();
    }

    private String getFormulaOrLookupFieldType(ListFieldResponse.FieldItem item, String baseId) {
        switch (item.getUIType()) {
            case FORMULA:
                return item.getFormulaGlueCatalogType();
            case LOOKUP:
                Optional<ListFieldResponse.FieldItem> fieldOptional = getLookupType(item, baseId);
                if (fieldOptional.isPresent()) {
                    ListFieldResponse.FieldItem field = fieldOptional.get();
                    if (field.getUIType() != null) {
                        try {
                            UITypeEnum newUIType = field.getUIType();

                            if (newUIType == UITypeEnum.LOOKUP) {
                                return getFormulaOrLookupFieldType(field, baseId);
                            } else {
                                return newUIType.getGlueCatalogType(null);
                            }

                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    } else {
                        return null;
                    }
                }

                return null;
            default:
                return null;
        }
    }

    /**
     * Create Tables for New Databases
     * @param databaseToCreate The database to create
     */
    private void createTablesForNewDatabases(Map<String, String> databaseToCreate) {
        Map<String, List<TableInput>> batchCreateTableRequest = new HashMap<>();

        for (Map.Entry<String, String> databaseItem : databaseToCreate.entrySet()) {
            String databaseName = databaseItem.getKey();
            String locationURI = databaseItem.getValue();
            String databaseId = Util.extractDatabaseIdFromLocationURI(locationURI);

            List<TableInput> tableInputs = new ArrayList<>();

            List<ListAllTableResponse.BaseItem> listTables = larkBaseService.listTables(databaseId);

            for (ListAllTableResponse.BaseItem tableItem : listTables) {
                TableInput newTableInput = this.constructNewTables(databaseId, tableItem.getTableId(), tableItem.getName());
                tableInputs.add(newTableInput);
            }

            if (!tableInputs.isEmpty()) {
                batchCreateTableRequest.put(databaseName, tableInputs);
            }
        }

        if (!batchCreateTableRequest.isEmpty()) {
            logger.info("Creating tables for {} databases", batchCreateTableRequest.size());
            glueCatalogService.batchCreateTable(batchCreateTableRequest);
            logger.info("Tables on database {} created successfully", databaseToCreate.keySet());
        } else {
            logger.info("No tables to create on database {}", databaseToCreate.keySet());
        }
    }

    /**
     * Create Glue Databases
     * @param databasesFromCatalog The databases from catalog
     * @param databaseFromLark The database from Lark
     * @return The database process result
     */
    private DatabaseProcessResult createGlueDatabases(List<Database> databasesFromCatalog,
                                                      List<LarkDatabaseRecord> databaseFromLark) {
        Map<String, String> databaseToCreate = new HashMap<>();
        Set<String> existingDatabaseNames = databasesFromCatalog.stream()
                .map(Database::name)
                .collect(Collectors.toSet());

        List<LarkDatabaseRecord> recordsToKeep = new ArrayList<>();
        List<Database> remainingDatabases = new ArrayList<>(databasesFromCatalog);

        for (LarkDatabaseRecord recordItem : databaseFromLark) {
            if (!existingDatabaseNames.contains(recordItem.name())) {
                databaseToCreate.put(recordItem.name(), Util.constructDatabaseLocationURI(
                        getCrawlingMethod(),getCrawlingSource(), recordItem.id()));
                logger.info("Marking database for creation: {}, locationUri: {}",
                        recordItem.name(), Util.constructDatabaseLocationURIPrefix(
                                getCrawlingMethod(), getCrawlingSource()
                        ));
            } else {
                recordsToKeep.add(recordItem);
            }
        }

        if (!databaseToCreate.isEmpty()) {
            logger.info("Creating {} databases", databaseToCreate.size());
            glueCatalogService.batchCreateDatabase(databaseToCreate);

            // Filter out databases that were created
            remainingDatabases = databasesFromCatalog.stream()
                    .filter(database -> !databaseToCreate.containsKey(database.name()))
                    .collect(Collectors.toList());

            logger.info("Databases created successfully");

            // Step 4.1: Create tables for new databases
            logger.info("Step 4.1: Creating tables for new databases");
            this.createTablesForNewDatabases(databaseToCreate);
            logger.info("Step 4.1: Tables created successfully");
        } else {
            logger.info("No databases to create");
        }

        return new DatabaseProcessResult(remainingDatabases, recordsToKeep, databaseToCreate);
    }

    /**
     * Update Glue Databases
     * @param databasesFromCatalog The databases from catalog
     * @param databaseFromLark The database from Lark
     */
    private void updateGlueDatabases(List<Database> databasesFromCatalog,
                                     List<LarkDatabaseRecord> databaseFromLark) {

        Map<String, String> databaseToUpdate = new HashMap<>();
        Map<String, List<TableInput>> tablesToCreate = new HashMap<>();
        Map<String, List<TableInput>> tablesToUpdate = new HashMap<>();
        Map<String, List<Table>> tablesToDelete = new HashMap<>();

        for (Database database : databasesFromCatalog) {
            for (LarkDatabaseRecord recordItem : databaseFromLark) {
                String pivotLocationURI = Util.constructDatabaseLocationURI(
                        getCrawlingMethod(), getCrawlingSource(), recordItem.id());

                if (database.name().equals(recordItem.name())) {
                    if (!database.locationUri().equals(pivotLocationURI)) {
                        logger.info("Step 5.1: Identifying databases that have changed");
                        databaseToUpdate.put(database.name(), pivotLocationURI);
                        logger.info("Step 5.1: Finished identifying databases that have changed");
                    } else {
                        logger.info("No changes needed for database: {}", database.name());
                    }

                    UpdateDatabaseProcessResult result = processTablesForDatabase(database, recordItem);

                    if (result.tablesToDelete() != null && !result.tablesToDelete().isEmpty()) {
                        tablesToDelete.put(database.name(), result.tablesToDelete());
                    }

                    if (result.tablesToCreate() != null && !result.tablesToCreate().isEmpty()) {
                        tablesToCreate.put(database.name(), result.tablesToCreate());
                    }

                    if (result.tablesToUpdate() != null && !result.tablesToUpdate().isEmpty()) {
                        tablesToUpdate.put(database.name(), result.tablesToUpdate());
                    }
                }
            }
        }

        logger.info("Step 6: Updating databases");
        if (!databaseToUpdate.isEmpty()) {
            glueCatalogService.batchUpdateDatabase(databaseToUpdate);
            logger.info("Step 6: Databases updated successfully");
        } else {
            logger.info("Step 6: No databases to update");
        }
        logger.info("Step 6: Finished updating databases");

        logger.info("Step 7: Deleting tables that don't exist in Lark anymore");
        if (!tablesToDelete.isEmpty()) {
            glueCatalogService.batchDeleteTable(tablesToDelete);
            logger.info("Step 7: Tables deleted successfully");
        } else {
            logger.info("Step 7: No tables to delete");
        }
        logger.info("Step 7: Finished deleting tables that don't exist in Lark anymore");

        logger.info("Step 8: Creating tables that exist in Lark but not in Glue");
        if (!tablesToCreate.isEmpty()) {
            glueCatalogService.batchCreateTable(tablesToCreate);
            logger.info("Step 8: Tables created successfully");
        } else {
            logger.info("Step 8: No tables to create");
        }
        logger.info("Step 8: Finished creating tables that exist in Lark but not in Glue");

        logger.info("Step 9: Updating tables with changed metadata");
        if (!tablesToUpdate.isEmpty()) {
            glueCatalogService.batchUpdateTable(tablesToUpdate);
            logger.info("Step 9: Tables updated successfully");
        } else {
            logger.info("Step 9: No tables to update");
        }
        logger.info("Step 9: Finished updating tables with changed metadata");
    }

    /**
     * Process Tables for Database
     * @param database The database
     * @param recordItem The record item
     * @return The update database process result
     */
    private UpdateDatabaseProcessResult processTablesForDatabase(Database database,
                                                                 LarkDatabaseRecord recordItem) {

        logger.info("Step 5.2.1: Getting existing tables from Glue");
        List<Table> originalExistingTables = glueCatalogService.getTables(database.name());
        logger.info("Step 5.2.1: Found {} tables in Glue for database: {}", originalExistingTables.size(), database.name());

        logger.info("Step 5.2.2: Getting tables from Lark");
        List<ListAllTableResponse.BaseItem> larkTables = larkBaseService.listTables(recordItem.id());
        logger.info("Step 5.2.2: Found {} tables in Lark for database: {}", larkTables.size(), database.name());

        Map<String, String> larkTableNameMap = new HashMap<>();
        Map<String, String> glueTableNameMap = new HashMap<>();

        Set<String> existingTableNamesLower = originalExistingTables.stream()
                .map(table -> {
                    String originalName = table.name();
                    String lowerName = originalName.toLowerCase();
                    glueTableNameMap.put(lowerName, originalName);
                    return lowerName;
                })
                .collect(Collectors.toSet());

        Set<String> larkTableNamesLower = larkTables.stream()
                .map(item -> {
                    String originalName = item.getName();
                    String lowerName = originalName.toLowerCase();
                    larkTableNameMap.put(lowerName, originalName);
                    return lowerName;
                })
                .collect(Collectors.toSet());

        logger.info("Step 5.2.3: Identifying tables to delete");
        TableOnUpdateDatabaseProcessResult<Table> deleteResult = identifyTablesToDelete(originalExistingTables, larkTableNamesLower);
        List<Table> existingTables = deleteResult.itemsToKeep();
        List<Table> tablesToDelete = deleteResult.itemsToProcess();
        logger.info("Step 5.2.3: Finished identifying tables to delete");

        logger.info("Step 5.2.4: Identifying tables to create");
        List<TableInput> tablesToCreate = identifyTablesToCreate(recordItem.id(), larkTables,
                existingTables, larkTableNamesLower, larkTableNameMap, glueTableNameMap);
        logger.info("Step 5.2.4: Finished identifying tables to create");

        logger.info("Step 5.2.5: Identifying tables to update");
        List<TableInput> tablesToUpdate = identifyTablesToUpdate(recordItem.id(),
                larkTables, existingTables, existingTableNamesLower);
        logger.info("Step 5.2.5: Finished identifying tables to update");

        return new UpdateDatabaseProcessResult(tablesToCreate, tablesToUpdate, tablesToDelete);
    }

    /**
     * Identify Tables to Delete
     * @param existingTables The existing tables
     * @param larkTableNamesLower The Lark table names lower
     * @return The table on update database process result
     */
    private TableOnUpdateDatabaseProcessResult<Table> identifyTablesToDelete(List<Table> existingTables,
                                                                             Set<String> larkTableNamesLower) {
        List<Table> tablesToDelete = new ArrayList<>();
        List<Table> tablesToKeep = new ArrayList<>();

        for (Table table : existingTables) {
            String tableName = table.name();
            String tableNameLower = tableName.toLowerCase();

            boolean existsInLark = larkTableNamesLower.contains(tableNameLower);

            if (!existsInLark) {
                logger.info("Step 5.3.1: Marking table for deletion: {}", tableName);
                tablesToDelete.add(table);
            } else {
                tablesToKeep.add(table);
            }
        }

        return new TableOnUpdateDatabaseProcessResult<>(tablesToDelete, tablesToKeep);
    }

    /**
     * Identify Tables to Create
     * @param databaseId The database ID
     * @param larkTables The Lark tables
     * @param existingTables The existing tables
     * @param larkTableNamesLower The Lark table names lower
     * @param larkTableNameMap The Lark table name map
     * @param glueTableNameMap The Glue table name map
     * @return The tables to create
     */
    private List<TableInput> identifyTablesToCreate(String databaseId,
                                                    List<ListAllTableResponse.BaseItem> larkTables,
                                                    List<Table> existingTables,
                                                    Set<String> larkTableNamesLower,
                                                    Map<String, String> larkTableNameMap,
                                                    Map<String, String> glueTableNameMap) {
        List<TableInput> tablesToCreate = new ArrayList<>();
        Set<String> larkTableNamesToProcess = new HashSet<>(larkTableNamesLower);

        for (String tableName : larkTableNamesToProcess) {
            // INFO: Find table name in Glue
            if (!existingTables.stream()
                    .map(Table::name)
                    .collect(Collectors.toSet())
                    .contains(glueTableNameMap.get(tableName))) {

                // INFO: Find ID table in Lark based on table name in Lark
                String tableId = larkTables.stream()
                        .filter(item -> item.getName().equals(larkTableNameMap.get(tableName)))
                        .map(ListAllTableResponse.BaseItem::getTableId)
                        .findFirst()
                        .orElse(null);

                if (tableId != null) {
                    TableInput newTableInput = this.constructNewTables(databaseId, tableId, tableName);
                    tablesToCreate.add(newTableInput);
                }
            }
        }

        return tablesToCreate;
    }

    /**
     * Identify Tables to Update
     * @param databaseId The database ID
     * @param larkTables The Lark tables
     * @param existingTables The existing tables
     * @param existingTableNamesLower The existing table names lower
     * @return The tables to update
     */
    private List<TableInput> identifyTablesToUpdate(String databaseId,
                                                    List<ListAllTableResponse.BaseItem> larkTables,
                                                    List<Table> existingTables,
                                                    Set<String> existingTableNamesLower) {
        List<TableInput> tablesToUpdate = new ArrayList<>();

        for (ListAllTableResponse.BaseItem tableItem : larkTables) {
            String tableName = tableItem.getName();
            if (existingTableNamesLower.contains(tableName.toLowerCase())) {

                // INFO: Find existing table in Glue based on table name in Lark
                Table existingTable = existingTables.stream()
                        .filter(table -> table.name().equalsIgnoreCase(tableName))
                        .findFirst()
                        .orElse(null);

                if (existingTable != null) {
                    TableInput tableInput = this.constructNewTables(databaseId, tableItem.getTableId(), tableName);
                    boolean hasChanged = doesTableInputChanged(tableInput, existingTable);
                    logger.info("Table {} has changed: {}", tableName, hasChanged);

                    if (hasChanged) {
                        tablesToUpdate.add(tableInput);
                        logger.info("Marking table for update: {}", tableName);
                    }
                }
            }
        }

        return tablesToUpdate;
    }

    /**
     * Handle Request
     * @param input The input
     * @param context The context
     * @return The result
     */
    @Override
    public String handleRequest(Object input, Context context) {
        // Step 1: Get records from Lark
        logger.info("Step 1: Fetching records from Lark Base");
        List<LarkDatabaseRecord> listRecordsResponse = this.getLarkDatabases();
        logger.info("Step 1.1: info listRecordsResponse: {}", listRecordsResponse);
        logger.info("Retrieved {} records from Lark Base", listRecordsResponse.size());

        // Step 2: Get databases from Glue Catalog
        logger.info("Step 2: Fetching databases from Glue Catalog");
        List<Database> databaseNames = this.getGlueDatabases();
        logger.info("Step 2.1: info databaseNames: {}", databaseNames);
        logger.info("Retrieved {} databases from Glue Catalog", databaseNames.size());

        // Step 3: Delete databases that don't exist in Lark anymore
        logger.info("Step 3: Deleting databases that don't exist in Lark anymore");
        List<Database> remainingDatabases = this.removeNonExistentLarkDatabases(databaseNames, listRecordsResponse);
        logger.info("Step 3: Databases deleted successfully");

        // Step 4: Create databases that exist in Lark but not in Glue
        logger.info("Step 4: Creating databases that exist in Lark but not in Glue");
        logger.info("Step 4.1: info remaining databases: {}", remainingDatabases);
        DatabaseProcessResult creationResult = this.createGlueDatabases(remainingDatabases, listRecordsResponse);
        logger.info("Step 4.2: info creation result: {}", creationResult);
        logger.info("Step 4: Databases created successfully");

        // Step 5: Update databases that have changed
        logger.info("Step 5: Updating databases that have changed");
        this.updateGlueDatabases(creationResult.remainingDatabases(), creationResult.remainingLarkRecords());
        logger.info("Step 5: Databases updated successfully");

        return "Success";
    }

}
