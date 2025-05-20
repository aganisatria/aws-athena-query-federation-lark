/*-
 * #%L
 * athena-lark-base
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

import com.amazonaws.glue.lark.base.crawler.model.LarkDatabaseRecord;
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllTableResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListFieldResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListRecordsResponse;
import com.amazonaws.glue.lark.base.crawler.util.Util;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Lark
 */
public class LarkBaseService extends CommonLarkService {
    private static final Logger logger = LoggerFactory.getLogger(LarkBaseService.class);
    private static final String LARK_BASE_URL = LARK_API_BASE_URL + "/bitable/v1/apps";
    final int PAGE_SIZE = 100;

    public LarkBaseService(String larkAppId, String larkAppSecret) {
        super(larkAppId, larkAppSecret);
    }

    /**
     * List all tables.
     * @see "https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table/list"
     * @param baseId The base ID
     * @return The list of tables
     */
    public List<ListAllTableResponse.BaseItem> listTables(String baseId) {
        try {
            refreshTenantAccessToken();
        } catch (IOException e) {
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<ListAllTableResponse.BaseItem> allTables = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;
        int count = 0;
        
        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables")
                        .addParameter("page_size", String.valueOf(PAGE_SIZE));
                
                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }
                
                URI uri = uriBuilder.build();
                
                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");
                
                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());
                
                ListAllTableResponse tableResponse = objectMapper.readValue(responseBody, ListAllTableResponse.class);

                System.out.println("Table response: " + tableResponse.getItems());
                System.out.println("Count: " + count);
                count++;
                
