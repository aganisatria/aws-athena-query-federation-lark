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
package com.amazonaws.athena.connectors.lark.testing.config;

/**
 * Enum representing different test environments.
 *
 * <p>Test modes:
 * <ul>
 *   <li>MOCK - All AWS services mocked in-memory (fastest, no infrastructure)</li>
 *   <li>HYBRID - LocalStack Community (Lambda, S3) + Mocks (Glue, Athena, Secrets, SSM)</li>
 *   <li>AWS - Real AWS services (slowest, requires AWS credentials)</li>
 * </ul>
 */
public enum TestEnvironment
{
    /**
     * All services mocked in-memory.
     * Best for: Unit tests, business logic testing, CI/CD
     */
    MOCK,

    /**
     * LocalStack Community (Lambda, S3) + Mocked services (Glue, Athena, Secrets, SSM).
     * Best for: Integration tests, Lambda handler testing
     */
    HYBRID,

    /**
     * Real AWS services.
     * Best for: E2E tests, production validation
     */
    AWS;

    /**
     * Get test environment from system property or environment variable.
     *
     * @return TestEnvironment based on TEST_ENVIRONMENT variable (defaults to MOCK)
     */
    public static TestEnvironment fromEnvironment()
    {
        String env = System.getProperty("TEST_ENVIRONMENT", System.getenv("TEST_ENVIRONMENT"));
        if (env == null || env.trim().isEmpty()) {
            return MOCK;
        }

        try {
            return valueOf(env.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            System.err.println("Invalid TEST_ENVIRONMENT: " + env + ", defaulting to MOCK");
            return MOCK;
        }
    }

    /**
     * Check if running in mock mode.
     *
     * @return true if MOCK or HYBRID (uses mocks for Glue/Athena/Secrets)
     */
    public boolean usesMocks()
    {
        return this == MOCK || this == HYBRID;
    }

    /**
     * Check if running with LocalStack.
     *
     * @return true if HYBRID (uses LocalStack for Lambda/S3)
     */
    public boolean usesLocalStack()
    {
        return this == HYBRID;
    }

    /**
     * Check if running against real AWS.
     *
     * @return true if AWS
     */
    public boolean usesRealAWS()
    {
        return this == AWS;
    }
}
