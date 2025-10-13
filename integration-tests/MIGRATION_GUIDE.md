# Migration Guide: Moving Tests to integration-tests Module

This guide helps you migrate existing tests from the project root to the new `integration-tests` module.

## Overview

**Goal:** Move all testing code from root directory to `integration-tests/` with support for MOCK/HYBRID/AWS modes.

**Benefits:**
- ✅ Zero-cost local testing
- ✅ Organized test structure
- ✅ Multi-environment support
- ✅ Faster test execution

---

## What to Migrate

### Python Test Scripts (Project Root)

**Current location:**
```
./test-glue-crawler.py
./test-pushdown-predicates.py
./test-api-comparison.py
./test-search-api-filters.py
./test-json-filters.py
./test-all-pushdown-filters.py
./test-like-pushdown.py
./setup-lark-test-data.py
```

**New location:**
```
integration-tests/python/tests/regression/
├── test_glue_crawler.py        ✅ MIGRATED
├── test_pushdown.py             ⏳ TODO
├── test_api_comparison.py       ⏳ TODO
├── test_filters.py               ⏳ TODO
└── ...
```

### Shell Scripts (Project Root)

**Current location:**
```
./regression-test-plan.sh
./test-timestamp-regression.sh
./verify-timestamps.sh
./test-reserved-fields-only.sh
```

**New location:**
```
integration-tests/scripts/
├── regression-test-plan.sh      ⏳ TODO
├── test-timestamp.sh            ⏳ TODO
└── ...
```

---

## Migration Steps

### Step 1: Migrate Python Test (Example: test-glue-crawler.py)

#### 1.1. Copy the File

```bash
cp test-glue-crawler.py integration-tests/python/tests/regression/test_glue_crawler.py
```

#### 1.2. Update Imports

**Old (test-glue-crawler.py):**
```python
import boto3
from dotenv import load_dotenv

load_dotenv()

AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
glue_client = boto3.client('glue', region_name=AWS_REGION)
```

**New (test_glue_crawler.py):**
```python
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from clients import AWSClientFactory
from config import get_environment, TestEnvironment

class GlueCrawlerTester(BaseRegressionTest):
    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.glue_client = None

    def setup(self):
        super().setup()
        # Get Glue client (auto-mocked in MOCK/HYBRID)
        self.glue_client = self.factory.create_glue_client()
```

#### 1.3. Update Environment Variables

**Old:**
```python
AWS_REGION = os.getenv("AWS_REGION", "us-east-1")
GLUE_CRAWLER_LAMBDA_NAME = os.getenv("GLUE_CRAWLER_LAMBDA_NAME")
```

**New:**
```python
from config import (
    get_environment,
    TestEnvironment,
    CRAWLER_FUNCTION_NAME,
    TEST_DATABASE,
    TEST_TABLE
)
```

#### 1.4. Add Environment-Aware Logic

**Old (always calls real AWS):**
```python
def invoke_crawler_lambda(self):
    lambda_client = boto3.client('lambda', region_name=AWS_REGION)
    response = lambda_client.invoke(
        FunctionName=GLUE_CRAWLER_LAMBDA_NAME,
        Payload=json.dumps(payload)
    )
```

**New (environment-aware):**
```python
def invoke_crawler_lambda(self):
    if self.environment == TestEnvironment.MOCK:
        # In MOCK mode, simulate crawler
        return self._simulate_crawler_mock()
    else:
        # In HYBRID/AWS mode, invoke real Lambda
        return self._invoke_crawler_lambda_real()

def _simulate_crawler_mock(self):
    self.log_info("[MOCK] Simulating crawler by populating Glue catalog")
    # Mock implementation
    return {"statusCode": 200}

def _invoke_crawler_lambda_real(self):
    lambda_client = self.factory.create_lambda_client()
    response = lambda_client.invoke(...)
    return response
```

#### 1.5. Update Execution

**Old:**
```bash
python3 test-glue-crawler.py
```

**New:**
```bash
# MOCK mode (default)
cd integration-tests/python
python tests/regression/test_glue_crawler.py

# Or use the master runner
python run_regression_tests.py --tests glue_crawler
```

---

## Migration Checklist

### For Each Python Test File

- [ ] Copy file to `integration-tests/python/tests/regression/`
- [ ] Rename to follow Python naming (underscores, not dashes)
- [ ] Add parent path to sys.path for imports
- [ ] Import `BaseRegressionTest` and extend it
- [ ] Replace `boto3.client()` with `AWSClientFactory`
- [ ] Replace `os.getenv()` with imports from `config.py`
- [ ] Add environment-aware logic for MOCK/HYBRID/AWS
- [ ] Update test data setup to use mock clients in MOCK mode
- [ ] Test in all three modes:
  ```bash
  export TEST_ENVIRONMENT=mock && python test_file.py
  export TEST_ENVIRONMENT=hybrid && python test_file.py
  export TEST_ENVIRONMENT=aws && python test_file.py
  ```
