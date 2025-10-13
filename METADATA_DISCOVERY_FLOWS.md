# Metadata Discovery Flows - AWS Athena Lark Base Connector

## Table of Contents
1. [Overview](#overview)
2. [Flow Comparison](#flow-comparison)
3. [Flow 1: Glue Catalog + Crawler](#flow-1-glue-catalog--crawler)
4. [Flow 2: Lark Base Source (Direct)](#flow-2-lark-base-source-direct)
5. [Flow 3: Lark Drive Source (Direct)](#flow-3-lark-drive-source-direct)
6. [Flow 4: Experimental Provider](#flow-4-experimental-provider)
7. [Hybrid Flows](#hybrid-flows)
8. [Flow Selection Guide](#flow-selection-guide)
9. [Migration Between Flows](#migration-between-flows)

---

## Overview

The AWS Athena Lark Base Connector supports **multiple metadata discovery strategies**, each with different characteristics, trade-offs, and use cases. Understanding these flows is crucial for choosing the right approach for your environment.

### Available Flows

1. **Glue Catalog + Crawler**: Traditional approach using AWS Glue Data Catalog
2. **Lark Base Source (Direct)**: Direct discovery from a Lark Base metadata table
3. **Lark Drive Source (Direct)**: Direct discovery from Lark Drive folder structure
4. **Experimental Provider**: Dynamic schema discovery via Athena metadata tables

### Key Differences

| Aspect | Glue + Crawler | Lark Base Source | Lark Drive Source | Experimental |
|--------|---------------|------------------|-------------------|--------------|
| **Setup Complexity** | High | Medium | Medium | Low |
| **Maintenance** | Manual (crawler runs) | Automatic | Automatic | Automatic |
| **Schema Caching** | Yes (Glue) | Yes (Memory) | Yes (Memory) | No (Dynamic) |
| **Latency** | Low | Low | Low | High |
| **Schema Updates** | Manual (re-run crawler) | Automatic (on restart) | Automatic (on restart) | Real-time |
| **AWS Dependencies** | Glue, Lambda | Lambda, Secrets Manager | Lambda, Secrets Manager | Lambda, Athena |
| **Lark API Calls** | Only during crawler | At Lambda startup | At Lambda startup | Every query |
| **Best For** | Production, stable schemas | Development, dynamic schemas | Folder-based organization | Testing, exploration |

---

## Flow Comparison

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Athena Query                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
                    ┌────────────────────┐
                    │ BaseMetadataHandler│
                    └────────┬───────────┘
                             │
        ┌────────────────────┼────────────────────────┐
        │                    │                        │
        │                    │                        │
┌───────▼─────────┐  ┌───────▼──────────┐  ┌────────▼────────┐
│ Glue Catalog    │  │ Lark Source      │  │ Experimental    │
│ (Flow 1)        │  │ Provider         │  │ Provider        │
│                 │  │ (Flow 2 & 3)     │  │ (Flow 4)        │
└───────┬─────────┘  └───────┬──────────┘  └────────┬────────┘
        │                    │                        │
        ▼                    ▼                        ▼
┌───────────────┐  ┌─────────────────┐    ┌─────────────────┐
│ Glue Catalog  │  │ In-Memory Cache │    │ Athena Catalog  │
│ (AWS Service) │  │ + Lark API      │    │ + Lark API      │
└───────────────┘  └─────────────────┘    └─────────────────┘
```

---

## Flow 1: Glue Catalog + Crawler

### Overview

The traditional AWS Glue approach where table metadata is stored in AWS Glue Data Catalog and discovered by running a crawler Lambda function.

### Architecture

```
┌────────────┐                                              ┌──────────┐
│ User runs  │                                              │ Lark API │
│ Crawler    │                                              └────┬─────┘
│ Lambda     │                                                   │
└─────┬──────┘                                                   │
      │                                                          │
      │ 1. Invoke                                                │
      ▼                                                          │
┌──────────────────────┐                                        │
│ LarkBaseCrawler      │    2. List tables & fields            │
│ Handler              ├───────────────────────────────────────►│
└──────────┬───────────┘                                        │
           │                                                    │
           │ 3. Create/Update tables                           │
           ▼                                                    │
┌──────────────────────┐                                        │
│ AWS Glue Data Catalog│                                        │
│                      │                                        │
│ - Databases          │                                        │
│ - Tables             │                                        │
│ - Column metadata    │                                        │
│   - larkFieldId      │                                        │
│   - larkFieldName    │                                        │
│   - larkUIType       │                                        │
└──────────┬───────────┘                                        │
           │                                                    │
           │ 4. Query time                                     │
           ▼                                                    │
┌──────────────────────┐                                        │
│ Athena Query         │                                        │
│                      │                                        │
│ SELECT * FROM table  │                                        │
└──────────┬───────────┘                                        │
           │                                                    │
           ▼                                                    │
┌──────────────────────┐                                        │
│ BaseMetadataHandler  │                                        │
│                      │                                        │
│ doGetTable()         │                                        │
│   └─► Get from Glue  │                                        │
└──────────┬───────────┘                                        │
           │                                                    │
           ▼                                                    │
┌──────────────────────┐                                        │
│ BaseRecordHandler    │    5. Fetch data                      │
│                      ├───────────────────────────────────────►│
│ readWithConstraint() │                                        │
└──────────────────────┘                                        │
```

### Setup Steps

**Step 1: Deploy Crawler Lambda**

```bash
# Build crawler
mvn clean package -pl glue-lark-base-crawler -am

# Deploy to Lambda
aws lambda create-function \
  --function-name lark-base-crawler \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/LarkCrawlerRole \
  --handler com.amazonaws.glue.lark.base.crawler.MainLarkBaseCrawlerHandler \
  --code S3Bucket=my-bucket,S3Key=glue-lark-base-crawler.jar \
  --timeout 900 \
  --memory-size 1024 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-credentials
  }"
```

**Step 2: Create Source Metadata Table in Lark**

Create a Lark Base table with this schema:
```
Table: databases
Columns:
  - id (TEXT): Lark Base ID (e.g., "bascnxxxxxx")
  - name (TEXT): Glue database name (lowercase, underscores only)
```

Example data:
```
| id                   | name              |
|---------------------|-------------------|
| bascnABC123456789   | sales_data        |
| bascnDEF987654321   | customer_records  |
```

**Step 3: Run Crawler**

```bash
aws lambda invoke \
  --function-name lark-base-crawler \
  --payload '{
    "handler_type": "lark_base",
    "larkBaseDataSourceId": "bascnSOURCE_BASE_ID",
    "larkTableDataSourceId": "tblSOURCE_TABLE_ID"
  }' \
  response.json
```

**Step 4: Deploy Athena Connector**

```bash
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/AthenaConnectorRole \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=my-bucket,S3Key=athena-lark-base.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-credentials,
    ACTIVATE_LARK_BASE_SOURCE=false,
    ACTIVATE_EXPERIMENTAL_FEATURES=false
  }"
```

**Note**: Both source flags are `false` - using Glue only!

**Step 5: Query**

```sql
-- Schema from Glue
SHOW DATABASES IN lark_base;

-- Tables from Glue
SHOW TABLES IN lark_base.sales_data;

-- Data from Lark API
SELECT * FROM lark_base.sales_data.orders LIMIT 10;
```

### Detailed Flow

#### Initial Setup (One-time)

```
┌────────┐
│ Admin  │
└───┬────┘
    │
    │ 1. Create Lark Base metadata table
    │    with database configurations
    ▼
┌─────────────┐
│ Lark Base   │
│             │
│ databases   │
│ ┌─────────┐ │
│ │id  name │ │
│ │bascn...│ │
│ │sales_db│ │
│ └─────────┘ │
└─────────────┘
    │
    │ 2. Run crawler Lambda
    ▼
┌──────────────────────┐
│ Crawler discovers:   │
│ - Read databases     │
│ - For each database: │
│   - List tables      │
│   - Get fields       │
│   - Map types        │
│   - Create in Glue   │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Glue Data Catalog    │
│                      │
│ Database: sales_db   │
│   Table: orders      │
│     Columns:         │
│       order_id (str) │
│       amount (double)│
│     Parameters:      │
│       larkBaseId=... │
│       larkTableId=...│
│     Column Params:   │
│       larkFieldId=...│
│       larkUIType=... │
└──────────────────────┘
```

#### Query Execution

```
┌──────┐
│Athena│
└──┬───┘
   │ SELECT * FROM lark_base.sales_db.orders
   │
   ▼
┌──────────────────┐
│BaseMetadata      │
│Handler           │
└──┬───────────────┘
   │ doGetTable()
   │
   ├─► Try LarkSourceMetadataProvider → Not enabled, skip
   │
   ├─► Try ExperimentalMetadataProvider → Not enabled, skip
   │
   └─► Fallback to Glue
       │
       ▼
   ┌───────────────┐
   │ Glue.getTable │
   │               │
   │ Returns:      │
   │ - Schema      │
   │ - Parameters  │
   │   - larkBaseId│
   │   - larkTableId
   └───────┬───────┘
           │
           ▼
   ┌───────────────┐
   │ Add reserved  │
   │ fields        │
   │ Return schema │
   └───────────────┘
```

### Pros and Cons

**Pros**:
- ✅ **Stable**: Schema cached in Glue, no real-time lookups
- ✅ **Fast**: Metadata queries are very fast (Glue is optimized)
- ✅ **Governance**: Integrates with AWS Lake Formation
- ✅ **Auditing**: Glue tracks schema changes
- ✅ **Separation**: Crawler runs separately from query execution
- ✅ **Production-ready**: Well-tested AWS service

**Cons**:
- ❌ **Manual updates**: Must re-run crawler after schema changes
- ❌ **Complex setup**: Requires separate crawler Lambda
- ❌ **Delayed updates**: Schema changes not reflected until crawler runs
- ❌ **More infrastructure**: Two Lambda functions instead of one
- ❌ **Glue costs**: AWS Glue Data Catalog API calls

### When to Use

✅ **Use this flow when**:
- Production environment with stable schemas
- Need AWS governance integration (Lake Formation, IAM)
- Want to minimize Lambda cold starts
- Have infrequent schema changes
- Need audit trail of schema changes
- Want fastest query performance

❌ **Avoid this flow when**:
- Schemas change frequently
- Development/testing environment
- Want automatic schema updates
- Prefer simpler deployment

### Schema Update Process

```
Lark Base Schema Changes
    ↓
Administrator Aware
    ↓
Re-run Crawler Lambda
    ↓
Crawler Updates Glue Catalog
    ↓
New Schema Available to Queries
```

**Manual steps required**:
1. Notice schema change in Lark Base
2. Run crawler: `aws lambda invoke --function-name lark-base-crawler ...`
3. Wait for crawler to complete (~1-5 minutes)
4. New queries use updated schema

---

## Flow 2: Lark Base Source (Direct)

### Overview

Direct schema discovery from Lark Base at Lambda initialization. No AWS Glue crawler needed. Schemas are cached in Lambda memory.

### Architecture

```
┌──────────────┐
│ Athena Query │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────┐
│ BaseMetadataHandler                     │
│                                         │
│ Initialization (Cold Start):           │
│   1. Read LARK_BASE_DATA_SOURCE_ID     │
│   2. Read LARK_TABLE_DATA_SOURCE_ID    │
│   3. Call LarkBaseTableResolver        │
│      └─► Fetch metadata from Lark Base │
│      └─► Build schemas in memory       │
│      └─► Cache as List<TableDirect...> │
│                                         │
│ Query Time (doGetTable):                │
│   1. Check LarkSourceMetadataProvider   │
│   2. Search in-memory cache             │
│   3. Return cached schema (fast!)       │
└─────────────────────────────────────────┘
       │
       │ Data reading
       ▼
┌──────────────────┐          ┌─────────┐
│ BaseRecordHandler│─────────►│Lark API │
└──────────────────┘          └─────────┘
```

### Setup Steps

**Step 1: Create Source Metadata Table in Lark**

Create a Lark Base table with this schema:
```
Table: athena_metadata
Columns:
  - database_name (TEXT): Glue database name
  - database_lark_base_id (TEXT): Lark Base ID
  - table_name (TEXT): Athena table name
  - table_lark_base_id (TEXT): Same Lark Base ID
  - table_lark_table_id (TEXT): Lark Table ID
```

Example data:
```
| database_name | database_lark_base_id | table_name | table_lark_base_id | table_lark_table_id |
|---------------|----------------------|------------|-------------------|-------------------|
| sales_data    | bascnABC123456789    | orders     | bascnABC123456789 | tblXYZ123456789   |
| sales_data    | bascnABC123456789    | customers  | bascnABC123456789 | tblDEF987654321   |
```

**Step 2: Deploy Athena Connector**

```bash
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/AthenaConnectorRole \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=my-bucket,S3Key=athena-lark-base.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-credentials,
    ACTIVATE_LARK_BASE_SOURCE=true,
    LARK_BASE_DATA_SOURCE_ID=bascnMETADATA_BASE_ID,
    LARK_TABLE_DATA_SOURCE_ID=tblMETADATA_TABLE_ID,
    ACTIVATE_EXPERIMENTAL_FEATURES=false
  }"
```

**Note**: `ACTIVATE_LARK_BASE_SOURCE=true` - No crawler needed!

**Step 3: Query**

```sql
-- Schema discovered automatically
SHOW DATABASES IN lark_base;

-- Tables discovered automatically
SHOW TABLES IN lark_base.sales_data;

-- Data from Lark API
SELECT * FROM lark_base.sales_data.orders LIMIT 10;
```

### Detailed Flow

#### Lambda Initialization (Cold Start)

```
┌─────────────────────┐
│ Lambda Cold Start   │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────────────┐
│ BaseMetadataHandler()        │
│ Constructor                  │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ initializeLarkServices()     │
│                              │
│ 1. Create LarkBaseService    │
│ 2. Create LarkDriveService   │
│ 3. Create LarkBaseTableResolver
└──────────┬───────────────────┘
           │
           ▼
┌─────────────────────────────────┐
│ LarkBaseTableResolver           │
│   .resolveTables()              │
└──────────┬──────────────────────┘
           │
           ├─► If ACTIVATE_LARK_BASE_SOURCE:
           │   │
           │   ▼
           │ ┌──────────────────────────┐
           │ │ resolveFromLarkBase()    │
           │ └──────────┬───────────────┘
           │            │
           │            │ 1. Fetch metadata from source table
           │            ▼
           │        ┌─────────────────────┐
           │        │ Lark API GET records│
           │        │ base: METADATA_BASE │
           │        │ table: METADATA_TBL │
           │        └──────────┬──────────┘
           │                   │
           │                   │ Returns:
           │                   │ [{database_name: "sales_data",
           │                   │   table_name: "orders",
           │                   │   table_lark_base_id: "bascn...",
           │                   │   table_lark_table_id: "tbl..."}]
           │                   │
           │                   ▼
           │        ┌─────────────────────────────┐
           │        │ For each table record:      │
           │        │   1. Get fields from Lark   │
           │        │   2. LarkBaseFieldResolver  │
           │        │      .buildSchema()         │
           │        │   3. Create TableDirect...  │
           │        └──────────┬──────────────────┘
           │                   │
           │                   ▼
           │        ┌─────────────────────────────┐
           │        │ List<TableDirect...>        │
           │        │ [                            │
           │        │   {database: {              │
           │        │      athenaName: "sales_..."│
           │        │      larkBaseId: "bascn..." │
           │        │    },                        │
           │        │    table: {                  │
           │        │      athenaName: "orders"   │
           │        │      larkTableId: "tbl..."  │
           │        │    },                        │
           │        │    schema: Schema(...),      │
           │        │    fields: [...]             │
           │        │   }                          │
           │        │ ]                            │
           │        └──────────┬──────────────────┘
           │                   │
           └───────────────────┘
                     │
                     │ Store in memory
                     ▼
           ┌──────────────────────────────┐
           │ mappingTableDirectInitialized│
           │ (cached in Lambda memory)    │
           └──────────────────────────────┘
```

#### Query Execution (Warm Lambda)

```
┌──────┐
│Athena│
└──┬───┘
   │ SELECT * FROM lark_base.sales_data.orders
   │
   ▼
┌──────────────────┐
│BaseMetadata      │
│Handler           │
└──┬───────────────┘
   │ doGetTable()
   │
   ├─► Try LarkSourceMetadataProvider
   │   │
   │   ▼
   │ ┌─────────────────────────────────┐
   │ │ Search in                       │
   │ │ mappingTableDirectInitialized   │
   │ │                                 │
   │ │ Filter:                         │
   │ │   database == "sales_data"      │
   │ │   table == "orders"             │
   │ └────────┬────────────────────────┘
   │          │
   │          │ Found!
   │          ▼
   │ ┌─────────────────────────────────┐
   │ │ Return cached schema            │
   │ │ - No Glue call                  │
   │ │ - No Lark API call              │
   │ │ - Just memory lookup (fast!)    │
   │ └─────────────────────────────────┘
   │
   └─► Skip other providers (already found)
```

### Pros and Cons

**Pros**:
- ✅ **No crawler**: Simpler deployment (one Lambda function)
- ✅ **Automatic updates**: Re-initialize Lambda to get new schemas
- ✅ **Fast queries**: Schema cached in memory
- ✅ **Self-contained**: Everything in connector Lambda
- ✅ **No Glue costs**: No AWS Glue Data Catalog usage
- ✅ **Version control**: Metadata table in Lark can be version controlled

**Cons**:
- ❌ **Cold start penalty**: Initial schema discovery adds ~5-10 seconds
- ❌ **Memory usage**: All schemas cached in Lambda memory
- ❌ **Manual metadata table**: Must maintain metadata table in Lark
- ❌ **Update delay**: Schema changes require Lambda restart (or wait for cold start)
- ❌ **No Glue integration**: Cannot use Lake Formation, Glue governance

### When to Use

✅ **Use this flow when**:
- Development/testing environment
- Schemas change frequently
- Want simpler deployment (one Lambda)
- Don't need AWS Glue governance
- Cost-sensitive (avoid Glue API costs)
- Can tolerate occasional cold starts

❌ **Avoid this flow when**:
- Production with strict latency requirements
- Need AWS governance (Lake Formation)
- Have hundreds of tables (memory limit)
- Cannot tolerate cold start delays

### Schema Update Process

```
Lark Base Schema Changes
    ↓
Update Metadata Table (if table added/removed)
    ↓
Wait for Lambda Cold Start
    OR
Force Update (delete + recreate Lambda)
    ↓
New Schema Automatically Discovered
```

**Automatic**: Lambda will rediscover schemas on next cold start!

---

## Flow 3: Lark Drive Source (Direct)

### Overview

Similar to Lark Base Source, but discovers tables from Lark Drive folder structure instead of a metadata table.

### Architecture

```
┌───────────────┐
│ Lark Drive    │
│               │
│ Folder        │
│ ├─ sales_data │ (Database)
│ │  ├─ orders  │ (Lark Base link)
│ │  └─ items   │ (Lark Base link)
│ └─ inventory  │ (Database)
│    └─ stock   │ (Lark Base link)
└───────┬───────┘
        │
        │ Discovery at Lambda initialization
        ▼
┌─────────────────────────────────────────┐
│ LarkBaseTableResolver                   │
│   .resolveFromLarkDrive()               │
│                                         │
│ 1. List folders (databases)             │
│ 2. For each folder:                     │
│    - List files (Lark Base links)       │
│    - Extract base_id, table_id          │
│    - Get fields from Lark               │
│    - Build schema                       │
└─────────────────────────────────────────┘
```

### Setup Steps

**Step 1: Create Folder Structure in Lark Drive**

```
Lark Drive
└─ athena_tables/
   ├─ sales_data/
   │  ├─ orders (link to Lark Base)
   │  └─ customers (link to Lark Base)
   └─ inventory/
      └─ stock (link to Lark Base)
```

**Step 2: Get Folder Token**

```
Right-click on athena_tables folder → Share → Get folder token
Example: fldcnABC123456789
```

**Step 3: Deploy Athena Connector**

```bash
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/AthenaConnectorRole \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=my-bucket,S3Key=athena-lark-base.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-credentials,
    ACTIVATE_LARK_DRIVE_SOURCE=true,
    LARK_FOLDER_TOKEN_DATA_SOURCE=fldcnABC123456789,
    ACTIVATE_EXPERIMENTAL_FEATURES=false
  }"
```

**Step 4: Query**

```sql
-- Databases = folders
SHOW DATABASES IN lark_base;

-- Tables = Lark Base links in folder
SHOW TABLES IN lark_base.sales_data;

-- Data from Lark API
SELECT * FROM lark_base.sales_data.orders LIMIT 10;
```

### Detailed Flow

```
Lambda Initialization
    ↓
LarkBaseTableResolver.resolveFromLarkDrive()
    ↓
Get folder token from env: LARK_FOLDER_TOKEN_DATA_SOURCE
    ↓
List folders in Drive
    │
    ├─► Folder: sales_data (→ Database name)
    │   ├─ List files in folder
    │   │  ├─► File: orders (Lark Base link)
    │   │  │   ├─ Extract: base_id, table_id
    │   │  │   ├─ Get fields from Lark
    │   │  │   └─ Build schema
    │   │  │
    │   │  └─► File: customers (Lark Base link)
    │   │      └─ ... same process ...
    │   │
    │   └─► Create TableDirectInitialized
    │       - database: sales_data
    │       - table: orders
    │       - schema: ...
    │
    └─► Folder: inventory
        └─ ... same process ...
    ↓
Store all in mappingTableDirectInitialized
    ↓
Ready for queries
```

### Pros and Cons

**Pros**:
- ✅ **Intuitive organization**: Folders = databases, files = tables
- ✅ **Visual management**: Use Lark Drive UI to organize
- ✅ **No metadata table**: Structure is implicit from folders
- ✅ **Easy sharing**: Share folder to grant access
- ✅ All other benefits of Lark Base Source

**Cons**:
- ❌ **Naming constraints**: Folder names must be valid database names
- ❌ **Less flexible**: Cannot customize database/table names
- ❌ **Drive API dependency**: Requires Drive permissions
- ❌ All other cons of Lark Base Source

### When to Use

✅ **Use this flow when**:
- Want visual organization in Lark Drive
- Prefer folder-based structure
- All users have Drive access
- Simple naming is acceptable

❌ **Avoid this flow when**:
- Need custom database/table names
- Complex metadata requirements
- Drive API has limitations

---

## Flow 4: Experimental Provider

### Overview

Dynamic schema discovery via Athena metadata tables. No pre-caching, queries Lark API on-demand for each schema request.

### Architecture

```
┌──────────────┐
│ Athena Query │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────────┐
│ BaseMetadataHandler                     │
│   .doGetTable()                         │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Try LarkSourceMetadataProvider          │
│   → Not enabled, skip                   │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Try ExperimentalMetadataProvider        │
│   → Enabled!                            │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Query Athena catalog metadata table     │
│ SELECT base_id, table_id                │
│ FROM metadata_table                     │
│ WHERE database=? AND table=?            │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Get base_id, table_id                   │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Call Lark API to get fields             │
│ GET /bitable/v1/apps/{base}/tables/     │
│     {table}/fields                      │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Build schema dynamically                │
│ LarkBaseFieldResolver.buildSchema()     │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ Return schema (NOT cached)              │
└─────────────────────────────────────────┘
```

### Setup Steps

**Step 1: Create Athena Metadata Table**

```sql
-- In Athena console
CREATE EXTERNAL TABLE lark_base.metadata_catalog (
  database_name STRING,
  table_name STRING,
  lark_base_id STRING,
  lark_table_id STRING
)
ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'
STORED AS TEXTFILE
LOCATION 's3://my-bucket/lark-metadata/';
```

**Step 2: Populate Metadata**

```sql
-- Insert metadata
INSERT INTO lark_base.metadata_catalog VALUES
  ('sales_data', 'orders', 'bascnABC123456789', 'tblXYZ123456789'),
  ('sales_data', 'customers', 'bascnABC123456789', 'tblDEF987654321');
```

**Step 3: Deploy Athena Connector**

```bash
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT:role/AthenaConnectorRole \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=my-bucket,S3Key=athena-lark-base.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-credentials,
    ACTIVATE_LARK_BASE_SOURCE=false,
    ACTIVATE_EXPERIMENTAL_FEATURES=true
  }"
```

**Step 4: Query**

```sql
-- Schema discovered on-demand
SELECT * FROM lark_base.sales_data.orders LIMIT 10;
```

### Detailed Flow

```
┌──────┐
│Athena│ SELECT * FROM lark_base.sales_data.orders
└──┬───┘
   │
   ▼
┌────────────────────┐
│ BaseMetadataHandler│
│   .doGetTable()    │
└──┬─────────────────┘
   │
   ├─► Try LarkSourceMetadataProvider → Not enabled
   │
   ├─► Try ExperimentalMetadataProvider → Enabled!
   │   │
   │   ▼
   │ ┌──────────────────────────────────┐
   │ │ experimentalMetadataProvider     │
   │ │   .getTableSchema()              │
   │ └──┬───────────────────────────────┘
   │    │
   │    │ 1. Query Athena metadata table
   │    ▼
   │ ┌──────────────────────────────────┐
   │ │ AthenaService                    │
   │ │   .queryMetadataTable()          │
   │ │                                  │
   │ │ Query:                           │
   │ │ SELECT lark_base_id,             │
   │ │        lark_table_id             │
   │ │ FROM metadata_catalog            │
   │ │ WHERE database_name='sales_data' │
   │ │   AND table_name='orders'        │
   │ └──┬───────────────────────────────┘
   │    │
   │    │ Result: base_id=bascn..., table_id=tbl...
   │    ▼
   │ ┌──────────────────────────────────┐
   │ │ 2. Get fields from Lark API      │
   │ │                                  │
   │ │ LarkBaseService.getTableFields() │
   │ │   GET /bitable/v1/apps/{base}/   │
   │ │       tables/{table}/fields      │
   │ └──┬───────────────────────────────┘
   │    │
   │    │ Result: [{fieldId, fieldName, type}, ...]
   │    ▼
   │ ┌──────────────────────────────────┐
   │ │ 3. Build schema dynamically      │
   │ │                                  │
   │ │ LarkBaseFieldResolver            │
   │ │   .buildSchema()                 │
   │ │                                  │
   │ │ Map Lark types → Arrow types     │
   │ └──┬───────────────────────────────┘
   │    │
   │    │ Return: Schema object
   │    ▼
   │ ┌──────────────────────────────────┐
   │ │ Return TableSchemaResult         │
   │ │ (NOT cached anywhere)            │
   │ └──────────────────────────────────┘
   │
   └─► Return to Athena
```

### Pros and Cons

**Pros**:
- ✅ **Real-time schema**: Always gets latest schema from Lark
- ✅ **No caching delays**: Schema changes reflected immediately
- ✅ **Flexible metadata**: Use any Athena table as source
- ✅ **Testing-friendly**: Great for experimentation
- ✅ **No Lambda initialization**: No cold start penalty for schema discovery

**Cons**:
- ❌ **Slower queries**: Lark API call for every schema request
- ❌ **More API calls**: Every query hits Lark API for schema
- ❌ **Athena dependency**: Requires functioning Athena catalog
- ❌ **Not production-ready**: Higher latency, more costs
- ❌ **No caching**: Cannot optimize repeated queries

### When to Use

✅ **Use this flow when**:
- Testing and development
- Schemas change constantly
- Need real-time schema reflection
- Exploring Lark Base data
- Short-term analysis

❌ **Avoid this flow when**:
- Production environment
- High query volume
- Need fast response times
- Cost-sensitive (API calls add up)

---

## Hybrid Flows

### Flow 5: Glue Catalog + Lark Base Source

Combine both approaches for maximum flexibility.

**Configuration**:
```bash
ACTIVATE_LARK_BASE_SOURCE=true
LARK_BASE_DATA_SOURCE_ID=bascn...
LARK_TABLE_DATA_SOURCE_ID=tbl...
# Glue tables will also be available
```

**How it works**:
1. Lambda initializes with Lark Base source
2. Schemas cached in memory
3. Query time: Try Lark Base source first
4. If not found: Try Glue catalog
5. Result: Tables from both sources available

**Use case**: Migrating from Glue to Lark Base source

### Flow 6: All Providers Enabled

Ultimate flexibility, try all providers in order.

**Configuration**:
```bash
ACTIVATE_LARK_BASE_SOURCE=true
ACTIVATE_EXPERIMENTAL_FEATURES=true
# Plus Glue tables
```

**Provider order**:
1. LarkSourceMetadataProvider (fastest, cached)
2. ExperimentalMetadataProvider (dynamic)
3. Glue Data Catalog (fallback)

**Use case**: Complex environments with mixed requirements

---

## Flow Selection Guide

### Decision Tree

```
Start
  │
  ├─► Production environment?
  │   ├─ Yes ─► Stable schemas?
  │   │         ├─ Yes ─► Use Flow 1 (Glue + Crawler)
  │   │         └─ No  ─► Use Flow 2 (Lark Base Source)
  │   │
  │   └─ No (Dev/Test)
  │       ├─► Frequent schema changes?
  │       │   ├─ Yes ─► Use Flow 2 or Flow 4
  │       │   └─ No  ─► Any flow works
  │       │
  │       └─► Need real-time schema?
  │           ├─ Yes ─► Use Flow 4 (Experimental)
  │           └─ No  ─► Use Flow 2 (Lark Base Source)
```

### Comparison Matrix

| Criteria | Flow 1 (Glue) | Flow 2 (Lark Base) | Flow 3 (Lark Drive) | Flow 4 (Experimental) |
|----------|--------------|-------------------|--------------------|--------------------|
| **Setup Complexity** | ★★★★☆ | ★★★☆☆ | ★★★☆☆ | ★★☆☆☆ |
| **Query Latency** | ★★★★★ | ★★★★☆ | ★★★★☆ | ★★☆☆☆ |
| **Schema Freshness** | ★★☆☆☆ | ★★★☆☆ | ★★★☆☆ | ★★★★★ |
| **Maintenance** | ★★☆☆☆ | ★★★★☆ | ★★★★☆ | ★★★★★ |
| **Production Ready** | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★☆☆☆☆ |
| **AWS Cost** | ★★☆☆☆ | ★★★★☆ | ★★★★☆ | ★★★☆☆ |
| **Lark API Calls** | ★★★★★ | ★★★★☆ | ★★★★☆ | ★★☆☆☆ |

---

## Migration Between Flows

### From Glue (Flow 1) to Lark Base Source (Flow 2)

**Step 1**: Create metadata table in Lark Base

**Step 2**: Populate with existing Glue table mappings
```sql
-- Export from Glue
aws glue get-tables --database-name my_db --output json

-- Transform to Lark Base format
-- Insert into metadata table
```

**Step 3**: Update Lambda environment
```bash
aws lambda update-function-configuration \
  --function-name athena-lark-connector \
  --environment Variables="{
    ...existing vars...,
    ACTIVATE_LARK_BASE_SOURCE=true,
    LARK_BASE_DATA_SOURCE_ID=bascn...,
    LARK_TABLE_DATA_SOURCE_ID=tbl...
  }"
```

**Step 4**: Test both sources work (hybrid mode)

**Step 5**: Verify queries work

**Step 6**: Optional: Delete Glue tables

### From Lark Base Source (Flow 2) to Glue (Flow 1)

**Step 1**: Deploy crawler Lambda

**Step 2**: Run crawler
```bash
aws lambda invoke \
  --function-name lark-base-crawler \
  --payload '...' \
  response.json
```

**Step 3**: Verify Glue tables created

**Step 4**: Update Lambda environment
```bash
aws lambda update-function-configuration \
  --function-name athena-lark-connector \
  --environment Variables="{
    ...existing vars...,
    ACTIVATE_LARK_BASE_SOURCE=false
  }"
```

**Step 5**: Test queries still work

---

## Summary

The AWS Athena Lark Base Connector supports multiple metadata discovery flows:

1. **Glue Catalog + Crawler**: Best for production, stable schemas, AWS governance
2. **Lark Base Source**: Best for development, dynamic schemas, simpler deployment
3. **Lark Drive Source**: Best for folder-based organization, visual management
4. **Experimental Provider**: Best for testing, exploration, real-time schemas

Choose based on your environment, requirements, and trade-offs. Most production systems use **Flow 1**, while development teams prefer **Flow 2**.

You can also combine flows (hybrid mode) for migration or special requirements.
