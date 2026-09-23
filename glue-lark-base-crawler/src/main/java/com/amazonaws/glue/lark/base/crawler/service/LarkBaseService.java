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
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.amazonaws.glue.lark.base.crawler.model.response.SearchRecordsResponse;
import com.amazonaws.glue.lark.base.crawler.util.SearchApiResponseNormalizer;
import com.amazonaws.glue.lark.base.crawler.util.Util;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for Lark
 */
public class LarkBaseService extends CommonLarkService
{
    private static final Logger logger = LoggerFactory.getLogger(LarkBaseService.class);
    private static final String LARK_BASE_URL = LARK_API_BASE_URL + "/bitable/v1/apps";
    final int pageSize = 100;

    public LarkBaseService(String larkAppId, String larkAppSecret)
    {
        super(larkAppId, larkAppSecret);
    }

    /**
     * List all tables.
     *
     * @param baseId The base ID
     * @return The list of tables
     * @see "https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table/list"
     */
    public List<ListAllTableResponse.BaseItem> listTables(String baseId)
    {
        try {
            refreshTenantAccessToken();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<ListAllTableResponse.BaseItem> allTables = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;
        int count = 0;

        do {
            try {
                final String currentPageToken = pageToken;
                ListAllTableResponse tableResponse = retry.invoke(() -> fetchTablesPage(baseId, currentPageToken));

                System.out.println("Table response: " + tableResponse.getItems());
                System.out.println("Count: " + count);
                count++;

                if (tableResponse.getItems() != null) {
                    allTables.addAll(tableResponse.getItems());
                }

                pageToken = tableResponse.getPageToken();
                hasMore = tableResponse.hasMore();

                logger.info("Retrieved {} tables from base {}, has_more={}",
                        tableResponse.getItems() != null ? tableResponse.getItems().size() : 0,
                        baseId, hasMore);
            }
            catch (Exception e) {
                logger.error("Failed to get records for base {}: {}", baseId, e.getMessage());
                throw new RuntimeException("Failed to get records for base: " + baseId, e);
            }
        }
        while (hasMore && pageToken != null && !pageToken.isEmpty());

        logger.info("Retrieved a total of {} tables from base {}", allTables.size(), baseId);
        return allTables;
    }

    /**
     * Fetches a single page of {@link #listTables}'s result. Split out so it can be retried in
     * isolation via {@link ThrottlingRetry} - a rate-limited page fetch backs off and retries just this
     * one page, not the whole paginated call.
     */
    private ListAllTableResponse fetchTablesPage(String baseId, String pageToken)
            throws IOException, java.net.URISyntaxException
    {
        URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables")
                .addParameter("page_size", String.valueOf(pageSize));

        if (!pageToken.isEmpty()) {
            uriBuilder.addParameter("page_token", pageToken);
        }

        URI uri = uriBuilder.build();

        HttpGet request = new HttpGet(uri);
        request.setHeader("Authorization", "Bearer " + tenantAccessToken);
        request.setHeader("Content-Type", "application/json");

        ListAllTableResponse tableResponse;
        String responseBody;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            responseBody = EntityUtils.toString(response.getEntity());
            tableResponse = objectMapper.readValue(responseBody, ListAllTableResponse.class);
        }

        // 1254002: No more data
        if (tableResponse.getCode() == 0 || tableResponse.getCode() == 1254002) {
            return tableResponse;
        }

        logger.error("Failed to list tables for base {}: {}", baseId, responseBody);
        throw new IOException("Failed to retrieve tables for base: " + baseId + ", Code: " + tableResponse.getCode() + ", Error: " + tableResponse.getMsg());
    }