- [ ] Add to `AVAILABLE_TESTS` in `run_regression_tests.py`
- [ ] Update root README to point to new location

---

## Common Migration Patterns

### Pattern 1: AWS Client Creation

**Before:**
```python
glue = boto3.client('glue', region_name=os.getenv('AWS_REGION'))
lambda_client = boto3.client('lambda', region_name=os.getenv('AWS_REGION'))
```

**After:**
```python
from clients import AWSClientFactory

factory = AWSClientFactory()
glue = factory.create_glue_client()  # Auto-mocked in MOCK/HYBRID
lambda_client = factory.create_lambda_client()  # Only in HYBRID/AWS
```

### Pattern 2: Environment Variables

**Before:**
```python
DATABASE = os.getenv('TEST_DATABASE', 'default_db')
TABLE = os.getenv('TEST_TABLE', 'default_table')
REGION = os.getenv('AWS_REGION', 'us-east-1')
```

**After:**
```python
from config import TEST_DATABASE, TEST_TABLE, AWS_REGION

# Use directly
database = TEST_DATABASE
table = TEST_TABLE
```

### Pattern 3: Test Data Setup

**Before (always uses real AWS):**
```python
glue.create_database(DatabaseInput={'Name': 'test_db'})
glue.create_table(DatabaseName='test_db', TableInput={...})
```

**After (environment-aware):**
```python
def setup(self):
    super().setup()

    # In MOCK/HYBRID, pre-populate mock data
    if self.environment != TestEnvironment.AWS:
        mock_glue = self.factory.get_mock_glue_client()
        mock_glue.create_database(DatabaseInput={'Name': 'test_db'})
        mock_glue.create_table(...)

    # In AWS mode, assume data exists or create it
    else:
        glue = self.factory.create_glue_client()
        try:
            glue.get_database(Name='test_db')
        except:
            glue.create_database(...)
```

### Pattern 4: Lambda Invocation

**Before:**
```python
lambda_client = boto3.client('lambda')
response = lambda_client.invoke(FunctionName='my-function', Payload=json.dumps(data))
```

**After:**
```python
def invoke_lambda(self):
    if self.environment == TestEnvironment.MOCK:
        # Test handler directly or simulate
        return self._mock_lambda_invocation()
    else:
        # Real invocation in HYBRID/AWS
        lambda_client = self.factory.create_lambda_client()
        return lambda_client.invoke(FunctionName='my-function', ...)
```

### Pattern 5: Lark API Calls

**Before (always real Lark API):**
```python
response = requests.post(
    'https://open.larksuite.com/open-apis/bitable/v1/...',
    headers={'Authorization': f'Bearer {token}'}
)
```

**After (uses WireMock in MOCK/HYBRID):**
```python
from config import get_lark_api_base_url

def call_lark_api(self):
    base_url = get_lark_api_base_url()  # Returns WireMock URL in MOCK/HYBRID
    response = requests.post(
        f'{base_url}/open-apis/bitable/v1/...',
        headers={'Authorization': f'Bearer {token}'}
    )
```

---

## Example: Complete Migration

### Before (test-pushdown-predicates.py)

```python
#!/usr/bin/env python3
import os
import boto3
from dotenv import load_dotenv

load_dotenv()

CATALOG = os.getenv('ATHENA_CATALOG')
DATABASE = os.getenv('TEST_DATABASE')
TABLE = os.getenv('TEST_TABLE')
REGION = os.getenv('AWS_REGION', 'us-east-1')

athena = boto3.client('athena', region_name=REGION)

def execute_query(query):
    response = athena.start_query_execution(
        QueryString=query,
        QueryExecutionContext={'Catalog': CATALOG, 'Database': DATABASE}
    )
    # ... wait for completion ...
    return results

def test_filters():
    query = f'SELECT * FROM "{DATABASE}"."{TABLE}" WHERE field_checkbox = true'
    results = execute_query(query)
    assert len(results) > 0

if __name__ == "__main__":
    test_filters()
```

### After (test_pushdown.py)

```python
#!/usr/bin/env python3
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from tests.base_test import BaseRegressionTest
from clients import AWSClientFactory
from config import get_environment, TestEnvironment

class PushdownTester(BaseRegressionTest):
    def __init__(self, verbose: bool = False):
        super().__init__(verbose)
        self.athena_client = None

    def setup(self):
        super().setup()

        if self.environment == TestEnvironment.AWS:
            # Only create Athena client in AWS mode
            self.athena_client = self.factory.create_athena_client()
        else:
            # In MOCK/HYBRID, test query generation logic
            self.log_info(f"[{self.environment.value.upper()}] Testing query generation only")

    def execute_query(self, query):
        if self.environment == TestEnvironment.MOCK:
            # In MOCK mode, validate query syntax and return mock results
            self.log_info(f"[MOCK] Validating query: {query}")
            return self._mock_query_results()
        else:
            # In AWS mode, execute real query
            response = self.athena_client.start_query_execution(
                QueryString=query,
                QueryExecutionContext={
                    'Catalog': self.test_catalog,
                    'Database': self.test_database
                }
            )
            # Wait for completion...
            return results

    def _mock_query_results(self):
        # Return mock data for testing
        return [
            {'field_text': 'Sample', 'field_checkbox': True}
        ]

    def test_filters(self):
        query = f'SELECT * FROM "{self.test_database}"."{self.test_table}" WHERE field_checkbox = true'
        results = self.execute_query(query)

        if results:
            self.log_success("Filter test passed")
        else:
            self.log_error("Filter test failed")

def main():
    tester = PushdownTester(verbose=True)
    tester.setup()
    tester.test_filters()
    tester.print_summary()
    tester.teardown()

if __name__ == "__main__":
    main()
```

