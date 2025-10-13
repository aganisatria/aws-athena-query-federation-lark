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
package com.amazonaws.athena.connectors.lark.testing.mock;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DeleteParameterRequest;
import software.amazon.awssdk.services.ssm.model.DeleteParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of AWS Systems Manager (SSM) Parameter Store for testing.
 * Provides in-memory storage for parameters.
 *
 * <p>This mock client:
 * <ul>
 *   <li>Stores parameters in memory</li>
 *   <li>Supports String, StringList, and SecureString types</li>
 *   <li>Implements parameter hierarchy with GetParametersByPath</li>
 *   <li>Thread-safe using ConcurrentHashMap</li>
 * </ul>
 */
public class MockSSMClient implements SsmClient
{
    private final Map<String, ParameterData> parameters = new ConcurrentHashMap<>();

    private static class ParameterData
    {
        String value;
        ParameterType type;
        String description;
        Instant lastModified;
        long version;

        ParameterData(String value, ParameterType type, String description)
        {
            this.value = value;
            this.type = type;
            this.description = description;
            this.lastModified = Instant.now();
            this.version = 1;
        }
    }

    @Override
    public String serviceName()
    {
        return "ssm";
    }

    @Override
    public void close()
    {
        parameters.clear();
    }

    @Override
    public GetParameterResponse getParameter(GetParameterRequest request)
    {
        String name = request.name();
        ParameterData data = parameters.get(name);

        if (data == null) {
            throw ParameterNotFoundException.builder()
                    .message("Parameter not found: " + name)
                    .build();
        }

        String value = data.value;
        // If WithDecryption is false and parameter is SecureString, return masked value
        if (Boolean.FALSE.equals(request.withDecryption()) && data.type == ParameterType.SECURE_STRING) {
            value = "****";
        }

        Parameter parameter = Parameter.builder()
                .name(name)
                .value(value)
                .type(data.type)
                .lastModifiedDate(data.lastModified)
                .version(data.version)
                .arn("arn:aws:ssm:us-east-1:123456789012:parameter" + name)
                .build();

        return GetParameterResponse.builder()
                .parameter(parameter)
                .build();
    }

    @Override
    public GetParametersByPathResponse getParametersByPath(GetParametersByPathRequest request)
    {
        String path = request.path();
        boolean recursive = Boolean.TRUE.equals(request.recursive());

        List<Parameter> matchingParameters = parameters.entrySet().stream()
                .filter(entry -> {
                    String paramName = entry.getKey();
                    if (recursive) {
                        return paramName.startsWith(path);
                    }
                    else {
                        // Non-recursive: only direct children
                        if (!paramName.startsWith(path)) {
                            return false;
                        }
                        String remainder = paramName.substring(path.length());
                        if (remainder.startsWith("/")) {
                            remainder = remainder.substring(1);
                        }
                        return !remainder.contains("/");
                    }
                })
                .map(entry -> {
                    String name = entry.getKey();
                    ParameterData data = entry.getValue();

                    String value = data.value;
                    if (Boolean.FALSE.equals(request.withDecryption()) && data.type == ParameterType.SECURE_STRING) {
                        value = "****";
                    }

                    return Parameter.builder()
                            .name(name)
                            .value(value)
                            .type(data.type)
                            .lastModifiedDate(data.lastModified)
                            .version(data.version)
                            .arn("arn:aws:ssm:us-east-1:123456789012:parameter" + name)
                            .build();
                })
                .collect(Collectors.toList());

        return GetParametersByPathResponse.builder()
                .parameters(matchingParameters)
                .build();
    }

    @Override
    public PutParameterResponse putParameter(PutParameterRequest request)
    {
        String name = request.name();
        ParameterData existing = parameters.get(name);

        ParameterType type = request.type() != null ? request.type() : ParameterType.STRING;
        ParameterData data = new ParameterData(request.value(), type, request.description());

        if (existing != null) {
            data.version = existing.version + 1;
        }

        parameters.put(name, data);

        return PutParameterResponse.builder()
                .version(data.version)
                .build();
    }

    @Override
    public DeleteParameterResponse deleteParameter(DeleteParameterRequest request)
    {
        String name = request.name();

        if (parameters.remove(name) == null) {
            throw ParameterNotFoundException.builder()
                    .message("Parameter not found: " + name)
                    .build();
        }

        return DeleteParameterResponse.builder().build();
    }

    // ========================================================================
    // Helper Methods for Testing
    // ========================================================================

    /**
     * Convenience method to put a String parameter.
     */
    public void putParameter(String name, String value)
    {
        putParameter(name, value, ParameterType.STRING);
    }

    /**
     * Convenience method to put a parameter with specific type.
     */
    public void putParameter(String name, String value, ParameterType type)
    {
        parameters.put(name, new ParameterData(value, type, null));
    }

    /**
     * Check if a parameter exists.
     */
    public boolean parameterExists(String name)
    {
        return parameters.containsKey(name);
    }

    /**
     * Get all parameter names (for testing/debugging).
     */
    public List<String> getAllParameterNames()
    {
        return new ArrayList<>(parameters.keySet());
    }

    /**
     * Clear all parameters (for cleanup between tests).
     */
    public void clearAll()
    {
        parameters.clear();
    }

    /**
     * Pre-populate common test parameters.
     */
    public void populateDefaultTestParameters()
    {
        // Lark configuration
        putParameter("/lark/app_id", "test_app_id");
        putParameter("/lark/app_secret", "test_app_secret", ParameterType.SECURE_STRING);
        putParameter("/lark/base/data_source_id", "test_base_id");
        putParameter("/lark/table/data_source_id", "test_table_id");

        // Athena configuration
        putParameter("/athena/catalog", "test_catalog");
        putParameter("/athena/workgroup", "primary");

        // Feature flags
        putParameter("/features/enable_parallel_split", "true");
        putParameter("/features/enable_debug_logging", "false");
    }
}
