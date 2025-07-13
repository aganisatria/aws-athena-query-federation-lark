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

import com.amazonaws.glue.lark.base.crawler.model.LarkDatabaseRecord;
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllFolderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LarkDriveServiceTest {

    private static final String TEST_APP_ID = "driveAppId";
    private static final String TEST_APP_SECRET = "driveAppSecret";
    private static final String MOCK_TENANT_ACCESS_TOKEN_DRIVE = "mock_tenant_token_for_drive_service";


    @Spy
    private LarkDriveService larkDriveService = new LarkDriveService(TEST_APP_ID, TEST_APP_SECRET);

    @Mock private HttpClient mockHttpClient;
    @Mock private ObjectMapper mockObjectMapper;
    @Mock private HttpResponse mockHttpResponse;
    @Mock private HttpEntity mockHttpEntity;

    @Before
    public void setUp() throws IOException {
        larkDriveService.httpClient = mockHttpClient;
        larkDriveService.objectMapper = mockObjectMapper;

        doAnswer(invocation -> {
            larkDriveService.tenantAccessToken = MOCK_TENANT_ACCESS_TOKEN_DRIVE;
            larkDriveService.tokenExpiry = System.currentTimeMillis() + 3600 * 1000;
            return null;
        }).when(larkDriveService).refreshTenantAccessToken();
    }

    @Test
    public void getLarkBases_success_singlePage_filtersBitable() throws Exception {
        String folderToken = "folderToken1";
        List<ListAllFolderResponse.DriveFile> files = List.of(
                ListAllFolderResponse.DriveFile.builder().token("bitable1").name("My Bitable").type("bitable").build(),
                ListAllFolderResponse.DriveFile.builder().token("doc1").name("My Document").type("docx").build()
        );
        ListAllFolderResponse.ListData listData = ListAllFolderResponse.ListData.builder()
                .files(files).hasMore(false).nextPageToken(null).build();
        ListAllFolderResponse mockResponse = (ListAllFolderResponse) ListAllFolderResponse.builder().code(0).data(listData).build();
        String mockJsonResponse = "{\"data\":{\"files\":[...],\"has_more\":false}}";

        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(mockJsonResponse, ListAllFolderResponse.class)).thenReturn(mockResponse);

        List<LarkDatabaseRecord> result = larkDriveService.getLarkBases(folderToken);

        assertEquals(1, result.size());
        assertEquals("bitable1", result.get(0).id());
        assertEquals("my_bitable", result.get(0).name());
        verify(larkDriveService, times(1)).refreshTenantAccessToken();
    }

    @Test(expected = RuntimeException.class)
    public void getLarkBases_refreshAccessTokenFails_shouldThrowRuntimeException() throws IOException {
        doThrow(new IOException("Token refresh failed for drive")).when(larkDriveService).refreshTenantAccessToken();
        larkDriveService.getLarkBases("folderTokenError");
    }
}
