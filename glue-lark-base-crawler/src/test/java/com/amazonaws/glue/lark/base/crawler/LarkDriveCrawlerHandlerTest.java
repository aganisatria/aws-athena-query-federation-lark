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
import com.amazonaws.glue.lark.base.crawler.service.GlueCatalogService;
import com.amazonaws.glue.lark.base.crawler.service.LarkBaseService;
import com.amazonaws.glue.lark.base.crawler.service.LarkDriveService;
import com.amazonaws.glue.lark.base.crawler.service.STSService;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LarkDriveCrawlerHandlerTest {

    @Mock
    private GlueCatalogService mockGlueCatalogService;
    @Mock
    private LarkBaseService mockLarkBaseService;
    @Mock
    private LarkDriveService mockLarkDriveService;
    @Mock
    private STSService mockStsService;
    @Mock
    private Context mockContext;

    private LarkDriveCrawlerHandler larkDriveCrawlerHandler;

    @Before
    public void setUp() {
        larkDriveCrawlerHandler = new LarkDriveCrawlerHandler(
                mockGlueCatalogService,
                mockLarkBaseService,
                mockLarkDriveService,
                mockStsService
        );
    }

    @Test
    public void handleRequest_shouldProcessPayloadAndSucceed() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "folder123");

        when(mockLarkDriveService.getLarkBases("folder123"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        String result = larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        assertEquals("Success", result);
    }

    @Test
    public void getCrawlingMethod_shouldReturnLarkDrive() {
        assertEquals("LarkDrive", larkDriveCrawlerHandler.getCrawlingMethod());
    }

    @Test
    public void testGetCrawlingSource() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "myFolderToken123");

        when(mockLarkDriveService.getLarkBases("myFolderToken123"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        assertEquals("myFolderToken123", larkDriveCrawlerHandler.getCrawlingSource());
    }

    @Test
    public void testGetLarkDatabases() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "folder456");

        List<LarkDatabaseRecord> expectedDatabases = Collections.singletonList(
                new LarkDatabaseRecord("db1", "Database One")
        );

        when(mockLarkDriveService.getLarkBases("folder456")).thenReturn(expectedDatabases);
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        List<LarkDatabaseRecord> actualDatabases = larkDriveCrawlerHandler.getLarkDatabases();

        assertEquals(expectedDatabases, actualDatabases);
    }

    @Test
    public void testGetAdditionalTableInputParameter() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "additionalToken789");

        when(mockLarkDriveService.getLarkBases("additionalToken789"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        Map<String, String> params = larkDriveCrawlerHandler.getAdditionalTableInputParameter();

        assertNotNull("Additional parameters should not be null", params);
        assertEquals("additionalToken789", params.get("larkDriveFolderToken"));
        assertEquals(1, params.size());
    }

    @Test
    public void testAdditionalTableInputChanged_whenFolderTokenDiffers() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "newToken");

        when(mockLarkDriveService.getLarkBases("newToken"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        Map<String, String> newParams = new HashMap<>();
        newParams.put("larkDriveFolderToken", "newToken");
        newParams.put("larkBaseId", "base123");
        newParams.put("larkTableId", "table456");

        Map<String, String> existingParams = new HashMap<>();
        existingParams.put("larkDriveFolderToken", "oldToken");
        existingParams.put("larkBaseId", "base123");
        existingParams.put("larkTableId", "table456");

        TableInput newTableInput = TableInput.builder()
                .parameters(newParams)
                .build();

        Table existingTable = Table.builder()
                .parameters(existingParams)
                .storageDescriptor(StorageDescriptor.builder()
                        .location("lark-base-flag/CrawlingMethod=LarkDrive/DataSource=oldToken/Base=base123/Table=table456")
                        .build())
                .build();

        boolean changed = larkDriveCrawlerHandler.additionalTableInputChanged(newTableInput, existingTable);

        assertTrue("Should detect change when folder token differs", changed);
    }

    @Test
    public void testAdditionalTableInputChanged_whenLocationDiffers() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "sameToken");

        when(mockLarkDriveService.getLarkBases("sameToken"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        Map<String, String> newParams = new HashMap<>();
        newParams.put("larkDriveFolderToken", "sameToken");
        newParams.put("larkBaseId", "base123");
        newParams.put("larkTableId", "table456");

        Map<String, String> existingParams = new HashMap<>();
        existingParams.put("larkDriveFolderToken", "sameToken");
        existingParams.put("larkBaseId", "base123");
        existingParams.put("larkTableId", "table456");

        TableInput newTableInput = TableInput.builder()
                .parameters(newParams)
                .build();

        Table existingTable = Table.builder()
                .parameters(existingParams)
                .storageDescriptor(StorageDescriptor.builder()
                        .location("lark-base-flag/CrawlingMethod=LarkDrive/DataSource=sameToken/Base=wrongBase/Table=table456")
                        .build())
                .build();

        boolean changed = larkDriveCrawlerHandler.additionalTableInputChanged(newTableInput, existingTable);

        assertTrue("Should detect change when location differs", changed);
    }

    @Test
    public void testAdditionalTableInputChanged_whenNothingChanged() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("larkDriveFolderToken", "unchangedToken");

        when(mockLarkDriveService.getLarkBases("unchangedToken"))
                .thenReturn(Collections.singletonList(new LarkDatabaseRecord("dbId", "dbName")));
        when(mockGlueCatalogService.getDatabases()).thenReturn(Collections.emptyList());

        larkDriveCrawlerHandler.handleRequest(payload, mockContext);

        Map<String, String> params = new HashMap<>();
        params.put("larkDriveFolderToken", "unchangedToken");
        params.put("larkBaseId", "base123");
        params.put("larkTableId", "table456");

        TableInput newTableInput = TableInput.builder()
                .parameters(params)
                .build();

        Table existingTable = Table.builder()
                .parameters(params)
                .storageDescriptor(StorageDescriptor.builder()
                        .location("lark-base-flag/CrawlingMethod=LarkDrive/DataSource=unchangedToken/Base=base123/Table=table456")
                        .build())
                .build();

        boolean changed = larkDriveCrawlerHandler.additionalTableInputChanged(newTableInput, existingTable);

        assertFalse("Should not detect change when nothing changed", changed);
    }
}