    /**
     * Get all fields for a table.
     *
     * @param baseId  The base ID
     * @param tableId The table ID
     * @return The list of fields
     * @see "https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-field/list"
     */
    public List<ListFieldResponse.FieldItem> getTableFields(String baseId, String tableId)
    {
        try {
            refreshTenantAccessToken();
        }
        catch (IOException e) {
            logger.error("Failed to refresh Lark access token", e);
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<ListFieldResponse.FieldItem> allFields = new ArrayList<>();
        String pageToken = "";
        boolean hasMore = false;

        do {
            try {
                final String currentPageToken = pageToken;
                ListFieldResponse fieldResponse = retry.invoke(() -> fetchFieldsPage(baseId, tableId, currentPageToken));

                logger.info("Field response for page {}: {}", currentPageToken.isEmpty() ? "initial" : currentPageToken, fieldResponse);

                List<ListFieldResponse.FieldItem> fields = fieldResponse.getItems();
                if (fields != null) {
                    allFields.addAll(fields);
                }

                pageToken = fieldResponse.getPageToken();
                hasMore = fieldResponse.hasMore();

                logger.info("Retrieved {} fields from table {}, has_more={}",
                        fields != null ? fields.size() : 0, tableId, hasMore);
            }
            catch (Exception e) {
                logger.error("Failed to get fields for table {}: {}", tableId, e.getMessage());
                throw new RuntimeException("Failed to get fields for table: " + tableId, e);
            }
        }
        while (hasMore && pageToken != null && !pageToken.isEmpty());

        logger.info("Retrieved a total of {} fields from table {}", allFields.size(), tableId);
        return allFields;
    }

    /**
     * Fetches a single page of {@link #getTableFields}'s result. Split out so it can be retried in
     * isolation via {@link ThrottlingRetry} - a rate-limited page fetch backs off and retries just this
     * one page, not the whole paginated call.
     */
    private ListFieldResponse fetchFieldsPage(String baseId, String tableId, String pageToken)
            throws IOException, java.net.URISyntaxException
    {
        URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/fields")
                .addParameter("page_size", String.valueOf(pageSize));

        if (!pageToken.isEmpty()) {
            uriBuilder.addParameter("page_token", pageToken);
        }

        URI uri = uriBuilder.build();

        HttpGet request = new HttpGet(uri);
        request.setHeader("Authorization", "Bearer " + tenantAccessToken);
        request.setHeader("Content-Type", "application/json");

        logger.info("Requesting fields for table {}: {}", tableId, uri);

        ListFieldResponse fieldResponse;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            fieldResponse = objectMapper.readValue(responseBody, ListFieldResponse.class);
        }

        if (fieldResponse.getCode() == 0) {
            return fieldResponse;
        }

        throw new IOException("Failed to retrieve fields for table: " + tableId + ", Code: " + fieldResponse.getCode() + ", Error: " + fieldResponse.getMsg());
    }

    /**
     * Get all records for a table using the Search API.
     *
     * @param baseId  The base ID
     * @param tableId The table ID
     * @return The list of records
     * @see "https://open.larksuite.com/document/uAjLw4CM/ukTMukTMukTM/reference/bitable-v1/app-table-record/search"
     */
    public List<LarkDatabaseRecord> getTableRecords(String baseId, String tableId)
    {
        try {
            refreshTenantAccessToken();
        }
        catch (IOException e) {
            logger.error("Failed to refresh Lark access token", e);
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<LarkDatabaseRecord> parsedRecords = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;

        do {
            try {
                final String currentPageToken = pageToken;
                SearchRecordsResponse recordsResponse = retry.invoke(() -> fetchRecordsPage(baseId, tableId, currentPageToken));

                if (recordsResponse.getItems() != null) {
                    for (SearchRecordsResponse.RecordItem record : recordsResponse.getItems()) {
                        Map<String, Object> originalFields = record.getFields();

                        // Normalize Search API response to List API format
                        Map<String, Object> fields = SearchApiResponseNormalizer.normalizeRecordFields(originalFields);

                        String id = null;
                        String name = null;
                        Set<String> whitelistTableIds = Collections.emptySet();
                        Set<String> blacklistTableIds = Collections.emptySet();

                        if (fields != null) {
                            if (fields.containsKey("id")) {
                                Object idObj = fields.get("id");
                                id = idObj != null ? idObj.toString() : null;
                            }

                            if (fields.containsKey("name")) {
                                Object nameObj = fields.get("name");
                                name = nameObj != null ? nameObj.toString() : null;
                            }

                            // Optional columns on the control table: comma-separated Lark table IDs that
                            // restrict which tables get crawled for this database. Absent/blank means no
                            // restriction from that list.
                            whitelistTableIds = parseTableIdList(fields.get("whitelist_tables"));
                            blacklistTableIds = parseTableIdList(fields.get("blacklist_tables"));
                        }

                        parsedRecords.add(new LarkDatabaseRecord(id, name, whitelistTableIds, blacklistTableIds));
                    }
                }

                pageToken = recordsResponse.getPageToken();
                hasMore = recordsResponse.hasMore();
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to get records for table: " + tableId, e);
            }
        }
        while (hasMore && pageToken != null && !pageToken.isEmpty());

        return sanitizeRecords(parsedRecords);
    }

    /**
     * Fetches a single page of {@link #getTableRecords}'s result. Split out so it can be retried in
     * isolation via {@link ThrottlingRetry} - a rate-limited page fetch backs off and retries just this
     * one page, not the whole paginated call.
     */
    private SearchRecordsResponse fetchRecordsPage(String baseId, String tableId, String pageToken)
            throws IOException, java.net.URISyntaxException
    {
        // page_size/page_token MUST be query parameters, not body fields: sending page_token in
        // the JSON body causes the Lark API to never advance past the first page - has_more
        // stays true and the same page_token/records get returned forever, no matter how many
        // times "the next page" is requested. Confirmed by direct comparison against the same
        // endpoint: identical request otherwise, only the query-param form actually reaches
        // has_more=false. See the same fix in athena-lark-base's LarkBaseService.
        URIBuilder uriBuilder = new URIBuilder(LARK_BASE_URL + "/" + baseId + "/tables/" + tableId + "/records/search")
                .addParameter("page_size", String.valueOf(pageSize));

        if (!pageToken.isEmpty()) {
            uriBuilder.addParameter("page_token", pageToken);
        }

        URI uri = uriBuilder.build();

        com.amazonaws.glue.lark.base.crawler.model.request.SearchRecordsRequest.Builder requestBuilder =
                com.amazonaws.glue.lark.base.crawler.model.request.SearchRecordsRequest.builder();

        String requestBody = objectMapper.writeValueAsString(requestBuilder.build());

        logger.info("Search API request for table {}: {}", tableId, requestBody);

        HttpPost request = new HttpPost(uri);
        request.setHeader("Authorization", "Bearer " + tenantAccessToken);
        request.setHeader("Content-Type", "application/json");
        request.setEntity(new org.apache.http.entity.StringEntity(requestBody, java.nio.charset.StandardCharsets.UTF_8));

        SearchRecordsResponse recordsResponse;
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseBody = EntityUtils.toString(response.getEntity());
            recordsResponse = objectMapper.readValue(responseBody, SearchRecordsResponse.class);
        }

        if (recordsResponse.getCode() == 0) {
            return recordsResponse;
        }

        throw new IOException("Failed to retrieve records for table: " + tableId +
                ", Code: " + recordsResponse.getCode() + ", Error: " + recordsResponse.getMsg());
    }

