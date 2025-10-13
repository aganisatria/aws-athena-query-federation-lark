# Class Diagrams - AWS Athena Lark Base Connector

## Table of Contents
1. [Handler Class Hierarchy](#handler-class-hierarchy)
2. [Service Layer Architecture](#service-layer-architecture)
3. [Model Layer](#model-layer)
4. [Metadata Provider Pattern](#metadata-provider-pattern)
5. [Translator Components](#translator-components)
6. [Resolver Components](#resolver-components)
7. [Crawler Components](#crawler-components)

---

## Handler Class Hierarchy

### Athena Connector Handlers

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CompositeHandler                                  │
│                (AWS Athena Federation SDK)                           │
│  - metadataHandler: MetadataHandler                                 │
│  - recordHandler: RecordHandler                                     │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ extends
                               │
                ┌──────────────▼─────────────────┐
                │   BaseCompositeHandler         │
                │  (Lambda Entry Point)          │
                │                                │
                │  + BaseCompositeHandler()      │
                └─────┬───────────────┬──────────┘
                      │               │
                      │ creates       │ creates
                      │               │
        ┌─────────────▼──────┐   ┌───▼──────────────────┐
        │ BaseMetadataHandler│   │ BaseRecordHandler    │
        └─────────────────────┘   └──────────────────────┘
```

### BaseMetadataHandler

```
┌─────────────────────────────────────────────────────────────────────┐
│                    GlueMetadataHandler                               │
│              (AWS Athena Federation SDK)                             │
│  # glueClient: GlueClient                                           │
│  # doListSchemaNames(): ListSchemasResponse                         │
│  # doListTables(): ListTablesResponse                               │
│  # doGetTable(): GetTableResponse                                   │
│  # doGetSplits(): GetSplitsResponse                                 │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ extends
                               │
                ┌──────────────▼─────────────────────────────────┐
                │         BaseMetadataHandler                    │
                │                                                │
                │ - envVarService: EnvVarService                 │
                │ - larkBaseService: LarkBaseService             │
                │ - glueCatalogService: GlueCatalogService       │
                │ - invoker: ThrottlingInvoker                   │
                │ - mappingTableDirectInitialized: List          │
                │ - larkSourceMetadataProvider                   │
                │ - experimentalMetadataProvider                 │
                │                                                │
                │ + doListSchemaNames(): ListSchemasResponse     │
                │ + doListTables(): ListTablesResponse           │
                │ + doGetTable(): GetTableResponse               │
                │ + getPartitions(): void                        │
                │ + doGetSplits(): GetSplitsResponse             │
                │ + doGetDataSourceCapabilities(): Response      │
                │ + enhancePartitionSchema(): void               │
                │                                                │
                │ - resolvePartitionInfo(): Optional             │
                │ - extractQueryLimit(): long                    │
                │ - buildFieldTypeMappingJson(): String          │
                │ - translateFilterExpression(): String          │
                │ - translateSortExpression(): String            │
                │ - writeParallelPartitions(): void              │
                │ - writeSinglePartition(): void                 │
                │ - getTotalRowCount(): int                      │
                └────────────────────────────────────────────────┘
```

### BaseRecordHandler

```
┌─────────────────────────────────────────────────────────────────────┐
│                      RecordHandler                                   │
│              (AWS Athena Federation SDK)                             │
│  # readWithConstraint(): void                                       │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ extends
                               │
                ┌──────────────▼─────────────────────────────────┐
                │          BaseRecordHandler                     │
                │                                                │
                │ - envVarService: EnvVarService                 │
                │ - larkBaseService: LarkBaseService             │
                │ - invokerCache: LoadingCache                   │
                │                                                │
                │ # readWithConstraint(): void                   │
                │ # writeItemsToBlock(): void                    │
                │ # getIterator(): Iterator<Map>                 │
                │                                                │
                │ - processRecords(): void                       │
                │ - getDefaultValueForType(): Object             │
                └────────────────────────────────────────────────┘
```

---

## Service Layer Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                        Service Layer                                │
└────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────┐      ┌─────────────────────────────┐
│    CommonLarkService        │◄─────┤   LarkBaseService           │
│                             │      │                             │
│ # larkAppId: String         │      │ - tableFieldsCache: Cache   │
│ # larkAppSecret: String     │      │                             │
│ # tenantAccessToken: String │      │ + getDatabaseRecords()      │
│ # httpClient: HttpClient    │      │ + getTableRecords()         │
│                             │      │ + getTableFields()          │
│ + refreshTenantAccessToken()│      │ + listTables()              │
│                             │      │ + getLookupType()           │
└─────────────────────────────┘      │ - fetchTableFieldsUncached()│
         ▲                            │ - sanitizeRecordFieldNames()│
         │                            └─────────────────────────────┘
         │ extends
         │
┌────────┴────────────────────┐
│   LarkDriveService          │
│                             │
│ + listFolders()             │
│ + getFolder()               │
└─────────────────────────────┘


┌─────────────────────────────┐      ┌─────────────────────────────┐
│   GlueCatalogService        │      │     EnvVarService           │
│                             │      │                             │
│ - glueClient: GlueClient    │      │ - configOptions: Map        │
│                             │      │ - invoker: Invoker          │
│ + getDatabase()             │      │ - secretValue: SecretValue  │
│ + getTable()                │      │                             │
│ + createTable()             │      │ + getLarkAppId()            │
│ + updateTable()             │      │ + getLarkAppSecret()        │
│ + getLarkBaseAndTableId()   │      │ + isActivateLarkBaseSource()│
│ + getFieldNameMappings()    │      │ + isActivateParallelSplit() │
└─────────────────────────────┘      │ + isEnableDebugLogging()    │
                                     └─────────────────────────────┘

┌─────────────────────────────┐      ┌─────────────────────────────┐
│     AthenaService           │      │   HttpClientWrapper         │
│                             │      │                             │
│ + queryMetadataTable()      │      │ - httpClient: HttpClient    │
│ + getBaseAndTableIds()      │      │                             │
└─────────────────────────────┘      │ + execute()                 │
                                     │ + close()                   │
                                     └─────────────────────────────┘
```

### Service Relationships

```
┌──────────────────────┐
│ BaseMetadataHandler  │
└──────────┬───────────┘
           │ uses
           │
           ├──────────► EnvVarService
           │
           ├──────────► LarkBaseService ──────► CommonLarkService
           │                    │
           │                    └──────────────► HttpClientWrapper
           │
           ├──────────► LarkDriveService ─────► CommonLarkService
           │
           └──────────► GlueCatalogService


┌──────────────────────┐
│ BaseRecordHandler    │
└──────────┬───────────┘
           │ uses
           │
           ├──────────► EnvVarService
           │
           └──────────► LarkBaseService ──────► CommonLarkService
```

---

## Model Layer

### Request Models

```
┌────────────────────────────────────────────────────────────────────┐
│                          Request Models                             │
└────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────┐
│  TenantAccessTokenRequest     │
│                               │
│ + appId: String               │
│ + appSecret: String           │
└───────────────────────────────┘

┌───────────────────────────────┐
│   TableRecordsRequest         │
│                               │
│ + baseId: String              │
│ + tableId: String             │
│ + pageSize: int               │
│ + pageToken: String           │
│ + filterJson: String          │
│ + sortJson: String            │
│                               │
│ + builder()                   │
└───────────────────────────────┘

┌───────────────────────────────┐
│   SearchRecordsRequest        │
│                               │
│ + pageSize: int               │
│ + pageToken: String           │
│ + filter: String (JSON)       │
│ + sort: String (JSON)         │
│                               │
│ + builder()                   │
└───────────────────────────────┘
```

### Response Models

```
┌────────────────────────────────────────────────────────────────────┐
│                         Response Models                             │
└────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────┐
│      BaseResponse             │
│                               │
│ + code: int                   │
│ + msg: String                 │
└───────────────┬───────────────┘
                │ extends
                │
    ┌───────────┴──────────────────────────────┬────────────────────┐
    │                                          │                    │
┌───▼──────────────────────┐  ┌───────────────▼─────┐  ┌──────────▼────────┐
│ TenantAccessTokenResponse│  │ ListRecordsResponse │  │ ListFieldResponse │
│                          │  │                     │  │                   │
│ + tenantAccessToken: Str │  │ + items: List       │  │ + items: List     │
│ + expire: int            │  │ + hasMore: boolean  │  │ + hasMore: bool   │
└──────────────────────────┘  │ + pageToken: String │  │ + pageToken: Str  │
                              │ + total: int        │  └───────────────────┘
                              │                     │
                              │ RecordItem          │
                              │ + recordId: String  │
                              │ + fields: Map       │
                              └─────────────────────┘

┌───────────────────────────────┐
│  ListAllTableResponse         │
│                               │
│ + items: List<BaseItem>       │
│ + hasMore: boolean            │
│ + pageToken: String           │
│                               │
│ BaseItem                      │
│ + tableId: String             │
│ + name: String                │
│ + revision: int               │
└───────────────────────────────┘

┌───────────────────────────────┐
│  ListAllFolderResponse        │
│                               │
│ + items: List<FolderItem>     │
│ + hasMore: boolean            │
│ + pageToken: String           │
│                               │
│ FolderItem                    │
│ + token: String               │
│ + name: String                │
│ + type: String                │
└───────────────────────────────┘
```

### Domain Models

```
┌────────────────────────────────────────────────────────────────────┐
│                          Domain Models                              │
└────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────┐
│  AthenaLarkBaseMapping        │
│  (Record)                     │
│                               │
│ + athenaName: String          │
│ + larkBaseName: String        │
│ + larkBaseId: String          │
└───────────────────────────────┘

┌───────────────────────────────┐
│ AthenaFieldLarkBaseMapping    │
│  (Record)                     │
│                               │
│ + athenaName: String          │
│ + larkBaseFieldName: String   │
│ + larkBaseFieldId: String     │
│ + nestedUIType: NestedUIType  │
└───────────┬───────────────────┘
            │ has
            ▼
┌───────────────────────────────┐
│      NestedUIType             │
│      (Record)                 │
│                               │
│ + uiType: UITypeEnum          │
│ + nestedFieldId: String       │
│ + nestedTableId: String       │
│ + nestedUIType: NestedUIType  │
└───────────────────────────────┘

┌───────────────────────────────┐
│   TableDirectInitialized      │
│   (Record)                    │
│                               │
│ + database: Mapping           │
│ + table: Mapping              │
│ + fields: List<FieldMapping>  │
│ + schema: Schema              │
└───────────────────────────────┘

┌───────────────────────────────┐
│    TableSchemaResult          │
│    (Record)                   │
│                               │
│ + schema: Schema              │
│ + partitionColumns: Set       │
└───────────────────────────────┘

┌───────────────────────────────┐
│   PartitionInfoResult         │
│   (Record)                    │
│                               │
│ + baseId: String              │
│ + tableId: String             │
│ + fieldNameMappings: List     │
└───────────────────────────────┘

┌───────────────────────────────┐
│    LarkDatabaseRecord         │
│    (Record)                   │
│                               │
│ + id: String                  │
│ + name: String                │
└───────────────────────────────┘

┌───────────────────────────────┐
│      SecretValue              │
│      (Record)                 │
│                               │
│ + appId: String               │
│ + appSecret: String           │
└───────────────────────────────┘
```

### Enums

```
┌────────────────────────────────────────────────────────────────────┐
│                            UITypeEnum                               │
│                       (Lark Field Types)                            │
│                                                                     │
│  TEXT(1), BARCODE(2), NUMBER(3), PROGRESS(4), CURRENCY(5),        │
│  RATING(6), CHECKBOX(7), DATE_TIME(11), PHONE(13),                │
│  EMAIL(15), URL(16), ATTACHMENT(17), SINGLE_SELECT(18),           │
│  MULTI_SELECT(19), PERSON(20), LOOKUP(21), CREATED_TIME(22),      │
│  MODIFIED_TIME(23), CREATED_BY(24), MODIFIED_BY(25),              │
│  AUTO_NUMBER(26), GROUP_CHAT(27), LOCATION(28), FORMULA(29),      │
│  DUPLEX_LINK(30), UNKNOWN(0)                                       │
│                                                                     │
│  + fromValue(int): UITypeEnum                                      │
└────────────────────────────────────────────────────────────────────┘
```

---

## Metadata Provider Pattern

```
┌────────────────────────────────────────────────────────────────────┐
│                  Metadata Provider Interface                        │
│                        (Implicit)                                   │
│                                                                     │
│  + getTableSchema(request): Optional<TableSchemaResult>            │
│  + getPartitionInfo(tableName): Optional<PartitionInfoResult>      │
└─────────────────────────────┬──────────────────────────────────────┘
                              │ implements
                              │
                ┌─────────────┴──────────────────┐
                │                                │
┌───────────────▼──────────────┐  ┌─────────────▼─────────────────┐
│ LarkSourceMetadataProvider   │  │ ExperimentalMetadataProvider  │
│                              │  │                               │
│ - tableDirectInitialized     │  │ - athenaService               │
│                              │  │ - larkBaseService             │
│ + getTableSchema()           │  │ - invoker                     │
│ + getPartitionInfo()         │  │                               │
│                              │  │ + getTableSchema()            │
│ - findTableMapping()         │  │ + getPartitionInfo()          │
└──────────────────────────────┘  │                               │
                                  │ - queryMetadataTable()        │
                                  │ - fetchSchemaFromLark()       │
                                  └───────────────────────────────┘
```

### Provider Pattern Flow

```
BaseMetadataHandler.doGetTable()
    │
    ├─► Try LarkSourceMetadataProvider
    │   └─► Search in-memory table mappings
    │       └─► Return cached schema
    │
    ├─► Try ExperimentalMetadataProvider
    │   └─► Query Athena metadata table
    │       └─► Fetch schema from Lark API
    │           └─► Build schema dynamically
    │
    └─► Fallback to Glue
        └─► super.doGetTable()
            └─► Query Glue Data Catalog
```

---

## Translator Components

### SearchApiFilterTranslator

```
┌────────────────────────────────────────────────────────────────────┐
│              SearchApiFilterTranslator (Utility)                    │
│                                                                     │
│  Static methods for constraint translation                         │
│                                                                     │
│  + toFilterJson(constraints, mappings): String                     │
│  + toSortJson(orderByFields, mappings): String                     │
│  + toSplitFilterJson(existingFilter, start, end): String           │
│                                                                     │
│  - translateValueSetToConditions(): List                           │
│  - translateRangeSet(): List                                       │
│  - translateEquatableValueSet(): List                              │
│  - createCondition(): Map                                          │
│  - convertValueForSearchApi(): Object                              │
│  - isUiTypeAllowedForPushdown(): boolean                           │
│  - findMappingForColumn(): Mapping                                 │
└────────────────────────────────────────────────────────────────────┘
```

### Translation Flow

```
Athena Constraints (ValueSet)
    │
    ├─► SortedRangeSet
    │   ├─► Single value → "is"
    │   ├─► Range → "isGreater", "isLess", etc.
    │   └─► IS NOT NULL → "isNotEmpty"
    │
    ├─► EquatableValueSet
    │   ├─► White list → "is" (multiple)
    │   └─► Black list → "isNot" (multiple)
    │
    └─► AllOrNoneValueSet
        └─► None → "isEmpty"

    ↓
Lark Search API JSON Filter
{
  "conjunction": "and",
  "conditions": [
    {
      "field_name": "...",
      "operator": "is",
      "value": ["..."]
    }
  ]
}
```

### RegistererExtractor

```
┌────────────────────────────────────────────────────────────────────┐
│                    RegistererExtractor                              │
│                                                                     │
│  - larkFieldTypeMap: Map<String, NestedUIType>                     │
│                                                                     │
│  + registerExtractorsForSchema(builder, schema): void              │
│                                                                     │
│  - registerExtractor(builder, field): void                         │
│  - createExtractor(field, uiType): Extractor                       │
│  - createListExtractor(field, uiType): Extractor                   │
│  - createStructExtractor(field, uiType): Extractor                 │
│  - createAttachmentExtractor(field): Extractor                     │
│  - createPersonExtractor(field): Extractor                         │
│  - createLocationExtractor(field): Extractor                       │
│  - createUrlExtractor(field): Extractor                            │
│  - createLookupExtractor(field, nested): Extractor                 │
└────────────────────────────────────────────────────────────────────┘
```

### Extractor Registration Flow

```
RegistererExtractor.registerExtractorsForSchema()
    │
    └─► For each field in schema:
        │
        ├─► Get Lark UI type from field metadata
        │
        ├─► Map to appropriate extractor:
        │   │
        │   ├─► Primitive (TEXT, NUMBER, etc.)
        │   │   └─► Simple field extractor
        │   │
        │   ├─► List (MULTI_SELECT, ATTACHMENT, etc.)
        │   │   └─► List extractor with child type
        │   │
        │   ├─► Struct (LOCATION, PERSON, URL, etc.)
        │   │   └─► Struct extractor with child fields
        │   │
        │   └─► Lookup (recursive)
        │       └─► Resolve target type, create nested extractor
        │
        └─► Register with GeneratedRowWriter.Builder
```

### SearchApiResponseNormalizer

```
┌────────────────────────────────────────────────────────────────────┐
│              SearchApiResponseNormalizer (Utility)                  │
│                                                                     │
│  Static methods to normalize Search API response format            │
│                                                                     │
│  + normalizeRecordFields(fields): Map                              │
│                                                                     │
│  - normalizeFieldValue(value): Object                              │
│  - normalizeListValue(list): List                                  │
│  - normalizeObjectValue(obj): Object                               │
└────────────────────────────────────────────────────────────────────┘
```

**Purpose**: Lark Search API returns different format than List API. Normalizer converts Search API format to match List API format for consistent processing.

---

## Resolver Components

### LarkBaseTableResolver

```
┌────────────────────────────────────────────────────────────────────┐
│                   LarkBaseTableResolver                             │
│                                                                     │
│  - envVarService: EnvVarService                                    │
│  - larkBaseService: LarkBaseService                                │
│  - larkDriveService: LarkDriveService                              │
│  - invoker: ThrottlingInvoker                                      │
│                                                                     │
│  + resolveTables(): List<TableDirectInitialized>                   │
│                                                                     │
│  - resolveFromLarkBase(): List                                     │
│  - resolveFromLarkDrive(): List                                    │
│  - fetchAndBuildTable(): TableDirectInitialized                    │
└────────────────────────────────────────────────────────────────────┘
```

### LarkBaseFieldResolver

```
┌────────────────────────────────────────────────────────────────────┐
│                  LarkBaseFieldResolver                              │
│                                                                     │
│  - larkBaseService: LarkBaseService                                │
│                                                                     │
│  + buildSchema(baseId, tableId): Pair<Schema, List>                │
│                                                                     │
│  - convertToArrowType(fieldItem): Field                            │
│  - getArrowTypeForUIType(uiType): ArrowType                        │
│  - createPersonStructType(): ArrowType                             │
│  - createAttachmentStructType(): ArrowType                         │
│  - createLocationStructType(): ArrowType                           │
│  - createUrlStructType(): ArrowType                                │
│  - resolveLookupType(fieldItem): NestedUIType                      │
└────────────────────────────────────────────────────────────────────┘
```

### Resolver Flow

```
LarkBaseTableResolver.resolveTables()
    │
    ├─► If LARK_BASE_SOURCE enabled:
    │   └─► resolveFromLarkBase()
    │       │
    │       ├─► Get database records from source table
    │       │
    │       └─► For each database:
    │           ├─► List tables in base
    │           └─► For each table:
    │               └─► fetchAndBuildTable()
    │                   ├─► Get table fields
    │                   ├─► LarkBaseFieldResolver.buildSchema()
    │                   └─► Create TableDirectInitialized
    │
    └─► If LARK_DRIVE_SOURCE enabled:
        └─► resolveFromLarkDrive()
            └─► (similar process using Drive API)

LarkBaseFieldResolver.buildSchema()
    │
    ├─► Get fields from LarkBaseService
    │
    └─► For each field:
        │
        ├─► Map UITypeEnum to ArrowType
        │   │
        │   ├─► TEXT → VARCHAR
        │   ├─► NUMBER → FLOAT8
        │   ├─► DATE_TIME → BIGINT
        │   ├─► MULTI_SELECT → LIST<VARCHAR>
        │   ├─► ATTACHMENT → LIST<STRUCT>
        │   ├─► PERSON → LIST<STRUCT>
        │   ├─► LOCATION → STRUCT
        │   └─► LOOKUP → (recursive resolution)
        │
        ├─► Create Field with metadata
        │   └─► Store: fieldId, originalName, uiType
        │
        └─► Build Schema from fields
```

---

## Crawler Components

### Crawler Class Hierarchy

```
┌────────────────────────────────────────────────────────────────────┐
│             MainLarkBaseCrawlerHandler                              │
│             (Lambda Entry Point)                                    │
│                                                                     │
│  + handleRequest(input, context): String                           │
│                                                                     │
│  Routes to:                                                         │
│  - "lark_base" → LarkBaseCrawlerHandler                            │
│  - "lark_drive" → LarkDriveCrawlerHandler                          │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│            BaseLarkBaseCrawlerHandler (Abstract)                    │
│                                                                     │
│  # glueCatalogService: GlueCatalogService                          │
│  # larkBaseService: LarkBaseService                                │
│  # larkDriveService: LarkDriveService                              │
│  # stsService: STSService                                          │
│                                                                     │
│  + handleRequest(input, context): String                           │
│                                                                     │
│  # abstract getCrawlingMethod(): String                            │
│  # abstract getCrawlingSource(): String                            │
│  # abstract getLarkDatabases(): List                               │
│  # abstract getAdditionalTableInputParameter(): Map                │
│  # abstract additionalTableInputChanged(): boolean                 │
│                                                                     │
│  - processDatabases(): List<DatabaseProcessResult>                 │
│  - processDatabase(): DatabaseProcessResult                        │
│  - processTables(): List<TableResult>                              │
│  - createOrUpdateTable(): TableResult                              │
│  - buildTableInput(): TableInput                                   │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ extends
                               │
                ┌──────────────┴──────────────────┐
                │                                 │
┌───────────────▼──────────────┐  ┌──────────────▼────────────────┐
│  LarkBaseCrawlerHandler      │  │  LarkDriveCrawlerHandler      │
│                              │  │                               │
│ - larkBaseDataSourceId       │  │ - larkFolderToken             │
│ - larkTableDataSourceId      │  │                               │
│                              │  │ # getCrawlingMethod()         │
│ # getCrawlingMethod()        │  │ # getCrawlingSource()         │
│ # getCrawlingSource()        │  │ # getLarkDatabases()          │
│ # getLarkDatabases()         │  │ # getAdditionalTableInput...()│
│ # getAdditionalTableInput()  │  │ # additionalTableInput...()   │
│ # additionalTableInputChanged│  └───────────────────────────────┘
└──────────────────────────────┘
```

### Crawler Data Flow

```
MainLarkBaseCrawlerHandler
    │
    ├─► Parse payload
    │   └─► handler_type, source IDs
    │
    ├─► Route to specific handler
    │
    └─► BaseLarkBaseCrawlerHandler.handleRequest()
        │
        ├─► getLarkDatabases()
        │   └─► Get database records from source
        │
        ├─► For each database:
        │   │
        │   ├─► Create/verify Glue database
        │   │
        │   ├─► List tables in Lark Base
        │   │
        │   └─► For each table:
        │       │
        │       ├─► Get fields from Lark
        │       │
        │       ├─► Build Glue schema
        │       │   └─► Map Lark types to Glue types
        │       │
        │       ├─► Build TableInput
        │       │   ├─► Table name (sanitized)
        │       │   ├─► Location URI
        │       │   ├─► Columns with metadata
        │       │   └─► Table parameters
        │       │
        │       ├─► Check if table exists
        │       │
        │       ├─► If exists:
        │       │   ├─► Compare schemas
        │       │   └─► Update if changed
        │       │
        │       └─► If not exists:
        │           └─► Create new table
        │
        └─► Return summary
            └─► Databases/tables processed, created, updated
```

### Crawler Models

```
┌───────────────────────────────┐
│  DatabaseProcessResult        │
│  (Record)                     │
│                               │
│ + databaseName: String        │
│ + tablesCreated: int          │
│ + tablesUpdated: int          │
│ + tablesSkipped: int          │
│ + tablesError: int            │
│ + errorDetails: List          │
└───────────────────────────────┘

┌───────────────────────────────┐
│ TableOnUpdateDatabase...      │
│  (Record)                     │
│                               │
│ + tableName: String           │
│ + status: String              │
│ + message: String             │
└───────────────────────────────┘

┌───────────────────────────────┐
│   ColumnParameters            │
│   (Record)                    │
│                               │
│ + larkFieldId: String         │
│ + larkFieldName: String       │
│ + larkUIType: String          │
│ + larkUITypeNested*: String   │
└───────────────────────────────┘

┌───────────────────────────────┐
│  TableInputParameters         │
│  (Record)                     │
│                               │
│ + classification: String      │
│ + larkBaseId: String          │
│ + larkTableId: String         │
│ + additionalParams: Map       │
└───────────────────────────────┘
```

---

## Utility Components

### LarkBaseTypeUtils

```
┌────────────────────────────────────────────────────────────────────┐
│                LarkBaseTypeUtils (Utility)                          │
│                                                                     │
│  Static methods for type conversion                                │
│                                                                     │
│  + getArrowTypeForUIType(uiType): ArrowType                        │
│  + getGlueTypeForUIType(uiType): String                            │
│  + isNumericType(uiType): boolean                                  │
│  + isDateTimeType(uiType): boolean                                 │
│  + isListType(uiType): boolean                                     │
│  + isStructType(uiType): boolean                                   │
└────────────────────────────────────────────────────────────────────┘
```

### CommonUtil

```
┌────────────────────────────────────────────────────────────────────┐
│                    CommonUtil (Utility)                             │
│                                                                     │
│  Static methods for common operations                              │
│                                                                     │
│  + sanitizeGlueRelatedName(name): String                           │
│  + addReservedFields(schema): Schema                               │
│  + extractFieldMetadata(field): Map                                │
│  + buildFieldMapping(column): AthenaFieldLarkBaseMapping           │
└────────────────────────────────────────────────────────────────────┘
```

---

## Complete Component Interaction

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Athena Query                                 │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    BaseCompositeHandler                              │
└─────────────┬───────────────────────────────────┬───────────────────┘
              │                                   │
              ▼                                   ▼
┌─────────────────────────┐         ┌────────────────────────────────┐
│  BaseMetadataHandler    │         │    BaseRecordHandler           │
└──────┬──────────────────┘         └──────┬─────────────────────────┘
       │                                   │
       ├─► LarkSourceMetadataProvider     ├─► LarkBaseService
       │                                   │
       ├─► ExperimentalMetadataProvider   ├─► RegistererExtractor
       │                                   │
       ├─► SearchApiFilterTranslator      └─► GeneratedRowWriter
       │
       ├─► LarkBaseService
       │
       └─► GlueCatalogService
```

---

This completes the detailed class diagram documentation for the AWS Athena Lark Base Connector.
