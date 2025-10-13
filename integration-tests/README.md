# Integration Tests

Integration tests for AWS Athena Lark Base Connector with support for LocalStack Community Edition and mocking.

## Overview

This module provides a comprehensive testing framework that supports three test modes:

| Mode | AWS Services | Infrastructure | Speed | Cost | Use Case |
|------|-------------|----------------|-------|------|----------|
| **MOCK** | All mocked in-memory | None | Fastest | $0 | Unit tests, CI/CD |
| **HYBRID** | LocalStack (Lambda, S3) + Mocks (Glue, Secrets, SSM) | Docker | Fast | $0 | Integration tests |
| **AWS** | Real AWS services | AWS Account | Slow | $ | E2E validation |

## Prerequisites

### For MOCK Mode (Default)
- Java 17+
- Python 3.9+
- Maven 3.6+

### For HYBRID Mode
- All MOCK requirements
- Docker + Docker Compose
- 4GB RAM available for containers

### For AWS Mode
- All MOCK requirements
- AWS account with appropriate permissions
- AWS CLI configured

## Quick Start

### 1. Setup

**Install dependencies:**

```bash
# Java dependencies
cd integration-tests
mvn clean install

# Python dependencies
cd python
pip install -r requirements.txt
```

**Configure environment:**

```bash
# For MOCK mode (default)
export TEST_ENVIRONMENT=mock

# For HYBRID mode (LocalStack Community)
export TEST_ENVIRONMENT=hybrid

# For AWS mode (real AWS)
export TEST_ENVIRONMENT=aws
export AWS_REGION=us-east-1
```

### 2. Run Tests

**Java Tests:**

```bash
# MOCK mode (default)
mvn clean verify -P test-mock

# HYBRID mode
mvn clean verify -P test-hybrid

# AWS mode
mvn clean verify -P test-aws
```

**Python Tests:**

```bash
cd python

# MOCK mode
export TEST_ENVIRONMENT=mock
python -m pytest tests/ -v

# HYBRID mode (requires Docker)
export TEST_ENVIRONMENT=hybrid
python -m pytest tests/ -v

# AWS mode
export TEST_ENVIRONMENT=aws
python -m pytest tests/ -v
```

## Directory Structure

```
integration-tests/
├── pom.xml                                    # Maven configuration
├── README.md                                  # This file
├── src/
│   ├── main/java/
│   │   └── com/amazonaws/athena/connectors/lark/testing/
│   │       ├── config/
│   │       │   └── TestEnvironment.java       # Test environment enum
│   │       ├── client/
│   │       │   └── TestClientFactory.java     # AWS client factory
│   │       ├── mock/
│   │       │   ├── MockGlueClient.java        # Mock Glue Data Catalog
│   │       │   ├── MockSecretsManagerClient.java
│   │       │   └── MockSSMClient.java
│   │       ├── container/                     # Testcontainers (future)
│   │       └── fixtures/                      # Test data fixtures
│   ├── test/java/
│   │   └── com/amazonaws/athena/connectors/lark/integration/
│   │       ├── lambda/                        # Lambda handler tests
│   │       ├── business/                      # Business logic tests
│   │       └── e2e/                           # End-to-end tests
│   └── main/resources/
│       ├── localstack/
│       │   ├── docker-compose.yml             # LocalStack + WireMock
│       │   └── init-scripts.sh                # LocalStack setup
│       └── mocks/
│           └── mappings/                      # WireMock Lark API mocks
└── python/
    ├── requirements.txt
    ├── config.py                              # Test configuration
    ├── clients/
    │   ├── aws_client.py                      # AWS client factory
    │   ├── mock_glue.py
    │   ├── mock_secrets.py
    │   └── mock_ssm.py
    └── tests/
        ├── integration/                       # Integration tests
        └── e2e/                               # End-to-end tests
```

## Using LocalStack Community Edition

### Start LocalStack

```bash
cd integration-tests/src/main/resources/localstack
docker-compose up -d
```

### Verify Services

```bash
# Check health
curl http://localhost:4566/_localstack/health

# List S3 buckets
aws --endpoint-url=http://localhost:4566 s3 ls

# Check WireMock
curl http://localhost:8080/__admin/health
```

### Stop LocalStack

```bash
cd integration-tests/src/main/resources/localstack
docker-compose down
```

## Writing Tests

### Java Example

```java
@Test
public void testMetadataHandler_withMockedGlue() {
    // Create client factory
    TestClientFactory factory = new TestClientFactory(TestEnvironment.MOCK);

    // Pre-populate test data
    MockGlueClient glue = factory.getMockGlueClient();
    glue.createDatabase(CreateDatabaseRequest.builder()
            .databaseInput(DatabaseInput.builder()
                    .name("test_db")
                    .build())
            .build());

    // Test your handler
    GlueClient glueClient = factory.createGlueClient();
    Database db = glueClient.getDatabase(GetDatabaseRequest.builder()
            .name("test_db")
            .build()).database();

    assertThat(db.name()).isEqualTo("test_db");
}
```

