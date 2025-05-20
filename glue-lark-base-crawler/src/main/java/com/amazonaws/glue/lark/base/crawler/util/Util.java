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
package com.amazonaws.glue.lark.base.crawler.util;

import com.amazonaws.glue.lark.base.crawler.model.ColumnParameters;
import com.amazonaws.glue.lark.base.crawler.model.TableInputParameters;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.*;
import java.util.stream.Collectors;

import static com.amazonaws.glue.lark.base.crawler.LarkBaseCrawlerConstants.CRAWLING_METHOD;
import static com.amazonaws.glue.lark.base.crawler.LarkBaseCrawlerConstants.LARK_BASE_FLAG;

/**
 * Utility class for Lark Base Crawler
 */
public class Util {

    /**
     * Construct Lark Base Database Location URI Prefix
     * Format: lark-base-flag/CrawlingMethod=LarkBase/DataSource=<dataSourceId>
     * @param crawlingMethod The crawling method
     * @param crawlingSource The crawling source
     * @return The Lark Base Database Location URI Prefix
     */
    public static String constructDatabaseLocationURIPrefix(String crawlingMethod, String crawlingSource) {
        return LARK_BASE_FLAG + "/" + constructCrawlingMethod(crawlingMethod) + "/" + constructLarkBaseDataSourceId(crawlingSource);
    }

    /**
     * Construct Lark Base Database Location URI
     * Format: lark-base-flag/CrawlingMethod=<crawlingMethod>/DataSource=<crawlingSource>/Base=<databaseId>
     * @param crawlingMethod The Crawling Method
     * @param crawlingSource The Crawling Source
     * @return The Lark Base Database Location URI
     */
    public static String constructDatabaseLocationURI(String crawlingMethod, String crawlingSource, String larkBaseId) {
        return constructDatabaseLocationURIPrefix(crawlingMethod, crawlingSource) + "/" + constructLarkBaseBaseId(larkBaseId);
    }

    /**
     * Construct Lark Base Table Location URI
     * Format: lark-base-flag/CrawlingMethod=<crawlingMethod>/DataSource=<crawlingSource>/Base=<databaseId>/Table=<tableId>
     * @param crawlingMethod The Crawling Method
     * @param crawlingSource The Crawling Source
     * @param larkBaseId The Lark Base ID
     * @param larkTableId The Lark Table ID
     * @return The Lark Base Table Location URI
     */
    public static String constructTableLocationURI(String crawlingMethod, String crawlingSource, String larkBaseId, String larkTableId) {
        return constructDatabaseLocationURIPrefix(crawlingMethod, crawlingSource) + "/" + constructLarkBaseBaseId(larkBaseId) + "/" + constructLarkBaseTableId(larkTableId);
    }

    /**
     * Construct Crawling Method
     * Format: CrawlingMethod=<crawlingMethod>
     * @param crawlingMethod The crawling method
     * @return The crawling method
     */
    public static String constructCrawlingMethod(String crawlingMethod) {
        return CRAWLING_METHOD + "=" + crawlingMethod;
    }

    /**
     * Construct Lark Base Data Source ID
     * Format: DataSource=<crawlingSource>
     * @param crawlingSource The crawling source
     * @return The Lark Base Data Source ID
     */
    public static String constructLarkBaseDataSourceId(String crawlingSource) {
        return "DataSource=" + crawlingSource;
    }

    /**
     * Construct Lark Base Base ID
     * Format: Base=<larkBaseId>
     * @param larkBaseId The Lark Base ID
     * @return The Lark Base Base ID
     */
    public static String constructLarkBaseBaseId(String larkBaseId) {
        return "Base=" + larkBaseId;
    }

    /**
     * Construct Lark Base Table ID
     * Format: Table=<larkTableId>
     * @param larkTableId The Lark Table ID
     * @return The Lark Base Table ID
     */
    public static String constructLarkBaseTableId(String larkTableId) {
        return "Table=" + larkTableId;
    }

    /**
     * Extract Database ID from Location URI
     * Format: DB:<databaseId>
     * @param locationURI The Location URI
     * @return The Database ID
     */
    public static String extractDatabaseIdFromLocationURI(String locationURI) {
        if (locationURI == null || locationURI.isEmpty()) {
            return null;
        }

        int dbIndex = locationURI.lastIndexOf("Base=");
        if (dbIndex == -1) {
            return null;
        }

        String databaseId =  locationURI.substring(dbIndex + 5); // Skip "Base="

        if (databaseId.isEmpty()) {
            throw new RuntimeException("Could not extract database ID from locationURI: " + locationURI);
        }

        return databaseId;
    }

    // For Glue table little bit different from the above, because it has table properties
    // For classification properties, we can use "lark-base-flag"
    // For larkTableId, we can fill its lark table id
    // For larkBaseId, we can fill its lark base id
    // For larkBaseDataSourceId, we can fill its lark base data source id
    // For larkTableDataSourceId, we can fill its lark table data source id
    
