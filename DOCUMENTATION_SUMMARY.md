# Documentation Summary - AWS Athena Lark Base Connector

## Overview

This project provides a comprehensive AWS Athena Federation connector that enables SQL queries against Lark Base (Feishu Bitable) data. The connector consists of two main components:

1. **Athena Connector** (`athena-lark-base`): Executes federated queries
2. **Glue Crawler** (`glue-lark-base-crawler`): Discovers and registers table metadata

## Documentation Structure

### 0. [DIAGRAMS.md](./DIAGRAMS.md) 🎨 **VISUAL DIAGRAMS**

**Purpose**: Interactive visual diagrams in Mermaid format

**Contents**:
- System architecture diagrams
- Metadata discovery flow comparisons
- Query execution flows
- Class hierarchy diagrams
- Sequence diagrams
- Component interactions

**Best for**:
- Visual learners
- Presentations and reports
- Quick understanding of system design
- Importing to Diagrams.io (draw.io)
- Embedding in documentation sites

**How to Use**:
- View directly in GitHub (auto-rendered)
- Import to Diagrams.io for editing
- Export to PNG/SVG/PDF
- See [DIAGRAMS_IMPORT_GUIDE.md](./DIAGRAMS_IMPORT_GUIDE.md) for instructions

**Key Sections**:
- Architecture Overview
- Flow Comparison Decision Tree
- Complete Query Lifecycle
- Parallel Split Execution

---

### 1. [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) ⭐ **START HERE**

**Purpose**: Understand different metadata discovery strategies and choose the right one

**Contents**:
- Overview of 4 different metadata discovery flows
- Detailed comparison matrix
- Flow 1: Glue Catalog + Crawler (traditional)
- Flow 2: Lark Base Source (direct discovery)
- Flow 3: Lark Drive Source (folder-based)
- Flow 4: Experimental Provider (dynamic)
- Hybrid flows combining multiple approaches
- Decision tree for choosing the right flow
- Migration guide between flows

**Best for**:
- **First-time users**: Understand which deployment model to use
- Choosing between crawler vs direct discovery
- Understanding trade-offs of each approach
- Planning production deployment
- Migrating between different approaches

**Key Sections**:
- Flow Comparison (quick decision matrix)
- Flow Selection Guide (decision tree)
- Detailed setup steps for each flow
- Pros/cons analysis
- When to use each flow

### 2. [DIAGRAMS_IMPORT_GUIDE.md](./DIAGRAMS_IMPORT_GUIDE.md) 📖 **HOW TO USE DIAGRAMS**

**Purpose**: Step-by-step guide for using visual diagrams

**Contents**:
- How to view diagrams in GitHub/GitLab
- How to import to Diagrams.io (draw.io)
- How to export to PNG/SVG/PDF
- How to embed in documentation sites
- Troubleshooting tips

**Best for**:
- Users who want to edit diagrams
- Creating presentations
- Exporting for reports
- Embedding in other tools

---

### 3. [ARCHITECTURE.md](./ARCHITECTURE.md)

**Purpose**: High-level system architecture and component descriptions

**Contents**:
- Complete architecture diagram showing all system components
- Module structure for both athena-lark-base and glue-lark-base-crawler
- Detailed flow documentation for:
  - Metadata operations (list schemas, list tables, get schema)
  - Record retrieval (pagination, type conversion)
  - Crawler operations (table discovery, Glue registration)
- Core component descriptions with responsibilities
- Type system mappings (Lark → Arrow, Lark → Glue)
- Query optimization strategies (filter pushdown, LIMIT, TOP-N)
- Configuration guide (environment variables, secrets)
- Deployment instructions (step-by-step Lambda setup)

**Best for**:
- Understanding the overall system design
- Learning how components interact
- Setting up the connector for the first time
- Understanding query optimization capabilities

**Key Sections**:
- Architecture Diagram (visual overview)
- Metadata Flow (how schemas are discovered)
- Record Retrieval Flow (how data is fetched)
- Core Components (detailed component descriptions)
- Type System (field type mappings)
- Configuration (environment variables, secrets)
- Deployment (step-by-step guide)

---

