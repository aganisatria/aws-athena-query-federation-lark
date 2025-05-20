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
import com.amazonaws.glue.lark.base.crawler.model.request.LarkDrivePayload;
import com.amazonaws.services.lambda.runtime.Context;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.List;
import java.util.Map;

import static com.amazonaws.glue.lark.base.crawler.util.Util.constructTableLocationURI;

/**
 * Lark Base Crawler Handler
 */
public class LarkDriveCrawlerHandler extends BaseLarkBaseCrawlerHandler {
    // {
    //     "larkDriveFolderToken": "token123"
    // }

    private String larkDriveFolderToken;

    public LarkDriveCrawlerHandler() {
        super();
    }

    public String handleRequest(Object input, Context context) {
        LarkDrivePayload payload = OBJECT_MAPPER.convertValue(input, LarkDrivePayload.class);

        this.larkDriveFolderToken = payload.larkDriveFolderToken();

        return super.handleRequest(input, context);
    }

    @Override
    String getCrawlingMethod() {
        return "LarkDrive";
    }

    @Override
    String getCrawlingSource() {
        return larkDriveFolderToken;
    }

    @Override
    List<LarkDatabaseRecord> getLarkDatabases() {
        return super.larkDriveService.getLarkBases(larkDriveFolderToken);
    }

    @Override
    Map<String, String> getAdditionalTableInputParameter() {
        return Map.of(
                "larkDriveFolderToken", larkDriveFolderToken
        );
    }

    @Override
    boolean additionalTableInputChanged(TableInput newTableInput, Table existingTable) {
        Map<String, String> newParams = newTableInput.parameters();
        Map<String, String> existingParams = existingTable.parameters();

        if (!newParams.get("larkDriveFolderToken").equals(existingParams.get("larkDriveFolderToken"))) {
            return true;
        }

        String expectedLocation = constructTableLocationURI(
                getCrawlingMethod(),
                newParams.get("larkDriveFolderToken"),
                newParams.get("larkBaseId"),
                newParams.get("larkTableId"));

        return !expectedLocation.equals(existingTable.storageDescriptor().location());
    }
}