### Python Example

```python
from clients import AWSClientFactory

def test_glue_operations():
    # Create client factory
    factory = AWSClientFactory()

    # Get Glue client (auto-mocked in MOCK/HYBRID mode)
    glue = factory.create_glue_client()

    # Pre-populate test data
    if factory.environment != TestEnvironment.AWS:
        mock_glue = factory.get_mock_glue_client()
        mock_glue.create_database(DatabaseInput={"Name": "test_db"})

    # Test operations
    response = glue.get_database(Name="test_db")
    assert response["Database"]["Name"] == "test_db"

    # Cleanup
    factory.cleanup()
```

## Test Modes in Detail

### MOCK Mode

**What's mocked:**
- ✅ Glue Data Catalog (in-memory)
- ✅ Secrets Manager (in-memory)
- ✅ SSM Parameter Store (in-memory)
- ✅ Lark API (WireMock responses)

**What's NOT available:**
- ❌ Lambda invocation
- ❌ S3 operations
- ❌ Athena queries

**Best for:**
- Unit tests
- Business logic testing
- Filter translation tests
- Type conversion tests
- CI/CD pipelines

### HYBRID Mode

**What's real (via LocalStack Community):**
- ✅ Lambda invocation
- ✅ S3 operations
- ✅ CloudWatch Logs

**What's mocked:**
- ✅ Glue (not in LocalStack Community)
- ✅ Secrets Manager (not in LocalStack Community)
- ✅ SSM (not in LocalStack Community)
- ✅ Athena (not in LocalStack Community)
- ✅ Lark API (WireMock)

**Best for:**
- Lambda handler testing
- Integration testing
- Pre-deployment validation

### AWS Mode

**What's real:**
- ✅ All AWS services
- ✅ Lark API (if configured)

**Best for:**
- Final E2E validation
- Production smoke tests
- Performance testing

## Mocking Lark API

WireMock is configured to mock Lark API responses. Mappings are in `src/main/resources/mocks/mappings/`.

**Example: Add new Lark API mock**

Create `src/main/resources/mocks/mappings/my-endpoint.json`:

```json
{
  "mappings": [{
    "name": "My Lark API Endpoint",
    "request": {
      "method": "GET",
      "urlPath": "/open-apis/bitable/v1/my/endpoint"
    },
    "response": {
      "status": 200,
      "jsonBody": {
        "code": 0,
        "msg": "success",
        "data": {}
      }
    }
  }]
}
```

## Troubleshooting

### LocalStack not starting

```bash
# Check Docker
docker ps

# Check logs
docker logs lark-connector-localstack

# Reset
docker-compose down -v
docker-compose up -d
```

### Tests failing in HYBRID mode

```bash
# Verify LocalStack health
curl http://localhost:4566/_localstack/health

# Check WireMock
curl http://localhost:8080/__admin/health

# View LocalStack logs
docker logs lark-connector-localstack -f
```

### Python import errors

```bash
# Install in editable mode
cd integration-tests/python
pip install -e .
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test-mock:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run MOCK tests
        run: |
          export TEST_ENVIRONMENT=mock
          mvn clean verify -P test-mock

  test-hybrid:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Start LocalStack
        run: |
          cd integration-tests/src/main/resources/localstack
          docker-compose up -d
      - name: Run HYBRID tests
        run: |
          export TEST_ENVIRONMENT=hybrid
          mvn clean verify -P test-hybrid
      - name: Stop LocalStack
        if: always()
        run: |
          cd integration-tests/src/main/resources/localstack
          docker-compose down
```

## Cost Analysis

| Mode | Daily Dev | CI/CD (per build) | Monthly (intensive) |
|------|-----------|-------------------|---------------------|
| MOCK | $0 | $0 | $0 |
| HYBRID | $0 | $0 | $0 |
| AWS | ~$0.10 | ~$0.50 | ~$15-30 |

## Next Steps

1. **Run existing tests**: `mvn clean verify -P test-mock`
2. **Start LocalStack**: See "Using LocalStack Community Edition"
3. **Write your first test**: See "Writing Tests"
4. **Migrate legacy tests**: See Python migration guide below

## Migrating Legacy Python Tests

Legacy tests from project root can be migrated:

```bash
# Old location
./test-glue-crawler.py

# New location
integration-tests/python/tests/integration/test_glue_crawler.py
```

Update imports to use the new client factory:

```python
# Old
import boto3
glue = boto3.client('glue')

# New
from clients import AWSClientFactory
factory = AWSClientFactory()
glue = factory.create_glue_client()
```

## Support

- **GitHub Issues**: [Report issues](https://github.com/aganisatria/aws-athena-query-federation-lark/issues)
- **Documentation**: See main [README.md](../README.md)

## License

Apache License 2.0 - See [LICENSE](../LICENSE)
