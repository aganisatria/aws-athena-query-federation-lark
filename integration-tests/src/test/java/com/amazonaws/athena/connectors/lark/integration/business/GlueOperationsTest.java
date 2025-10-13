/*-
 * #%L
 * Integration Tests
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
package com.amazonaws.athena.connectors.lark.integration.business;

import com.amazonaws.athena.connectors.lark.testing.client.TestClientFactory;
import com.amazonaws.athena.connectors.lark.testing.config.TestEnvironment;
import com.amazonaws.athena.connectors.lark.testing.mock.MockGlueClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.CreateDatabaseRequest;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.Table;
import software.amazon.awssdk.services.glue.model.TableInput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example integration test demonstrating the testing framework.
 *
 * <p>This test works in all three modes:
 * <ul>
 *   <li>MOCK - Uses in-memory mock Glue client</li>
 *   <li>HYBRID - Uses in-memory mock Glue client (Glue not in LocalStack Community)</li>
 *   <li>AWS - Uses real AWS Glue Data Catalog</li>
 * </ul>
 */
class GlueOperationsTest
{
    private TestClientFactory factory;
    private GlueClient glueClient;

    @BeforeEach
    void setUp()
    {
        factory = new TestClientFactory();
        glueClient = factory.createGlueClient();

        // Pre-populate test data only if using mocks
        if (factory.getEnvironment().usesMocks()) {
            MockGlueClient mockGlue = factory.getMockGlueClient();

            // Create test database
            mockGlue.createDatabase(CreateDatabaseRequest.builder()
                    .databaseInput(DatabaseInput.builder()
                            .name("test_database")
                            .description("Test database for integration tests")
                            .build())
                    .build());

            // Create test table
            Map<String, String> parameters = new HashMap<>();
            parameters.put("larkBaseId", "test_base_123");
            parameters.put("larkTableId", "test_table_456");

            mockGlue.createSimpleTable(
                    "test_database",
                    "test_table",
                    List.of(
                            Column.builder().name("field_text").type("string").build(),
                            Column.builder().name("field_number").type("decimal").build(),
                            Column.builder().name("field_checkbox").type("boolean").build()
                    ),
                    parameters
            );
        }
    }

    @AfterEach
    void tearDown()
    {
        factory.close();
    }

    @Test
    void testGetDatabase()
    {
        // Act
        Database database = glueClient.getDatabase(GetDatabaseRequest.builder()
                .name("test_database")
                .build()).database();

        // Assert
        assertThat(database).isNotNull();
        assertThat(database.name()).isEqualTo("test_database");
        assertThat(database.description()).isEqualTo("Test database for integration tests");
    }

    @Test
    void testGetTable()
    {
        // Act
        Table table = glueClient.getTable(GetTableRequest.builder()
                .databaseName("test_database")
                .name("test_table")
                .build()).table();

        // Assert
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("test_table");
        assertThat(table.databaseName()).isEqualTo("test_database");

        // Check columns
        List<Column> columns = table.storageDescriptor().columns();
        assertThat(columns).hasSize(3);
        assertThat(columns).extracting(Column::name)
                .containsExactlyInAnyOrder("field_text", "field_number", "field_checkbox");

        // Check parameters
        Map<String, String> parameters = table.parameters();
        assertThat(parameters).containsEntry("larkBaseId", "test_base_123");
        assertThat(parameters).containsEntry("larkTableId", "test_table_456");
    }

    @Test
    void testTableMetadata()
    {
        // Arrange
        Table table = glueClient.getTable(GetTableRequest.builder()
                .databaseName("test_database")
                .name("test_table")
                .build()).table();

        // Act - Simulate reading Lark metadata from table parameters
        String larkBaseId = table.parameters().get("larkBaseId");
        String larkTableId = table.parameters().get("larkTableId");

        // Assert
        assertThat(larkBaseId).isEqualTo("test_base_123");
        assertThat(larkTableId).isEqualTo("test_table_456");
    }
}
