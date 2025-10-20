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
python tests/examples/test_glue_operations.py -v

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** MOCK mode tests pass instantly. **Cost so far: $0** ✅

### Step 4: Run Regression Test in MOCK Mode (1 minute)

```bash
cd integration-tests/python

# Run comprehensive tests
export TEST_ENVIRONMENT=mock
python comprehensive_test_runner.py --providers all --verbose

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** Regression tests work in MOCK mode. **Cost so far: $0** ✅

### Step 5: Start LocalStack Community (1 minute)

```bash
cd integration-tests/localstack

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
| **MOCK** | All mocked in-memory | None | Fastest | $0 | Unit tests, CI/CD, framework validation |
| **HYBRID** | LocalStack (Lambda, S3) + Mocks (Glue, Secrets, SSM) | Docker | Fast | $0 | Integration tests, pre-deployment |
| **AWS** | Real AWS services | AWS Account | Slow | $ | E2E validation, comprehensive testing |

### Test Coverage by Mode

| Test Type | MOCK | HYBRID | AWS | What's Tested |
|-----------|------|--------|-----|---------------|
| **Framework Tests** | ✅ | ✅ | ✅ | Client factory, environment config, Glue metadata mocks |
| **Glue Metadata** | ✅ | ✅ | ✅ | Database/table operations, schema management |
| **Athena Queries** | ❌ | ❌ | ✅ | SELECT, WHERE, ORDER BY, LIMIT |
| **Field Types** | ❌ | ❌ | ✅ | All 26+ Lark field types (TEXT, USER, URL, LINK, etc.) |
| **Filter Pushdown** | ❌ | ❌ | ✅ | WHERE filters, comparisons, NULL checks |
| **Complex Queries** | ❌ | ❌ | ✅ | JOIN, aggregations, pagination |

**Why Athena tests need AWS**: Mocking Athena's SQL execution engine is impractical. Comprehensive query tests require real AWS Athena infrastructure.

### Testing Framework

This module uses a **Python-based testing framework** for all integration and end-to-end testing:

| Aspect | Details |
|--------|---------|
| **Language** | Python (pytest-style) |
| **Focus** | Athena queries, Lark integration, Glue crawlers, metadata providers |
| **Test Modes** | MOCK (in-memory), HYBRID (LocalStack), AWS (real services) |
| **Infrastructure** | LocalStack Community + WireMock for Lark API |
| **Run With** | `python comprehensive_test_runner.py` or `./run_comprehensive_tests.sh` |
| **Test Coverage** | - 4 metadata providers<br>- Glue crawler validation<br>- Comprehensive query regression<br>- Filter pushdown<br>- All field types |

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

# MOCK mode - Framework tests only
export TEST_ENVIRONMENT=mock
python -m pytest tests/examples/ -v  # Fast: 4 tests in ~0.1s

# HYBRID mode - Framework + LocalStack (requires Docker)
export TEST_ENVIRONMENT=hybrid
python -m pytest tests/examples/ -v  # Fast: 4 tests in ~0.2s

# AWS mode - Comprehensive tests (requires AWS credentials + deployed connector)
export TEST_ENVIRONMENT=aws

# Framework tests (quick validation)
python -m pytest tests/examples/ -v

# Comprehensive tests (all field types, filters, queries)
python tests/regression/test_comprehensive_queries.py
```

**Note**: Comprehensive regression tests in `tests/regression/` require:
- AWS credentials configured
- Lark Base test data set up (see [LARK_TEST_DATA_SETUP.md](./LARK_TEST_DATA_SETUP.md))
- Lambda connector deployed
- Glue catalog populated

## Directory Structure

```
integration-tests/
├── pom.xml                                    # Maven module descriptor (no Java code)
├── README.md                                  # This file
├── LARK_TEST_DATA_SETUP.md                    # Test data setup guide
├── MANUAL_FIELDS_GUIDE.md                     # Manual field creation guide
├── ZERO_COST_TESTING.md                       # Zero-cost testing strategy
├── COMPREHENSIVE_TESTING_GUIDE.md             # Comprehensive testing guide
├── STRUCTURE_GUIDE.md                         # Structure documentation
├── CLEANUP_SUMMARY.md                         # Cleanup summary
├── localstack/
│   ├── docker-compose.yml                     # LocalStack + WireMock
│   └── init-scripts.sh                        # LocalStack setup
├── mocks/
│   ├── __files/                               # WireMock response bodies
│   └── mappings/                              # WireMock Lark API mocks
└── python/
    ├── requirements.txt
    ├── config.py                              # Test configuration
    ├── comprehensive_test_runner.py           # Main test runner
    ├── run_comprehensive_tests.sh             # Shell wrapper for tests
    ├── run_all_tests.py                       # Legacy test runner
    ├── scripts/
    │   ├── setup/                             # Setup scripts for test data
    │   │   ├── setup_lark_test_data.py                    # Lark Base Source
    │   │   ├── setup_lark_drive_source_test_data.py      # Lark Drive Source
    │   │   ├── setup_experimental_provider_test_data.py  # Experimental
    │   │   └── setup_base_metadata_handler_test_data.py  # Base Handler
    │   ├── validation/                        # Validation scripts
    │   │   └── test_glue_crawler.py           # Glue crawler validation
    │   └── archive/                           # Archived debugging scripts
    ├── clients/
    │   ├── aws_client.py                      # AWS client factory
    │   ├── mock_glue.py
    │   ├── mock_secrets.py
    │   └── mock_ssm.py
    └── tests/
        ├── base_test.py                       # Base test utilities
        ├── crawlers/                          # Glue crawler tests
        │   ├── test_glue_crawler.py           # Lark Base crawler
        │   └── test_lark_drive_crawler.py     # Lark Drive crawler
        ├── providers/                         # Metadata provider tests
        │   ├── test_lark_base_source.py       # Lark Base provider
        │   ├── test_lark_drive_source.py      # Lark Drive provider
        │   └── test_experimental_provider.py  # Experimental provider
        ├── examples/                          # Example tests
        │   └── test_glue_operations.py        # Example integration test
        └── regression/                        # Regression tests
            ├── test_comprehensive_queries.py  # Comprehensive queries
            └── ...                            # Other regression tests
```

## Using LocalStack Community Edition

### Start LocalStack

```bash
cd integration-tests/localstack
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
cd integration-tests/localstack
docker-compose down
```

## Writing Tests

### Python Example

```python
# File: tests/examples/test_glue_operations.py
from tests.base_test import BaseIntegrationTest

class TestGlueOperations(BaseIntegrationTest):
    def test_glue_database_operations(self):
        # Get Glue client (auto-mocked in MOCK/HYBRID mode)
        glue = self.get_glue_client()

        # Pre-populate test data (automatically handled by base class)
        # Test operations
        response = glue.get_database(Name="test_database")
        assert response["Database"]["Name"] == "test_database"

# Run with: python tests/examples/test_glue_operations.py -v
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