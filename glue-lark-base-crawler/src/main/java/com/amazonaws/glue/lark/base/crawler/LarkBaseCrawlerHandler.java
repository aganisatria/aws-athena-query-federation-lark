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

import com.amazonaws.glue.lark.base.crawler.model.LarkDatabaseRecord;
import com.amazonaws.glue.lark.base.crawler.model.request.LarkBasePayload;
import com.amazonaws.glue.lark.base.crawler.service.GlueCatalogService;
import com.amazonaws.glue.lark.base.crawler.service.LarkBaseService;
import com.amazonaws.glue.lark.base.crawler.service.LarkDriveService;
import com.amazonaws.glue.lark.base.crawler.service.STSService;
import com.amazonaws.glue.lark.base.crawler.util.Util;
import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.amazonaws.glue.lark.base.crawler.util.Util.constructTableLocationURI;

/**
 * Lark Base Crawler Handler
 */
public class LarkBaseCrawlerHandler extends BaseLarkBaseCrawlerHandler {
    // {
    //     "larkBaseDataSourceId": "base123",
    //     "larkTableDataSourceId": "table456"
    // }

    private String larkBaseDataSourceId;
    private String larkTableDataSourceId;

    public LarkBaseCrawlerHandler() {
        super();
    }

    // Constructor for testing with mocks
    LarkBaseCrawlerHandler(GlueCatalogService glueCatalogService, LarkBaseService larkBaseService, LarkDriveService larkDriveService, STSService stsService) {
        super(glueCatalogService, larkBaseService, larkDriveService, stsService);
    }

    public String handleRequest(Object input, Context context) {
        LarkBasePayload payload = OBJECT_MAPPER.convertValue(input, LarkBasePayload.class);

        this.larkBaseDataSourceId = payload.larkBaseDataSourceId();
        this.larkTableDataSourceId = payload.larkTableDataSourceId();

        return super.handleRequest(input, context);
    }

    @Override
    String getCrawlingMethod() {
        return "LarkBase";
    }

    @Override
    String getCrawlingSource() {
        return larkBaseDataSourceId + ":" + larkTableDataSourceId;
    }

    @Override
    List<LarkDatabaseRecord> getLarkDatabases() {
        List<LarkDatabaseRecord> listRecordsResponse = super.larkBaseService.getTableRecords(
                larkBaseDataSourceId, larkTableDataSourceId);

        if (Util.doesGlueDatabasesNameValid(listRecordsResponse.stream()
                .map(LarkDatabaseRecord::name)
                .collect(Collectors.toList()))) {
            throw new RuntimeException("Glue database name is not valid, only lowercase letters, numbers, and underscores are allowed.");
        }

        return listRecordsResponse;
    }

    @Override
    Map<String, String> getAdditionalTableInputParameter() {
        return Map.of(
                "larkBaseDataSourceId", larkBaseDataSourceId,
                "larkTableDataSourceId", larkTableDataSourceId
        );
    }

    @Override
    boolean additionalTableInputChanged(TableInput newTableInput, Table existingTable) {
        Map<String, String> newParams = newTableInput.parameters();
        Map<String, String> existingParams = existingTable.parameters();

        if (!newParams.get("larkBaseDataSourceId").equals(existingParams.get("larkBaseDataSourceId")) ||
                !newParams.get("larkTableDataSourceId").equals(existingParams.get("larkTableDataSourceId"))) {
            return true;
        }

        String expectedLocation = constructTableLocationURI(
                getCrawlingMethod(),
                newParams.get("larkBaseDataSourceId") + ":" + newParams.get("larkTableDataSourceId"),
                newParams.get("larkBaseId"),
                newParams.get("larkTableId"));

        return !expectedLocation.equals(existingTable.storageDescriptor().location());
    }
}