### 4. [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md)

**Purpose**: Detailed class structure and relationships

**Contents**:
- Handler class hierarchy (CompositeHandler → MetadataHandler → RecordHandler)
- Service layer architecture (LarkBaseService, LarkDriveService, GlueCatalogService, etc.)
- Model layer (requests, responses, domain models, enums)
- Metadata Provider Pattern (strategy pattern for schema resolution)
- Translator components (filter translation, type extraction)
- Resolver components (table discovery, schema building)
- Crawler components (discovery, registration)
- Utility components (type utils, common utils)

**Best for**:
- Understanding code organization
- Learning class responsibilities
- Identifying dependencies between classes
- Extending or modifying the codebase
- Understanding design patterns used

**Key Sections**:
- Handler Class Hierarchy (main entry points)
- Service Layer Architecture (API communication)
- Model Layer (data structures)
- Metadata Provider Pattern (strategy pattern)
- Translator Components (constraint translation)
- Resolver Components (schema discovery)

---

### 5. [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)

**Purpose**: Step-by-step execution flows

**Contents**:
- Query execution overview (complete lifecycle)
- Metadata discovery flow (list schemas, list tables)
- Schema retrieval flow (strategy pattern in action)
- Partition planning flow (filter translation, row count estimation)
- Split generation flow (partition to split conversion)
- Data reading flow (pagination, record processing)
- Filter pushdown flow (SQL to Lark API translation)
- Crawler execution flow (table discovery and registration)

**Best for**:
- Understanding execution order
- Debugging issues
- Tracing data flow through the system
- Understanding API call patterns
- Learning how query optimization works

**Key Sections**:
- Query Execution Overview (complete flow)
- Partition Planning Flow (filter translation)
- Data Reading Flow (pagination details)
- Filter Pushdown Flow (constraint translation)
- Crawler Execution Flow (table discovery)

---

## Quick Start Guide

### For Users (Setting up the connector)

1. **START HERE**: [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md)
   - **CRITICAL**: Choose your metadata discovery flow
   - Understand the 4 different approaches
   - Use the decision tree to pick the right one
   - Flow 1 (Glue + Crawler): Production, stable schemas
   - Flow 2 (Lark Base Source): Development, dynamic schemas
   - Flow 3 (Lark Drive): Folder-based organization
   - Flow 4 (Experimental): Testing, exploration

2. **Next**: Follow the setup steps for your chosen flow
   - Each flow has detailed step-by-step instructions in METADATA_DISCOVERY_FLOWS.md
   - Configuration examples provided
   - Environment variable setup

