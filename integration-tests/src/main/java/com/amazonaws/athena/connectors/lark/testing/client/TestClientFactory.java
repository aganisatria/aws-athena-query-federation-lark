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
package com.amazonaws.athena.connectors.lark.testing.client;

import com.amazonaws.athena.connectors.lark.testing.config.TestEnvironment;
import com.amazonaws.athena.connectors.lark.testing.mock.MockGlueClient;
import com.amazonaws.athena.connectors.lark.testing.mock.MockSSMClient;
import com.amazonaws.athena.connectors.lark.testing.mock.MockSecretsManagerClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

/**
 * Factory for creating AWS clients based on test environment.
 *
 * <p>Supports three modes:
 * <ul>
 *   <li>MOCK - All clients mocked in-memory</li>
 *   <li>HYBRID - LocalStack (Lambda, S3) + Mocks (Glue, Secrets, SSM)</li>
 *   <li>AWS - Real AWS clients</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * TestClientFactory factory = new TestClientFactory();
 * GlueClient glue = factory.createGlueClient();
 * LambdaClient lambda = factory.createLambdaClient();
 * }</pre>
 */
public class TestClientFactory
{
    private final TestEnvironment environment;
    private final MockGlueClient mockGlue;
    private final MockSecretsManagerClient mockSecrets;
    private final MockSSMClient mockSSM;

    // LocalStack configuration
    private static final String LOCALSTACK_ENDPOINT = System.getenv("LOCALSTACK_ENDPOINT") != null
            ? System.getenv("LOCALSTACK_ENDPOINT")
            : "http://localhost:4566";
    private static final Region LOCALSTACK_REGION = Region.US_EAST_1;
    private static final StaticCredentialsProvider LOCALSTACK_CREDENTIALS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    // AWS configuration
    private static final String AWS_REGION_STR = System.getenv("AWS_REGION") != null
            ? System.getenv("AWS_REGION")
            : "us-east-1";
    private static final Region AWS_REGION = Region.of(AWS_REGION_STR);

    /**
     * Create factory with environment from system property/environment variable.
     */
    public TestClientFactory()
    {
        this(TestEnvironment.fromEnvironment());
    }

    /**
     * Create factory with specific test environment.
     *
     * @param environment Test environment mode
     */
    public TestClientFactory(TestEnvironment environment)
    {
        this.environment = environment;

        // Initialize mock clients (reused across all mock/hybrid calls)
        this.mockGlue = new MockGlueClient();
        this.mockSecrets = new MockSecretsManagerClient();
        this.mockSSM = new MockSSMClient();

        // Pre-populate test data for mock/hybrid modes
        if (environment.usesMocks()) {
            mockSecrets.populateDefaultTestSecrets();
            mockSSM.populateDefaultTestParameters();
        }
    }

    // ========================================================================
    // Glue Client (Always mocked in MOCK/HYBRID)
    // ========================================================================

    /**
     * Create Glue client based on environment.
     * - MOCK/HYBRID: Returns mock Glue client
     * - AWS: Returns real AWS Glue client
     */
    public GlueClient createGlueClient()
    {
        if (environment.usesRealAWS()) {
            return GlueClient.builder()
                    .region(AWS_REGION)
                    .build();
        }
        // MOCK or HYBRID - always use mock Glue (not available in LocalStack Community)
        return mockGlue;
    }

    // ========================================================================
    // Secrets Manager Client (Always mocked in MOCK/HYBRID)
    // ========================================================================

    /**
     * Create Secrets Manager client based on environment.
     * - MOCK/HYBRID: Returns mock Secrets Manager
     * - AWS: Returns real AWS Secrets Manager
     */
    public SecretsManagerClient createSecretsManagerClient()
    {
        if (environment.usesRealAWS()) {
            return SecretsManagerClient.builder()
                    .region(AWS_REGION)
                    .build();
        }
        // MOCK or HYBRID - always use mock (not available in LocalStack Community)
        return mockSecrets;
    }

