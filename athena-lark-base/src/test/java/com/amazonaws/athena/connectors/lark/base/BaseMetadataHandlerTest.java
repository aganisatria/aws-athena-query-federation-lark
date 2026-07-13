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
package com.amazonaws.athena.connectors.lark.base;

import com.amazonaws.athena.connector.lambda.ThrottlingInvoker;
import com.amazonaws.athena.connector.lambda.data.BlockAllocator;
import com.amazonaws.athena.connector.lambda.data.BlockAllocatorImpl;
import com.amazonaws.athena.connector.lambda.metadata.*;
import com.amazonaws.athena.connector.lambda.security.EncryptionKeyFactory;
import com.amazonaws.athena.connector.lambda.security.LocalKeyFactory;
import com.amazonaws.athena.connectors.lark.base.metadataProvider.ExperimentalMetadataProvider;
import com.amazonaws.athena.connectors.lark.base.metadataProvider.LarkSourceMetadataProvider;
import com.amazonaws.athena.connectors.lark.base.model.TableDirectInitialized;
import com.amazonaws.athena.connectors.lark.base.model.response.SearchRecordsResponse;
import com.amazonaws.athena.connectors.lark.base.service.EnvVarService;
import com.amazonaws.athena.connectors.lark.base.service.GlueCatalogService;
import com.amazonaws.athena.connectors.lark.base.service.LarkBaseService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BaseMetadataHandlerTest {

    @Mock
    private GlueClient mockGlueClient;

    @Mock
    private SecretsManagerClient mockSecretsManager;

    @Mock
    private AthenaClient mockAthena;

    @Mock
    private EnvVarService mockEnvVarService;

    @Mock
    private LarkBaseService mockLarkBaseService;

    @Mock
    private GlueCatalogService mockGlueCatalogService;

    @Mock
    private LarkSourceMetadataProvider mockLarkSourceMetadataProvider;

    @Mock
    private ExperimentalMetadataProvider mockExperimentalMetadataProvider;

    @Mock
    private ThrottlingInvoker mockInvoker;

    private BlockAllocator allocator;
    private BaseMetadataHandler handler;

    @Before
    public void setUp() {
        allocator = new BlockAllocatorImpl();
        EncryptionKeyFactory keyFactory = new LocalKeyFactory();
        Map<String, String> configOptions = new HashMap<>();
        List<TableDirectInitialized> mockMappingTable = Collections.emptyList();

        handler = new BaseMetadataHandler(
                mockGlueClient,
                keyFactory,
                mockSecretsManager,
                mockAthena,
                "test-bucket",
                "test-prefix",
                configOptions,
                mockEnvVarService,
                mockLarkBaseService,
                mockGlueCatalogService,
                mockMappingTable,
                mockLarkSourceMetadataProvider,
                mockExperimentalMetadataProvider,
                mockInvoker
        );
    }

    @After
    public void tearDown() {
        if (allocator != null) {
            allocator.close();
        }
    }

    @Test
    public void testConstructor() {
        assertNotNull(handler);
    }

    @Test
    public void testDoGetTable_BlacklistedTable_ThrowsAthenaConnectorException() {
        when(mockEnvVarService.getWhitelistTables()).thenReturn("");
        when(mockEnvVarService.getBlacklistTables()).thenReturn("schemaa:table1");

        com.amazonaws.athena.connector.lambda.security.FederatedIdentity identity =
                new com.amazonaws.athena.connector.lambda.security.FederatedIdentity("arn", "account", Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
        GetTableRequest request = new GetTableRequest(identity, "queryId", "catalog",
                new com.amazonaws.athena.connector.lambda.domain.TableName("schemaA", "table1"), Collections.emptyMap());

        assertThrows(com.amazonaws.athena.connector.lambda.exceptions.AthenaConnectorException.class,
                () -> handler.doGetTable(allocator, request));
    }

    @Test
    public void testDoGetTable_TableNotInWhitelistedSchema_ThrowsAthenaConnectorException() {
        when(mockEnvVarService.getWhitelistTables()).thenReturn("schemaa:table1,schemaa:table2");
        when(mockEnvVarService.getBlacklistTables()).thenReturn("");

        com.amazonaws.athena.connector.lambda.security.FederatedIdentity identity =
                new com.amazonaws.athena.connector.lambda.security.FederatedIdentity("arn", "account", Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
        GetTableRequest request = new GetTableRequest(identity, "queryId", "catalog",
                new com.amazonaws.athena.connector.lambda.domain.TableName("schemaA", "table3"), Collections.emptyMap());

        assertThrows(com.amazonaws.athena.connector.lambda.exceptions.AthenaConnectorException.class,
                () -> handler.doGetTable(allocator, request));
    }

    @Test
    public void testDoGetTable_TableInWhitelistedSchema_DoesNotThrowFromAccessControlGuard() {
        when(mockEnvVarService.getWhitelistTables()).thenReturn("schemaa:table1");
        when(mockEnvVarService.getBlacklistTables()).thenReturn("");
        when(mockEnvVarService.isActivateLarkBaseSource()).thenReturn(false);
        when(mockEnvVarService.isActivateLarkDriveSource()).thenReturn(false);
        when(mockEnvVarService.isActivateExperimentalFeatures()).thenReturn(false);

        com.amazonaws.athena.connector.lambda.security.FederatedIdentity identity =
                new com.amazonaws.athena.connector.lambda.security.FederatedIdentity("arn", "account", Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
        GetTableRequest request = new GetTableRequest(identity, "queryId", "catalog",
                new com.amazonaws.athena.connector.lambda.domain.TableName("schemaA", "table1"), Collections.emptyMap());

        // The access-control guard must not be what blocks this table (it's allowed); whatever the handler
        // does next (Glue fallback failing since nothing is mocked further) is out of scope for this test.
        try {
            handler.doGetTable(allocator, request);
        }
        catch (com.amazonaws.athena.connector.lambda.exceptions.AthenaConnectorException e) {
            assertFalse("Access-control guard should not be the cause of failure for an allowed table",
                    e.getMessage() != null && e.getMessage().contains("Table not found"));
        }
        catch (Exception e) {
            // Any other failure downstream (e.g. Glue fallback) is unrelated to the access-control guard.
        }
    }

    @Test
    public void testDoGetDataSourceCapabilities() {
        GetDataSourceCapabilitiesRequest request = mock(GetDataSourceCapabilitiesRequest.class);
        when(request.getCatalogName()).thenReturn("test-catalog");

        GetDataSourceCapabilitiesResponse response = handler.doGetDataSourceCapabilities(allocator, request);

        assertNotNull(response);
        assertEquals("test-catalog", response.getCatalogName());
        assertNotNull(response.getCapabilities());
        assertFalse(response.getCapabilities().isEmpty());
        // Verify it has at least 3 capabilities
        assertTrue(response.getCapabilities().size() >= 3);
    }

    @Test
    public void testEnhancePartitionSchema() {
        GetTableLayoutRequest request = mock(GetTableLayoutRequest.class);
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(request.getTableName()).thenReturn(tableName);

        com.amazonaws.athena.connector.lambda.data.SchemaBuilder schemaBuilder =
            com.amazonaws.athena.connector.lambda.data.SchemaBuilder.newBuilder();

        handler.enhancePartitionSchema(schemaBuilder, request);

        org.apache.arrow.vector.types.pojo.Schema schema = schemaBuilder.build();
        assertNotNull(schema);
        // Verify required partition fields are added
        assertNotNull(schema.findField("base_id"));
        assertNotNull(schema.findField("table_id"));
        assertNotNull(schema.findField("filter_expression"));
        assertNotNull(schema.findField("page_size"));
        assertNotNull(schema.findField("expected_row_count"));
    }

    @Test
    public void testDoGetSplitsWithNoPartitions() {
        GetSplitsRequest request = mock(GetSplitsRequest.class);
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(request.getTableName()).thenReturn(tableName);
        when(request.getCatalogName()).thenReturn("test-catalog");

        // Create empty block
        com.amazonaws.athena.connector.lambda.data.Block partitions = allocator.createBlock(
            com.amazonaws.athena.connector.lambda.data.SchemaBuilder.newBuilder().build());
        when(request.getPartitions()).thenReturn(partitions);

        GetSplitsResponse response = handler.doGetSplits(allocator, request);

        assertNotNull(response);
        assertEquals("test-catalog", response.getCatalogName());
        assertTrue(response.getSplits().isEmpty());
    }

    @Test
    public void testShouldUseParallelSplits_falseWhenTableHasNoParallelSplitKey() {
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");

        // Absence of the split key must short-circuit before even checking the activation flag.
        boolean result = handler.shouldUseParallelSplits(false, "base1", "tbl1", "", tableName);

        assertFalse(result);
    }

    @Test
    public void testShouldUseParallelSplits_falseWhenFeatureNotActivated() {
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(mockEnvVarService.isActivateParallelSplit()).thenReturn(false);

        boolean result = handler.shouldUseParallelSplits(true, "base1", "tbl1", "", tableName);

        assertFalse(result);
    }

    @Test
    public void testShouldUseParallelSplits_trueWhenNoFilter() throws Exception {
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(mockEnvVarService.isActivateParallelSplit()).thenReturn(true);

        boolean result = handler.shouldUseParallelSplits(true, "base1", "tbl1", "", tableName);

        assertTrue(result);
        // No filter means no selectivity check is needed, so the row-count lookup must never fire.
        verify(mockInvoker, never()).invoke(any());
    }

    @Test
    public void testShouldUseParallelSplits_falseWhenFilterIsHighlySelective() throws Exception {
        // A selective filter (e.g. WHERE id = 'x') matching only a handful of rows must not trigger parallel
        // splitting, because splits are sized off the table's full row count and would mostly return zero rows.
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(mockEnvVarService.isActivateParallelSplit()).thenReturn(true);

        SearchRecordsResponse response = (SearchRecordsResponse) SearchRecordsResponse.builder()
                .data(SearchRecordsResponse.ListData.builder()
                        .items(Collections.emptyList())
                        .hasMore(false)
                        .total(1)
                        .build())
                .build();
        when(mockInvoker.invoke(any())).thenReturn(response);

        boolean result = handler.shouldUseParallelSplits(true, "base1", "tbl1", "{\"conditions\":[]}", tableName);

        assertFalse(result);
    }

    @Test
    public void testShouldUseParallelSplits_trueWhenFilterMatchesManyRows() throws Exception {
        com.amazonaws.athena.connector.lambda.domain.TableName tableName =
            new com.amazonaws.athena.connector.lambda.domain.TableName("test_schema", "test_table");
        when(mockEnvVarService.isActivateParallelSplit()).thenReturn(true);

        SearchRecordsResponse response = (SearchRecordsResponse) SearchRecordsResponse.builder()
                .data(SearchRecordsResponse.ListData.builder()
                        .items(Collections.emptyList())
                        .hasMore(false)
                        .total(50_000)
                        .build())
                .build();
        when(mockInvoker.invoke(any())).thenReturn(response);

        boolean result = handler.shouldUseParallelSplits(true, "base1", "tbl1", "{\"conditions\":[]}", tableName);

        assertTrue(result);
    }
}
