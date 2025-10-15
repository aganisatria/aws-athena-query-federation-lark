# Integration Tests

This document provides a comprehensive guide to the testing framework. For a fast path to verify your setup, start with the quick verification steps below.

---

## Quick Verification (5 Minutes)

This guide verifies your zero-cost testing infrastructure is working correctly.

### Step 1: Verify Java Build (1 minute)

```bash
cd integration-tests

# Compile Java tests
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home" \
  mvn clean compile -Dcheckstyle.skip=true

# Expected output:
# [INFO] BUILD SUCCESS
```

✅ **Success:** Java framework compiled successfully

### Step 2: Verify Python Setup (1 minute)

```bash
cd integration-tests/python

# Install dependencies
pip install -r requirements.txt

# Verify configuration
python -c "import config; config.print_test_config()"

# Expected output:
# Environment: MOCK
# AWS Region: us-east-1
# Lark API Mock: http://localhost:8080
```

✅ **Success:** Python framework configured correctly

### Step 3: Run Example Tests in MOCK Mode (1 minute)

```bash
cd integration-tests/python

# Run example integration test
export TEST_ENVIRONMENT=mock
python tests/integration/test_glue_operations.py -v

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** MOCK mode tests pass instantly. **Cost so far: $0** ✅

### Step 4: Run Regression Test in MOCK Mode (1 minute)

```bash
cd integration-tests/python

# Run migrated regression test
export TEST_ENVIRONMENT=mock
python tests/regression/test_glue_crawler.py --verbose

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** Regression tests work in MOCK mode. **Cost so far: $0** ✅

### Step 5: Start LocalStack Community (1 minute)

```bash
cd integration-tests/src/main/resources/localstack

# Start LocalStack + WireMock
docker-compose up -d

# Wait for services to start
sleep 10

# Verify LocalStack is healthy
curl http://localhost:4566/_localstack/health

# Expected:
# {"services": {"lambda": "running", "s3": "running", ...}}

# Verify WireMock is healthy
curl http://localhost:8080/__admin/health

# Expected: 200 OK
```

✅ **Success:** LocalStack Community is running. **Cost so far: $0** ✅

---
*End of Quick Verification. See below for detailed documentation.*
---


## Overview

This module provides a comprehensive testing framework that supports three test modes:

| Mode | AWS Services | Infrastructure | Speed | Cost | Use Case |
|------|-------------|----------------|-------|------|----------|
| **MOCK** | All mocked in-memory | None | Fastest | $0 | Unit tests, CI/CD |
| **HYBRID** | LocalStack (Lambda, S3) + Mocks (Glue, Secrets, SSM) | Docker | Fast | $0 | Integration tests |
| **AWS** | Real AWS services | AWS Account | Slow | $ | E2E validation |

## Detailed Guides

For a deeper dive into specific topics, see the following guides:

- **[ZERO_COST_TESTING.md](./ZERO_COST_TESTING.md)**: Explains the three-tier testing strategy (MOCK, HYBRID, AWS) to achieve $0 testing costs.
- **[LARK_TEST_DATA_SETUP.md](./LARK_TEST_DATA_SETUP.md)**: A technical guide on how to automatically generate a comprehensive test data set in Lark Base.
- **[MANUAL_FIELDS_GUIDE.md](./MANUAL_FIELDS_GUIDE.md)**: Instructions for manually adding the 5 field types that cannot be created via the Lark API.

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

## Running Tests

### Configure Environment

```bash
# For MOCK mode (default)
export TEST_ENVIRONMENT=mock

# For HYBRID mode (LocalStack Community)
export TEST_ENVIRONMENT=hybrid

# For AWS mode (real AWS)
export TEST_ENVIRONMENT=aws
export AWS_REGION=us-east-1
```

### Java Tests

```bash
# MOCK mode (default)
mvn clean verify -P test-mock

# HYBRID mode
mvn clean verify -P test-hybrid

# AWS mode
mvn clean verify -P test-aws
```

### Python Tests

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

**What\'s mocked:**
- ✅ Glue Data Catalog (in-memory)
- ✅ Secrets Manager (in-memory)
- ✅ SSM Parameter Store (in-memory)
- ✅ Lark API (WireMock responses)

**What\'s NOT available:**
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

**What\'s real (via LocalStack Community):**
- ✅ Lambda invocation
- ✅ S3 operations
- ✅ CloudWatch Logs

**What\'s mocked:**
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

**What\'s real:**
- ✅ All AWS services
- ✅ Lark API (if configured)

**Best for:**
- Final E2E validation
- Production smoke tests
- Performance testing