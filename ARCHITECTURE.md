# AWS Athena Lark Base Connector - Architecture Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture Diagram](#architecture-diagram)
3. [Module Structure](#module-structure)
4. [Metadata Flow](#metadata-flow)
5. [Record Retrieval Flow](#record-retrieval-flow)
6. [Crawler Flow](#crawler-flow)
7. [Core Components](#core-components)
8. [Type System](#type-system)
9. [Query Optimizations](#query-optimizations)
10. [Configuration](#configuration)
11. [Deployment](#deployment)

---

## Overview

The AWS Athena Lark Base Connector enables querying Lark Base (Feishu Bitable) data directly from Amazon Athena using SQL. It consists of two main modules:

1. **athena-lark-base**: The Athena Federation connector that handles query execution
2. **glue-lark-base-crawler**: AWS Glue crawler that discovers and registers Lark Base tables

### Key Features
- Federated query execution using AWS Athena Federation SDK v2025.37.1
- Automatic schema discovery via AWS Glue Crawler
- Filter pushdown to Lark Base Search API
- TOP-N optimization (ORDER BY + LIMIT)
- LIMIT pushdown
- Parallel split execution for large tables
- Type mapping between Lark Base and Apache Arrow
- Support for complex Lark Base field types (Lookup, Attachment, Person, etc.)

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Amazon Athena                                │
│                      (SQL Query Engine)                              │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ SQL Query
                            ├─── GetTableLayout
                            ├─── GetSplits
                            └─── ReadRecords
                            │
┌───────────────────────────▼─────────────────────────────────────────┐
│              AWS Lambda (Athena Federation)                          │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │              BaseCompositeHandler                          │    │
│  │    (Entry Point - AWS Lambda Handler)                      │    │
│  └────────┬──────────────────────────────────┬────────────────┘    │
│           │                                   │                      │
│  ┌────────▼────────────┐          ┌──────────▼──────────────┐      │
│  │ BaseMetadataHandler │          │  BaseRecordHandler      │      │
│  │  (Metadata Ops)     │          │  (Data Retrieval)       │      │
│  └────────┬────────────┘          └──────────┬──────────────┘      │
│           │                                   │                      │
│           │ ┌─────────────────────────────┐  │                      │
│           ├─┤ LarkSourceMetadataProvider  │  │                      │
│           │ │ (Lark Base Source)          │  │                      │
│           │ └─────────────────────────────┘  │                      │
│           │ ┌─────────────────────────────┐  │                      │
│           ├─┤ ExperimentalMetadataProvider│  │                      │
│           │ │ (Experimental Features)     │  │                      │
│           │ └─────────────────────────────┘  │                      │
│           │                                   │                      │
│           ├───────────────────────────────────┤                      │
│           │         Service Layer             │                      │
│           │  ┌─────────────────────────┐     │                      │
│           │  │   LarkBaseService       │◄────┘                      │
│           │  │  (API Communication)    │                            │
│           │  └──────────┬──────────────┘                            │
│           │             │                                            │
│           │  ┌──────────▼──────────────┐                            │
│           │  │   LarkDriveService      │                            │
│           │  │  (Drive/Folder APIs)    │                            │
│           │  └──────────┬──────────────┘                            │
│           │             │                                            │
│           │  ┌──────────▼──────────────┐                            │
│           │  │  CommonLarkService      │                            │
│           │  │  (Auth & Common)        │                            │
│           │  └─────────────────────────┘                            │
│           │                                                          │
│           │  ┌─────────────────────────┐                            │
│           └─►│  GlueCatalogService     │                            │
│              │  (Glue Metadata)        │                            │
│              └──────────┬──────────────┘                            │
└─────────────────────────┼──────────────────────────────────────────┘
                          │
┌─────────────────────────▼─────────────────────────────────────────┐
│                   AWS Glue Data Catalog                            │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Databases & Tables (Metadata)                             │  │
│  │  - Schema definitions                                       │  │
│  │  - Table locations (lark-base://)                          │  │
│  │  - Field mappings (Athena ↔ Lark Base)                    │  │
│  └────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
                          │
                          │ Populated by
                          │
┌─────────────────────────▼─────────────────────────────────────────┐
│            AWS Lambda (Glue Crawler)                               │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │          MainLarkBaseCrawlerHandler                        │  │
│  │        (Routes to specific crawler)                        │  │
│  └────────┬──────────────────────────────────┬────────────────┘  │
│           │                                   │                    │
│  ┌────────▼────────────┐          ┌──────────▼──────────────┐    │
│  │ LarkBaseCrawler     │          │  LarkDriveCrawler       │    │
│  │ Handler             │          │  Handler                │    │
│  └────────┬────────────┘          └──────────┬──────────────┘    │
│           │                                   │                    │
│           └───────────────┬───────────────────┘                    │
│                           │                                        │
│                ┌──────────▼──────────────┐                        │
│                │  GlueCatalogService     │                        │
│                │  (Create/Update Tables) │                        │
│                └──────────┬──────────────┘                        │
│                           │                                        │
│                ┌──────────▼──────────────┐                        │
│                │  LarkBaseService        │                        │
│                │  (Discover Schema)      │                        │
│                └─────────────────────────┘                        │
└────────────────────────────────────────────────────────────────────┘
                          │
                          │ API Calls
                          │
┌─────────────────────────▼─────────────────────────────────────────┐
│                      Lark Base API                                 │
│                   (Feishu Bitable)                                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  - List Tables                                              │  │
│  │  - List Fields (Schema)                                     │  │
│  │  - Search Records (with filter/sort/pagination)            │  │
│  │  - List Folders (Drive API)                                 │  │
│  └────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

---

## Module Structure

### athena-lark-base Module

```
athena-lark-base/
├── handlers/
│   ├── BaseCompositeHandler           # Lambda entry point
│   ├── BaseMetadataHandler            # Metadata operations
│   └── BaseRecordHandler              # Data reading operations
├── metadataProvider/
│   ├── LarkSourceMetadataProvider     # Direct Lark Base source
│   └── ExperimentalMetadataProvider   # Experimental features
├── service/
│   ├── LarkBaseService                # Lark Base API client
│   ├── LarkDriveService               # Lark Drive API client
│   ├── CommonLarkService              # Authentication & common
│   ├── GlueCatalogService             # Glue catalog operations
│   ├── EnvVarService                  # Environment configuration
│   ├── AthenaService                  # Athena operations
│   └── HttpClientWrapper              # HTTP client abstraction
├── translator/
│   ├── SearchApiFilterTranslator      # SQL → Lark filter
│   ├── RegistererExtractor            # Type extractors
│   └── SearchApiResponseNormalizer    # Response normalization
├── resolver/
│   ├── LarkBaseTableResolver          # Table discovery
│   └── LarkBaseFieldResolver          # Field discovery
├── model/
│   ├── request/                       # Request models
│   ├── response/                      # Response models
│   └── enums/UITypeEnum              # Lark field types
├── util/
│   ├── LarkBaseTypeUtils             # Type conversion
│   └── CommonUtil                    # Common utilities
└── throttling/
    └── BaseExceptionFilter           # Retry logic
```

### glue-lark-base-crawler Module

```
glue-lark-base-crawler/
├── MainLarkBaseCrawlerHandler        # Router handler
├── BaseLarkBaseCrawlerHandler        # Base crawler logic
├── LarkBaseCrawlerHandler            # Lark Base source
├── LarkDriveCrawlerHandler           # Lark Drive source
├── service/
│   ├── LarkBaseService               # Lark API client
│   ├── LarkDriveService              # Drive API client
│   ├── CommonLarkService             # Authentication
│   ├── GlueCatalogService            # Glue operations
│   └── STSService                    # AWS STS operations
└── model/
    ├── request/                      # Request payloads
    └── response/                     # Response models
```

---

## Metadata Flow

### 1. List Schemas (Databases)

**Entry Point**: `BaseMetadataHandler.doListSchemaNames()`

**Flow**:
```
1. Athena calls doListSchemaNames()
   │
2. Try to get schemas from Glue Catalog (with DB_FILTER)
   │
3. If Lark Base source is enabled:
   │  └─ Get databases from LarkSourceMetadataProvider
   │     └─ Read from mappingTableDirectInitialized
   │        └─ Discovered by LarkBaseTableResolver during initialization
   │
4. Merge schemas from both sources (Set eliminates duplicates)
   │
5. Return ListSchemasResponse
```

**Classes Involved**:
- `BaseMetadataHandler`: Main handler (line 250-285)
- `LarkSourceMetadataProvider`: Provides schemas from Lark Base mapping
- `LarkBaseTableResolver`: Discovers tables during initialization
- `GlueCatalogService`: AWS Glue operations

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:250-285`

### 2. List Tables

**Entry Point**: `BaseMetadataHandler.doListTables()`

**Flow**:
```
1. Athena calls doListTables(schemaName)
   │
2. Get all tables from Glue Catalog (with TABLE_FILTER)
   │  └─ Filters tables with classification='lark-base'
   │
3. If Lark Base source is enabled:
   │  └─ Add tables from LarkSourceMetadataProvider
   │     └─ Filter by matching schema name
   │        └─ Read from mappingTableDirectInitialized
   │
4. Use LinkedHashSet to maintain order and eliminate duplicates
   │
5. Return ListTablesResponse
```

**Classes Involved**:
- `BaseMetadataHandler`: Main handler (line 299-367)
- `LarkSourceMetadataProvider`: Provides table metadata
- `TableDirectInitialized`: Stores database-table mappings

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:299-367`

### 3. Get Table Schema

**Entry Point**: `BaseMetadataHandler.doGetTable()`

**Flow**:
```
1. Athena calls doGetTable(catalogName, schemaName, tableName)
   │
2. Strategy Pattern: Try providers in order
   │
3. First: Try LarkSourceMetadataProvider (if enabled)
   │  └─ Find table in mappingTableDirectInitialized
   │     └─ Get cached schema from TableDirectInitialized
   │        └─ Schema was built by LarkBaseFieldResolver
   │
4. Second: Try ExperimentalMetadataProvider (if enabled)
   │  └─ Query Athena catalog for base_id/table_id
   │     └─ Call Lark Base API to get fields
   │        └─ Build schema dynamically
   │
5. Third: Fallback to Glue Catalog
   │  └─ Call super.doGetTable()
   │     └─ Get schema from Glue table metadata
   │
6. Add reserved fields ($reserved_record_id, etc.)
   │
7. Return GetTableResponse with Schema
```

**Classes Involved**:
- `BaseMetadataHandler`: Main handler (line 384-441)
- `LarkSourceMetadataProvider`: Primary schema source
- `ExperimentalMetadataProvider`: Alternative schema source
- `LarkBaseFieldResolver`: Builds Arrow schema from Lark fields
- `CommonUtil`: Adds reserved fields

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:384-441`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/resolver/LarkBaseFieldResolver.java`

### 4. Get Partitions

**Entry Point**: `BaseMetadataHandler.getPartitions()`

**Flow**:
```
1. Athena calls getPartitions() with constraints
   │
2. Resolve partition info (base_id, table_id, field mappings)
   │  └─ Try LarkSourceMetadataProvider
   │  └─ Try ExperimentalMetadataProvider
   │  └─ Fallback to GlueCatalogService
   │
3. Extract query LIMIT clause (if present)
   │
4. Translate WHERE clause to Lark filter JSON
   │  └─ SearchApiFilterTranslator.toFilterJson()
   │     └─ Map Athena field names to Lark field names
   │     └─ Convert constraints to Lark filter format
   │
5. Check if ORDER BY clause exists
   │
6. Translate ORDER BY to Lark sort JSON
   │  └─ SearchApiFilterTranslator.toSortJson()
   │
7. Estimate total row count
   │  └─ Call Lark API with filter (page_size=1)
   │     └─ Use 'total' from response
   │
8. Calculate effective row count (considering LIMIT)
   │
9. Decide split strategy:
   │  If $reserved_split_key exists AND parallel_split enabled:
   │     └─ Write multiple partition rows (parallel splits)
   │        └─ Each split covers a range (e.g., 1-500, 501-1000)
   │  Else:
   │     └─ Write single partition row
   │        └─ Sequential pagination
   │
10. Write partition data to BlockWriter
    └─ base_id, table_id, filter, sort, page_size, expected_count
```

**Classes Involved**:
- `BaseMetadataHandler`: Main handler (line 714-755)
- `SearchApiFilterTranslator`: Constraint translation
- `PartitionInfoResult`: Holds partition metadata
- `LarkBaseService`: API calls for row count estimation

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:714-755`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/translator/SearchApiFilterTranslator.java`

### 5. Get Splits

**Entry Point**: `BaseMetadataHandler.doGetSplits()`

**Flow**:
```
1. Athena calls doGetSplits() with partition data
   │
2. Read partition Block (from getPartitions)
   │  └─ Extract: base_id, table_id, filter, sort, page_size,
   │              expected_count, split range, etc.
   │
3. For each partition row:
   │  └─ Create one Split object
   │     └─ Copy all partition properties to split
   │     └─ Apply LIMIT optimization (if LIMIT < PAGE_SIZE)
   │        └─ Reduce page_size to LIMIT value
   │
4. Return GetSplitsResponse with Set<Split>
```

**Classes Involved**:
- `BaseMetadataHandler`: Main handler (line 774-854)
- `Split`: Athena SDK split object

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:774-854`

---

## Record Retrieval Flow

### Entry Point: `BaseRecordHandler.readWithConstraint()`

**Flow**:
```
1. Athena calls readWithConstraint(spiller, request, queryChecker)
   │
2. Extract split properties:
   │  - base_id, table_id
   │  - filter_expression, sort_expression
   │  - page_size, expected_row_count
   │  - is_parallel_split, split_start_index, split_end_index
   │  - lark_field_type_mapping (JSON)
   │
3. Deserialize lark_field_type_mapping
   │  └─ Map<String, NestedUIType> for field types
   │
4. Create RegistererExtractor with field type map
   │  └─ Registers type-specific extractors for Arrow vectors
   │
5. Create record iterator
   │  └─ getIterator() returns Iterator<Map<String, Object>>
   │     │
   │     ├─ If parallel split:
   │     │  └─ Build filter with split range
   │     │     └─ $reserved_split_key >= start AND <= end
   │     │
   │     ├─ Fetch records page by page
   │     │  └─ Call LarkBaseService.getTableRecords()
   │     │     └─ POST /bitable/v1/apps/{base}/tables/{table}/records/search
   │     │        {
   │     │          "page_size": 500,
   │     │          "page_token": "...",
   │     │          "filter": {...},
   │     │          "sort": [...]
   │     │        }
   │     │
   │     ├─ Handle pagination
   │     │  └─ Track currentPageToken, hasMorePages
   │     │  └─ Stop when:
   │     │     - No more pages
   │     │     - Reached expected_row_count
   │     │     - Query cancelled
   │     │
   │     └─ Add reserved fields to each record
   │        └─ $reserved_record_id, $reserved_table_id, $reserved_base_id
   │
6. Write records to BlockSpiller
   │  └─ writeItemsToBlock()
   │     │
   │     ├─ Build GeneratedRowWriter with RegistererExtractor
   │     │  └─ Extractors handle type conversion:
   │     │     - Primitive types (String, Number, Boolean)
   │     │     - Arrays (Multi-select, Attachment, Person)
   │     │     - Structs (Location, created_by, modified_by)
   │     │     - Complex types (Lookup → nested)
   │     │
   │     ├─ For each record:
   │     │  ├─ Ensure all schema fields are present
   │     │  │  └─ Add default values for missing fields
   │     │  │     └─ Respect constraints (nullable, type)
   │     │  │
   │     │  ├─ Call rowWriter.writeRow(block, rowNum, data)
   │     │  │  └─ Extractors write to Arrow vectors
   │     │  │     └─ Type conversion happens here
   │     │  │        └─ e.g., Lark timestamp → Arrow BIGINT
   │     │  │
   │     │  └─ Handle errors gracefully
   │     │     └─ Log and skip malformed records
   │     │
   │     └─ Track success/error counts
   │
7. Return when:
   │  - Iterator exhausted (no more records)
   │  - Query cancelled (queryStatusChecker)
   │  - Error occurred
```

**Classes Involved**:
- `BaseRecordHandler`: Main handler (line 140-208, 218-353)
- `LarkBaseService`: API communication (line 178-232)
- `RegistererExtractor`: Type extraction and conversion
- `GeneratedRowWriter`: Arrow record writing
- `SearchApiResponseNormalizer`: Response normalization
- `CommonUtil`: Field sanitization

**Key Code Paths**:
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseRecordHandler.java:140-208`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseRecordHandler.java:428-558`
- `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/service/LarkBaseService.java:178-232`

---

## Crawler Flow

### Entry Point: `MainLarkBaseCrawlerHandler.handleRequest()`

**Flow**:
```
1. AWS Lambda invokes MainLarkBaseCrawlerHandler
   │  Payload: {
   │    "handler_type": "lark_base" | "lark_drive",
   │    "larkBaseDataSourceId": "...",
   │    "larkTableDataSourceId": "...",
   │    ...
   │  }
   │
2. Route to specific crawler based on handler_type
   │  ├─ "lark_base" → LarkBaseCrawlerHandler
   │  └─ "lark_drive" → LarkDriveCrawlerHandler
   │
3. Get Lark databases (table records)
   │  └─ LarkBaseService.getDatabaseRecords(baseId, tableId)
   │     └─ Each record represents one target database
   │        Schema: { id: "...", name: "database_name" }
   │
4. For each database record:
   │  │
   │  ├─ Parse database configuration
   │  │  └─ id: Lark Base ID
   │  │  └─ name: Glue database name
   │  │
   │  ├─ Get all tables in the Lark Base
   │  │  └─ LarkBaseService.listTables(baseId)
   │  │     └─ Returns List<BaseItem> (table metadata)
   │  │
   │  ├─ For each table:
   │  │  │
   │  │  ├─ Get table fields (schema)
   │  │  │  └─ LarkBaseService.getTableFields(baseId, tableId)
   │  │  │     └─ Returns List<FieldItem>
   │  │  │
   │  │  ├─ Build Glue table schema
   │  │  │  └─ Map Lark field types to Glue types
   │  │  │     └─ UITypeEnum → Glue ColumnType
   │  │  │        Examples:
   │  │  │        - TEXT → string
   │  │  │        - NUMBER → double
   │  │  │        - DATE_TIME → bigint
   │  │  │        - MULTI_SELECT → array<string>
   │  │  │        - ATTACHMENT → array<struct<...>>
   │  │  │
   │  │  ├─ Create Glue TableInput
   │  │  │  └─ Name: sanitized table name
   │  │  │  └─ Location: "lark-base://method:source/baseId/tableId"
   │  │  │  └─ Columns: Glue column definitions
   │  │  │  └─ Parameters:
   │  │  │     - classification: "lark-base"
   │  │  │     - larkBaseId: base_id
   │  │  │     - larkTableId: table_id
   │  │  │     - larkBaseDataSourceId: ...
   │  │  │     - larkTableDataSourceId: ...
   │  │  │     - Column parameters (per-column metadata):
   │  │  │       - larkFieldId: field_id
   │  │  │       - larkFieldName: original_field_name
   │  │  │       - larkUIType: UI_TYPE_ENUM_VALUE
   │  │  │
   │  │  ├─ Check if table exists in Glue
   │  │  │  └─ GlueCatalogService.getTable(dbName, tableName)
   │  │  │
   │  │  ├─ If exists:
   │  │  │  ├─ Compare table inputs
   │  │  │  ├─ If changed:
   │  │  │  │  └─ Update table
   │  │  │  │     └─ GlueCatalogService.updateTable()
   │  │  │  └─ If unchanged:
   │  │  │     └─ Skip (no update needed)
   │  │  │
   │  │  └─ If not exists:
   │  │     └─ Create new table
   │  │        └─ GlueCatalogService.createTable()
   │  │
   │  └─ Return DatabaseProcessResult
   │     └─ Statistics: tables created/updated/skipped/errors
   │
5. Return summary response
   └─ Total databases processed
   └─ Total tables created/updated/skipped
   └─ Any errors encountered
```

**Classes Involved**:
- `MainLarkBaseCrawlerHandler`: Router (entry point)
- `LarkBaseCrawlerHandler`: Lark Base specific crawler
- `LarkDriveCrawlerHandler`: Lark Drive specific crawler
- `BaseLarkBaseCrawlerHandler`: Common crawler logic
- `LarkBaseService`: Lark API client
- `GlueCatalogService`: Glue catalog operations
- `Util`: Utility functions (name sanitization, validation)

**Key Code Paths**:
- `glue-lark-base-crawler/src/main/java/com/amazonaws/glue/lark/base/crawler/MainLarkBaseCrawlerHandler.java`
- `glue-lark-base-crawler/src/main/java/com/amazonaws/glue/lark/base/crawler/LarkBaseCrawlerHandler.java`
- `glue-lark-base-crawler/src/main/java/com/amazonaws/glue/lark/base/crawler/BaseLarkBaseCrawlerHandler.java`

---

## Core Components

### 1. BaseCompositeHandler

**Purpose**: AWS Lambda entry point for Athena Federation

**Responsibilities**:
- Instantiates BaseMetadataHandler and BaseRecordHandler
- Delegates requests to appropriate handler

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseCompositeHandler.java:36-55`

### 2. BaseMetadataHandler

**Purpose**: Handles all metadata operations for Athena

**Responsibilities**:
- List schemas (databases)
- List tables within a schema
- Get table schema (field definitions)
- Get table layout (partition strategy)
- Generate splits for query execution
- Report data source capabilities (pushdown support)

**Key Methods**:
- `doListSchemaNames()`: Returns available databases
- `doListTables()`: Returns tables in a database
- `doGetTable()`: Returns table schema
- `getPartitions()`: Creates partition rows with filter/sort
- `doGetSplits()`: Converts partitions to execution splits
- `doGetDataSourceCapabilities()`: Declares optimization support

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseMetadataHandler.java:113-975`

### 3. BaseRecordHandler

**Purpose**: Reads actual data from Lark Base

**Responsibilities**:
- Execute splits (read records)
- Handle pagination
- Type conversion (Lark → Arrow)
- Write data to Apache Arrow blocks
- Apply runtime constraints

**Key Methods**:
- `readWithConstraint()`: Main entry point for reading
- `getIterator()`: Creates paginated record iterator
- `writeItemsToBlock()`: Writes records to Arrow blocks

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/BaseRecordHandler.java:87-559`

### 4. LarkBaseService

**Purpose**: Client for Lark Base (Bitable) API

**Responsibilities**:
- Authentication (tenant access token)
- List tables in a base
- Get table fields (schema discovery)
- Search/list records with filters and sorting
- Handle pagination
- Cache frequently accessed data (field schemas)

**Key Methods**:
- `getTableRecords()`: Fetch records with Search API
- `getTableFields()`: Get table schema (cached)
- `listTables()`: List all tables in a base
- `getLookupType()`: Resolve lookup field types recursively

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/service/LarkBaseService.java:56-433`

### 5. SearchApiFilterTranslator

**Purpose**: Translate SQL constraints to Lark Search API format

**Responsibilities**:
- Convert WHERE clause predicates to JSON filter
- Convert ORDER BY clause to JSON sort
- Map Athena field names to Lark field names
- Handle various constraint types:
  - EquatableValueSet (IN, NOT IN)
  - SortedRangeSet (>, <, BETWEEN)
  - AllOrNoneValueSet (IS NULL)
- Support parallel split filters

**Key Methods**:
- `toFilterJson()`: Translate constraints to filter JSON
- `toSortJson()`: Translate ORDER BY to sort JSON
- `toSplitFilterJson()`: Add split range filters

**Supported Operators**:
```
SQL              Lark API
=============    =============
=                is
!=               isNot
>                isGreater
>=               isGreaterEqual
<                isLess
<=               isLessEqual
IS NULL          isEmpty
IS NOT NULL      isNotEmpty
IN (...)         multiple 'is' conditions
NOT IN (...)     multiple 'isNot' conditions
```

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/translator/SearchApiFilterTranslator.java:49-414`

### 6. RegistererExtractor

**Purpose**: Register type extractors for Arrow vector writing

**Responsibilities**:
- Map Lark field types to Arrow extractors
- Handle type conversion during record writing
- Support complex nested types
- Handle nullable fields gracefully

**Supported Type Mappings**:
```
Lark Type           Arrow Type               Extractor
===============     ===================      ====================
TEXT                VARCHAR                  String extractor
NUMBER              FLOAT8                   Double extractor
CURRENCY            FLOAT8                   Double extractor
PROGRESS            FLOAT8                   Double extractor
RATING              INT                      Integer extractor
CHECKBOX            BIT                      Boolean extractor
DATE_TIME           BIGINT                   Timestamp extractor
SINGLE_SELECT       VARCHAR                  String extractor
MULTI_SELECT        LIST<VARCHAR>            List<String> extractor
ATTACHMENT          LIST<STRUCT>             Complex struct extractor
PERSON              LIST<STRUCT>             Person struct extractor
LOOKUP              (varies)                 Recursive type resolution
LOCATION            STRUCT                   Location struct extractor
URL                 STRUCT                   URL struct extractor
CREATED_BY          STRUCT                   Person struct extractor
MODIFIED_BY         STRUCT                   Person struct extractor
```

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/translator/RegistererExtractor.java`

### 7. GlueCatalogService

**Purpose**: Interact with AWS Glue Data Catalog

**Responsibilities**:
- Get database metadata
- Get table metadata
- Extract Lark Base/Table IDs from table parameters
- Extract field mappings from column parameters
- Create/update Glue tables (used by crawler)

**Key Methods**:
- `getDatabase()`: Get database details
- `getTable()`: Get table details
- `getLarkBaseAndTableIdFromTable()`: Extract IDs from parameters
- `getFieldNameMappings()`: Extract field mappings

**Location**:
- Athena: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/service/GlueCatalogService.java`
- Crawler: `glue-lark-base-crawler/src/main/java/com/amazonaws/glue/lark/base/crawler/service/GlueCatalogService.java`

### 8. LarkBaseTableResolver

**Purpose**: Discover Lark Base tables at initialization

**Responsibilities**:
- Read table mapping configuration
- Support two discovery modes:
  1. Lark Base source: Read from a Lark Base table
  2. Lark Drive source: Read from folder metadata
- Build Arrow schemas for each table
- Cache discovered tables for fast access

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/resolver/LarkBaseTableResolver.java`

### 9. LarkBaseFieldResolver

**Purpose**: Build Apache Arrow schema from Lark Base fields

**Responsibilities**:
- Map Lark field types to Arrow types
- Handle complex nested types
- Create field metadata
- Add reserved fields

**Location**: `athena-lark-base/src/main/java/com/amazonaws/athena/connectors/lark/base/resolver/LarkBaseFieldResolver.java`

---

## Type System

### Lark Base to Arrow Type Mapping

| Lark Type | Arrow Type | Notes |
|-----------|-----------|-------|
| TEXT | VARCHAR | Plain text |
| BARCODE | VARCHAR | Barcode value as string |
| NUMBER | FLOAT8 | Double precision |
| PROGRESS | FLOAT8 | Progress percentage (0-1) |
| CURRENCY | FLOAT8 | Numeric value (no currency symbol) |
| RATING | INT | Integer rating value |
| CHECKBOX | BIT | Boolean (true/false) |
| DATE_TIME | BIGINT | Unix timestamp in milliseconds |
| CREATED_TIME | BIGINT | Unix timestamp (auto-generated) |
| MODIFIED_TIME | BIGINT | Unix timestamp (auto-generated) |
| SINGLE_SELECT | VARCHAR | Selected option text |
| MULTI_SELECT | LIST<VARCHAR> | Array of selected options |
| PHONE | VARCHAR | Phone number as string |
| EMAIL | VARCHAR | Email address |
| URL | STRUCT<text:VARCHAR, link:VARCHAR> | URL with display text |
| ATTACHMENT | LIST<STRUCT<name:VARCHAR, url:VARCHAR, size:BIGINT, type:VARCHAR, token:VARCHAR>> | File attachments |
| PERSON | LIST<STRUCT<id:VARCHAR, name:VARCHAR, email:VARCHAR>> | User references |
| LOOKUP | (recursive) | Resolves to target field type |
| FORMULA | VARCHAR | Formula result as string |
| LOCATION | STRUCT<location:VARCHAR, latitude:FLOAT8, longitude:FLOAT8, address:VARCHAR, full_address:VARCHAR, ...> | Geographic location |
| CREATED_BY | STRUCT<id:VARCHAR, name:VARCHAR, email:VARCHAR> | User who created |
| MODIFIED_BY | STRUCT<id:VARCHAR, name:VARCHAR, email:VARCHAR> | User who modified |
| GROUP_CHAT | VARCHAR | Group chat ID |
| AUTO_NUMBER | VARCHAR | Auto-generated number |
| DUPLEX_LINK | LIST<STRUCT<record_id:VARCHAR, text:VARCHAR>> | Bidirectional links |

### Reserved Fields

All tables automatically include these fields:

| Field Name | Arrow Type | Description |
|------------|-----------|-------------|
| $reserved_record_id | VARCHAR | Lark record ID |
| $reserved_table_id | VARCHAR | Lark table ID |
| $reserved_base_id | VARCHAR | Lark base ID |
| $reserved_split_key | BIGINT | Optional: Enables parallel splits |

---

## Query Optimizations

### 1. Filter Pushdown

**Supported Constraint Types**:
- `EQUATABLE_VALUE_SET`: Equality and IN/NOT IN
- `SORTED_RANGE_SET`: Range queries (>, <, BETWEEN)
- `ALL_OR_NONE_VALUE_SET`: IS NULL checks
- `NULLABLE_COMPARISON`: NULL comparisons

**Supported Field Types for Pushdown**:
- TEXT, BARCODE, PHONE, EMAIL
- SINGLE_SELECT
- NUMBER, PROGRESS, CURRENCY, RATING
- CHECKBOX
- DATE_TIME, CREATED_TIME, MODIFIED_TIME

**Unsupported** (filtered in Athena):
- LIKE patterns
- REGEX
- Complex types (ATTACHMENT, PERSON, etc.)
- FORMULA fields

**Example**:
```sql
-- SQL Query
SELECT * FROM mytable WHERE status = 'active' AND created_date > 1609459200000

-- Translated to Lark Filter
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "Status",
      "operator": "is",
      "value": ["active"]
    },
    {
      "field_name": "Created Date",
      "operator": "isGreater",
      "value": ["1609459200000"]
    }
  ]
}
```

### 2. LIMIT Pushdown

**How it works**:
- Athena passes LIMIT value to connector
- Connector uses it to:
  1. Adjust `page_size` (if LIMIT < default page size)
  2. Stop fetching when limit reached
  3. Calculate effective row count for partitioning

**Example**:
```sql
SELECT * FROM mytable LIMIT 100
```
- If default page_size=500, reduces to 100
- Stops after first page
- Avoids unnecessary API calls

### 3. TOP-N Pushdown (ORDER BY + LIMIT)

**How it works**:
- Requires both ORDER BY and LIMIT
- Translates ORDER BY to Lark sort JSON
- Lark API returns pre-sorted results
- Stops after LIMIT rows fetched

**Example**:
```sql
SELECT * FROM mytable ORDER BY created_date DESC LIMIT 10
```

Translated to Lark API:
```json
{
  "page_size": 10,
  "sort": [
    {
      "field_name": "Created Date",
      "desc": true
    }
  ]
}
```

**Important**: ORDER BY without LIMIT is NOT pushed down (would require scanning all data)

### 4. Parallel Split Execution

**Requirements**:
- Table must have `$reserved_split_key` field (auto-number or similar)
- Environment variable `ACTIVATE_PARALLEL_SPLIT=true`

**How it works**:
1. Estimate total row count
2. Divide into chunks (e.g., 1-500, 501-1000, ...)
3. Create one split per chunk
4. Each split filters by range:
   ```
   $reserved_split_key >= startIndex AND $reserved_split_key <= endIndex
   ```
5. Athena executes splits in parallel (multiple Lambda invocations)

**Benefits**:
- Faster query execution for large tables
- Better Lambda concurrency utilization
- Reduced individual Lambda memory pressure

---

## Configuration

### Environment Variables (Athena Connector)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `LARK_APP_ID_SECRET_NAME` | Yes | - | AWS Secrets Manager secret name for Lark app ID |
| `LARK_APP_SECRET_SECRET_NAME` | Yes | - | AWS Secrets Manager secret name for Lark app secret |
| `ACTIVATE_LARK_BASE_SOURCE` | No | false | Enable Lark Base table discovery |
| `ACTIVATE_LARK_DRIVE_SOURCE` | No | false | Enable Lark Drive folder discovery |
| `ACTIVATE_EXPERIMENTAL_FEATURES` | No | false | Enable experimental metadata provider |
| `ACTIVATE_PARALLEL_SPLIT` | No | false | Enable parallel split execution |
| `ENABLE_DEBUG_LOGGING` | No | false | Enable detailed debug logs |
| `LARK_BASE_DATA_SOURCE_ID` | Conditional | - | Base ID for table discovery (if LARK_BASE_SOURCE) |
| `LARK_TABLE_DATA_SOURCE_ID` | Conditional | - | Table ID for table discovery (if LARK_BASE_SOURCE) |
| `LARK_FOLDER_TOKEN_DATA_SOURCE` | Conditional | - | Folder token for discovery (if LARK_DRIVE_SOURCE) |

### AWS Secrets Manager

**Secret Format** (JSON):
```json
{
  "app_id": "cli_xxxxxxxxxx",
  "app_secret": "xxxxxxxxxxxxxx"
}
```

### Glue Table Parameters

Stored in table metadata:
```
classification = "lark-base"
larkBaseId = "bascnxxxxxxxxxxxxxx"
larkTableId = "tblxxxxxxxxxxxxxx"
larkBaseDataSourceId = "..." (optional)
larkTableDataSourceId = "..." (optional)
```

### Glue Column Parameters

Stored per-column:
```
larkFieldId = "fldxxxxxxxxxxxxxx"
larkFieldName = "Original Field Name"
larkUIType = "1" (UITypeEnum ordinal)
larkUITypeNestedFieldId = "..." (for lookup fields)
larkUITypeNestedTableId = "..." (for lookup fields)
larkUITypeNestedUIType = "..." (for lookup fields)
```

---

## Deployment

### Prerequisites

1. **AWS Account** with permissions for:
   - Lambda (create, update, invoke)
   - Glue (create database/table, update)
   - Athena (create data source)
   - S3 (spill bucket)
   - Secrets Manager (read secrets)
   - CloudWatch Logs (write logs)

2. **Lark Application**:
   - Create app at [Lark Open Platform](https://open.larksuite.com/)
   - Grant permissions:
     - `bitable:app:readonly` - Read base metadata
     - `bitable:table:readonly` - Read table data
     - `drive:drive:readonly` - Read drive folders (if using Drive source)
   - Get `app_id` and `app_secret`

3. **Java 17** and Maven for building

### Build

```bash
# Set JAVA_HOME
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home"

# Build connector
mvn clean package -pl athena-lark-base -am -Dcheckstyle.skip=true

# Build crawler
mvn clean package -pl glue-lark-base-crawler -am -Dcheckstyle.skip=true
```

Artifacts:
- `athena-lark-base/target/athena-lark-base-2022.47.1.jar`
- `glue-lark-base-crawler/target/glue-lark-base-crawler-2022.47.1.jar`

### Deploy Athena Connector

1. **Store Lark credentials in Secrets Manager**:
```bash
aws secretsmanager create-secret \
  --name lark-app-credentials \
  --secret-string '{"app_id":"cli_xxxxxxxxxx","app_secret":"xxxxxxxxxxxxxx"}'
```

2. **Upload JAR to S3**:
```bash
aws s3 cp athena-lark-base/target/athena-lark-base-2022.47.1.jar \
  s3://your-bucket/athena-connectors/
```

3. **Create Lambda function**:
```bash
aws lambda create-function \
  --function-name athena-lark-base-connector \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT_ID:role/AthenaFederationRole \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=your-bucket,S3Key=athena-connectors/athena-lark-base-2022.47.1.jar \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-app-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-app-credentials,
    ACTIVATE_LARK_BASE_SOURCE=true,
    LARK_BASE_DATA_SOURCE_ID=bascnxxxxxxxxxxxxxx,
    LARK_TABLE_DATA_SOURCE_ID=tblxxxxxxxxxxxxxx,
    ENABLE_DEBUG_LOGGING=false
  }"
```

4. **Register in Athena**:
```sql
-- In Athena console
CREATE EXTERNAL DATA SOURCE lark_base
USING LAMBDA 'arn:aws:lambda:us-east-1:ACCOUNT_ID:function:athena-lark-base-connector'
```

### Deploy Crawler

1. **Upload JAR to S3**:
```bash
aws s3 cp glue-lark-base-crawler/target/glue-lark-base-crawler-2022.47.1.jar \
  s3://your-bucket/glue-crawlers/
```

2. **Create Lambda function**:
```bash
aws lambda create-function \
  --function-name lark-base-crawler \
  --runtime java17 \
  --role arn:aws:iam::ACCOUNT_ID:role/GlueCrawlerRole \
  --handler com.amazonaws.glue.lark.base.crawler.MainLarkBaseCrawlerHandler \
  --code S3Bucket=your-bucket,S3Key=glue-crawlers/glue-lark-base-crawler-2022.47.1.jar \
  --timeout 900 \
  --memory-size 1024 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-app-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-app-credentials
  }"
```

3. **Invoke crawler**:
```bash
aws lambda invoke \
  --function-name lark-base-crawler \
  --payload '{
    "handler_type": "lark_base",
    "larkBaseDataSourceId": "bascnxxxxxxxxxxxxxx",
    "larkTableDataSourceId": "tblxxxxxxxxxxxxxx"
  }' \
  response.json
```

4. **Verify in Glue**:
```bash
aws glue get-databases
aws glue get-tables --database-name your_database
```

### Query Data

```sql
-- List databases
SHOW DATABASES IN lark_base;

-- List tables
SHOW TABLES IN lark_base.your_database;

-- Query data
SELECT * FROM lark_base.your_database.your_table LIMIT 10;

-- Query with filter pushdown
SELECT * FROM lark_base.your_database.your_table
WHERE status = 'active' AND created_date > 1609459200000;

-- Query with TOP-N pushdown
SELECT * FROM lark_base.your_database.your_table
ORDER BY created_date DESC LIMIT 100;
```

---

## Performance Considerations

### 1. Pagination

- Default page size: 500 records
- Adjustable via `PAGE_SIZE` constant
- Trade-off: Larger pages = fewer API calls but more memory

### 2. Caching

- Field schemas cached for 5 minutes (prevents N+1 lookup queries)
- Tenant access tokens cached (reduces auth overhead)
- Table metadata cached during initialization

### 3. Throttling

- Uses `ThrottlingInvoker` for automatic retry with exponential backoff
- Respects Lark API rate limits
- Configurable via SDK settings

### 4. Parallel Splits

- Requires `$reserved_split_key` field
- Best for tables with >10,000 rows
- Each split runs in separate Lambda (parallel execution)
- Trade-off: More Lambda invocations vs faster total time

### 5. Spill to S3

- Large result sets automatically spill to S3
- Configured via Athena workgroup settings
- Ensure sufficient S3 bucket permissions

---

## Error Handling

### Common Errors

1. **"Unable to retrieve table schema from Glue or Lark Base source"**
   - Cause: Table not found in any provider
   - Solution: Run crawler or check table name

2. **"Failed to refresh Lark access token"**
   - Cause: Invalid app credentials
   - Solution: Verify Secrets Manager secret

3. **"No mapping found for column"**
   - Cause: Field not in Glue metadata
   - Solution: Re-run crawler to update schema

4. **"Failed to translate filter constraints"**
   - Cause: Unsupported filter type
   - Solution: Check supported operators, may filter in Athena

5. **"Rate limit exceeded"**
   - Cause: Too many API requests
   - Solution: Increase throttling settings or reduce query frequency

### Logging

- Set `ENABLE_DEBUG_LOGGING=true` for detailed logs
- Check CloudWatch Logs: `/aws/lambda/function-name`
- Key log messages:
  - Filter translation: Shows translated JSON
  - API requests: Shows URL and payload
  - Record processing: Shows row count and errors

---

## Testing

### Unit Tests
```bash
mvn test -Dcheckstyle.skip=true
```

### Integration Tests
Requires live Lark Base and AWS credentials:
```bash
# Set environment
export LARK_APP_ID_SECRET_NAME=test-credentials
export LARK_APP_SECRET_SECRET_NAME=test-credentials

# Run tests
mvn verify -Dcheckstyle.skip=true
```

### Manual Testing
```sql
-- Test metadata discovery
SHOW DATABASES IN lark_base;
SHOW TABLES IN lark_base.test_db;
DESCRIBE lark_base.test_db.test_table;

-- Test record retrieval
SELECT COUNT(*) FROM lark_base.test_db.test_table;
SELECT * FROM lark_base.test_db.test_table LIMIT 10;

-- Test filter pushdown (check CloudWatch logs)
SELECT * FROM lark_base.test_db.test_table WHERE field = 'value';

-- Test TOP-N pushdown
SELECT * FROM lark_base.test_db.test_table ORDER BY date DESC LIMIT 100;
```

---

## Contributing

### Code Style
- Follows Google Java Style Guide
- Enforced by Checkstyle
- Run: `mvn checkstyle:check`

### Pull Request Process
1. Create feature branch
2. Write tests
3. Run full test suite
4. Pass Checkstyle validation
5. Update documentation
6. Submit PR with detailed description

---

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

---

## References

- [AWS Athena Federation SDK](https://github.com/awslabs/aws-athena-query-federation)
- [Lark Open Platform](https://open.larksuite.com/)
- [Lark Bitable API](https://open.larksuite.com/document/server-docs/docs/bitable-v1)
- [Apache Arrow](https://arrow.apache.org/)
- [AWS Glue Data Catalog](https://docs.aws.amazon.com/glue/latest/dg/catalog-and-crawler.html)