3. **Then**: [ARCHITECTURE.md#Configuration](./ARCHITECTURE.md#configuration)
   - Understand additional configuration options
   - Set up AWS Secrets Manager
   - Configure query optimizations

4. **Finally**: [ARCHITECTURE.md#Deployment](./ARCHITECTURE.md#deployment)
   - Review deployment best practices
   - Execute queries
   - Monitor performance

### For Developers (Understanding the codebase)

1. **Start with**: [ARCHITECTURE.md#Architecture-Diagram](./ARCHITECTURE.md#architecture-diagram)
   - Get high-level understanding
   - Identify major components

2. **Next**: [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md)
   - Understand class organization
   - Learn component responsibilities

3. **Then**: [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)
   - Trace execution flows
   - Understand component interactions

4. **Deep dive**: Read specific class implementations
   - `BaseMetadataHandler.java` (metadata operations)
   - `BaseRecordHandler.java` (data reading)
   - `SearchApiFilterTranslator.java` (filter pushdown)
   - `LarkBaseService.java` (API communication)

### For Contributors (Extending functionality)

1. **Start with**: [CLASS_DIAGRAMS.md#Core-Components](./CLASS_DIAGRAMS.md#core-components)
   - Identify where to make changes

2. **Understand patterns**:
   - Metadata Provider Pattern: Adding new schema sources
   - Translator Pattern: Adding new constraint types
   - Resolver Pattern: Changing table discovery logic

3. **Reference**: [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)
   - Understand how your changes fit into execution flow

4. **Test**: Follow testing patterns in existing code
   - Unit tests for individual components
   - Integration tests for end-to-end flows

---

## Common Use Cases

### 1. Adding a New Lark Field Type

**Files to modify**:
- `UITypeEnum.java`: Add enum value
- `LarkBaseFieldResolver.java`: Add Arrow type mapping
- `LarkBaseTypeUtils.java`: Add type utility methods
- `RegistererExtractor.java`: Add extractor implementation

**Reference**:
- [CLASS_DIAGRAMS.md#Type-System](./CLASS_DIAGRAMS.md#type-system)
- [ARCHITECTURE.md#Type-System](./ARCHITECTURE.md#type-system)

### 2. Adding Support for New SQL Operators

**Files to modify**:
- `SearchApiFilterTranslator.java`: Add translation logic
- `BaseMetadataHandler.java`: Update capabilities (if needed)

**Reference**:
- [SEQUENCE_DIAGRAMS.md#Filter-Pushdown-Flow](./SEQUENCE_DIAGRAMS.md#filter-pushdown-flow)
- [CLASS_DIAGRAMS.md#Translator-Components](./CLASS_DIAGRAMS.md#translator-components)

### 3. Implementing New Metadata Discovery Method

**Files to create**:
- New class implementing metadata provider interface
- Add to `BaseMetadataHandler` initialization

**Reference**:
- [CLASS_DIAGRAMS.md#Metadata-Provider-Pattern](./CLASS_DIAGRAMS.md#metadata-provider-pattern)
- [SEQUENCE_DIAGRAMS.md#Schema-Retrieval-Flow](./SEQUENCE_DIAGRAMS.md#schema-retrieval-flow)

### 4. Adding New Crawler Source

**Files to modify**:
- Create new handler extending `BaseLarkBaseCrawlerHandler`
- Update `MainLarkBaseCrawlerHandler` routing

**Reference**:
- [CLASS_DIAGRAMS.md#Crawler-Components](./CLASS_DIAGRAMS.md#crawler-components)
- [SEQUENCE_DIAGRAMS.md#Crawler-Execution-Flow](./SEQUENCE_DIAGRAMS.md#crawler-execution-flow)

### 5. Optimizing Query Performance

**Areas to investigate**:
- Partition strategy (`BaseMetadataHandler.getPartitions`)
- Caching (`LarkBaseService` field cache)
- Pagination (`BaseRecordHandler.getIterator`)
- Filter translation (`SearchApiFilterTranslator`)

**Reference**:
- [ARCHITECTURE.md#Query-Optimizations](./ARCHITECTURE.md#query-optimizations)
- [ARCHITECTURE.md#Performance-Considerations](./ARCHITECTURE.md#performance-considerations)

---

## Key Concepts

### 1. Metadata Provider Pattern

**What**: Strategy pattern for resolving table schemas from different sources

**Why**: Allows flexible schema resolution without modifying core handler logic

**Where**: `BaseMetadataHandler.doGetTable()`

**Reference**:
- [CLASS_DIAGRAMS.md#Metadata-Provider-Pattern](./CLASS_DIAGRAMS.md#metadata-provider-pattern)
- [SEQUENCE_DIAGRAMS.md#Schema-Retrieval-Flow](./SEQUENCE_DIAGRAMS.md#schema-retrieval-flow)

### 2. Filter Pushdown

**What**: Translating SQL WHERE clauses to Lark Search API filters

**Why**: Reduces data transfer by filtering at the source

**Where**: `SearchApiFilterTranslator.toFilterJson()`

**Reference**:
- [ARCHITECTURE.md#Filter-Pushdown](./ARCHITECTURE.md#query-optimizations)
- [SEQUENCE_DIAGRAMS.md#Filter-Pushdown-Flow](./SEQUENCE_DIAGRAMS.md#filter-pushdown-flow)

### 3. Parallel Splits

**What**: Dividing large tables into multiple concurrent reads

**Why**: Improves query performance through parallel Lambda execution

**Where**: `BaseMetadataHandler.writeParallelPartitions()`

**Reference**:
- [ARCHITECTURE.md#Parallel-Split-Execution](./ARCHITECTURE.md#query-optimizations)
- [SEQUENCE_DIAGRAMS.md#Parallel-Partition-Strategy](./SEQUENCE_DIAGRAMS.md#partition-planning-flow)

### 4. Type Extraction

**What**: Converting Lark data types to Apache Arrow types during record writing

**Why**: Ensures correct data representation in Athena

**Where**: `RegistererExtractor.registerExtractorsForSchema()`

**Reference**:
- [CLASS_DIAGRAMS.md#RegistererExtractor](./CLASS_DIAGRAMS.md#translator-components)
- [ARCHITECTURE.md#Type-System](./ARCHITECTURE.md#type-system)

### 5. Pagination

**What**: Fetching records page-by-page from Lark API

**Why**: Handles large datasets without memory issues

**Where**: `BaseRecordHandler.getIterator()`

**Reference**:
- [SEQUENCE_DIAGRAMS.md#Data-Reading-Flow](./SEQUENCE_DIAGRAMS.md#data-reading-flow)
- [ARCHITECTURE.md#Record-Retrieval-Flow](./ARCHITECTURE.md#record-retrieval-flow)

---

## Architecture Highlights

### 1. Two-Phase Query Execution

**Phase 1: Metadata (BaseMetadataHandler)**
```
Athena Query
    ↓
List Schemas → List Tables → Get Schema
    ↓
Get Partitions (translate filters, estimate rows)
    ↓
Get Splits (create execution units)
    ↓
Return to Athena
```

**Phase 2: Data Reading (BaseRecordHandler)**
```
For each Split:
    ↓
Read properties (base_id, table_id, filter, sort)
    ↓
Fetch records page-by-page from Lark API
    ↓
Convert types and write to Arrow blocks
    ↓
Return to Athena
```

### 2. Layered Service Architecture

```
Handlers (BaseMetadataHandler, BaseRecordHandler)
    ↓
Services (LarkBaseService, GlueCatalogService)
    ↓
HTTP Client / AWS SDK
    ↓
External APIs (Lark API, AWS Glue)
```

### 3. Strategy Pattern for Schema Resolution

```
BaseMetadataHandler.doGetTable()
    ↓
Try Provider 1: LarkSourceMetadataProvider (cached)
    ↓ (if not found)
Try Provider 2: ExperimentalMetadataProvider (dynamic)
    ↓ (if not found)
Fallback: Glue Data Catalog
```

### 4. Constraint Translation Pipeline

```
Athena SQL WHERE clause
    ↓
Athena Constraints (ValueSet objects)
    ↓
SearchApiFilterTranslator.toFilterJson()
    ↓
Lark Search API Filter JSON
    ↓
Passed to Lark API in request body
```

---

## Important Files Reference

### Entry Points
| File | Purpose | Documentation |
|------|---------|---------------|
| `BaseCompositeHandler.java` | Lambda entry point for Athena | [CLASS_DIAGRAMS.md#BaseCompositeHandler](./CLASS_DIAGRAMS.md#handler-class-hierarchy) |
| `MainLarkBaseCrawlerHandler.java` | Lambda entry point for Crawler | [CLASS_DIAGRAMS.md#Crawler-Components](./CLASS_DIAGRAMS.md#crawler-components) |

### Core Handlers
| File | Purpose | Documentation |
|------|---------|---------------|
| `BaseMetadataHandler.java` | Metadata operations | [ARCHITECTURE.md#BaseMetadataHandler](./ARCHITECTURE.md#core-components), [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md#basemetadatahandler) |
| `BaseRecordHandler.java` | Data reading | [ARCHITECTURE.md#BaseRecordHandler](./ARCHITECTURE.md#core-components), [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md#data-reading-flow) |

### Services
| File | Purpose | Documentation |
|------|---------|---------------|
| `LarkBaseService.java` | Lark API client | [CLASS_DIAGRAMS.md#Service-Layer](./CLASS_DIAGRAMS.md#service-layer-architecture) |
| `GlueCatalogService.java` | Glue operations | [CLASS_DIAGRAMS.md#Service-Layer](./CLASS_DIAGRAMS.md#service-layer-architecture) |

### Translators
| File | Purpose | Documentation |
|------|---------|---------------|
| `SearchApiFilterTranslator.java` | Constraint translation | [SEQUENCE_DIAGRAMS.md#Filter-Pushdown](./SEQUENCE_DIAGRAMS.md#filter-pushdown-flow) |
| `RegistererExtractor.java` | Type extraction | [CLASS_DIAGRAMS.md#RegistererExtractor](./CLASS_DIAGRAMS.md#translator-components) |

### Resolvers
| File | Purpose | Documentation |
|------|---------|---------------|
| `LarkBaseTableResolver.java` | Table discovery | [CLASS_DIAGRAMS.md#LarkBaseTableResolver](./CLASS_DIAGRAMS.md#resolver-components) |
| `LarkBaseFieldResolver.java` | Schema building | [CLASS_DIAGRAMS.md#LarkBaseFieldResolver](./CLASS_DIAGRAMS.md#resolver-components) |

---

## Testing Guide

### Unit Testing

**Key test classes**:
- `BaseMetadataHandlerTest`: Metadata operations
- `BaseRecordHandlerTest`: Record reading
- `SearchApiFilterTranslatorTest`: Filter translation
- `LarkBaseServiceTest`: API client

**Testing patterns**:
```java
// Mock dependencies
@Mock
private LarkBaseService mockLarkBaseService;

@Mock
private GlueCatalogService mockGlueCatalogService;

// Inject mocks
@InjectMocks
private BaseMetadataHandler handler;

// Test specific behavior
@Test
public void testFilterTranslation() {
    // Arrange
    Map<String, ValueSet> constraints = ...;

    // Act
    String result = SearchApiFilterTranslator.toFilterJson(constraints, mappings);

    // Assert
    assertThat(result).contains("\"operator\":\"is\"");
}
```

### Integration Testing

**Setup**:
1. Set up test Lark Base with known data
2. Configure test environment variables
3. Run end-to-end tests

**Test scenarios**:
- List schemas from Glue
- Get table schema
- Execute query with filters
- Verify data correctness

### Manual Testing

**Using Athena Console**:
```sql
-- Test schema discovery
SHOW DATABASES IN lark_base;

-- Test table discovery
SHOW TABLES IN lark_base.test_db;

-- Test schema retrieval
DESCRIBE lark_base.test_db.test_table;

-- Test data reading
SELECT * FROM lark_base.test_db.test_table LIMIT 10;

-- Test filter pushdown (check CloudWatch logs for filter JSON)
SELECT * FROM lark_base.test_db.test_table
WHERE status = 'active';

-- Test TOP-N pushdown
SELECT * FROM lark_base.test_db.test_table
ORDER BY created_date DESC LIMIT 100;
```

---

## Troubleshooting Guide

### Issue: "Unable to retrieve table schema"

**Check**:
1. Does table exist in Glue? Run `aws glue get-table --database-name X --name Y`
2. Is Lark source enabled? Check `ACTIVATE_LARK_BASE_SOURCE` environment variable
3. Are Lark credentials correct? Check Secrets Manager

**Reference**: [SEQUENCE_DIAGRAMS.md#Schema-Retrieval-Flow](./SEQUENCE_DIAGRAMS.md#schema-retrieval-flow)

### Issue: "No mapping found for column"

**Check**:
1. Column parameters in Glue table metadata
2. Field name sanitization (lowercase, special characters)
3. Re-run crawler to update metadata

**Reference**: [ARCHITECTURE.md#Glue-Column-Parameters](./ARCHITECTURE.md#configuration)

### Issue: Query returns no results but data exists

**Check**:
1. CloudWatch logs for filter translation
2. Verify filter pushdown is working correctly
3. Check if field types support pushdown
4. Test without WHERE clause

**Reference**: [SEQUENCE_DIAGRAMS.md#Filter-Pushdown-Flow](./SEQUENCE_DIAGRAMS.md#filter-pushdown-flow)

### Issue: Query timeout

**Check**:
1. Lambda timeout configuration (should be 900s)
2. Lambda memory configuration (should be 3008 MB)
3. Enable parallel splits for large tables
4. Check Lark API rate limits

**Reference**: [ARCHITECTURE.md#Performance-Considerations](./ARCHITECTURE.md#performance-considerations)

### Issue: Type conversion errors

**Check**:
1. Field type mapping in Glue column parameters
2. Null handling for non-nullable fields
3. RegistererExtractor implementation for the field type

**Reference**: [CLASS_DIAGRAMS.md#RegistererExtractor](./CLASS_DIAGRAMS.md#translator-components)

---

## Performance Tuning

### 1. Enable Parallel Splits

**Configuration**:
```bash
ACTIVATE_PARALLEL_SPLIT=true
```

**Requirements**:
- Table must have `$reserved_split_key` field

**Impact**: 5-10x faster for tables >10,000 rows

**Reference**: [ARCHITECTURE.md#Parallel-Split-Execution](./ARCHITECTURE.md#query-optimizations)

### 2. Optimize Filter Pushdown

**Best practices**:
- Use equality filters on indexed fields
- Combine multiple filters (AND conjunction)
- Use supported field types (TEXT, NUMBER, SELECT, DATE_TIME)

**Avoid**:
- LIKE patterns (not pushed down)
- Filters on ATTACHMENT, FORMULA fields

**Reference**: [ARCHITECTURE.md#Filter-Pushdown](./ARCHITECTURE.md#query-optimizations)

### 3. Use TOP-N Optimization

**Pattern**:
```sql
SELECT * FROM table ORDER BY date DESC LIMIT N
```

**Impact**: Returns results immediately, no full table scan

**Reference**: [ARCHITECTURE.md#TOP-N-Pushdown](./ARCHITECTURE.md#query-optimizations)

### 4. Adjust Page Size

**Trade-off**:
- Larger page size: Fewer API calls, more memory
- Smaller page size: More API calls, less memory

**Default**: 500 records

**Location**: `BaseConstants.PAGE_SIZE`

### 5. Increase Lambda Resources

**Recommendations**:
- Memory: 3008 MB (maximum network bandwidth)
- Timeout: 900 seconds
- Ephemeral storage: 10 GB (for spill)

---

## Future Enhancements

### Potential improvements documented in code

1. **Incremental Crawler Updates**
   - Only update changed tables
   - Track last crawl timestamp

2. **Caching Improvements**
   - Cache table schemas longer
   - Share cache across Lambda invocations

3. **Additional Optimizations**
   - Projection pushdown (SELECT specific columns)
   - Aggregation pushdown (COUNT, SUM, etc.)

4. **Enhanced Type Support**
   - Better handling of complex nested types
   - Support for more Lark field types

5. **Monitoring and Metrics**
   - CloudWatch metrics for query performance
   - API call tracking
   - Cache hit rates

---

## Additional Resources

### AWS Documentation
- [Athena Federation SDK](https://github.com/awslabs/aws-athena-query-federation)
- [AWS Glue Data Catalog](https://docs.aws.amazon.com/glue/latest/dg/catalog-and-crawler.html)
- [AWS Lambda](https://docs.aws.amazon.com/lambda/)

### Lark Documentation
- [Lark Open Platform](https://open.larksuite.com/)
- [Bitable API](https://open.larksuite.com/document/server-docs/docs/bitable-v1)
- [Search API](https://open.larksuite.com/document/uAjLw4CM/ukTMukTMukTM/reference/bitable-v1/app-table-record/search)

### Apache Arrow
- [Apache Arrow Documentation](https://arrow.apache.org/)
- [Arrow Java](https://arrow.apache.org/docs/java/)

---

## Contact and Support

For issues, questions, or contributions:

1. **GitHub Issues**: [Project Issues](https://github.com/aganisatria/aws-athena-query-federation-lark/issues)
2. **Documentation**: This repository
3. **Code Review**: Submit pull requests

---

## Document Version

- **Version**: 1.0
- **Last Updated**: 2025-01-13
- **SDK Version**: AWS Athena Federation SDK v2025.37.1
- **Connector Version**: 2022.47.1

---

This documentation provides a complete reference for understanding, deploying, and extending the AWS Athena Lark Base Connector. For specific implementation details, refer to the source code and inline comments.
