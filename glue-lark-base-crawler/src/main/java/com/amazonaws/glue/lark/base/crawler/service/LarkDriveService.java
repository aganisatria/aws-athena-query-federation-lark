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
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllFolderResponse;
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
import java.util.List;

public class LarkDriveService extends CommonLarkService
{
    private static final Logger logger = LoggerFactory.getLogger(LarkDriveService.class);
    private static final String LARK_DRIVE_URL = LARK_API_BASE_URL + "/drive/v1";
    final int pageSize = 200;

    public LarkDriveService(String larkAppId, String larkAppSecret)
    {
        super(larkAppId, larkAppSecret);
    }

    public List<LarkDatabaseRecord> getLarkBases(String folderToken)
    {
        try {
            refreshTenantAccessToken();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to refresh Lark access token", e);
        }

        List<LarkDatabaseRecord> allTables = new ArrayList<>();
        String pageToken = "";
        boolean hasMore;

        do {
            try {
                URIBuilder uriBuilder = new URIBuilder(LARK_DRIVE_URL + "/" + "files")
                        .addParameter("folder_token", folderToken)
                        .addParameter("page_size", String.valueOf(pageSize));

                if (!pageToken.isEmpty()) {
                    uriBuilder.addParameter("page_token", pageToken);
                }

                URI uri = uriBuilder.build();

                HttpGet request = new HttpGet(uri);
                request.setHeader("Authorization", "Bearer " + tenantAccessToken);
                request.setHeader("Content-Type", "application/json");

                HttpResponse response = httpClient.execute(request);
                String responseBody = EntityUtils.toString(response.getEntity());

                ListAllFolderResponse tableResponse = objectMapper.readValue(responseBody, ListAllFolderResponse.class);

                // 1254002: No more data
                if (tableResponse.getCode() == 0 || tableResponse.getCode() == 1254002) {
                    if (tableResponse.getFiles() != null) {
                        List<ListAllFolderResponse.DriveFile> filteredFiles = tableResponse.getFiles().stream()
                                .filter(file -> file.getType().equalsIgnoreCase("bitable")).toList();

                        for (ListAllFolderResponse.DriveFile file : filteredFiles) {
                            allTables.add(
                                    new LarkDatabaseRecord(
                                            file.getToken(),
                                            Util.sanitizeGlueRelatedName(file.getName())
                                    )
                            );
                        }
                    }

                    pageToken = tableResponse.getNextPageToken();
                    hasMore = tableResponse.hasMore();
                }
                else {
                    logger.error("Failed to list tables for folder {}: {}", folderToken, responseBody);
                    throw new IOException("Failed to retrieve tables for folder: " + folderToken + ", Error: " + tableResponse.getMsg());
                }
            }
            catch (Exception e) {
                logger.error("Failed to get records for folder {}: {}", folderToken, e.getMessage());
                throw new RuntimeException("Failed to get records for folder: " + folderToken, e);
            }
        }
        while (hasMore && pageToken != null && !pageToken.isEmpty());

        logger.info("Retrieved a total of {} tables from folder {}", allTables.size(), folderToken);
        return allTables;
    }
}