    // ========================================================================
    // SSM Client (Always mocked in MOCK/HYBRID)
    // ========================================================================

    /**
     * Create SSM client based on environment.
     * - MOCK/HYBRID: Returns mock SSM
     * - AWS: Returns real AWS SSM
     */
    public SsmClient createSsmClient()
    {
        if (environment.usesRealAWS()) {
            return SsmClient.builder()
                    .region(AWS_REGION)
                    .build();
        }
        // MOCK or HYBRID - always use mock (not available in LocalStack Community)
        return mockSSM;
    }

    // ========================================================================
    // Lambda Client (LocalStack in HYBRID, Real in AWS)
    // ========================================================================

    /**
     * Create Lambda client based on environment.
     * - MOCK: Not applicable (test handlers directly)
     * - HYBRID: Returns LocalStack Lambda client
     * - AWS: Returns real AWS Lambda client
     */
    public LambdaClient createLambdaClient()
    {
        if (environment == TestEnvironment.HYBRID) {
            return LambdaClient.builder()
                    .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                    .region(LOCALSTACK_REGION)
                    .credentialsProvider(LOCALSTACK_CREDENTIALS)
                    .build();
        }
        else if (environment.usesRealAWS()) {
            return LambdaClient.builder()
                    .region(AWS_REGION)
                    .build();
        }
        else {
            throw new UnsupportedOperationException(
                    "Lambda client not supported in MOCK mode. Test handlers directly.");
        }
    }

    // ========================================================================
    // S3 Client (LocalStack in HYBRID, Real in AWS)
    // ========================================================================

    /**
     * Create S3 client based on environment.
     * - MOCK: Not applicable
     * - HYBRID: Returns LocalStack S3 client
     * - AWS: Returns real AWS S3 client
     */
    public S3Client createS3Client()
    {
        if (environment == TestEnvironment.HYBRID) {
            return S3Client.builder()
                    .endpointOverride(URI.create(LOCALSTACK_ENDPOINT))
                    .region(LOCALSTACK_REGION)
                    .credentialsProvider(LOCALSTACK_CREDENTIALS)
                    .forcePathStyle(true) // Required for LocalStack
                    .build();
        }
        else if (environment.usesRealAWS()) {
            return S3Client.builder()
                    .region(AWS_REGION)
                    .build();
        }
        else {
            throw new UnsupportedOperationException(
                    "S3 client not supported in MOCK mode.");
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Get the current test environment.
     */
    public TestEnvironment getEnvironment()
    {
        return environment;
    }

    /**
     * Get mock Glue client (for pre-populating test data).
     */
    public MockGlueClient getMockGlueClient()
    {
        if (!environment.usesMocks()) {
            throw new UnsupportedOperationException(
                    "Mock Glue client only available in MOCK/HYBRID mode");
        }
        return mockGlue;
    }

    /**
     * Get mock Secrets Manager client (for pre-populating test secrets).
     */
    public MockSecretsManagerClient getMockSecretsManagerClient()
    {
        if (!environment.usesMocks()) {
            throw new UnsupportedOperationException(
                    "Mock Secrets Manager only available in MOCK/HYBRID mode");
        }
        return mockSecrets;
    }

    /**
     * Get mock SSM client (for pre-populating test parameters).
     */
    public MockSSMClient getMockSsmClient()
    {
        if (!environment.usesMocks()) {
            throw new UnsupportedOperationException(
                    "Mock SSM only available in MOCK/HYBRID mode");
        }
        return mockSSM;
    }

    /**
     * Clean up all resources.
     */
    public void close()
    {
        if (mockGlue != null) {
            mockGlue.close();
        }
        if (mockSecrets != null) {
            mockSecrets.close();
        }
        if (mockSSM != null) {
            mockSSM.close();
        }
    }
}