                // 1254002: No more data
                if (tableResponse.getCode() == 0 || tableResponse.getCode() == 1254002) {
                    if (tableResponse.getItems() != null) {
                        allTables.addAll(tableResponse.getItems());
                    }
                    
                    pageToken = tableResponse.getPageToken();
                    hasMore = tableResponse.hasMore();
                    
                    logger.info("Retrieved {} tables from base {}, has_more={}",
                            tableResponse.getItems() != null ? tableResponse.getItems().size() : 0,
                            baseId, hasMore);
                } else {
                    logger.error("Failed to list tables for base {}: {}", baseId, responseBody);
                    throw new IOException("Failed to retrieve tables for base: " + baseId + ", Error: " + tableResponse.getMsg());
                }
            } catch (Exception e) {
                logger.error("Failed to get records for base {}: {}", baseId, e.getMessage());
                throw new RuntimeException("Failed to get records for base: " + baseId, e);
            }
        } while (hasMore && pageToken != null && !pageToken.isEmpty());
        
        logger.info("Retrieved a total of {} tables from base {}", allTables.size(), baseId);
        return allTables;
    }

    /**
     * Get all fields for a table.
     * @see "https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-field/list"
     * @param baseId The base ID
     * @param tableId The table ID
     * @return The list of fields
     */
    public List<ListFieldResponse.FieldItem> getTableFields(String baseId, String tableId) {
        try {
            refreshTenantAccessToken();
        } catch (IOException e) {
            logger.error("Failed to refresh Lark access token", e);
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<ListFieldResponse.FieldItem> allFields = new ArrayList<>();
        String pageToken = "";
        boolean hasMore = false;

        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/fields")
                        .addParameter("page_size", String.valueOf(PAGE_SIZE));

                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }

                URI uri = uriBuilder.build();

                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");

                logger.info("Requesting fields for table {}: {}", tableId, uri);

                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());

                ListFieldResponse fieldResponse =
                        objectMapper.readValue(responseBody, ListFieldResponse.class);

                logger.info("Field response for page {}: {}", pageToken.isEmpty() ? "initial" : pageToken, fieldResponse);

                if (fieldResponse.getCode() == 0) {
                    List<ListFieldResponse.FieldItem> fields = fieldResponse.getItems();
                    if (fields != null) {
                        allFields.addAll(fields);
                    }

                    pageToken = fieldResponse.getPageToken();
                    hasMore = fieldResponse.hasMore();

                    logger.info("Retrieved {} fields from table {}, has_more={}",
                            fields != null ? fields.size() : 0, tableId, hasMore);
                } else {
                    throw new IOException("Failed to retrieve fields for table: " + tableId + ", Error: " + fieldResponse.getMsg());
                }
            } catch (Exception e) {
                logger.error("Failed to get fields for table {}: {}", tableId, e.getMessage());
                throw new RuntimeException("Failed to get fields for table: " + tableId, e);
            }
        } while (hasMore && pageToken != null && !pageToken.isEmpty());

        logger.info("Retrieved a total of {} fields from table {}", allFields.size(), tableId);
        return allFields;
    }

    /**
     * Get all records for a table.
     * @see "https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-record/list"
     * @param baseId The base ID
     * @param tableId The table ID
     * @return The list of records
     */
    public List<LarkDatabaseRecord> getTableRecords(String baseId, String tableId) {
        try {
            refreshTenantAccessToken();
        } catch (IOException e) {
            logger.error("Failed to refresh Lark access token", e);
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<LarkDatabaseRecord> parsedRecords = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;
        
        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/records")
                        .addParameter("page_size", String.valueOf(PAGE_SIZE));
                
                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }
                
                URI uri = uriBuilder.build();
                
                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");
                
                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());
                
                ListRecordsResponse recordsResponse = 
                    objectMapper.readValue(responseBody, ListRecordsResponse.class);
                
                if (recordsResponse.getCode() == 0) {
                    if (recordsResponse.getItems() != null) {
                        for (ListRecordsResponse.RecordItem record : recordsResponse.getItems()) {
                            Map<String, Object> fields = record.getFields();
                            
                            String id = null;
                            String name = null;
                            
                            if (fields != null) {
                                if (fields.containsKey("id")) {
                                    Object idObj = fields.get("id");
                                    id = idObj != null ? idObj.toString() : null;
                                }

                                if (fields.containsKey("name")) {
                                    Object nameObj = fields.get("name");
                                    name = nameObj != null ? nameObj.toString() : null;
                                }
                            }
                            
                            parsedRecords.add(new LarkDatabaseRecord(id, name));
                        }
                    }

                    pageToken = recordsResponse.getPageToken();
                    hasMore = recordsResponse.hasMore();
                } else {
                    throw new IOException("Failed to retrieve records for table: " + tableId + 
                            ", Error: " + recordsResponse.getMsg());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get records for table: " + tableId, e);
            }
        } while (hasMore && pageToken != null && !pageToken.isEmpty());

        return sanitizeRecords(parsedRecords);
    }

    /**
     * Sanitize records.
     * @param records The list of records
     * @return The sanitized list of records
     */
    public List<LarkDatabaseRecord> sanitizeRecords(List<LarkDatabaseRecord> records) {
        List<LarkDatabaseRecord> sanitizedRecords = records.stream()
                .map(record -> {
                    String sanitizedId = record.id();
                    String sanitizedName = Util.sanitizeGlueRelatedName(record.name());
                    return new LarkDatabaseRecord(sanitizedId, sanitizedName);
                })
                .collect(Collectors.toList());

        List<String> duplicateNames = sanitizedRecords.stream()
                .map(LarkDatabaseRecord::name)
                .filter(name -> Collections.frequency(sanitizedRecords.stream().map(LarkDatabaseRecord::name).collect(Collectors.toList()), name) > 1)
                .toList();

        if (!duplicateNames.isEmpty()) {
            throw new RuntimeException("Duplicate record names found duplicates: " + duplicateNames);
        }

        List<String> nullFields = sanitizedRecords.stream()
                .filter(record -> record.id() == null || record.name() == null)
                .map(record -> record.id() == null ? "id" : "name")
                .toList();

        if (!nullFields.isEmpty()) {
            throw new RuntimeException("Null record fields found null fields: " + nullFields);
        }

        return sanitizedRecords;
    }
}