---

## Adding Tests to Master Runner

After migrating a test, add it to `run_regression_tests.py`:

```python
AVAILABLE_TESTS = {
    "glue_crawler": {
        "name": "Glue Crawler Test",
        "script": "tests/regression/test_glue_crawler.py",
        "description": "Tests Glue crawler Lambda function",
        "modes": ["mock", "hybrid", "aws"]
    },
    "pushdown": {  # NEW TEST
        "name": "Pushdown Predicates Test",
        "script": "tests/regression/test_pushdown.py",
        "description": "Tests filter pushdown and sorting",
        "modes": ["mock", "aws"]  # No HYBRID (Athena not in LocalStack Community)
    },
}
```

---

## Testing Your Migration

### 1. Test in MOCK Mode

```bash
cd integration-tests/python
export TEST_ENVIRONMENT=mock
python tests/regression/test_your_test.py --verbose
```

**Expected:** Instant execution (< 5 seconds), no AWS calls

### 2. Test in HYBRID Mode

```bash
# Start LocalStack
cd integration-tests/src/main/resources/localstack
docker-compose up -d

# Run test
cd ../../python
export TEST_ENVIRONMENT=hybrid
python tests/regression/test_your_test.py --verbose
```

**Expected:** Fast execution (~30 seconds), LocalStack Lambda calls

### 3. Test in AWS Mode

```bash
cd integration-tests/python
export TEST_ENVIRONMENT=aws
export AWS_REGION=ap-southeast-1
python tests/regression/test_your_test.py --verbose
```

**Expected:** Slower execution (~minutes), real AWS calls

### 4. Test via Master Runner

```bash
cd integration-tests/python
python run_regression_tests.py --tests your_test --verbose
```

---

## Cleanup After Migration

Once a test is fully migrated and verified:

1. **Archive the old test:**
   ```bash
   mkdir -p legacy-tests
   mv test-old-name.py legacy-tests/
   ```

2. **Update documentation:**
   - Update main README to point to new location
   - Add note that old script is deprecated

3. **Update CI/CD:**
   - Replace old test commands with new ones
   - Update GitHub Actions workflows

4. **Notify team:**
   - Send migration guide to team
   - Update development docs

---

## Migration Priority

### High Priority (Migrate First)

1. ✅ `test-glue-crawler.py` → `test_glue_crawler.py` (DONE)
2. ⏳ `test-pushdown-predicates.py` → `test_pushdown.py`
3. ⏳ `regression-test-plan.sh` → Shell wrapper

### Medium Priority

4. ⏳ `test-search-api-filters.py` → `test_filters.py`
5. ⏳ `test-api-comparison.py` → `test_api_comparison.py`

### Low Priority

6. ⏳ `test-json-filters.py` → Merge into `test_filters.py`
7. ⏳ `test-all-pushdown-filters.py` → Merge into `test_pushdown.py`
8. ⏳ `setup-lark-test-data.py` → `fixtures/setup.py`

---

## Need Help?

### Common Issues

**"Import errors when running tests"**
```bash
# Make sure you're in the right directory
cd integration-tests/python

# Or add to PYTHONPATH
export PYTHONPATH=$PWD:$PYTHONPATH
```

**"AWS clients not working in MOCK mode"**
- Check that `TEST_ENVIRONMENT=mock` is set
- Verify you're using `AWSClientFactory` not `boto3.client()`

**"LocalStack not starting"**
```bash
cd integration-tests/src/main/resources/localstack
docker-compose logs
docker-compose restart
```

### Get Support

- Read: `integration-tests/README.md`
- See examples: `tests/integration/test_glue_operations.py`
- Check: `ZERO_COST_TESTING.md`

---

## Summary

**Migration Steps:**
1. Copy test to `integration-tests/python/tests/regression/`
2. Update imports to use `AWSClientFactory`
3. Add environment-aware logic for MOCK/HYBRID/AWS
4. Test in all three modes
5. Add to master runner
6. Archive old test

**Result:**
- ✅ Zero-cost testing
- ✅ Multi-environment support
- ✅ Organized structure
- ✅ Faster execution

Happy migrating! 🚀
