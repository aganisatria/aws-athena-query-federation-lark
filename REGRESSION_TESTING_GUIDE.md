# Athena-Lark-Base Connector - Regression Testing Guide

**Last Updated**: 2025-10-04
**Purpose**: Comprehensive guide for regression testing all Lark Base data types with Athena Query Federation

---

## Table of Contents

1. [Overview](#overview)
2. [Testing Workflow](#testing-workflow)
3. [Test Environment Setup](#test-environment-setup)
4. [Supported Data Types](#supported-data-types)
5. [Test Coverage Matrix](#test-coverage-matrix)
6. [Running Tests](#running-tests)
7. [Known Edge Cases](#known-edge-cases)
8. [Troubleshooting](#troubleshooting)

---

## Overview

This connector bridges AWS Athena with Lark Base (Feishu/飞书多维表格), enabling SQL queries over Lark's spreadsheet-like data. The regression test suite validates:

- ✅ All 26 supported Lark data types
- ✅ Type mapping accuracy (Lark → Arrow → Athena)
- ✅ Filter pushdown for primitive types
- ✅ Sort and LIMIT optimization
- ✅ Complex nested structures (LIST, STRUCT)
- ✅ NULL handling and edge cases
- ✅ Reserved system fields

**Test Script**: `regression-test-plan.sh` (51 test cases)

---

## Testing Workflow

### Workflow for Code Changes

When making any code changes to the connector, follow this workflow:

```
1. Make Code Changes
   ↓
2. Run Unit Tests (make test)
   ↓
3. If Tests Pass → Deploy to AWS (if needed)
   ↓
4. Ask Permission from User
   ↓
5. Run Regression Tests (regression-test-plan.sh)
   ↓
6. Validate Results
   ↓
7. Report Findings
```

### Sub-Agent Instructions

**Alternative invocation**:

If you are a sub-agent working on this codebase and the terminal has restarted:

1. **Read this document** to understand the testing approach
2. **Check the latest changes** in git history
3. **Run unit tests** after any code modification:
   ```bash
   make test
   ```
4. **For AWS-testable changes**, ask the user for permission before running:
   ```bash
   ./regression-test-plan.sh
   ```
5. **Never run AWS tests** without explicit user approval (costs money!)

---

## Test Environment Setup

### Step 1: Create Test Lark Base

Create a Lark Base (多维表格) with the following structure:

**Database Name**: `lark_base_test`
**Table Name**: `data_type_test_table`

#### Required Fields (All 26 Types):

| Field Name | Lark Type | Athena Type | Sample Data |
|-----------|-----------|-------------|-------------|
| `field_text` | TEXT | string | "Sample text", "Test 123", "" |
| `field_barcode` | BARCODE | string | "1234567890", "ABC-123" |
| `field_single_select` | SINGLE_SELECT | string | "Option A", "Option B", null |
| `field_phone` | PHONE | string | "+1-555-1234", "555-5678" |
| `field_email` | EMAIL | string | "test@example.com", null |
| `field_auto_number` | AUTO_NUMBER | string | "AUTO-001", "AUTO-002" |
| `field_number` | NUMBER | decimal(38,18) | 123.456, -789.012, 0 |
| `field_progress` | PROGRESS | decimal(38,18) | 0.5, 0.75, 1.0 |
| `field_currency` | CURRENCY | decimal(38,18) | 1000.50, 2500.75 |
| `field_rating` | RATING | tinyint | 1, 3, 5 |
| `field_checkbox` | CHECKBOX | boolean | true, false, null |
| `field_date_time` | DATE_TIME | timestamp | 2024-01-01 12:00:00 |
| `field_created_time` | CREATED_TIME | timestamp | Auto-generated |
| `field_modified_time` | MODIFIED_TIME | timestamp | Auto-generated |
| `field_multi_select` | MULTI_SELECT | array\<string\> | ["Tag1", "Tag2"], [] |
| `field_user` | USER | array\<struct\> | [{"id": "u1", "name": "Alice"}] |
| `field_group_chat` | GROUP_CHAT | array\<struct\> | [{"id": "g1", "name": "Team"}] |
| `field_attachment` | ATTACHMENT | array\<struct\> | [{"name": "file.pdf", "size": 1024}] |
| `field_single_link` | SINGLE_LINK | array\<struct\> | [{"record_ids": ["r1"], "text": "Link"}] |
| `field_duplex_link` | DUPLEX_LINK | array\<struct\> | [{"record_ids": ["r2"], "text": "BiLink"}] |
| `field_url` | URL | struct | {"link": "https://...", "text": "Label"} |
| `field_location` | LOCATION | struct | {"address": "123 Main St", "full_address": "..."} |
| `field_created_user` | CREATED_USER | struct | {"id": "u1", "name": "Alice", "email": "..."} |
| `field_modified_user` | MODIFIED_USER | struct | {"id": "u2", "name": "Bob", "email": "..."} |
| `field_formula` | FORMULA | varies | CONCATENATE({field_text}, " - ", {field_number}) |
| `field_lookup` | LOOKUP | varies | Lookup to another table's field |

#### Sample Data Requirements:

Create **at least 10 rows** with the following edge cases:

1. **Row 1**: All fields populated with typical values
2. **Row 2**: All fields NULL/empty where possible
3. **Row 3**: Maximum length strings (test boundaries)
4. **Row 4**: Numeric edge cases (0, negative, very large numbers)
5. **Row 5**: Past dates (before 2000)
6. **Row 6**: Future dates (after 2030)
7. **Row 7**: Empty arrays for all list types
8. **Row 8**: Arrays with single elements
9. **Row 9**: Arrays with multiple elements (3+)
10. **Row 10**: Complex formula and lookup scenarios

### Step 2: Deploy AWS Infrastructure

#### 2.1 Build and Package the Connector

```bash
# Build the project
make build

# Package for Lambda deployment
mvn clean package shade:shade
```

#### 2.2 Create Lambda Function

```bash
aws lambda create-function \
  --function-name athena-lark-base-connector \
  --runtime java11 \
  --role arn:aws:iam::ACCOUNT_ID:role/lambda-athena-execution-role \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=your-bucket,S3Key=athena-lark-base-connector.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    SPILL_BUCKET=your-spill-bucket,
    LARK_APP_ID=your_app_id,
    LARK_APP_SECRET=your_app_secret
  }"
```

#### 2.3 Register Athena Data Catalog

```bash
aws athena create-data-catalog \
  --name lark_base_catalog \
  --type LAMBDA \
  --parameters "function=arn:aws:lambda:REGION:ACCOUNT:function:athena-lark-base-connector"
```

#### 2.4 Run Glue Crawler (Optional but Recommended)

If using Glue integration for metadata:

```bash
# Deploy the Glue crawler Lambda
# (see glue-lark-base-crawler module)

# Run crawler to populate catalog
aws lambda invoke \
  --function-name lark-base-glue-crawler \
  --payload '{"databases": ["lark_base_test"]}' \
  response.json
```

### Step 3: Configure Environment Variables

```bash
export TEST_DATABASE="lark_base_test"
export TEST_TABLE="data_type_test_table"
export OUTPUT_LOCATION="s3://your-athena-results-bucket/regression-tests/"
export AWS_REGION="us-east-1"
export ATHENA_WORKGROUP="primary"
```

### Step 4: Verify Setup

```bash
# Test connectivity
aws athena start-query-execution \
  --query-string "SHOW DATABASES IN lark_base_catalog" \
  --result-configuration "OutputLocation=$OUTPUT_LOCATION"

# Check table schema
aws athena start-query-execution \
  --query-string "DESCRIBE lark_base_catalog.$TEST_DATABASE.$TEST_TABLE" \
  --result-configuration "OutputLocation=$OUTPUT_LOCATION"
```

---

## Supported Data Types

### Type Mapping Reference

| Lark Type | Arrow Type | SQL Type | Filter Pushdown | Notes |
|-----------|-----------|----------|-----------------|-------|
| TEXT | VARCHAR | string | ✅ Yes | Simple string |
| BARCODE | VARCHAR | string | ✅ Yes | String representation |
| SINGLE_SELECT | VARCHAR | string | ✅ Yes | Selected option |
| PHONE | VARCHAR | string | ✅ Yes | Phone number |
| EMAIL | VARCHAR | string | ✅ Yes | Email address |
| AUTO_NUMBER | VARCHAR | string | ❌ No | Auto-generated ID |
| NUMBER | DECIMAL(38,18) | decimal | ✅ Yes | High precision |
| PROGRESS | DECIMAL(38,18) | decimal | ✅ Yes | 0.0 - 1.0 |
| CURRENCY | DECIMAL(38,18) | decimal | ✅ Yes | Currency amount |
| RATING | TINYINT | tinyint | ✅ Yes | 0-5 stars |
| CHECKBOX | BIT | boolean | ✅ Yes | true/false |
| DATE_TIME | DATEMILLI | timestamp | ❌ No | Milliseconds epoch |
| CREATED_TIME | DATEMILLI | timestamp | ❌ No | Auto-populated |
| MODIFIED_TIME | DATEMILLI | timestamp | ❌ No | Auto-populated |
| MULTI_SELECT | LIST<VARCHAR> | array\<string\> | ❌ No | Array of options |
| USER | LIST<STRUCT> | array\<struct\> | ❌ No | id, name, email, en_name |
| GROUP_CHAT | LIST<STRUCT> | array\<struct\> | ❌ No | id, name, avatar_url |
| ATTACHMENT | LIST<STRUCT> | array\<struct\> | ❌ No | name, size, url, type, file_token, tmp_url |
| SINGLE_LINK | LIST<STRUCT> | array\<struct\> | ❌ No | record_ids, table_id, text, text_arr, type |
| DUPLEX_LINK | LIST<STRUCT> | array\<struct\> | ❌ No | Same as SINGLE_LINK |
| URL | STRUCT | struct | ❌ No | link, text |
| LOCATION | STRUCT | struct | ❌ No | address, adname, cityname, full_address, location, name, pname |
| CREATED_USER | STRUCT | struct | ❌ No | id, name, en_name, email |
| MODIFIED_USER | STRUCT | struct | ❌ No | id, name, en_name, email |
| FORMULA | Varies | varies | ❌ No | Depends on formula result type |
| LOOKUP | Varies | varies | ❌ No | Recursively resolved |

### Special Behaviors

#### CHECKBOX Null Handling
- `WHERE checkbox IS NULL` → Translated to `checkbox = false` in FQL
- `WHERE checkbox IS NOT NULL` → Translated to `checkbox = true` in FQL
- Rationale: Lark treats unchecked as false, not NULL

#### Date Type Detection (Heuristic)
```java
// Excel epoch: 1899-12-30 (with 2-day adjustment for 1900 leap year bug)
// Threshold: 2 billion (approx year 2033 in milliseconds)
if (numericValue < 2_000_000_000) {
    // Treat as Excel days
    daysSinceExcelEpoch = numericValue;
    millisSinceUnixEpoch = (daysSinceExcelEpoch - 25567) * 86400000;
} else {
    // Treat as Unix timestamp milliseconds
    millisSinceUnixEpoch = numericValue;
}
```

**Edge Case**: Dates between 1970-01-24 and 2033-05-18 might be ambiguous!

#### FORMULA and LOOKUP Resolution
- FORMULA fields have a `childType` indicating result type
- LOOKUP fields recursively resolve via API call to target table
- Special handling for TEXT formulas: extracts `{text: "..."}` format

---

## Test Coverage Matrix

### Test Categories (51 Tests)

| Category | Tests | Description |
|----------|-------|-------------|
| **1. Basic Data Types** | 5 | TEXT, BARCODE, SINGLE_SELECT, PHONE, EMAIL |
| **2. Numeric Types** | 5 | NUMBER, PROGRESS, CURRENCY, RATING, aggregations |
| **3. Boolean & Dates** | 5 | CHECKBOX, DATE_TIME, CREATED_TIME, MODIFIED_TIME, formatting |
| **4. Array Types** | 5 | MULTI_SELECT, USER, GROUP_CHAT, ATTACHMENT, cardinality |
| **5. Struct Types** | 5 | URL, LOCATION, CREATED_USER, nested field access |
| **6. Complex Types** | 4 | FORMULA, LOOKUP, SINGLE_LINK, DUPLEX_LINK |
| **7. Filter Pushdown** | 7 | =, >, <, !=, IN, IS NULL, combined filters |
| **8. Sort & Limit** | 3 | ORDER BY, LIMIT, Top-N optimization |
| **9. NULL Handling** | 4 | IS NULL, IS NOT NULL, COALESCE |
| **10. Edge Cases** | 5 | Empty arrays, pagination, nested access, reserved fields |
| **11. Type Conversions** | 3 | CAST operations |

### Filter Pushdown Support

**Supported (9 types)**:
- TEXT, BARCODE, SINGLE_SELECT, PHONE, EMAIL
- NUMBER, PROGRESS, CURRENCY, RATING
- CHECKBOX

**Operators**:
- `=`, `!=`, `>`, `>=`, `<`, `<=`
- `IN (...)`, `NOT IN (...)`
- `IS NULL`, `IS NOT NULL`
- `BETWEEN ... AND ...`

**FQL Translation Examples**:

```sql
-- SQL
WHERE status = 'active'
-- FQL
CurrentValue.[status]="active"

-- SQL
WHERE age > 18 AND age <= 65
-- FQL
AND(CurrentValue.[age]>18,CurrentValue.[age]<=65)

-- SQL
WHERE status IN ('active', 'pending')
-- FQL
OR(CurrentValue.[status]="active",CurrentValue.[status]="pending")

-- SQL
WHERE checkbox IS TRUE
-- FQL
CurrentValue.[checkbox]=1

-- SQL
WHERE checkbox IS NULL
-- FQL (special handling)
CurrentValue.[checkbox]=false
```

---

## Running Tests

### Show Setup Instructions

```bash
./regression-test-plan.sh --setup
```

### Run Full Test Suite

```bash
./regression-test-plan.sh
```

### Run with Custom Configuration

```bash
TEST_DATABASE=my_custom_db \
TEST_TABLE=my_table \
OUTPUT_LOCATION=s3://my-bucket/results/ \
./regression-test-plan.sh
```

### Expected Output

```
================================================================================
  Athena-Lark-Base Connector - Regression Test Suite
================================================================================

Configuration:
  Database: lark_base_test
  Table: data_type_test_table
  Workgroup: primary
  Region: us-east-1
  Output Location: s3://your-bucket/

================================================================================

[INFO] Running pre-flight checks...
[PASS] Pre-flight checks completed
[INFO] ==== Testing Basic Data Types ====
[INFO] Executing: Read TEXT field
[PASS] Read TEXT field
[INFO] Executing: Read BARCODE field
[PASS] Read BARCODE field
...
================================================================================
  Test Summary
================================================================================
  Total Tests: 51
  Passed: 48
  Failed: 3
  Skipped: 0
  Duration: 245s
================================================================================
```

---

## Known Edge Cases

### 1. Date Type Ambiguity
**Issue**: Dates between 1970-01-24 and 2033-05-18 might be misinterpreted
**Detection**: Numeric values < 2 billion treated as Excel epoch days
**Workaround**: Use explicit date formatting in Lark
**Files**: `RegistererExtractor.java:324-355`

### 2. Checkbox NULL Semantics
**Issue**: `IS NULL` maps to `= false`, not actual NULL
**Reason**: Lark doesn't distinguish unchecked from NULL
**Impact**: Can't detect truly missing checkbox values
**Files**: `ConstraintTranslator.java:157-163`

### 3. FORMULA TEXT Extraction
**Issue**: TEXT formulas return `[{text: "..."}]` format
**Handling**: Special case to extract first element's text field
**Edge Case**: Multi-value formula results might be truncated
**Files**: `RegistererExtractor.java:244-260`

### 4. LOOKUP Chains
**Issue**: Deep lookup chains require multiple API calls
**Impact**: Slow schema discovery for complex lookups
**Limitation**: No circular reference detection
**Files**: `LarkBaseService.java:getLookupType()`

### 5. Empty String vs NULL
**Issue**: VARCHAR fields use `""` for NULL in filters
**Behavior**: `IS NOT NULL` adds `NOT(field="")` check
**Edge Case**: Explicit empty strings treated as NULL
**Files**: `ConstraintTranslator.java:244-260`

### 6. Date Filter Not Pushed Down
**Issue**: DATE_TIME, CREATED_TIME, MODIFIED_TIME filters not in pushdown whitelist
**Impact**: All date filtering happens in Athena after fetching all data
**Performance**: Slow for large tables with date filters
**Files**: `ConstraintTranslator.java:498-502`

### 7. Parallel Splits Disable Sorting
**Issue**: ORDER BY disabled when using parallel splits
**Reason**: Split-level sorting can't guarantee global order
**Workaround**: Disable parallel splits for sorted queries
**Files**: `BaseMetadataHandler.java:getPartitions()`

---

## Troubleshooting

### Tests Fail to Connect

**Symptom**: `AWS credentials not configured properly`

**Solution**:
```bash
aws configure
# Or set environment variables
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_SESSION_TOKEN=...  # If using temporary credentials
```

### Query Timeout

**Symptom**: `Query timeout after 300s`

**Possible Causes**:
1. Large dataset without LIMIT
2. Complex filters not pushed down
3. Lambda cold start

**Solutions**:
- Increase timeout in script (edit `max_wait` variable)
- Add LIMIT to queries
- Enable filter pushdown for more types
- Warm up Lambda with test query

### Schema Not Found

**Symptom**: `Database/table does not exist`

**Solutions**:
1. Check catalog registration:
   ```bash
   aws athena list-data-catalogs
   ```
2. Verify Lark Base credentials in Lambda environment
3. Run Glue crawler to populate metadata
4. Check table name sanitization (Glue converts special chars)

### Type Mismatch Errors

**Symptom**: `Cannot cast VARCHAR to ARRAY`

**Possible Causes**:
- Field type changed in Lark after schema discovery
- Formula result type changed
- Lookup target changed

**Solutions**:
1. Re-run Glue crawler to refresh schema
2. Check `lark_field_type_mapping` in Glue table parameters
3. Manually update Glue table schema if needed

### Filter Pushdown Not Working

**Symptom**: Queries slow despite WHERE clause

**Verification**:
```sql
-- Check execution plan
EXPLAIN SELECT * FROM table WHERE field = 'value';

-- Check CloudWatch logs for Lambda
-- Look for "filter_expression" in RecordHandler logs
```

**Common Issues**:
- Field type not in pushdown whitelist (check `ConstraintTranslator.isUiTypeAllowedForPushdown()`)
- Complex filter expressions (nested OR/AND)
- Date/time filters (not supported)

### NULL Values Returning Defaults

**Symptom**: Expected NULL but getting empty string or 0

**Explanation**: `BaseRecordHandler.getDefaultValueForType()` provides defaults:
- VARCHAR → `""`
- DECIMAL → `0`
- BIT → `false`
- DATEMILLI → `0` (1970-01-01)

**Files**: `BaseRecordHandler.java:313-352`

---

## Files Reference

### Key Source Files

| File | Purpose | Line References |
|------|---------|-----------------|
| `BaseRecordHandler.java` | Data fetching & transformation | 313-352 (defaults), 252-254 (boolean) |
| `ConstraintTranslator.java` | Filter & sort pushdown | 157-163 (checkbox null), 244-260 (text null), 498-502 (whitelist) |
| `RegistererExtractor.java` | Type extractors | 244-260 (formula text), 324-355 (date detection) |
| `LarkBaseTypeUtils.java` | Type mapping | Full file for mappings |
| `UITypeEnum.java` | Lark type definitions | All type constants |
| `BaseMetadataHandler.java` | Schema & partition logic | getPartitions(), doGetSplits() |
| `LarkBaseService.java` | Lark API client | getLookupType() for recursion |

### Test Files

| File | Purpose |
|------|---------|
| `regression-test-plan.sh` | Main regression test script (51 tests) |
| `REGRESSION_TESTING_GUIDE.md` | This document |
| `glue-lark-base-crawler/src/test/**/*Test.java` | Unit tests for crawler |

---

## Contributing

When adding new features or fixing bugs:

1. **Update type mappings** in `LarkBaseTypeUtils.java`
2. **Add filter support** in `ConstraintTranslator.java` if applicable
3. **Register extractors** in `RegistererExtractor.java`
4. **Add unit tests** in `athena-lark-base/src/test/`
5. **Update this guide** with new edge cases
6. **Add regression tests** to `regression-test-plan.sh`
7. **Run full test suite** before submitting PR

---

## Quick Reference Commands

```bash
# Build project
make build

# Run unit tests
make test

# Show regression test setup
./regression-test-plan.sh --setup

# Run regression tests
./regression-test-plan.sh

# Deploy to AWS Lambda
aws lambda update-function-code \
  --function-name athena-lark-base-connector \
  --zip-file fileb://target/athena-lark-base-connector.jar

# Query from Athena CLI
aws athena start-query-execution \
  --query-string "SELECT * FROM lark_base_catalog.my_db.my_table LIMIT 10" \
  --result-configuration "OutputLocation=s3://bucket/"

# Get query results
aws athena get-query-results --query-execution-id <execution-id>
```

---

**End of Regression Testing Guide**
