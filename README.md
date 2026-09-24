# AWS Athena Lark Base Connector

> Query Lark Base (Feishu Bitable) data directly from Amazon Athena using SQL

[![AWS Athena Federation SDK](https://img.shields.io/badge/AWS%20Athena%20SDK-v2025.37.1-orange)](https://github.com/awslabs/aws-athena-query-federation)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

## Overview

The AWS Athena Lark Base Connector is a federated connector that enables you to run SQL queries on [Lark Base](https://www.larksuite.com/en_us/product/base) (Feishu Bitable) data using [Amazon Athena](https://aws.amazon.com/athena/). It seamlessly integrates Lark Base tables with your data lake, allowing you to join, aggregate, and analyze Lark data alongside other data sources.

**Key Features:**
- **SQL Queries on Lark Base**: Use standard SQL to query Lark Base tables
- **Filter Pushdown**: WHERE clauses are translated to Lark API filters for optimal performance
- **TOP-N Optimization**: ORDER BY + LIMIT queries pushed down to Lark API
- **Parallel Execution**: Large tables automatically split for concurrent processing
- **Flexible Metadata Discovery**: Choose from 4 different discovery methods
- **Production Ready**: Built on AWS Athena Federation SDK v2025.37.1 with 90%+ test coverage

## Quick Start

### Prerequisites

- AWS Account with Athena, Lambda, and Glue permissions
- Lark Application with Bitable API access ([Get credentials](https://open.larksuite.com/))
- Java 17 and Maven (for building)
- S3 bucket for Lambda deployment

### 1. Store Lark Credentials

```bash
aws secretsmanager create-secret \
  --name lark-app-credentials \
  --secret-string '{"app_id":"cli_xxx","app_secret":"xxx"}'
```

### 2. Build the Connector

```bash
export JAVA_HOME="/path/to/jdk-17"
mvn clean package -pl athena-lark-base -am -Dcheckstyle.skip=true
```

### 3. Deploy to Lambda

```bash
# Upload JAR to S3
aws s3 cp athena-lark-base/target/athena-lark-base-2022.47.1.jar \
  s3://your-bucket/connectors/

# Create Lambda function
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=your-bucket,S3Key=connectors/athena-lark-base-2022.47.1.jar \
  --role arn:aws:iam::ACCOUNT_ID:role/AthenaFederationRole \
  --timeout 900 \
  --memory-size 3008 \
  --environment Variables="{
    LARK_APP_ID_SECRET_NAME=lark-app-credentials,
    LARK_APP_SECRET_SECRET_NAME=lark-app-credentials,
    ACTIVATE_LARK_BASE_SOURCE=true,
    LARK_BASE_DATA_SOURCE_ID=your_base_id,
    LARK_TABLE_DATA_SOURCE_ID=your_table_id
  }"
```

### 4. Register Data Source in Athena

```sql
CREATE EXTERNAL DATA SOURCE lark_base
USING LAMBDA 'arn:aws:lambda:REGION:ACCOUNT:function:athena-lark-connector';
```

### 5. Query Your Data

```sql
-- List databases
SHOW DATABASES IN lark_base;

-- Query data
SELECT * FROM lark_base.my_database.my_table
WHERE status = 'active'
ORDER BY created_date DESC
LIMIT 100;
```

## Documentation

### Getting Started

- **[Metadata Discovery Flows](./METADATA_DISCOVERY_FLOWS.md)** - Choose your deployment strategy (MUST READ)
- **[Architecture Guide](./ARCHITECTURE.md)** - Understand the system design
- **[Visual Diagrams](./DIAGRAMS.md)** - Interactive Mermaid diagrams

### Visual Documentation

- **[DIAGRAMS.md](./DIAGRAMS.md)** - All system diagrams in Mermaid format (GitHub-rendered)

## Features

### Metadata Discovery Options

Choose the method that best fits your needs:

| Method | Best For | Setup | Maintenance |
|--------|----------|-------|-------------|
| **Glue Catalog + Crawler** | Production | 30 min | Manual refresh |
| **Lark Base Source** | Development | 15 min | Automatic |
| **Lark Drive Source** | Folder-based org | 15 min | Automatic |
| **Experimental Provider** | Testing | 10 min | Real-time |

See [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) for detailed comparison.

### Query Optimizations

- **Filter Pushdown**: WHERE clauses translated to Lark API filters
  ```sql
  -- Efficiently pushed to Lark API
  SELECT * FROM table WHERE status = 'active' AND amount > 1000
  ```

- **LIMIT Pushdown**: Fetch only required rows
  ```sql
  -- Stops after fetching 100 rows
  SELECT * FROM table LIMIT 100
  ```

- **TOP-N Optimization**: ORDER BY + LIMIT combined
  ```sql
  -- Sorted at source, returns immediately
  SELECT * FROM table ORDER BY date DESC LIMIT 10
  ```

- **Parallel Splits**: Concurrent execution for large tables
  - Automatically divides tables into chunks
  - Runs in parallel Lambda invocations
  - 5-10x faster for tables >10,000 rows

### Type Support

All Lark Base field types are supported:

- **Basic**: Text, Number, Date, Checkbox, URL, Email, Phone
- **Selection**: Single Select, Multi Select
- **Advanced**: Attachment, Person, Location, Formula
- **Special**: Lookup (with recursive resolution), Duplex Link, Auto Number

### Known Limitations

- **NUMBER field precision**: Lark Base stores `Number` field values as IEEE 754 double-precision floats, which only preserve about 15-17 significant digits. Values that exceed this (e.g. bank card numbers, long numeric IDs) are silently rounded by Lark Base itself - trailing digits are replaced with zeros - before the data ever reaches this connector. This is a Lark Base platform limitation, not a connector bug, and it cannot be corrected downstream by the connector. Per [Lark's own documentation](https://www.larksuite.com/hc/en-US/articles/890398616778-base-limits-faqs): *"To record information containing large numbers, such as bank card numbers, use the text field."* If you need exact large numbers or high-precision decimals, store them in a `Text` field in Lark Base instead of a `Number` field.
- **`WHERE` constraints on List/Struct-typed columns crash the query**: any `WHERE` clause that references a column whose type is array/struct-shaped - `Multi Select`, `User`, `Attachment`, `Url`, `Location`, `Single Link`, `Duplex Link`, `Group Chat`, `Lookup`, `Created User`, `Modified User`, or a `Formula`/`Lookup` that resolves to one of these - fails the whole query with `GENERIC_INTERNAL_ERROR: java.lang.RuntimeException: java.lang.IllegalArgumentException: Lists have one child Field. Found: none`, even for the simplest case (`IS NOT NULL`). This reproduces regardless of the column's declared nullability and regardless of this connector's advertised filter-pushdown capabilities (both were tested and ruled out) - it happens inside Amazon Athena's own managed query engine, before this connector's Lambda ever receives a `GetSplitsRequest` or `ReadRecordsRequest`, so nothing in this connector's code can intercept or work around it. It is a platform-level limitation of Amazon Athena Federated Query's handling of complex (List/Struct) column types in predicates, not a bug in this connector. **Workaround options**: (1) don't filter on these columns directly - select the column without a `WHERE` clause on it, or filter on a related scalar column instead (unconstrained `SELECT *` and constraints on scalar columns both work normally); or (2) set `default_does_activate_complex_type_as_json_string=true` (on **both** the connector and crawler Lambdas - see below) to represent these columns as a JSON string instead, which sidesteps the crash entirely at the cost of losing native array/struct access in Athena.

#### Opt-in: representing List/Struct columns as JSON strings

Setting the environment variable `default_does_activate_complex_type_as_json_string=true` on both the connector (`athena-lark-base`) and crawler (`glue-lark-base-crawler`) Lambdas changes every column that would otherwise be array/struct-shaped (`Multi Select`, `User`, `Attachment`, `Url`, `Location`, `Single Link`, `Duplex Link`, `Group Chat`, `Lookup`, `Created User`, `Modified User`, and any `Formula`/`Lookup` resolving to one of these) into a plain `VARCHAR`/`string` column holding a JSON-serialized representation of the same value instead. This is **opt-in and off by default** - existing tables/queries that rely on List/Struct-typed columns are completely unaffected unless you explicitly set this.

Because the column becomes a normal string instead of a List/Struct type, `WHERE` constraints (including `IS NOT NULL`) on it work exactly like any other text column - Athena's engine no longer touches the crashing code path at all. To parse the JSON back out in a query, use Athena/Trino's `json_extract`/`json_extract_scalar` functions, e.g.:

```sql
SELECT json_extract_scalar(field_user, '$[0].name') AS assignee_name
FROM my_table
WHERE field_user IS NOT NULL
```

**This must be set on both Lambdas together** for a crawler-populated (Glue-backed) table: the crawler decides the *stored* Glue column type at crawl time (independent of the connector), so setting it only on the connector has no effect until the table is re-crawled with the same setting, and vice versa. For a live (non-crawled) Lark source, only the connector's setting matters. Toggling this on a table that already exists changes its column type on the next crawl/query - existing queries or dashboards built against the array/struct shape will need updating to the JSON-string shape (or you can leave the setting off and use the workaround above instead).

## Architecture

```
Amazon Athena (SQL Queries)
    ↓
AWS Lambda (Athena Connector)
    ↓
Metadata Discovery (choose one):
  • AWS Glue Data Catalog
  • Lark Base Metadata Table
  • Lark Drive Folders
  • Athena Catalog + Dynamic
    ↓
Query Execution
  • Filter Translation
  • Parallel Splits
  • Type Conversion
    ↓
Lark Base API
    ↓
Return Results to Athena
```

For detailed architecture, see [ARCHITECTURE.md](./ARCHITECTURE.md).

## Building from Source

### Requirements

- Java 17+
- Maven 3.6+
- AWS CLI (for deployment)

### Build Commands

```bash
# Build connector
JAVA_HOME="/path/to/jdk-17" mvn clean package -pl athena-lark-base -am -Dcheckstyle.skip=true

# Build crawler (optional)
JAVA_HOME="/path/to/jdk-17" mvn clean package -pl glue-lark-base-crawler -am -Dcheckstyle.skip=true

# Run tests
JAVA_HOME="/path/to/jdk-17" mvn test -Dcheckstyle.skip=true

# Run checkstyle
JAVA_HOME="/path/to/jdk-17" mvn checkstyle:check
```

## Configuration

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `LARK_APP_ID_SECRET_NAME` | Yes | AWS Secrets Manager secret name for app ID |
| `LARK_APP_SECRET_SECRET_NAME` | Yes | AWS Secrets Manager secret name for app secret |
| `ACTIVATE_LARK_BASE_SOURCE` | No | Enable Lark Base metadata discovery |
| `ACTIVATE_PARALLEL_SPLIT` | No | Enable parallel split execution |
| `ENABLE_DEBUG_LOGGING` | No | Enable detailed debug logs |
| `LARK_LOOKUP_MAX_DEPTH` | No | Max hops followed when resolving a chained LOOKUP field's type (default: 20). Also caps runaway resolution if a Lark Base has a misconfigured circular LOOKUP reference |
| `WHITELIST_TABLES` | No | Restricts, per schema, which tables the connector exposes. Format: `schemaName:tableName,schemaName:tableName2,...`. A schema with no entries here is unrestricted by this setting |
| `BLACKLIST_TABLES` | No | Excludes, per schema, specific tables from the connector regardless of `WHITELIST_TABLES`. Same format. A table listed here is never visible or queryable (blocked in both `SHOW TABLES` and direct `SELECT`) |
| `default_does_activate_complex_type_as_json_string` | No | Represents List/Struct-shaped columns (Multi Select, User, Attachment, Url, Location, Single/Duplex Link, Group Chat, Lookup, Created/Modified User, ...) as a JSON string instead, sidestepping the `WHERE`-on-List/Struct crash documented under [Known Limitations](#known-limitations). Set on **both** the connector and crawler Lambdas for a crawler-populated table. Off by default |

See [ARCHITECTURE.md#Configuration](./ARCHITECTURE.md#configuration) for complete reference.

## Deployment Options

### Deployment with CloudFormation

You can deploy the connector using the provided CloudFormation template.

```bash
aws cloudformation create-stack --stack-name lark-athena-connector --template-body file://athena-larkbase-console-standard.yaml --parameters ParameterKey=SpillBucket,ParameterValue=your-spill-bucket ParameterKey=ConnectorCodeS3Bucket,ParameterValue=your-connector-bucket ParameterKey=ConnectorCodeS3Key,ParameterValue=path/to/athena-lark-base.jar ParameterKey=CrawlerCodeS3Bucket,ParameterValue=your-crawler-bucket ParameterKey=CrawlerCodeS3Key,ParameterValue=path/to/glue-lark-base-crawler.jar --capabilities CAPABILITY_IAM
```

Alternatively, you can deploy the template using the [AWS CloudFormation console](https://console.aws.amazon.com/cloudformation/).

### Deployment with Terraform

A Terraform module is available to deploy the connector and all its resources. See the [Terraform module README](https://github.com/aganisatria/terraform-aws-lark-base-federation-query) for instructions.

### Module 1: Athena Connector (Required)

The main connector that executes queries:
- **Handler**: `BaseCompositeHandler`
- **Runtime**: Java 17
- **Memory**: 3008 MB (recommended)
- **Timeout**: 900 seconds

### Module 2: Glue Crawler (Optional)

Discovers and registers Lark Base tables in AWS Glue:
- **Handler**: `MainLarkBaseCrawlerHandler`
- **Use Cases**: Production deployments, scheduled updates
- **Alternative**: Use direct Lark Base source for automatic discovery

See [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) to choose.

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

## Troubleshooting Guide

### Issue: "Unable to retrieve table schema"

**Check**:
1. Does table exist in Glue? Run `aws glue get-table --database-name X --name Y`
2. Is Lark source enabled? Check `ACTIVATE_LARK_BASE_SOURCE` environment variable
3. Are Lark credentials correct? Check Secrets Manager

**Reference**: [DIAGRAMS.md#Metadata-Discovery-Strategy-Pattern](./DIAGRAMS.md#metadata-discovery-strategy-pattern)

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

**Reference**: [DIAGRAMS.md#Filter-Pushdown-Translation](./DIAGRAMS.md#filter-pushdown-translation)

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

**Reference**: [DIAGRAMS.md#Class-Hierarchy](./DIAGRAMS.md#class-hierarchy)

### Issue: `GENERIC_INTERNAL_ERROR: ... IllegalArgumentException: Lists have one child Field. Found: none`

This is not a connector bug - see [Known Limitations](#known-limitations) above. It happens whenever a `WHERE` clause references a List/Struct-typed column (Multi Select, User, Attachment, Url, Location, Single/Duplex Link, Group Chat, Lookup, Created/Modified User, or a Formula/Lookup resolving to one of these), inside Amazon Athena's own query engine before this connector's Lambda is ever invoked for splits or records.

**Fix**: remove the `WHERE` condition on that column (filter on a scalar column instead, or drop the filter and post-filter client-side).

## Common Development Scenarios

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
- [DIAGRAMS.md#Filter-Pushdown-Translation](./DIAGRAMS.md#filter-pushdown-translation)
- [DIAGRAMS.md#Class-Hierarchy](./DIAGRAMS.md#class-hierarchy)

### 3. Implementing New Metadata Discovery Method

**Files to create**:
- New class implementing metadata provider interface
- Add to `BaseMetadataHandler` initialization

**Reference**:
- [DIAGRAMS.md#Metadata-Provider-Pattern](./DIAGRAMS.md#metadata-provider-pattern)
- [DIAGRAMS.md#Metadata-Discovery-Strategy-Pattern](./DIAGRAMS.md#metadata-discovery-strategy-pattern)

### 4. Adding New Crawler Source

**Files to modify**:
- Create new handler extending `BaseLarkBaseCrawlerHandler`
- Update `MainLarkBaseCrawlerHandler` routing

**Reference**:
- [DIAGRAMS.md#Component-Interaction](./DIAGRAMS.md#component-interaction)

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

## Contributing

Contributions are welcome! This connector is designed for upstream contribution to the AWS Athena Federation SDK.

### Before Contributing

1. Read [DIAGRAMS.md#Class-Hierarchy](./DIAGRAMS.md#class-hierarchy) to understand structure
2. Review [DIAGRAMS.md#Query-Execution-Overview](./DIAGRAMS.md#query-execution-overview) for execution flows
3. Follow existing patterns (Strategy, Provider, Resolver)
4. Write tests (maintain 90%+ coverage)
5. Update documentation

### Development Setup

```bash
# Clone repository
git clone https://github.com/aganisatria/aws-athena-query-federation-lark.git
cd aws-athena-query-federation-lark

# Build
export JAVA_HOME="/path/to/jdk-17"
mvn clean package -Dcheckstyle.skip=true

# Run tests
mvn test -Dcheckstyle.skip=true
```

## Version Information

- **Connector Version**: 2022.47.1
- **AWS Athena Federation SDK**: v2025.37.1
- **Java**: 17
- **Last Updated**: 2025-01-13

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Links

- [AWS Athena Federation SDK](https://github.com/awslabs/aws-athena-query-federation)
- [Lark Open Platform](https://open.larksuite.com/)
- [Lark Bitable API Documentation](https://open.larksuite.com/document/server-docs/docs/bitable-v1)
- [Apache Arrow](https://arrow.apache.org/)

## Acknowledgments

Built with:
- AWS Athena Federation SDK
- Apache Arrow for columnar data format
- Lark Open Platform APIs

---

**Ready to get started?** See [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) to choose your deployment approach.

**Questions?** Check the [complete documentation](./ARCHITECTURE.md) or [troubleshooting guide](./README.md#troubleshooting-guide).