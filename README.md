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

### Core Documentation

| Document | Purpose | Time |
|----------|---------|------|
| [README_DOCUMENTATION.md](./README_DOCUMENTATION.md) | Complete documentation index | 10 min |
| [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) | Choose deployment approach | 15 min |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | System architecture | 30 min |
| [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) | Code structure | 20 min |
| [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md) | Execution flows | 25 min |
| [DOCUMENTATION_SUMMARY.md](./DOCUMENTATION_SUMMARY.md) | Quick reference | 10 min |

### Visual Documentation

- **[DIAGRAMS.md](./DIAGRAMS.md)** - All system diagrams in Mermaid format (GitHub-rendered)
- **[DIAGRAMS_IMPORT_GUIDE.md](./DIAGRAMS_IMPORT_GUIDE.md)** - How to import diagrams to Diagrams.io

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

## Performance

### Benchmarks

- **Small queries (<1000 rows)**: ~2-5 seconds
- **Large queries (>10,000 rows)**: 10-30 seconds with parallel splits
- **Filter pushdown**: 5-10x faster than post-filtering
- **TOP-N queries**: Returns immediately (no full scan)

### Tuning Tips

1. **Enable parallel splits** for tables >10,000 rows
2. **Use filter pushdown** with supported field types
3. **Apply LIMIT** to reduce data transfer
4. **Increase Lambda memory** to 3008 MB for better network performance

See [ARCHITECTURE.md#Performance-Considerations](./ARCHITECTURE.md#performance-considerations).

## Testing

```bash
# Unit tests
JAVA_HOME="/path/to/jdk-17" mvn test -Dcheckstyle.skip=true

# Coverage report
JAVA_HOME="/path/to/jdk-17" mvn clean test jacoco:report -Dcheckstyle.skip=true

# View coverage
open athena-lark-base/target/site/jacoco/index.html
```

Current coverage: **90%+**

## Troubleshooting

### Common Issues

**"Unable to retrieve table schema"**
- Check if crawler has run or Lark source is enabled
- Verify table exists in Glue or Lark Base mapping

**Query returns no results**
- Check CloudWatch logs for filter translation
- Verify field names match (case-sensitive in Lark API)

**Timeout errors**
- Increase Lambda timeout to 900 seconds
- Enable parallel splits for large tables
- Check Lambda memory (should be 3008 MB)

See [DOCUMENTATION_SUMMARY.md#Troubleshooting](./DOCUMENTATION_SUMMARY.md#troubleshooting-guide).

## Contributing

Contributions are welcome! This connector is designed for upstream contribution to the AWS Athena Federation SDK.

### Before Contributing

1. Read [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) to understand structure
2. Review [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md) for execution flows
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

**Questions?** Check the [complete documentation](./README_DOCUMENTATION.md) or [troubleshooting guide](./DOCUMENTATION_SUMMARY.md#troubleshooting-guide).
