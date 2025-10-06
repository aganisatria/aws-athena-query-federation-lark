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
package com.amazonaws.athena.connectors.lark.base.service;

import com.amazonaws.athena.connectors.lark.base.model.LarkDatabaseRecord;
import com.amazonaws.athena.connectors.lark.base.model.enums.UITypeEnum;
import com.amazonaws.athena.connectors.lark.base.model.response.ListAllTableResponse;
import com.amazonaws.athena.connectors.lark.base.model.response.ListFieldResponse;
import com.amazonaws.athena.connectors.lark.base.model.response.ListRecordsResponse;
import com.amazonaws.athena.connectors.lark.base.util.CommonUtil;
import com.amazonaws.athena.connectors.lark.base.util.SearchApiResponseNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.utils.Pair;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazonaws.athena.connectors.lark.base.BaseConstants.PAGE_SIZE;

public class LarkBaseService extends CommonLarkService {
    private static final Logger logger = LoggerFactory.getLogger(LarkBaseService.class);
    private static final String LARK_BASE_URL = LARK_API_BASE_URL + "/bitable/v1/apps";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public LarkBaseService(String larkAppId, String larkAppSecret) {
        super(larkAppId, larkAppSecret);
    }

    /**
     * Get all records from a table
     * @param baseId base ID
     * @param tableId table ID
     * @return Response with list of records and pagination token
     */
    public List<LarkDatabaseRecord> getDatabaseRecords(String baseId, String tableId) throws IOException {
        List<LarkDatabaseRecord> parsedRecords = new ArrayList<>();
        String pageToken = null;
        boolean hasMore;

        do {
            ListRecordsResponse recordsResponse = getTableRecords(baseId, tableId, PAGE_SIZE, pageToken, null, null);

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
                throw new IOException("Failed to retrieve records for table: " + tableId + ", Error: " + recordsResponse.getMsg());
            }
        } while (hasMore);