    // agani.satria@XXXXX ~ % aws glue get-table --database-name test2 --name testlarkbase
    // {
    //     "Table": {
    //         "Name": "testlarkbase",
    //         "DatabaseName": "test2",
    //         "Description": "testlarkbase",
    //         "CreateTime": "2025-03-08T18:14:49+07:00",
    //         "UpdateTime": "2025-03-09T22:36:25+07:00",
    //         "Retention": 0,
    //         "StorageDescriptor": {
    //             "Columns": [
    //                 {
    //                     "Name": "ticket_id",
    //                     "Type": "string",
    //                     "Comment": ""
    //                 },
    //             "Location": "s3://abcdef-12345-ap-southeast-1/",
    //             "InputFormat": "org.apache.hadoop.mapred.TextInputFormat",
    //             "OutputFormat": "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
    //             "Compressed": false,
    //             "NumberOfBuckets": 0,
    //             "SerdeInfo": {
    //                 "SerializationLibrary": "org.apache.hadoop.hive.serde2.OpenCSVSerde",
    //                 "Parameters": {
    //                     "separatorChar": ","
    //                 }
    //             },
    //             "SortColumns": [],
    //             "StoredAsSubDirectories": false
    //         },
    //         "PartitionKeys": [],
    //         "TableType": "EXTERNAL_TABLE",
    //         "Parameters": {
    //             "classification": "lark-base-flag",
    //             "larkTableId": "test123",
    //             "larkBaseId": "test456"
    //         },
    //         "CreatedBy": "arn:aws:sts::12345:assumed-role/TEST/aganisatria1@gmail.com",
    //         "IsRegisteredWithLakeFormation": false,
    //         "CatalogId": "12345",
    //         "VersionId": "3"
    //     }
    // }

    /**
     * Construct Lark Base Table Input
     * @param params The Lark Base Table Input Parameters
     * @param columns The columns
     * @return The Lark Base Table Input
     */
    public static TableInput constructTableInput(
            TableInputParameters params,
            Collection<Column> columns,
            String crawlingMethod,
            String crawlingSource,
            Map<String, String> additionalParameters) {

        Map<String, String> paramsMap = new HashMap<>(Map.of(
                "classification", "lark-base-flag",
                "crawlingMethod", crawlingMethod,
                "larkTableId", params.getLarkTableId(),
                "larkBaseId", params.getLarkBaseId()
        ));

        paramsMap.putAll(additionalParameters);

        return TableInput.builder()
                .name(params.getLarkTableName())
                .parameters(paramsMap)
                .storageDescriptor(
                        StorageDescriptor.builder()
                                .columns(columns)
                                .location(constructTableLocationURI(crawlingMethod,
                                        crawlingSource,
                                        params.getLarkBaseId(),
                                        params.getLarkTableId()))
                                .inputFormat("org.apache.hadoop.mapred.TextInputFormat")
                                .outputFormat("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat")
                                .build()
                )
                .tableType("LARK_BASE_TABLE")
                .build();
    }

    /**
     * Construct Columns
     * @param params The column parameters
     * @return The columns
     */
    public static Collection<Column> constructColumns(ArrayList<ColumnParameters> params) {
        return params.stream()
                .map(param -> Column.builder()
                        .name(param.getColumnName())
                        .type(param.getColumnType())
                        // Comment: LarkBaseId=<larkBaseId>/LarkBaseTableId=<larkTableId>/
                        // LarkBaseFieldId=<larkBaseFieldId>/LarkBaseFieldName=<originalFieldName>/
                        // LarkBaseFieldType=<larkBaseFieldType>
                        // for larkBaseFieldType "Formula", we fill with "Formula<formula-type>"
                        .comment(generateColumnComment(param))
                        .build())
                .collect(Collectors.toList());
    }

    public static String generateColumnComment(ColumnParameters parameters) {
        return "LarkBaseId=" + parameters.getLarkBaseId() + "/LarkBaseTableId=" + parameters.getLarkBaseTableId() +
                "/LarkBaseFieldId=" + parameters.getLarkBaseRecordId() + "/LarkBaseFieldName=" + parameters.getOriginalColumnName() +
                "/LarkBaseFieldType=" + parameters.getLarkBaseColumnType();
    }

    /**
     * Check if the glue database name is valid
     * @param glueDatabaseNames The glue database names
     * @return True if the glue database name is valid, false otherwise
     */
    public static boolean doesGlueDatabasesNameValid(List<String> glueDatabaseNames) {
        // checking glue database name is only allowed to contain alphanumeric characters and underscores
        for (String glueDatabaseName : glueDatabaseNames) {
            if (!glueDatabaseName.matches("^[a-zA-Z0-9$_]+$")) {
                return true;
            }
        }

        // checking duplicate glue database name
        Set<String> glueDatabaseNameSet = new HashSet<>(glueDatabaseNames);
        return glueDatabaseNameSet.size() != glueDatabaseNames.size();
    }

    public static String sanitizeGlueRelatedName(String tableName) {
        return tableName.toLowerCase().replaceAll("[^a-zA-Z0-9$]", "_");
    }
}
