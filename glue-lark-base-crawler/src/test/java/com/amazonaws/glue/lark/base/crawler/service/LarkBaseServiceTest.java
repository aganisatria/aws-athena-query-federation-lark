package com.amazonaws.glue.lark.base.crawler.service;

import com.amazonaws.glue.lark.base.crawler.model.LarkDatabaseRecord;
import com.amazonaws.glue.lark.base.crawler.model.response.ListAllTableResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListFieldResponse;
import com.amazonaws.glue.lark.base.crawler.model.response.ListRecordsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LarkBaseServiceTest {

    private static final String TEST_APP_ID = "testAppId";
    private static final String TEST_APP_SECRET = "testAppSecret";
    private static final String MOCK_TENANT_ACCESS_TOKEN = "mock_tenant_token_for_base_service";

    @Spy
    private LarkBaseService larkBaseService = new LarkBaseService(TEST_APP_ID, TEST_APP_SECRET);

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private ObjectMapper mockObjectMapper;

    @Mock
    private HttpResponse mockHttpResponse;

    @Mock
    private HttpEntity mockHttpEntity;

    @Before
    public void setUp() throws IOException {
        larkBaseService.httpClient = mockHttpClient;
        larkBaseService.objectMapper = mockObjectMapper;

        doAnswer(invocation -> {
            larkBaseService.tenantAccessToken = MOCK_TENANT_ACCESS_TOKEN;
            larkBaseService.tokenExpiry = System.currentTimeMillis() + 3600 * 1000;
            return null;
        }).when(larkBaseService).refreshTenantAccessToken();
    }

    @Test
    public void listTables_success_singlePage() throws Exception {
        String baseId = "base123";
        List<ListAllTableResponse.BaseItem> items = Collections.singletonList(
                ListAllTableResponse.BaseItem.builder().tableId("tbl1").name("Table 1").build()
        );
        ListAllTableResponse.ListData listData = ListAllTableResponse.ListData.builder()
                .items(items)
                .hasMore(false)
                .pageToken(null)
                .build();
        ListAllTableResponse mockResponse = (ListAllTableResponse) ListAllTableResponse.builder()
                .code(0)
                .msg("success")
                .data(listData)
                .build();
        String mockJsonResponse = "{\"code\":0, \"msg\":\"success\", \"data\":{\"items\":[{\"table_id\":\"tbl1\",\"name\":\"Table 1\"}],\"has_more\":false}}";

        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(mockJsonResponse, ListAllTableResponse.class)).thenReturn(mockResponse);

        List<ListAllTableResponse.BaseItem> result = larkBaseService.listTables(baseId);

        assertEquals(1, result.size());
        assertEquals("tbl1", result.get(0).getTableId());
        assertEquals("table_1", result.get(0).getName());
        verify(larkBaseService, times(1)).refreshTenantAccessToken();
        verify(mockHttpClient, times(1)).execute(any(HttpGet.class));
    }

    @Test
    public void listTables_success_multiplePages() throws Exception {
        String baseId = "baseMultiPage";
        List<ListAllTableResponse.BaseItem> itemsPage1 = Collections.singletonList(
                ListAllTableResponse.BaseItem.builder().tableId("tblA").name("Table A").build());
        ListAllTableResponse.ListData listDataPage1 = ListAllTableResponse.ListData.builder()
                .items(itemsPage1).hasMore(true).pageToken("page_token_2").build();
        ListAllTableResponse mockResponsePage1 = (ListAllTableResponse) ListAllTableResponse.builder().code(0).data(listDataPage1).build();
        String jsonPage1 = "{\"data\":{\"items\":[{\"name\":\"Table A\"}],\"has_more\":true,\"page_token\":\"page_token_2\"}}";

        List<ListAllTableResponse.BaseItem> itemsPage2 = Collections.singletonList(
                ListAllTableResponse.BaseItem.builder().tableId("tblB").name("Table B").build());
        ListAllTableResponse.ListData listDataPage2 = ListAllTableResponse.ListData.builder()
                .items(itemsPage2).hasMore(false).pageToken(null).build();
        ListAllTableResponse mockResponsePage2 = (ListAllTableResponse) ListAllTableResponse.builder().code(0).data(listDataPage2).build();
        String jsonPage2 = "{\"data\":{\"items\":[{\"name\":\"Table B\"}],\"has_more\":false}}";

        ArgumentCaptor<HttpGet> httpGetCaptor = ArgumentCaptor.forClass(HttpGet.class);
        when(mockHttpClient.execute(httpGetCaptor.capture()))
                .thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent())
                .thenReturn(new ByteArrayInputStream(jsonPage1.getBytes()))
                .thenReturn(new ByteArrayInputStream(jsonPage2.getBytes()));
        when(mockObjectMapper.readValue(jsonPage1, ListAllTableResponse.class)).thenReturn(mockResponsePage1);
        when(mockObjectMapper.readValue(jsonPage2, ListAllTableResponse.class)).thenReturn(mockResponsePage2);

        List<ListAllTableResponse.BaseItem> result = larkBaseService.listTables(baseId);

        assertEquals(2, result.size());
        assertEquals("tblA", result.get(0).getTableId());
        assertEquals("table_a", result.get(0).getName());
        assertEquals("tblB", result.get(1).getTableId());
        assertEquals("table_b", result.get(1).getName());

        verify(larkBaseService, times(1)).refreshTenantAccessToken();
        verify(mockHttpClient, times(2)).execute(any(HttpGet.class));

        List<HttpGet> capturedRequests = httpGetCaptor.getAllValues();
        assertTrue(capturedRequests.get(0).getURI().toString().contains("page_size=" + larkBaseService.PAGE_SIZE));
        assertFalse(capturedRequests.get(0).getURI().toString().contains("page_token"));
        assertTrue(capturedRequests.get(1).getURI().toString().contains("page_token=page_token_2"));
    }

    @Test(expected = RuntimeException.class)
    public void listTables_refreshAccessTokenFails_shouldThrowRuntimeException() throws IOException {
        doThrow(new IOException("Token refresh failed")).when(larkBaseService).refreshTenantAccessToken();
        larkBaseService.listTables("baseError");
    }

    @Test(expected = RuntimeException.class)
    public void listTables_apiError_shouldThrowRuntimeException() throws Exception {
        String baseId = "baseApiError";
        ListAllTableResponse mockErrorResponse = (ListAllTableResponse) ListAllTableResponse.builder().code(10001).msg("API Error").build();
        String mockErrorJson = "{\"code\":10001, \"msg\":\"API Error\"}";

        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockErrorJson.getBytes()));
        when(mockObjectMapper.readValue(mockErrorJson, ListAllTableResponse.class)).thenReturn(mockErrorResponse);

        larkBaseService.listTables(baseId);
    }

    @Test
    public void getTableFields_success_singlePage() throws Exception {
        String baseId = "base1";
        String tableId = "tbl1";
        List<ListFieldResponse.FieldItem> items = Collections.singletonList(
                ListFieldResponse.FieldItem.builder().fieldId("fld1").fieldName("Field 1").uiType("Text").build()
        );
        ListFieldResponse.ListData listData = ListFieldResponse.ListData.builder()
                .items(items).hasMore(false).total(1).build();
        ListFieldResponse mockResponse = (ListFieldResponse) ListFieldResponse.builder().code(0).data(listData).build();
        String mockJsonResponse = "{\"code\":0, \"data\":{\"items\":[{\"field_id\":\"fld1\",\"field_name\":\"Field 1\",\"ui_type\":\"Text\"}],\"has_more\":false}}";

        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(mockJsonResponse, ListFieldResponse.class)).thenReturn(mockResponse);

        List<ListFieldResponse.FieldItem> result = larkBaseService.getTableFields(baseId, tableId);

        assertEquals(1, result.size());
        assertEquals("fld1", result.get(0).getFieldId());
        assertEquals("Field 1", result.get(0).getFieldName());
        verify(larkBaseService, times(1)).refreshTenantAccessToken();
        verify(mockHttpClient, times(1)).execute(any(HttpGet.class));
    }

    @Test
    public void getTableRecords_success_singlePage() throws Exception {
        String baseId = "baseR1";
        String tableId = "tblR1";
        Map<String, Object> recordFields = Map.of("id", "rec123", "name", "Record Name");
        ListRecordsResponse.RecordItem recordItem = ListRecordsResponse.RecordItem.builder().recordId("rec123").fields(recordFields).build();
        List<ListRecordsResponse.RecordItem> items = Collections.singletonList(recordItem);

        ListRecordsResponse.ListData listData = ListRecordsResponse.ListData.builder()
                .items(items).hasMore(false).pageToken(null).total(1).build();
        ListRecordsResponse mockResponse = (ListRecordsResponse) ListRecordsResponse.builder().code(0).data(listData).build();
        String mockJsonResponse = "{\"data\":{\"items\":[{\"record_id\":\"rec123\",\"fields\":{\"id\":\"rec123\",\"name\":\"Record Name\"}}],\"has_more\":false}}";


        when(mockHttpClient.execute(any(HttpGet.class))).thenReturn(mockHttpResponse);
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream(mockJsonResponse.getBytes()));
        when(mockObjectMapper.readValue(mockJsonResponse, ListRecordsResponse.class)).thenReturn(mockResponse);

        List<LarkDatabaseRecord> result = larkBaseService.getTableRecords(baseId, tableId);

        assertEquals(1, result.size());
        assertEquals("rec123", result.get(0).id());
        assertEquals("record_name", result.get(0).name());
        verify(larkBaseService, times(1)).refreshTenantAccessToken();
    }

    @Test
    public void sanitizeRecords_success_validNames() {
        List<LarkDatabaseRecord> records = Arrays.asList(
                new LarkDatabaseRecord("id1", "Record One"),
                new LarkDatabaseRecord("id2", "record_two_123")
        );
        List<LarkDatabaseRecord> sanitized = larkBaseService.sanitizeRecords(records);
        assertEquals(2, sanitized.size());
        assertEquals("record_one", sanitized.get(0).name());
        assertEquals("record_two_123", sanitized.get(1).name());
    }

    @Test(expected = RuntimeException.class)
    public void sanitizeRecords_duplicateNames_shouldThrowException() {
        List<LarkDatabaseRecord> records = Arrays.asList(
                new LarkDatabaseRecord("id1", "Duplicate Name"),
                new LarkDatabaseRecord("id2", "Duplicate Name")
        );
        larkBaseService.sanitizeRecords(records);
    }

    @Test(expected = RuntimeException.class)
    public void sanitizeRecords_nullId_shouldThrowException() {
        List<LarkDatabaseRecord> records = Collections.singletonList(
                new LarkDatabaseRecord(null, "Valid Name")
        );
        larkBaseService.sanitizeRecords(records);
    }

    @Test(expected = RuntimeException.class)
    public void sanitizeRecords_nullName_shouldThrowException() {
        List<LarkDatabaseRecord> records = Collections.singletonList(
                new LarkDatabaseRecord("id1", null)
        );
        larkBaseService.sanitizeRecords(records);
    }

    @Test
    public void sanitizeRecords_nameWithSpecialChars() {
        List<LarkDatabaseRecord> records = Collections.singletonList(
                new LarkDatabaseRecord("id1", "Name@With-Special!Chars#")
        );
        List<LarkDatabaseRecord> sanitized = larkBaseService.sanitizeRecords(records);
        assertEquals("name_with_special_chars_", sanitized.get(0).name());
    }
}