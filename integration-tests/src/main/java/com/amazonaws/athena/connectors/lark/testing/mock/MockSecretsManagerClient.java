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

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of AWS Secrets Manager Client for testing.
 * Provides in-memory storage for secrets.
 *
 * <p>This mock client:
 * <ul>
 *   <li>Stores secrets in memory</li>
 *   <li>Supports both string and binary secrets</li>
 *   <li>Thread-safe using ConcurrentHashMap</li>
 *   <li>Throws appropriate exceptions for missing secrets</li>
 * </ul>
 */
public class MockSecretsManagerClient implements SecretsManagerClient
{
    private final Map<String, SecretValue> secrets = new ConcurrentHashMap<>();

    private static class SecretValue
    {
        String stringValue;
        byte[] binaryValue;
        String versionId;

        SecretValue(String stringValue, byte[] binaryValue)
        {
            this.stringValue = stringValue;
            this.binaryValue = binaryValue;
            this.versionId = UUID.randomUUID().toString();
        }
    }

    @Override
    public String serviceName()
    {
        return "secretsmanager";
    }

    @Override
    public void close()
    {
        secrets.clear();
    }

    @Override
    public GetSecretValueResponse getSecretValue(GetSecretValueRequest request)
    {
        String secretId = request.secretId();
        SecretValue value = secrets.get(secretId);

        if (value == null) {
            throw ResourceNotFoundException.builder()
                    .message("Secret not found: " + secretId)
                    .build();
        }

        GetSecretValueResponse.Builder builder = GetSecretValueResponse.builder()
                .arn("arn:aws:secretsmanager:us-east-1:123456789012:secret:" + secretId)
                .name(secretId)
                .versionId(value.versionId);

        if (value.stringValue != null) {
            builder.secretString(value.stringValue);
        }
        if (value.binaryValue != null) {
            builder.secretBinary(SdkBytes.fromByteArray(value.binaryValue));
        }

        return builder.build();
    }

    @Override
    public CreateSecretResponse createSecret(CreateSecretRequest request)
    {
        String name = request.name();
        String stringValue = request.secretString();
        byte[] binaryValue = request.secretBinary() != null ? request.secretBinary().asByteArray() : null;

        SecretValue value = new SecretValue(stringValue, binaryValue);
        secrets.put(name, value);

        return CreateSecretResponse.builder()
                .arn("arn:aws:secretsmanager:us-east-1:123456789012:secret:" + name)
                .name(name)
                .versionId(value.versionId)
                .build();
    }

    @Override
    public PutSecretValueResponse putSecretValue(PutSecretValueRequest request)
    {
        String secretId = request.secretId();
        String stringValue = request.secretString();
        byte[] binaryValue = request.secretBinary() != null ? request.secretBinary().asByteArray() : null;

        SecretValue value = new SecretValue(stringValue, binaryValue);
        secrets.put(secretId, value);

        return PutSecretValueResponse.builder()
                .arn("arn:aws:secretsmanager:us-east-1:123456789012:secret:" + secretId)
                .name(secretId)
                .versionId(value.versionId)
                .build();
    }

    @Override
    public DeleteSecretResponse deleteSecret(DeleteSecretRequest request)
    {
        String secretId = request.secretId();

        if (secrets.remove(secretId) == null) {
            throw ResourceNotFoundException.builder()
                    .message("Secret not found: " + secretId)
                    .build();
        }

        return DeleteSecretResponse.builder()
                .arn("arn:aws:secretsmanager:us-east-1:123456789012:secret:" + secretId)
                .name(secretId)
                .build();
    }

    // ========================================================================
    // Helper Methods for Testing
    // ========================================================================

    /**
     * Convenience method to store a string secret.
     */
    public void putSecret(String name, String value)
    {
        secrets.put(name, new SecretValue(value, null));
    }

    /**
     * Convenience method to store a binary secret.
     */
    public void putBinarySecret(String name, byte[] value)
    {
        secrets.put(name, new SecretValue(null, value));
    }

    /**
     * Check if a secret exists.
     */
    public boolean secretExists(String name)
    {
        return secrets.containsKey(name);
    }

    /**
     * Get all secret names (for testing/debugging).
     */
    public Map<String, SecretValue> getAllSecrets()
    {
        return new ConcurrentHashMap<>(secrets);
    }

    /**
     * Clear all secrets (for cleanup between tests).
     */
    public void clearAll()
    {
        secrets.clear();
    }

    /**
     * Pre-populate common test secrets.
     */
    public void populateDefaultTestSecrets()
    {
        // Lark credentials
        putSecret("lark-app-credentials",
                "{\"app_id\":\"test_app_id\",\"app_secret\":\"test_app_secret\"}");

        // Alternative format
        putSecret("lark-app-id", "test_app_id");
        putSecret("lark-app-secret", "test_app_secret");
    }
}
