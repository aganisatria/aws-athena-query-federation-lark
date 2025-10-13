# Visual Diagrams - AWS Athena Lark Base Connector

This document contains all diagrams in **Mermaid** format.

## 📊 How to Use These Diagrams

### Option 1: View in GitHub/GitLab
- These diagrams render automatically in GitHub and GitLab
- Just view this file in your repository

### Option 2: Import to Diagrams.io (draw.io)
1. Copy the Mermaid code block
2. Go to https://app.diagrams.io/
3. Click "Arrange" → "Insert" → "Advanced" → "Mermaid"
4. Paste the code
5. Edit and export as needed

### Option 3: Export to PNG/SVG
1. Use Mermaid Live Editor: https://mermaid.live/
2. Paste the code
3. Download as PNG/SVG

---

## Table of Contents
1. [System Architecture Overview](#1-system-architecture-overview)
2. [Metadata Discovery Flows](#2-metadata-discovery-flows)
3. [Query Execution Flow](#3-query-execution-flow)
4. [Class Hierarchy](#4-class-hierarchy)
5. [Sequence Diagrams](#5-sequence-diagrams)
6. [Component Interaction](#6-component-interaction)

---

## 1. System Architecture Overview

### High-Level Architecture

```mermaid
graph TB
    subgraph "User Layer"
        User[User/Analyst]
    end

    subgraph "AWS Services"
        Athena[Amazon Athena<br/>SQL Query Engine]
        Glue[AWS Glue<br/>Data Catalog]
        Lambda1[AWS Lambda<br/>Athena Connector]
        Lambda2[AWS Lambda<br/>Glue Crawler]
        Secrets[AWS Secrets Manager<br/>Lark Credentials]
        S3[Amazon S3<br/>Spill Bucket]
    end

    subgraph "Connector Components"
        Composite[BaseCompositeHandler<br/>Entry Point]
        Metadata[BaseMetadataHandler<br/>Metadata Operations]
        Record[BaseRecordHandler<br/>Data Reading]

        subgraph "Services"
            LarkSvc[LarkBaseService<br/>API Client]
            GlueSvc[GlueCatalogService<br/>Glue Operations]
        end
    end

    subgraph "External"
        LarkAPI[Lark Base API<br/>Feishu Bitable]
    end

    User -->|SQL Query| Athena
    Athena -->|Federated Query| Lambda1
    Lambda1 --> Composite
    Composite --> Metadata
    Composite --> Record

    Metadata --> LarkSvc
    Metadata --> GlueSvc
    Record --> LarkSvc

    Lambda2 -->|Crawl| LarkAPI
    Lambda2 -->|Create/Update| Glue

    Metadata -->|Read Metadata| Glue

    LarkSvc -->|API Calls| LarkAPI
    GlueSvc -->|Read/Write| Glue

    Lambda1 -->|Read Credentials| Secrets
    Lambda2 -->|Read Credentials| Secrets

    Record -->|Spill Large Results| S3

    style User fill:#e1f5ff
    style Athena fill:#ff9900
    style Lambda1 fill:#ff9900
    style Lambda2 fill:#ff9900
    style Glue fill:#ff9900
    style Secrets fill:#ff9900
    style S3 fill:#ff9900
    style LarkAPI fill:#00d4aa
    style Composite fill:#4CAF50
    style Metadata fill:#4CAF50
    style Record fill:#4CAF50
```

---

## 2. Metadata Discovery Flows

### Flow Comparison Decision Tree

```mermaid
graph TD
    Start[Start: Choose Metadata Discovery Flow]

    Start --> Q1{Production or<br/>Development?}

    Q1 -->|Production| Q2{Schema changes<br/>frequently?}
    Q1 -->|Development| Q3{Need real-time<br/>schema updates?}

    Q2 -->|No - Stable| Flow1[Flow 1: Glue + Crawler<br/>✅ Best performance<br/>✅ AWS governance<br/>⚙️ Manual updates]
    Q2 -->|Yes - Dynamic| Flow2A[Flow 2: Lark Base Source<br/>✅ Auto updates on restart<br/>✅ No crawler needed<br/>⚠️ Cold start penalty]

    Q3 -->|Yes| Flow4[Flow 4: Experimental<br/>✅ Real-time schema<br/>✅ No caching<br/>⚠️ Higher latency]
    Q3 -->|No| Q4{Prefer folder-based<br/>organization?}

    Q4 -->|Yes| Flow3[Flow 3: Lark Drive Source<br/>✅ Visual organization<br/>✅ Folder = Database<br/>⚠️ Naming constraints]
    Q4 -->|No| Flow2B[Flow 2: Lark Base Source<br/>✅ Simple setup<br/>✅ Flexible naming<br/>✅ Metadata in Lark]

    Flow1 --> End[Deploy and Query!]
    Flow2A --> End
    Flow2B --> End
    Flow3 --> End
    Flow4 --> End

    style Start fill:#e1f5ff
    style Flow1 fill:#4CAF50
    style Flow2A fill:#2196F3
    style Flow2B fill:#2196F3
    style Flow3 fill:#FF9800
    style Flow4 fill:#9C27B0
    style End fill:#4CAF50
```

### Flow 1: Glue Catalog + Crawler

```mermaid
sequenceDiagram
    participant Admin
    participant Crawler as Crawler Lambda
    participant LarkAPI as Lark Base API
    participant Glue as AWS Glue Catalog
    participant Athena as Athena Query
    participant Connector as Athena Connector

    rect rgb(200, 220, 240)
        Note over Admin,Glue: One-Time Setup
        Admin->>Crawler: 1. Invoke Crawler Lambda
        Crawler->>LarkAPI: 2. List tables & fields
        LarkAPI-->>Crawler: Table metadata
        Crawler->>Glue: 3. Create/Update tables
        Note over Glue: Tables stored with<br/>larkBaseId, larkTableId<br/>in parameters
    end

    rect rgb(220, 240, 220)
        Note over Athena,LarkAPI: Query Time
        Athena->>Connector: 4. SELECT * FROM table
        Connector->>Glue: 5. Get table metadata
        Glue-->>Connector: Schema + IDs
        Connector->>LarkAPI: 6. Fetch data
        LarkAPI-->>Connector: Records
        Connector-->>Athena: Results
    end

    Note over Admin,Connector: ✅ Fast queries (cached)<br/>❌ Manual schema updates
```

### Flow 2: Lark Base Source (Direct)

```mermaid
sequenceDiagram
    participant Admin
    participant Lark as Lark Base<br/>Metadata Table
    participant Lambda as Lambda Cold Start
    participant Memory as In-Memory Cache
    participant Athena as Athena Query
    participant LarkAPI as Lark Base API

    rect rgb(200, 220, 240)
        Note over Admin,Lark: Setup
        Admin->>Lark: 1. Create metadata table<br/>with db/table mappings
    end

    rect rgb(240, 220, 200)
        Note over Lambda,Memory: Lambda Initialization
        Lambda->>Lark: 2. Read metadata table
        Lark-->>Lambda: All table mappings
        Lambda->>LarkAPI: 3. Get schemas for each table
        LarkAPI-->>Lambda: Field definitions
        Lambda->>Memory: 4. Cache all schemas
        Note over Memory: Schemas stored<br/>in Lambda memory
    end

    rect rgb(220, 240, 220)
        Note over Athena,LarkAPI: Query Time (Warm)
        Athena->>Lambda: 5. SELECT * FROM table
        Lambda->>Memory: 6. Get schema (fast!)
        Memory-->>Lambda: Cached schema
        Lambda->>LarkAPI: 7. Fetch data
        LarkAPI-->>Lambda: Records
        Lambda-->>Athena: Results
    end

    Note over Admin,LarkAPI: ✅ Auto-updates (on restart)<br/>❌ Cold start penalty
```

### Flow 3: Lark Drive Source

```mermaid
graph TB
    subgraph "Lark Drive Structure"
        Root[Root Folder<br/>athena_tables/]
        DB1[Folder: sales_data<br/>→ Database]
        DB2[Folder: inventory<br/>→ Database]
        T1[File: orders<br/>Lark Base link]
        T2[File: customers<br/>Lark Base link]
        T3[File: stock<br/>Lark Base link]

        Root --> DB1
        Root --> DB2
        DB1 --> T1
        DB1 --> T2
        DB2 --> T3
    end

    subgraph "Discovery Process"
        Discover[Lambda Initialization]
        ListFolders[List Folders<br/>→ Databases]
        ListFiles[List Files<br/>→ Tables]
        Extract[Extract base_id<br/>table_id from links]
        GetSchema[Get field schema<br/>from Lark API]
        Cache[Cache in Memory]
    end

    subgraph "Query Time"
        Query[Athena Query]
        MemLookup[Memory Lookup]
        FetchData[Fetch Data]
    end

    Root -.->|Scan| Discover
    Discover --> ListFolders
    ListFolders --> ListFiles
    ListFiles --> Extract
    Extract --> GetSchema
    GetSchema --> Cache

    Query --> MemLookup
    MemLookup --> FetchData

    style Root fill:#e1f5ff
    style DB1 fill:#fff3cd
    style DB2 fill:#fff3cd
    style T1 fill:#d4edda
    style T2 fill:#d4edda
    style T3 fill:#d4edda
    style Cache fill:#4CAF50
```

### Flow 4: Experimental Provider

```mermaid
sequenceDiagram
    participant Athena as Athena Query
    participant Connector as Athena Connector
    participant AthenaCatalog as Athena Catalog<br/>Metadata Table
    participant LarkAPI as Lark Base API

    rect rgb(240, 220, 220)
        Note over Athena,LarkAPI: Every Query (Dynamic)
        Athena->>Connector: 1. SELECT * FROM table

        Connector->>AthenaCatalog: 2. Query metadata table<br/>SELECT base_id, table_id<br/>WHERE db=? AND table=?
        AthenaCatalog-->>Connector: IDs returned

        Connector->>LarkAPI: 3. Get table schema<br/>(Fresh from API)
        LarkAPI-->>Connector: Field definitions

        Note over Connector: 4. Build schema<br/>dynamically<br/>(NOT cached)

        Connector->>LarkAPI: 5. Fetch data
        LarkAPI-->>Connector: Records
        Connector-->>Athena: Results
    end

    Note over Athena,LarkAPI: ✅ Real-time schema<br/>❌ Higher latency
```

---

## 3. Query Execution Flow

### Complete Query Lifecycle

```mermaid
graph TB
    Start[User: SELECT * FROM table<br/>WHERE status='active'<br/>ORDER BY date DESC<br/>LIMIT 100]

    subgraph "Phase 1: Metadata"
        GetLayout[GetTableLayout]
        GetSchema[Get Table Schema]
        GetPartitions[getPartitions]
        TranslateFilter[Translate WHERE<br/>to Lark filter JSON]
        TranslateSort[Translate ORDER BY<br/>to Lark sort JSON]
        EstimateRows[Estimate Row Count<br/>via Lark API]
        CreatePartitions[Create Partition Block]
        GetSplits[GetSplits]
        CreateSplits[Create Split Objects]
    end

    subgraph "Phase 2: Data Reading"
        ReadRecords[ReadRecords<br/>for each split]
        GetIterator[Create Record Iterator]
        FetchPage1[Fetch Page 1<br/>500 records]
        FetchPage2[Fetch Page 2<br/>500 records]
        FetchPageN[Fetch Page N<br/>until complete]
        TypeConvert[Type Conversion<br/>Lark → Arrow]
        WriteBlocks[Write to Arrow Blocks]
    end

    Result[Return Results to Athena]

    Start --> GetLayout
    GetLayout --> GetSchema
    GetSchema --> GetPartitions
    GetPartitions --> TranslateFilter
    TranslateFilter --> TranslateSort
    TranslateSort --> EstimateRows
    EstimateRows --> CreatePartitions
    CreatePartitions --> GetSplits
    GetSplits --> CreateSplits

    CreateSplits --> ReadRecords
    ReadRecords --> GetIterator
    GetIterator --> FetchPage1
    FetchPage1 --> TypeConvert
    TypeConvert --> WriteBlocks
    WriteBlocks --> FetchPage2
    FetchPage2 --> WriteBlocks
    WriteBlocks --> FetchPageN
    FetchPageN --> WriteBlocks

    WriteBlocks --> Result

    style Start fill:#e1f5ff
    style GetPartitions fill:#fff3cd
    style TranslateFilter fill:#d4edda
    style TranslateSort fill:#d4edda
    style ReadRecords fill:#f8d7da
    style Result fill:#4CAF50
```

### Filter Pushdown Translation

```mermaid
graph LR
    subgraph "Athena SQL"
        SQL[WHERE status = 'active'<br/>AND age > 18<br/>AND category IN 'A', 'B']
    end

    subgraph "Athena Constraints"
        C1[EquatableValueSet<br/>status: 'active']
        C2[SortedRangeSet<br/>age: > 18]
        C3[EquatableValueSet<br/>category: IN 'A','B']
    end

    subgraph "Translator"
        Map[Map Athena field names<br/>to Lark field names]
        Convert[Convert constraint types<br/>to Lark operators]
        Build[Build JSON structure]
    end

    subgraph "Lark API Filter JSON"
        JSON["{\n  'conjunction': 'and',\n  'conditions': [\n    {field_name: 'Status',\n     operator: 'is',\n     value: ['active']},\n    {field_name: 'Age',\n     operator: 'isGreater',\n     value: ['18']},\n    {field_name: 'Category',\n     operator: 'is',\n     value: ['A']},\n    {field_name: 'Category',\n     operator: 'is',\n     value: ['B']}\n  ]\n}"]
    end

    SQL --> C1
    SQL --> C2
    SQL --> C3

    C1 --> Map
    C2 --> Map
    C3 --> Map

    Map --> Convert
    Convert --> Build
    Build --> JSON

    style SQL fill:#e1f5ff
    style JSON fill:#d4edda
```

---

## 4. Class Hierarchy

### Handler Classes

```mermaid
classDiagram
    class CompositeHandler {
        <<AWS SDK>>
        #metadataHandler
        #recordHandler
    }

    class BaseCompositeHandler {
        +BaseCompositeHandler()
        Entry point for Lambda
    }

    class GlueMetadataHandler {
        <<AWS SDK>>
        #glueClient
        +doListSchemaNames()
        +doListTables()
        +doGetTable()
        +doGetSplits()
    }

    class BaseMetadataHandler {
        -envVarService
        -larkBaseService
        -glueCatalogService
        -larkSourceMetadataProvider
        -experimentalMetadataProvider
        +doListSchemaNames()
        +doListTables()
        +doGetTable()
        +getPartitions()
        +doGetSplits()
        +doGetDataSourceCapabilities()
    }

    class RecordHandler {
        <<AWS SDK>>
        #readWithConstraint()
    }

    class BaseRecordHandler {
        -envVarService
        -larkBaseService
        -invokerCache
        #readWithConstraint()
        #getIterator()
        #writeItemsToBlock()
    }

    CompositeHandler <|-- BaseCompositeHandler
    BaseCompositeHandler *-- BaseMetadataHandler
    BaseCompositeHandler *-- BaseRecordHandler
    GlueMetadataHandler <|-- BaseMetadataHandler
    RecordHandler <|-- BaseRecordHandler
```

### Service Layer

```mermaid
classDiagram
    class CommonLarkService {
        #larkAppId
        #larkAppSecret
        #tenantAccessToken
        #httpClient
        +refreshTenantAccessToken()
    }

    class LarkBaseService {
        -tableFieldsCache
        +getDatabaseRecords()
        +getTableRecords()
        +getTableFields()
        +listTables()
        +getLookupType()
    }

    class LarkDriveService {
        +listFolders()
        +getFolder()
    }

    class GlueCatalogService {
        -glueClient
        +getDatabase()
        +getTable()
        +createTable()
        +updateTable()
        +getLarkBaseAndTableId()
        +getFieldNameMappings()
    }

    class EnvVarService {
        -configOptions
        -secretValue
        +getLarkAppId()
        +getLarkAppSecret()
        +isActivateLarkBaseSource()
        +isActivateParallelSplit()
    }

    class HttpClientWrapper {
        -httpClient
        +execute()
        +close()
    }

    CommonLarkService <|-- LarkBaseService
    CommonLarkService <|-- LarkDriveService
    CommonLarkService *-- HttpClientWrapper

    BaseMetadataHandler --> EnvVarService
    BaseMetadataHandler --> LarkBaseService
    BaseMetadataHandler --> GlueCatalogService
    BaseRecordHandler --> LarkBaseService
```

### Metadata Provider Pattern

```mermaid
classDiagram
    class MetadataProvider {
        <<interface>>
        +getTableSchema() Optional~TableSchemaResult~
        +getPartitionInfo() Optional~PartitionInfoResult~
    }

    class LarkSourceMetadataProvider {
        -tableDirectInitialized
        +getTableSchema()
        +getPartitionInfo()
        -findTableMapping()
    }

    class ExperimentalMetadataProvider {
        -athenaService
        -larkBaseService
        -invoker
        +getTableSchema()
        +getPartitionInfo()
        -queryMetadataTable()
        -fetchSchemaFromLark()
    }

    class BaseMetadataHandler {
        -larkSourceMetadataProvider
        -experimentalMetadataProvider
        +doGetTable()
    }

    MetadataProvider <|.. LarkSourceMetadataProvider
    MetadataProvider <|.. ExperimentalMetadataProvider
    BaseMetadataHandler --> LarkSourceMetadataProvider
    BaseMetadataHandler --> ExperimentalMetadataProvider

    note for BaseMetadataHandler "Strategy Pattern:\n1. Try LarkSourceMetadataProvider\n2. Try ExperimentalMetadataProvider\n3. Fallback to Glue"
```

---

## 5. Sequence Diagrams

### Query Execution Overview

```mermaid
sequenceDiagram
    participant User
    participant Athena
    participant Metadata as BaseMetadataHandler
    participant Record as BaseRecordHandler
    participant Lark as Lark API

    User->>Athena: SELECT * FROM table<br/>WHERE status='active'

    rect rgb(240, 248, 255)
        Note over Athena,Metadata: Phase 1: Metadata
        Athena->>Metadata: GetTableLayout
        Metadata->>Metadata: Translate WHERE to filter JSON
        Metadata->>Lark: Estimate row count
        Lark-->>Metadata: total: 5000
        Metadata-->>Athena: Partition block

        Athena->>Metadata: GetSplits
        Metadata->>Metadata: Create splits from partitions
        Metadata-->>Athena: Split objects
    end

    rect rgb(240, 255, 240)
        Note over Athena,Lark: Phase 2: Data Reading
        Athena->>Record: ReadRecords (split)

        loop For each page
            Record->>Lark: Fetch page (500 records)
            Lark-->>Record: Records
            Record->>Record: Convert types<br/>Lark → Arrow
            Record->>Record: Write to Arrow blocks
        end

        Record-->>Athena: Arrow blocks
    end

    Athena-->>User: Result set
```

### Metadata Discovery (Strategy Pattern)

```mermaid
sequenceDiagram
    participant Athena
    participant Handler as BaseMetadataHandler
    participant Provider1 as LarkSourceMetadataProvider
    participant Provider2 as ExperimentalMetadataProvider
    participant Glue as AWS Glue
    participant Lark as Lark API

    Athena->>Handler: doGetTable(database, table)

    rect rgb(230, 255, 230)
        Note over Handler,Provider1: Try Provider 1
        Handler->>Provider1: getTableSchema()
        Provider1->>Provider1: Search in-memory cache

        alt Schema found in cache
            Provider1-->>Handler: Schema (cached)
            Handler-->>Athena: Return schema ✓
        else Not found
            Provider1-->>Handler: Empty
        end
    end

    rect rgb(255, 243, 205)
        Note over Handler,Lark: Try Provider 2
        Handler->>Provider2: getTableSchema()
        Provider2->>Provider2: Query Athena catalog
        Provider2->>Lark: Get table fields
        Lark-->>Provider2: Field definitions
        Provider2->>Provider2: Build schema dynamically

        alt Schema built
            Provider2-->>Handler: Schema (dynamic)
            Handler-->>Athena: Return schema ✓
        else Failed
            Provider2-->>Handler: Empty
        end
    end

    rect rgb(248, 215, 218)
        Note over Handler,Glue: Fallback to Glue
        Handler->>Glue: getTable()
        Glue-->>Handler: Table metadata
        Handler-->>Athena: Return schema ✓
    end
```

### Data Reading with Pagination

```mermaid
sequenceDiagram
    participant Athena
    participant Handler as BaseRecordHandler
    participant Iterator
    participant Lark as Lark API

    Athena->>Handler: ReadRecords(split)

    Handler->>Handler: Extract split properties:<br/>base_id, table_id, filter, sort

    Handler->>Iterator: Create iterator
    Iterator->>Iterator: Initialize:<br/>pageToken = null<br/>hasMore = true

    loop While hasNext()
        Handler->>Iterator: hasNext()?

        alt Current page exhausted
            Iterator->>Lark: POST /search<br/>{pageSize:500, pageToken, filter}
            Lark-->>Iterator: 500 records + new pageToken
            Iterator->>Iterator: Update state
        end

        Iterator-->>Handler: true

        Handler->>Iterator: next()
        Iterator-->>Handler: Record + reserved fields

        Handler->>Handler: Type conversion<br/>Lark → Arrow
        Handler->>Handler: Write to Arrow block
    end

    Handler->>Iterator: hasNext()?
    Iterator-->>Handler: false (no more pages)

    Handler-->>Athena: Arrow blocks (all data)
```

---

## 6. Component Interaction

### Complete System Interaction

```mermaid
graph TB
    subgraph "User Layer"
        User[Analyst/Data Engineer]
    end

    subgraph "AWS Athena"
        Query[SQL Query]
        Engine[Query Engine]
    end

    subgraph "Lambda - Athena Connector"
        Entry[BaseCompositeHandler]

        subgraph "Metadata Handler"
            ListDB[List Databases]
            ListTbl[List Tables]
            GetSchema[Get Schema]
            GetPart[Get Partitions]
            GetSplit[Get Splits]
        end

        subgraph "Record Handler"
            ReadData[Read Records]
            Paginate[Pagination Loop]
            Convert[Type Conversion]
        end

        subgraph "Services"
            LarkSvc[LarkBaseService]
            GlueSvc[GlueCatalogService]
            EnvSvc[EnvVarService]
        end

        subgraph "Translators"
            FilterTrans[Filter Translator]
            TypeExtract[Type Extractor]
        end
    end

    subgraph "Lambda - Crawler"
        CrawlerEntry[MainCrawlerHandler]
        Discovery[Table Discovery]
        SchemaMap[Schema Mapping]
    end

    subgraph "AWS Services"
        Glue[AWS Glue Catalog]
        Secrets[Secrets Manager]
        S3[S3 Spill Bucket]
    end

    subgraph "External API"
        LarkAuth[Lark Auth]
        LarkBitable[Lark Bitable API]
        LarkDrive[Lark Drive API]
    end

    User -->|SQL| Query
    Query --> Engine
    Engine -->|Federated| Entry

    Entry --> ListDB
    Entry --> GetSchema
    Entry --> GetPart
    Entry --> GetSplit
    Entry --> ReadData

    ListDB --> LarkSvc
    ListDB --> GlueSvc
    GetSchema --> LarkSvc
    GetSchema --> GlueSvc
    GetPart --> FilterTrans
    GetSplit --> FilterTrans

    ReadData --> Paginate
    Paginate --> LarkSvc
    Paginate --> Convert
    Convert --> TypeExtract

    FilterTrans --> LarkSvc

    LarkSvc --> EnvSvc
    GlueSvc --> Glue
    EnvSvc --> Secrets

    LarkSvc --> LarkAuth
    LarkSvc --> LarkBitable

    CrawlerEntry --> Discovery
    Discovery --> LarkBitable
    Discovery --> LarkDrive
    Discovery --> SchemaMap
    SchemaMap --> Glue

    ReadData -.->|Large Results| S3

    style User fill:#e1f5ff
    style Engine fill:#ff9900
    style Entry fill:#4CAF50
    style ReadData fill:#4CAF50
    style LarkBitable fill:#00d4aa
    style Glue fill:#ff9900
```

### Parallel Split Execution

```mermaid
graph TB
    Query[Query: SELECT *<br/>5000 total rows<br/>PAGE_SIZE=500]

    subgraph "getPartitions"
        Detect[Detect $reserved_split_key]
        Calculate[Calculate splits:<br/>10 splits of 500 rows each]
        WritePart[Write 10 partition rows]
    end

    subgraph "doGetSplits"
        ReadPart[Read 10 partition rows]
        CreateSplit[Create 10 split objects]
    end

    subgraph "Parallel Execution"
        Split1[Split 1: rows 1-500]
        Split2[Split 2: rows 501-1000]
        Split3[Split 3: rows 1001-1500]
        SplitN[Split 10: rows 4501-5000]

        Lambda1[Lambda Instance 1]
        Lambda2[Lambda Instance 2]
        Lambda3[Lambda Instance 3]
        LambdaN[Lambda Instance N]
    end

    subgraph "Lark API"
        API1[Filter: $key >= 1<br/>AND $key <= 500]
        API2[Filter: $key >= 501<br/>AND $key <= 1000]
        API3[Filter: $key >= 1001<br/>AND $key <= 1500]
        APIN[Filter: $key >= 4501<br/>AND $key <= 5000]
    end

    Merge[Athena merges results]

    Query --> Detect
    Detect --> Calculate
    Calculate --> WritePart
    WritePart --> ReadPart
    ReadPart --> CreateSplit

    CreateSplit --> Split1
    CreateSplit --> Split2
    CreateSplit --> Split3
    CreateSplit --> SplitN

    Split1 --> Lambda1
    Split2 --> Lambda2
    Split3 --> Lambda3
    SplitN --> LambdaN

    Lambda1 --> API1
    Lambda2 --> API2
    Lambda3 --> API3
    LambdaN --> APIN

    API1 --> Merge
    API2 --> Merge
    API3 --> Merge
    APIN --> Merge

    style Query fill:#e1f5ff
    style Lambda1 fill:#4CAF50
    style Lambda2 fill:#4CAF50
    style Lambda3 fill:#4CAF50
    style LambdaN fill:#4CAF50
    style Merge fill:#ff9900
```

---

## 📝 Notes

### Editing Diagrams

1. **In Diagrams.io**:
   - Copy the Mermaid code
   - Arrange → Insert → Advanced → Mermaid
   - Edit the code and click "Insert"
   - The diagram will be editable as a diagram.io object

2. **Color Schemes**:
   - Blue (#e1f5ff): User/Input
   - Orange (#ff9900): AWS Services
   - Green (#4CAF50): Connector Components
   - Teal (#00d4aa): External APIs
   - Yellow (#fff3cd): Warning/Important
   - Red (#f8d7da): Critical path

3. **Export Options**:
   - PNG: For documentation
   - SVG: For scalable graphics
   - PDF: For reports
   - XML: For sharing diagrams.io files

### Best Practices

- **Keep it simple**: Don't overcomplicate diagrams
- **Use colors consistently**: Same color = same type of component
- **Add notes**: Explain complex parts
- **Update regularly**: Keep diagrams in sync with code

---

## 🔗 Related Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - Detailed architecture description
- [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) - Flow comparison and setup
- [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md) - ASCII sequence diagrams
- [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) - Class structure details

---

**Last Updated**: 2025-01-13
**Format**: Mermaid (Compatible with GitHub, GitLab, Diagrams.io)
