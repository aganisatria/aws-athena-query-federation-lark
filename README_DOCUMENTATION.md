# AWS Athena Lark Base Connector - Complete Documentation

Query Lark Base (Feishu Bitable) data directly from Amazon Athena using SQL!

## 🚀 Quick Navigation

### New Users - Start Here!

**👉 [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) - READ THIS FIRST!**

Before deploying, you MUST choose a metadata discovery approach:

| Flow | Best For | Setup Time | Maintenance |
|------|----------|------------|-------------|
| **[Flow 1: Glue + Crawler](./METADATA_DISCOVERY_FLOWS.md#flow-1-glue-catalog--crawler)** | Production | 30 min | Manual |
| **[Flow 2: Lark Base Source](./METADATA_DISCOVERY_FLOWS.md#flow-2-lark-base-source-direct)** | Development | 15 min | Automatic |
| **[Flow 3: Lark Drive Source](./METADATA_DISCOVERY_FLOWS.md#flow-3-lark-drive-source-direct)** | Folder-based | 15 min | Automatic |
| **[Flow 4: Experimental](./METADATA_DISCOVERY_FLOWS.md#flow-4-experimental-provider)** | Testing | 10 min | Real-time |

**Not sure which to choose?** Use the [Decision Tree](./METADATA_DISCOVERY_FLOWS.md#flow-selection-guide)

---

## 📚 Documentation Index

### Visual Diagrams 🎨

**[DIAGRAMS.md](./DIAGRAMS.md)** - **Interactive Mermaid Diagrams**
- ✅ View directly in GitHub/GitLab (auto-rendered)
- ✅ Import to Diagrams.io (draw.io) for editing
- ✅ Export to PNG/SVG/PDF
- Includes: Architecture, flows, class diagrams, sequences

**[DIAGRAMS_IMPORT_GUIDE.md](./DIAGRAMS_IMPORT_GUIDE.md)** - **How to Use Diagrams**
- Step-by-step import to Diagrams.io
- Export instructions
- Troubleshooting tips

### Core Documentation

1. **[METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md)** ⭐ **MUST READ**
   - Choose your deployment strategy
   - 4 different metadata discovery approaches
   - Detailed comparison and decision guide
   - Step-by-step setup for each flow

2. **[ARCHITECTURE.md](./ARCHITECTURE.md)**
   - System architecture overview
   - Component descriptions
   - Query optimization strategies
   - Configuration reference
   - Deployment guide

3. **[CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md)**
   - Detailed class structure
   - Service layer architecture
   - Design patterns used
   - Component relationships

4. **[SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)**
   - Step-by-step execution flows
   - API call sequences
   - Data flow diagrams

5. **[DOCUMENTATION_SUMMARY.md](./DOCUMENTATION_SUMMARY.md)**
   - Navigation guide
   - Quick reference
   - Common use cases
   - Troubleshooting

---

## 🎯 I Want To...

### Set Up the Connector

→ **[METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md)** - Choose your flow, then follow setup steps

### Understand the Architecture

→ **[ARCHITECTURE.md](./ARCHITECTURE.md)** - High-level overview with diagrams

### Learn How Queries Work

→ **[SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)** - See detailed execution flows

### Extend the Connector

→ **[CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md)** - Understand class structure and patterns

### Troubleshoot Issues

→ **[DOCUMENTATION_SUMMARY.md#Troubleshooting](./DOCUMENTATION_SUMMARY.md#troubleshooting-guide)** - Common issues and solutions

### Optimize Query Performance

→ **[ARCHITECTURE.md#Query-Optimizations](./ARCHITECTURE.md#query-optimizations)** - Filter pushdown, parallel splits, etc.

---

## 🏃 Quick Start (5 Minutes)

**Fastest way to get started** (Development environment):

### 1. Choose Flow 2 (Lark Base Source)

```bash
# Why Flow 2?
# ✅ No crawler needed (simpler)
# ✅ Automatic schema updates
# ✅ Perfect for development
```

### 2. Create Metadata Table in Lark

```
Create a Lark Base table with these columns:
- database_name (TEXT)
- database_lark_base_id (TEXT)
- table_name (TEXT)
- table_lark_base_id (TEXT)
- table_lark_table_id (TEXT)
```

### 3. Store Lark Credentials in AWS Secrets Manager

```bash
aws secretsmanager create-secret \
  --name lark-app-credentials \
  --secret-string '{"app_id":"cli_xxx","app_secret":"xxx"}'
```

### 4. Deploy Lambda

```bash
# Build
mvn clean package -pl athena-lark-base -am -Dcheckstyle.skip=true

# Deploy
aws lambda create-function \
  --function-name athena-lark-connector \
  --runtime java17 \
  --handler com.amazonaws.athena.connectors.lark.base.BaseCompositeHandler \
  --code S3Bucket=my-bucket,S3Key=athena-lark-base.jar \
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

### 5. Register in Athena

```sql
-- In Athena console
CREATE EXTERNAL DATA SOURCE lark_base
USING LAMBDA 'arn:aws:lambda:REGION:ACCOUNT:function:athena-lark-connector';
```

### 6. Query!

```sql
-- List discovered databases
SHOW DATABASES IN lark_base;

-- List tables
SHOW TABLES IN lark_base.your_database;

-- Query data
SELECT * FROM lark_base.your_database.your_table LIMIT 10;
```

**📖 For production setup, see [Flow 1: Glue + Crawler](./METADATA_DISCOVERY_FLOWS.md#flow-1-glue-catalog--crawler)**

---

## 🎓 Learning Path

### Beginner (Just Getting Started)

1. Read [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md)
2. Choose a flow and follow setup steps
3. Run example queries
4. Explore [ARCHITECTURE.md - Query Optimizations](./ARCHITECTURE.md#query-optimizations)

### Intermediate (Understanding Internals)

1. Study [ARCHITECTURE.md](./ARCHITECTURE.md) - Full architecture
2. Review [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) - Class structure
3. Trace flows in [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md)
4. Read source code with documentation as guide

### Advanced (Extending/Contributing)

1. Deep dive into [CLASS_DIAGRAMS.md - Design Patterns](./CLASS_DIAGRAMS.md#metadata-provider-pattern)
2. Study [SEQUENCE_DIAGRAMS.md - Complete Flows](./SEQUENCE_DIAGRAMS.md)
3. Review [DOCUMENTATION_SUMMARY.md - Common Use Cases](./DOCUMENTATION_SUMMARY.md#common-use-cases)
4. Contribute improvements!

---

## 🔍 Key Features

### Metadata Discovery (Choose Your Approach!)
- **Glue Catalog**: Traditional AWS Glue approach
- **Lark Base Source**: Direct discovery from Lark
- **Lark Drive Source**: Folder-based organization
- **Experimental**: Dynamic, real-time schemas

### Query Optimizations
- ✅ **Filter Pushdown**: WHERE clauses → Lark API filters
- ✅ **LIMIT Pushdown**: Fetch only needed rows
- ✅ **TOP-N Optimization**: ORDER BY + LIMIT pushed down
- ✅ **Parallel Splits**: Concurrent execution for large tables

### Type Support
- ✅ All Lark field types (TEXT, NUMBER, DATE, SELECT, etc.)
- ✅ Complex types (ATTACHMENT, PERSON, LOCATION)
- ✅ Nested types (LOOKUP with recursion)
- ✅ Automatic type mapping to Arrow/Athena types

### Production Ready
- ✅ AWS Athena Federation SDK v2025.37.1
- ✅ Comprehensive error handling
- ✅ Throttling and retry logic
- ✅ CloudWatch logging
- ✅ 90%+ test coverage

---

## 📊 Architecture at a Glance

```
Athena SQL Query
    ↓
AWS Lambda (Connector)
    ↓
Metadata Discovery (4 Options):
  1. AWS Glue Data Catalog
  2. Lark Base Metadata Table
  3. Lark Drive Folders
  4. Athena Catalog + Dynamic
    ↓
Query Execution
  - Filter Translation
  - Pagination
  - Type Conversion
    ↓
Lark Base API
    ↓
Return Results to Athena
```

**[See Full Architecture Diagram →](./ARCHITECTURE.md#architecture-diagram)**

---

## 🆚 Flow Comparison

### Which Flow Should I Use?

| Scenario | Recommended Flow | Why? |
|----------|-----------------|------|
| Production with stable schemas | **Flow 1 (Glue + Crawler)** | Fast, governed, cached |
| Development environment | **Flow 2 (Lark Base Source)** | Simple, automatic updates |
| Visual organization | **Flow 3 (Lark Drive)** | Folder-based, intuitive |
| Testing/exploration | **Flow 4 (Experimental)** | Real-time, flexible |
| Migrating from Glue | **Hybrid (1 + 2)** | Gradual transition |

**[See Detailed Comparison →](./METADATA_DISCOVERY_FLOWS.md#flow-comparison)**

---

## 🛠️ Common Tasks

### Add Support for New Lark Field Type
→ See [DOCUMENTATION_SUMMARY.md - Adding New Field Type](./DOCUMENTATION_SUMMARY.md#1-adding-a-new-lark-field-type)

### Optimize Query Performance
→ See [ARCHITECTURE.md - Performance Tuning](./ARCHITECTURE.md#performance-considerations)

### Migrate Between Flows
→ See [METADATA_DISCOVERY_FLOWS.md - Migration Guide](./METADATA_DISCOVERY_FLOWS.md#migration-between-flows)

### Debug Query Issues
→ See [DOCUMENTATION_SUMMARY.md - Troubleshooting](./DOCUMENTATION_SUMMARY.md#troubleshooting-guide)

---

## 🐛 Troubleshooting

### "Unable to retrieve table schema"
**Solution**: Check which flow you're using and verify configuration.
→ [Troubleshooting Guide](./DOCUMENTATION_SUMMARY.md#issue-unable-to-retrieve-table-schema)

### Query returns no results
**Solution**: Check filter pushdown in CloudWatch logs.
→ [Filter Pushdown Flow](./SEQUENCE_DIAGRAMS.md#filter-pushdown-flow)

### Slow queries
**Solution**: Enable parallel splits or optimize filters.
→ [Performance Tuning](./DOCUMENTATION_SUMMARY.md#performance-tuning)

---

## 📖 Documentation Hierarchy

```
README_DOCUMENTATION.md (You are here!)
│
├─► METADATA_DISCOVERY_FLOWS.md ⭐ START HERE
│   ├─► Flow 1: Glue + Crawler (Production)
│   ├─► Flow 2: Lark Base Source (Development)
│   ├─► Flow 3: Lark Drive Source (Folder-based)
│   └─► Flow 4: Experimental (Testing)
│
├─► ARCHITECTURE.md (System Design)
│   ├─► Architecture Diagrams
│   ├─► Component Descriptions
│   ├─► Query Optimizations
│   └─► Deployment Guide
│
├─► CLASS_DIAGRAMS.md (Code Structure)
│   ├─► Handler Hierarchy
│   ├─► Service Layer
│   └─► Design Patterns
│
├─► SEQUENCE_DIAGRAMS.md (Execution Flows)
│   ├─► Query Execution
│   ├─► Metadata Discovery
│   └─► Data Reading
│
└─► DOCUMENTATION_SUMMARY.md (Quick Reference)
    ├─► Navigation Guide
    ├─► Common Use Cases
    └─► Troubleshooting
```

---

## 🤝 Contributing

This connector is designed for upstream contribution to AWS Athena Federation SDK.

### Before Contributing
1. Read [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) to understand structure
2. Review [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md) for execution flows
3. Follow existing patterns (Strategy, Provider, Resolver)
4. Write tests (90%+ coverage required)
5. Update documentation

---

## 📝 Version Information

- **Connector Version**: 2022.47.1
- **SDK Version**: AWS Athena Federation SDK v2025.37.1
- **Java Version**: 17
- **Documentation Version**: 1.0
- **Last Updated**: 2025-01-13

---

## 🔗 External Resources

- [AWS Athena Federation SDK](https://github.com/awslabs/aws-athena-query-federation)
- [Lark Open Platform](https://open.larksuite.com/)
- [Lark Bitable API](https://open.larksuite.com/document/server-docs/docs/bitable-v1)
- [Apache Arrow](https://arrow.apache.org/)

---

## ⚡ Quick Links

| Document | Read Time | Purpose |
|----------|-----------|---------|
| [DIAGRAMS.md](./DIAGRAMS.md) 🎨 | 5 min | **Visual diagrams (import to draw.io)** |
| [DIAGRAMS_IMPORT_GUIDE.md](./DIAGRAMS_IMPORT_GUIDE.md) | 10 min | How to use diagrams |
| [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md) | 15 min | **Choose deployment approach** |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 30 min | Understand system design |
| [CLASS_DIAGRAMS.md](./CLASS_DIAGRAMS.md) | 20 min | Learn code structure |
| [SEQUENCE_DIAGRAMS.md](./SEQUENCE_DIAGRAMS.md) | 25 min | Trace execution flows |
| [DOCUMENTATION_SUMMARY.md](./DOCUMENTATION_SUMMARY.md) | 10 min | Quick reference |

---

## 💡 Pro Tips

1. **Always start with [METADATA_DISCOVERY_FLOWS.md](./METADATA_DISCOVERY_FLOWS.md)** - choosing the right flow is critical!

2. **Use Flow 2 for development** - simpler setup, automatic updates

3. **Use Flow 1 for production** - faster, more stable, better governance

4. **Enable debug logging** during setup - helps troubleshoot issues

5. **Monitor CloudWatch logs** - shows filter translation, API calls

6. **Use parallel splits** for tables >10,000 rows - much faster

7. **Test filter pushdown** - check logs to verify filters reach Lark API

---

**Ready to get started?** → **[Choose Your Flow →](./METADATA_DISCOVERY_FLOWS.md)**

---

*This connector enables SQL queries on Lark Base data through AWS Athena. Choose your deployment model, follow the setup guide, and start querying!*