        return parsedRecords;
    }

    /**
     * Getting records from a table with or without filter using the Search API
     * <a href="https://open.larksuite.com/document/uAjLw4CM/ukTMukTMukTM/reference/bitable-v1/app-table-record/search">DOCS</a>
     * @param baseId base ID
     * @param tableId table ID
     * @param pageSize Total record per page
     * @param pageToken Token pagination (empty if first page)
     * @param filterJson JSON string filter in lark format
     * @param sortJson JSON string sort in lark format
     * @return Response with list of records and pagination token
     */
    public ListRecordsResponse getTableRecords(String baseId, String tableId, long pageSize, String pageToken, String filterJson, String sortJson) throws IOException {
        refreshTenantAccessToken();

        try {
            URI uri = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/records/search").build();

            logger.info("Fetching records from Lark Base Search API, url: {}", uri);

            // Build request body
            com.amazonaws.athena.connectors.lark.base.model.request.SearchRecordsRequest.Builder requestBuilder =
                    com.amazonaws.athena.connectors.lark.base.model.request.SearchRecordsRequest.builder()
                            .pageSize((int) pageSize);

            if (pageToken != null && !pageToken.isEmpty()) {
                requestBuilder.pageToken(pageToken);
            }

            if (filterJson != null && !filterJson.isEmpty()) {
                requestBuilder.filter(filterJson);
            }

            if (sortJson != null && !sortJson.isEmpty()) {
                requestBuilder.sort(sortJson);
            }

            String requestBody = OBJECT_MAPPER.writeValueAsString(requestBuilder.build());

            logger.info("Search API request body: {}", requestBody);

            HttpPost request = new HttpPost(uri);
            request.setHeader("Authorization", "Bearer " + tenantAccessToken);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new org.apache.http.entity.StringEntity(requestBody, java.nio.charset.StandardCharsets.UTF_8));

            HttpResponse response = httpClient.execute(request);
            String responseBody = EntityUtils.toString(response.getEntity());

            ListRecordsResponse recordsResponse =
                    OBJECT_MAPPER.readValue(responseBody, ListRecordsResponse.class);

            if (recordsResponse.getCode() == 0) {
                sanitizeRecordFieldNames(recordsResponse);
                return recordsResponse;
            } else {
                throw new IOException("Failed to retrieve records for table: " + tableId + ", Error: " + recordsResponse.getMsg());
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for Lark Base API", e);
        }
    }

    /**
     * Sanitize field names and normalize Search API response format for all records
     * @param response Response object
     */
    private void sanitizeRecordFieldNames(ListRecordsResponse response) {
        if (response.getItems() == null) {
            return;
        }

        for (ListRecordsResponse.RecordItem item : response.getItems()) {
            Map<String, Object> sanitizedFields = new HashMap<>();
            Map<String, Object> originalFields = item.getFields();

            if (originalFields != null) {
                // First normalize Search API format to List API format
                Map<String, Object> normalizedFields = SearchApiResponseNormalizer.normalizeRecordFields(originalFields);

                // Then sanitize field names for Glue compatibility
                for (Map.Entry<String, Object> entry : normalizedFields.entrySet()) {
                    String sanitizedKey = CommonUtil.sanitizeGlueRelatedName(entry.getKey());
                    sanitizedFields.put(sanitizedKey, entry.getValue());
                }

                item.setFields(sanitizedFields);
            }
        }
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
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<ListFieldResponse.FieldItem> allFields = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;

        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/fields")
                        .addParameter("page_size", String.valueOf(PAGE_SIZE));

                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }

                URI uri = uriBuilder.build();

                logger.info("Fetching fields from Lark Base API, url: {}", uri);

                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");

                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());

                ListFieldResponse fieldResponse =
                        OBJECT_MAPPER.readValue(responseBody, ListFieldResponse.class);

                if (fieldResponse.getCode() == 0) {
                    List<ListFieldResponse.FieldItem> fields = fieldResponse.getItems();
                    if (fields != null) {
                        allFields.addAll(fields);
                    }

                    pageToken = fieldResponse.getPageToken();
                    hasMore = fieldResponse.hasMore();
                } else {
                    throw new IOException("Failed to retrieve fields for table: " + tableId + ", Error: " + fieldResponse.getMsg());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get fields for table: " + tableId, e);
            }
        } while (hasMore && pageToken != null && !pageToken.isEmpty());

        return allFields;
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

        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables")
                        .addParameter("page_size", String.valueOf(PAGE_SIZE));

                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }

                URI uri = uriBuilder.build();

                logger.info("Fetching tables from Lark Base API, url: {}", uri);

                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");

                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());
                ListAllTableResponse tableResponse = OBJECT_MAPPER.readValue(responseBody, ListAllTableResponse.class);

                // 1254002: No more data
                if (tableResponse.getCode() == 0 || tableResponse.getCode() == 1254002) {
                    if (tableResponse.getItems() != null) {
                        allTables.addAll(tableResponse.getItems());
                    }

                    pageToken = tableResponse.getPageToken();
                    hasMore = tableResponse.hasMore();
                } else {
                    throw new IOException("Failed to retrieve tables for base: " + baseId + ", Error: " + tableResponse.getMsg());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get records for base: " + baseId, e);
            }
        } while (hasMore && pageToken != null && !pageToken.isEmpty());
        return allTables;
    }

    public UITypeEnum getLookupType(String baseId, String tableId, String fieldId) {
        List<ListFieldResponse.FieldItem> fields = getTableFields(baseId, tableId);
        for (ListFieldResponse.FieldItem field : fields) {
            if (field.getFieldId().equalsIgnoreCase(fieldId)) {
                if (field.getUIType().equals(UITypeEnum.LOOKUP)) {
                    Pair<String, String> lookupId = field.getTargetFieldAndTableForLookup();
                    String newTableId = lookupId.right();
                    String newFieldId = lookupId.left();
                    return getLookupType(baseId, newTableId, newFieldId);
                }

                return field.getUIType();
            }
        }

        return UITypeEnum.UNKNOWN;
    }
}