    /**
     * Parses a comma-separated list of Lark table IDs from a raw control-table cell value into a Set.
     * Returns an empty set (meaning "no restriction from this list") for a null, blank, or unparseable value.
     *
     * @param rawValue The raw cell value from the control table (expected to be a String).
     * @return A set of trimmed, non-empty table IDs.
     */
    private Set<String> parseTableIdList(Object rawValue)
    {
        if (rawValue == null) {
            return Collections.emptySet();
        }

        String rawString = rawValue.toString();
        if (rawString.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(rawString.split(","))
                .map(String::trim)
                .filter(tableId -> !tableId.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Sanitize records.
     *
     * @param records The list of records
     * @return The sanitized list of records
     */
    public List<LarkDatabaseRecord> sanitizeRecords(List<LarkDatabaseRecord> records)
    {
        // Util.sanitizeGlueRelatedName is null-safe (returns null for a null name), so a control-table
        // record with a blank Name cell survives this mapping as a record with a null name, instead of
        // crashing here with a raw NullPointerException - leaving the null-fields check below (which
        // exists specifically to catch and report this case clearly) able to actually run.
        List<LarkDatabaseRecord> sanitizedRecords = records.stream()
                .map(record -> {
                    String sanitizedId = record.id();
                    String sanitizedName = Util.sanitizeGlueRelatedName(record.name());
                    return new LarkDatabaseRecord(sanitizedId, sanitizedName, record.whitelistTableIds(), record.blacklistTableIds());
                })
                .collect(Collectors.toList());

        // Checked before the duplicate-name check below so a blank Name cell is reported as "null
        // fields", not misattributed as a "duplicate" (multiple null names are otherwise indistinguishable
        // from each other under Collections.frequency, and would report every null-name row as a
        // "duplicate" of every other one instead of the more specific, actionable null-field error).
        List<String> nullFields = sanitizedRecords.stream()
                .filter(record -> record.id() == null || record.name() == null)
                .map(record -> record.id() == null ? "id" : "name")
                .toList();

        if (!nullFields.isEmpty()) {
            throw new RuntimeException("Null record fields found null fields: " + nullFields);
        }

        List<String> duplicateNames = sanitizedRecords.stream()
                .map(LarkDatabaseRecord::name)
                .filter(name -> Collections.frequency(sanitizedRecords.stream().map(LarkDatabaseRecord::name).collect(Collectors.toList()), name) > 1)
                .toList();

        if (!duplicateNames.isEmpty()) {
            throw new RuntimeException("Duplicate record names found duplicates: " + duplicateNames);
        }

        return sanitizedRecords;
    }
}
