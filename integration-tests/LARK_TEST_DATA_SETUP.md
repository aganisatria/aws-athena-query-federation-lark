# Lark Base Test Data Setup Guide

**Last Updated**: 2025-10-04
**Purpose**: Guide for setting up Lark Base test data for comprehensive regression testing

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Setup Scripts](#setup-scripts)
5. [Testing Workflow](#testing-workflow)
6. [Field Type Coverage](#field-type-coverage)
7. [Troubleshooting](#troubleshooting)
8. [Adding New Fields](#adding-new-fields)

---

## Overview

This guide explains how to automatically set up test data in Lark Base for testing the Athena Query Federation connector. The setup includes:

- ✅ Creating a Lark Base (bitable) with all 26 supported field types
- ✅ Populating 10+ test records with edge cases
- ✅ Testing the Glue crawler to populate AWS Glue catalog
- ✅ Running Athena regression tests against the data

### Architecture

```
┌─────────────────┐
│  Lark Base API  │
│  (Source Data)  │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────┐
│  setup-lark-test-data.py    │
│  - Creates Base & Table     │
│  - Creates 26 Field Types   │
│  - Populates Test Records   │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  Lark Base                  │
│  ├── data_type_test_table   │
│  └── lookup_target_table    │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  test-glue-crawler.py       │
│  - Invokes Glue Crawler     │
│  - Validates Metadata       │
│  - Tests Field Mappings     │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  AWS Glue Catalog           │
│  ├── Database               │
│  └── Table Schema           │
└────────┬────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  regression-test-plan.sh    │
│  - Runs 51 Athena Tests     │
│  - Validates Results        │
└─────────────────────────────┘
```

---

## Prerequisites

### 1. Lark (Feishu) Application Setup

1. **Create a Lark App**:
   - Go to [Lark Open Platform](https://open.larksuite.com/app)
   - Create a new app or use an existing one
   - Note down the **App ID** and **App Secret**

2. **Enable Required Permissions**:
   - `bitable:app` - Read and write bitable (Lark Base) data
   - `drive:drive` - Create files in Lark Drive (Required for folder access)
   - `drive:file` - File management

3. **Create a Folder in Lark Drive** (REQUIRED):
   - Open Lark Drive in your browser
   - Create a new folder for test data (e.g., "Athena Test Data")
   - **IMPORTANT**: Right-click the folder → Share → Add the Lark App as a collaborator with **Edit** permission
   - Copy the folder token from URL: `https://drive.larksuite.com/folder/{FOLDER_TOKEN}`
   - This ensures you own the created base and can edit it

4. **Add App to Folder**:
   - In the folder settings, add your Lark App
   - Grant "Can edit" permissions
   - This is required for the app to create bases in your folder

### 2. AWS Setup

1. **AWS Credentials**:
   ```bash
   aws configure
   # Or export credentials
   export AWS_ACCESS_KEY_ID=your_key
   export AWS_SECRET_ACCESS_KEY=your_secret
   export AWS_REGION=us-east-1
   ```

2. **Deploy Lambda Functions**:
   - Glue Crawler Lambda (from `glue-lark-base-crawler` module)
   - Athena Connector Lambda (from `athena-lark-base` module)

3. **S3 Bucket for Athena Results**:
   ```bash
   aws s3 mb s3://your-athena-results-bucket
   ```

### 3. Python Dependencies

```bash
pip install -r requirements.txt
# Or install manually:
pip install requests boto3 python-dotenv
```

---

## Quick Start

### Step 1: Configure Environment

Create a `.env` file in the project root:

```bash
# Lark Configuration
LARK_APP_ID=cli_xxxxxxxxxxxxxxxxx
LARK_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
LARK_FOLDER_TOKEN=xxxxxxxxxxxxxxxxxxxxxx  # Optional

# AWS Configuration
AWS_REGION=us-east-1
GLUE_CRAWLER_LAMBDA_NAME=lark-base-glue-crawler

# Test Configuration (will be populated by setup script)
LARK_BASE_APP_TOKEN=
LARK_BASE_TABLE_ID=
LARK_BASE_TABLE_NAME=data_type_test_table
```

### Step 2: Create Lark Base Test Data

```bash
# Make scripts executable
chmod +x setup-lark-test-data.py
chmod +x test-glue-crawler.py
chmod +x regression-test-plan.sh

# Run setup
./setup-lark-test-data.py --verbose
```

**Output**:
```
================================================================================
  Lark Base Test Data Setup
================================================================================

[INFO] Fetching tenant access token...
[SUCCESS] Token obtained successfully
[INFO] Creating Lark Base: athena_lark_base_regression_test...
[SUCCESS] Base created with token: bascnXXXXXXXXXXXXXXXX
[INFO] Creating lookup target table...
[SUCCESS] Lookup target table created with ID: tblXXXXXXXXXXXXX
[INFO] Creating test table: data_type_test_table...
[SUCCESS] Table created with ID: tblYYYYYYYYYYYYY
[INFO] Adding link fields...
[SUCCESS] SINGLE_LINK field added
[SUCCESS] DUPLEX_LINK field added
[INFO] Adding formula field...
[SUCCESS] FORMULA field added
[INFO] Adding lookup field...
[SUCCESS] LOOKUP field added
[INFO] Creating test records...
[SUCCESS] Created 10 test records

================================================================================
  Setup Summary
================================================================================

✅ Lark Base Token: bascnXXXXXXXXXXXXXXXX
✅ Test Table ID: tblYYYYYYYYYYYYY
✅ Lookup Target Table ID: tblXXXXXXXXXXXXX

📊 Total Fields: 26
📝 Total Records: 10

🔗 Base URL: https://base.larksuite.com/bascnXXXXXXXXXXXXXXXX

Environment Variables for Testing:
export LARK_BASE_APP_TOKEN="bascnXXXXXXXXXXXXXXXX"
export LARK_BASE_TABLE_ID="tblYYYYYYYYYYYYY"
export LARK_BASE_TABLE_NAME="data_type_test_table"
```

### Step 3: Update Environment Variables

Copy the export commands from the output:

```bash
export LARK_BASE_APP_TOKEN="bascnXXXXXXXXXXXXXXXX"
export LARK_BASE_TABLE_ID="tblYYYYYYYYYYYYY"
export LARK_BASE_TABLE_NAME="data_type_test_table"

# Or add to .env file
```

### Step 4: Run Glue Crawler Test

```bash
./test-glue-crawler.py --verbose
```

**Output**:
```
================================================================================
  Glue Lark Base Crawler Regression Test
================================================================================

[INFO] Invoking Glue crawler Lambda: lark-base-glue-crawler
[SUCCESS] Crawler Lambda invoked successfully
[INFO] Waiting 10 seconds for crawler to complete...
[INFO] Validating Glue database: athena_lark_base_regression_test
[SUCCESS] Database 'athena_lark_base_regression_test' exists
[INFO] Validating Glue table: athena_lark_base_regression_test.data_type_test_table
[SUCCESS] Table 'data_type_test_table' exists with 29 columns

================================================================================
  Field Type Validation
================================================================================

[PASS] field_text: string ✓
[PASS] field_number: decimal(38,18) ✓
[PASS] field_checkbox: boolean ✓
...

================================================================================
  Test Summary
================================================================================

Total Tests: 50
Passed: 48
Failed: 0
Warnings: 2

✓ All tests passed!
```

### Step 5: Run Athena Regression Tests

```bash
export TEST_DATABASE="athena_lark_base_regression_test"
export TEST_TABLE="data_type_test_table"
export OUTPUT_LOCATION="s3://your-athena-results-bucket/"

./regression-test-plan.sh
```

---

## Setup Scripts

### 1. setup-lark-test-data.py

**Purpose**: Creates Lark Base with test data

**Features**:
- Creates a new Lark Base (bitable) file
- Creates lookup target table for LOOKUP field testing
- Creates main test table with all 26 field types
- Populates 10 test records with edge cases
- Returns environment variables for next steps

**Usage**:
```bash
# Basic usage
./setup-lark-test-data.py

# Verbose output
./setup-lark-test-data.py --verbose

# Cleanup (manual - opens browser)
./setup-lark-test-data.py --cleanup
```

**What it Creates**:

**Base Name**: `athena_lark_base_regression_test`

**Tables**:
1. `data_type_test_table` (main test table)
   - 26 fields covering all Lark types
   - 10 test records with edge cases

2. `lookup_target_table` (for LOOKUP testing)
   - 2 fields: target_text_field, target_number_field
   - 2 sample records

### 2. test-glue-crawler.py

**Purpose**: Tests Glue Crawler and validates Glue catalog metadata

**Features**:
- Invokes Glue Crawler Lambda function
- Validates Glue database creation
- Validates table schema
- Checks field type mappings
- Validates reserved system fields
- Checks table parameters

**Usage**:
```bash
# Run full test (invoke crawler + validate)
./test-glue-crawler.py

# Validate existing metadata only
./test-glue-crawler.py --validate-only

# Verbose output
./test-glue-crawler.py --verbose
```

**Test Coverage**:
- ✅ Database existence
- ✅ Table existence
- ✅ Field type mappings (26 fields)
- ✅ Reserved fields ($reserved_record_id, $reserved_table_id, $reserved_base_id)
- ✅ Table parameters (lark_base_app_token, lark_table_id, lark_field_type_mapping)

### 3. regression-test-plan.sh

**Purpose**: Runs comprehensive Athena query tests

(See [REGRESSION_TESTING_GUIDE.md](./REGRESSION_TESTING_GUIDE.md) for details)

---

## Testing Workflow

### Complete End-to-End Workflow

```bash
# 1. Setup Lark Base test data
./setup-lark-test-data.py --verbose

# 2. Copy environment variables from output
export LARK_BASE_APP_TOKEN="..."
export LARK_BASE_TABLE_ID="..."

# 3. Test Glue crawler
./test-glue-crawler.py --verbose

# 4. Run Athena regression tests
export TEST_DATABASE="athena_lark_base_regression_test"
export TEST_TABLE="data_type_test_table"
export OUTPUT_LOCATION="s3://your-bucket/"
./regression-test-plan.sh
```

### Workflow for Code Changes

Following the documented testing workflow:

```
1. Make Code Changes to Connector
   ↓
2. Run Unit Tests: make test
   ↓
3. If Tests Pass → Deploy to AWS
   ↓
4. Ask Permission from User
   ↓
5. Re-run Glue Crawler Test
   ./test-glue-crawler.py
   ↓
6. Run Athena Regression Tests
   ./regression-test-plan.sh
   ↓
7. Validate All Tests Pass
   ↓
8. Report Findings
```

---

## Field Type Coverage

### All 26 Supported Field Types

| Field Name | Lark Type | Type Number | Athena Type | Test Coverage |
|-----------|-----------|-------------|-------------|---------------|
| field_text | TEXT | 1 | string | ✅ |
| field_barcode | BARCODE | 27 | string | ✅ |
| field_single_select | SINGLE_SELECT | 3 | string | ✅ |
| field_phone | PHONE | 13 | string | ✅ |
| field_email | EMAIL | 26 | string | ✅ |
| field_auto_number | AUTO_NUMBER | 1005 | string | ✅ |
| field_number | NUMBER | 2 | decimal(38,18) | ✅ |
| field_progress | PROGRESS | 10 | decimal(38,18) | ✅ |
| field_currency | CURRENCY | 23 | decimal(38,18) | ✅ |
| field_rating | RATING | 9 | tinyint | ✅ |
| field_checkbox | CHECKBOX | 7 | boolean | ✅ |
| field_date_time | DATE_TIME | 5 | timestamp | ✅ |
| field_created_time | CREATED_TIME | 1001 | timestamp | ✅ |
| field_modified_time | MODIFIED_TIME | 1002 | timestamp | ✅ |
| field_multi_select | MULTI_SELECT | 4 | array\<string\> | ✅ |
| field_user | USER | 11 | array\<struct\> | ✅ |
| field_group_chat | GROUP_CHAT | 12 | array\<struct\> | ✅ |
| field_attachment | ATTACHMENT | 17 | array\<struct\> | ✅ |
| field_single_link | SINGLE_LINK | 18 | array\<struct\> | ✅ |
| field_duplex_link | DUPLEX_LINK | 21 | array\<struct\> | ✅ |
| field_url | URL | 15 | struct | ✅ |
| field_location | LOCATION | 22 | struct | ✅ |
| field_created_user | CREATED_USER | 1003 | struct | ✅ |
| field_modified_user | MODIFIED_USER | 1004 | struct | ✅ |
| field_formula | FORMULA | 20 | varies | ✅ |
| field_lookup | LOOKUP | 19 | varies | ✅ |

### Test Data Edge Cases

The setup script creates 10 records with the following edge cases:

1. **Record 1**: Typical values for all fields
2. **Record 2**: NULL/empty values
3. **Record 3**: Maximum length strings, boundary numeric values
4. **Record 4**: Negative numbers, zero values
5. **Record 5**: Past dates (1995)
6. **Record 6**: Future dates (2035)
7. **Record 7**: Empty arrays for all list types
8. **Record 8**: Single element arrays
9. **Record 9**: Multiple element arrays
10. **Record 10**: Excel date format edge case (year 2010)

---

## Troubleshooting

### Issue: "Missing required environment variables"

**Solution**:
```bash
# Check current environment
env | grep LARK

# Set required variables
export LARK_APP_ID="cli_xxxxxxxxx"
export LARK_APP_SECRET="xxxxxxxxxxxxxxx"

# Or create .env file
cat > .env << EOF
LARK_APP_ID=cli_xxxxxxxxx
LARK_APP_SECRET=xxxxxxxxxxxxxxx
EOF
```

### Issue: "Failed to create base: Permission denied"

**Cause**: App doesn't have required permissions

**Solution**:
1. Go to [Lark Open Platform](https://open.larksuite.com/app)
2. Select your app
3. Go to "Permissions & Scopes"
4. Enable:
   - `bitable:app` - Read and write bitable
   - `drive:drive` - Create files
   - `drive:file` - File management
5. Re-run the script

### Issue: "Lambda invocation failed"

**Cause**: Lambda function not deployed or wrong name

**Solution**:
```bash
# Check Lambda function exists
aws lambda get-function --function-name lark-base-glue-crawler

# If not found, deploy the crawler Lambda
cd glue-lark-base-crawler
mvn clean package
aws lambda create-function \
  --function-name lark-base-glue-crawler \
  --runtime java11 \
  --role arn:aws:iam::ACCOUNT:role/lambda-role \
  --handler com.amazonaws.glue.lark.base.crawler.MainLarkBaseCrawlerHandler \
  --zip-file fileb://target/glue-lark-base-crawler.jar
```

### Issue: "Field type mismatch"

**Cause**: Connector code updated but test expectations not updated

**Solution**:
1. Check the actual type in Glue catalog:
   ```bash
   aws glue get-table --database-name athena_lark_base_regression_test \
     --name data_type_test_table
   ```
2. Update `EXPECTED_TYPE_MAPPINGS` in `test-glue-crawler.py`
3. Re-run test

### Issue: "Table not found in Glue"

**Cause**: Crawler failed or hasn't run yet

**Solution**:
```bash
# Check Glue tables
aws glue get-tables --database-name athena_lark_base_regression_test

# If empty, check crawler Lambda logs
aws logs tail /aws/lambda/lark-base-glue-crawler --follow

# Re-run crawler test
./test-glue-crawler.py --verbose
```

---

## Adding New Fields

When you add support for a new Lark field type to the connector:

### Step 1: Update setup-lark-test-data.py

Add the new field type in the `create_table()` method:

```python
fields = [
    # ... existing fields ...

    # New field type
    {
        "field_name": "field_new_type",
        "type": XX,  # Lark type number
        "property": {  # If needed
            # Field-specific properties
        }
    },
]
```

### Step 2: Update test-glue-crawler.py

Add expected type mapping:

```python
EXPECTED_TYPE_MAPPINGS = {
    # ... existing mappings ...
    "field_new_type": "expected_athena_type",
}
```

### Step 3: Update regression-test-plan.sh

Add test cases for the new field:

```bash
# Test XX: NEW_TYPE field
run_test_query \
    "SELECT field_new_type FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
    "Read NEW_TYPE field"
```

### Step 4: Run Complete Workflow

```bash
# 1. Create new test data
./setup-lark-test-data.py --verbose

# 2. Test Glue crawler (should detect new field)
./test-glue-crawler.py --verbose

# 3. Run Athena tests (should include new field test)
./regression-test-plan.sh
```

### Step 5: Document

Update documentation:
- `REGRESSION_TESTING_GUIDE.md` - Add to type mapping table
- `LARK_TEST_DATA_SETUP.md` - This file, update field count
- Code comments in connector classes

---

## Quick Reference

### Environment Variables

```bash
# Lark Configuration
LARK_APP_ID=              # Required: Lark app ID
LARK_APP_SECRET=          # Required: Lark app secret
LARK_FOLDER_TOKEN=        # Optional: Folder to create base in

# AWS Configuration
AWS_REGION=               # Default: us-east-1
GLUE_CRAWLER_LAMBDA_NAME= # Default: lark-base-glue-crawler

# Test Data (populated by setup script)
LARK_BASE_APP_TOKEN=      # Created base token
LARK_BASE_TABLE_ID=       # Created table ID
LARK_BASE_TABLE_NAME=     # Table name

# Athena Test Configuration
TEST_DATABASE=            # Glue database name
TEST_TABLE=               # Glue table name
OUTPUT_LOCATION=          # S3 path for Athena results
ATHENA_WORKGROUP=         # Default: primary
```

### Script Commands

```bash
# Setup test data in Lark Base
./setup-lark-test-data.py [--verbose] [--cleanup]

# Test Glue crawler
./test-glue-crawler.py [--validate-only] [--verbose]

# Run Athena regression tests
./regression-test-plan.sh [--setup]

# Show regression test setup instructions
./regression-test-plan.sh --setup
```

### API References

- **Lark Base API**: https://open.larksuite.com/document/server-docs/docs/bitable-v1/
- **Create Bitable**: https://open.larksuite.com/document/server-docs/docs/drive-v1/file/create-online-document
- **Create Table**: https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table/create
- **Create Records**: https://open.larksuite.com/document/server-docs/docs/bitable-v1/app-table-record/create

---

## Files Structure

```
.
├── setup-lark-test-data.py          # Creates Lark Base test data
├── test-glue-crawler.py              # Tests Glue crawler Lambda
├── regression-test-plan.sh           # Runs Athena regression tests
├── requirements.txt                  # Python dependencies
├── .env                              # Environment variables (gitignored)
├── LARK_TEST_DATA_SETUP.md          # This file
├── REGRESSION_TESTING_GUIDE.md       # Athena regression testing guide
├── athena-lark-base/                 # Connector source code
├── glue-lark-base-crawler/          # Glue crawler Lambda source
└── Makefile                          # Build commands
```

---

**End of Lark Test Data Setup Guide**
