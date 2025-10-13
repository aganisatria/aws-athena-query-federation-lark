# Sequence Diagrams - AWS Athena Lark Base Connector

## Table of Contents
1. [Query Execution Overview](#query-execution-overview)
2. [Metadata Discovery Flow](#metadata-discovery-flow)
3. [Schema Retrieval Flow](#schema-retrieval-flow)
4. [Partition Planning Flow](#partition-planning-flow)
5. [Split Generation Flow](#split-generation-flow)
6. [Data Reading Flow](#data-reading-flow)
7. [Filter Pushdown Flow](#filter-pushdown-flow)
8. [Crawler Execution Flow](#crawler-execution-flow)

---

## Query Execution Overview

### Complete Query Lifecycle

```
User → Athena → Lambda(Connector) → Lark API

┌────┐   ┌──────┐   ┌────────────┐   ┌──────────────┐   ┌─────────┐
│User│   │Athena│   │BaseMetadata│   │BaseRecord    │   │Lark API │
│    │   │      │   │Handler     │   │Handler       │   │         │
└─┬──┘   └──┬───┘   └─────┬──────┘   └──────┬───────┘   └────┬────┘
  │         │              │                 │                 │
  │ SELECT * FROM table    │                 │                 │
  ├────────>│              │                 │                 │
  │         │              │                 │                 │
  │         │ GetTableLayout                 │                 │
  │         ├─────────────>│                 │                 │
  │         │              │                 │                 │
  │         │              │ getPartitions() │                 │
  │         │              │<────┐           │                 │
  │         │              │     │           │                 │
  │         │              │ Translate       │                 │
  │         │              │ WHERE clause    │                 │
  │         │              │     │           │                 │
  │         │              │ Estimate        │                 │
  │         │              │ row count       │                 │
  │         │              ├────────────────────────────────────>│
  │         │              │                 │   GET records   │
  │         │              │<────────────────────────────────────│
  │         │              │     total: 5000 │                 │
  │         │              │     │           │                 │
  │         │              │ Write partition │                 │
  │         │              │ block           │                 │
  │         │              │<────┘           │                 │
  │         │              │                 │                 │
  │         │ Partition Block                │                 │
  │         │<─────────────┤                 │                 │
  │         │              │                 │                 │
  │         │ GetSplits    │                 │                 │
  │         ├─────────────>│                 │                 │
  │         │              │                 │                 │
  │         │              │ Read partitions │                 │
  │         │              │ Create splits   │                 │
  │         │              │<────┐           │                 │
  │         │              │     │           │                 │
  │         │ Split objects│     │           │                 │
  │         │<─────────────┤     │           │                 │
  │         │              │     │           │                 │
  │         │              │                 │                 │
  │         │ ReadRecords (for each split)   │                 │
  │         ├────────────────────────────────>│                 │
  │         │              │                 │                 │
  │         │              │                 │ Fetch page 1    │
  │         │              │                 ├────────────────>│
  │         │              │                 │<────────────────┤
  │         │              │                 │   500 records   │
  │         │              │                 │                 │
  │         │              │                 │ Write to Arrow  │
  │         │              │                 │ blocks          │
  │         │              │                 │<────┐           │
  │         │              │                 │     │           │
  │         │              │                 │ Fetch page 2    │
  │         │              │                 ├────────────────>│
  │         │              │                 │<────────────────┤
  │         │              │                 │   500 records   │
  │         │              │                 │                 │
  │         │              │                 │ Write to Arrow  │
  │         │              │                 │<────┐           │
  │         │              │                 │     │           │
  │         │              │                 │ ... continues   │
  │         │              │                 │                 │
  │         │              │ Arrow blocks    │                 │
  │         │<────────────────────────────────┤                 │
  │         │              │                 │                 │
  │ Result Set             │                 │                 │
  │<────────┤              │                 │                 │
  │         │              │                 │                 │
```

---

## Metadata Discovery Flow

### List Schemas (Databases)

```
┌──────┐   ┌──────────────┐   ┌─────────────┐   ┌──────────────┐   ┌─────┐
│Athena│   │BaseMetadata  │   │LarkSource   │   │GlueCatalog   │   │Glue │
│      │   │Handler       │   │Metadata     │   │Service       │   │     │
└──┬───┘   └──────┬───────┘   └──────┬──────┘   └──────┬───────┘   └──┬──┘
   │              │                   │                 │              │
   │ ListSchemas  │                   │                 │              │
   ├─────────────>│                   │                 │              │
   │              │                   │                 │              │
   │              │ Try Glue first    │                 │              │
   │              ├──────────────────────────────────────>│ listDatabases
   │              │                   │                 ├─────────────>│
   │              │                   │                 │<─────────────┤
   │              │<──────────────────────────────────────┤ databases   │
   │              │                   │                 │              │
   │              │ Get from mapping  │                 │              │
   │              ├──────────────────>│                 │              │
   │              │                   │                 │              │
   │              │                   │ Read cached     │              │
   │              │                   │ mappings        │              │
   │              │                   │<────┐           │              │
   │              │                   │     │           │              │
   │              │<──────────────────┤     │           │              │
   │              │ database names    │     │           │              │
   │              │                   │     │           │              │
   │              │ Merge results     │                 │              │
   │              │ (Set eliminates   │                 │              │
   │              │  duplicates)      │                 │              │
   │              │<────┐             │                 │              │
   │              │     │             │                 │              │
   │ Schemas      │     │             │                 │              │
   │<─────────────┤     │             │                 │              │
   │              │                   │                 │              │
```

### List Tables

```
┌──────┐   ┌──────────────┐   ┌─────────────┐   ┌──────────────┐   ┌─────┐
│Athena│   │BaseMetadata  │   │LarkSource   │   │GlueCatalog   │   │Glue │
│      │   │Handler       │   │Metadata     │   │Service       │   │     │
└──┬───┘   └──────┬───────┘   └──────┬──────┘   └──────┬───────┘   └──┬──┘
   │              │                   │                 │              │
   │ ListTables   │                   │                 │              │
   │ (schemaName) │                   │                 │              │
   ├─────────────>│                   │                 │              │
   │              │                   │                 │              │
   │              │ Get from Glue     │                 │              │
   │              ├──────────────────────────────────────>│ getTables   │
   │              │                   │                 ├─────────────>│
   │              │                   │                 │ with filter │
   │              │                   │                 │<─────────────┤
   │              │<──────────────────────────────────────┤ tables      │
   │              │                   │                 │              │
   │              │ Get from mapping  │                 │              │
   │              ├──────────────────>│                 │              │
   │              │                   │                 │              │
   │              │                   │ Filter by schema│              │
   │              │                   │<────┐           │              │
   │              │                   │     │           │              │
   │              │<──────────────────┤     │           │              │
   │              │ table names       │     │           │              │
   │              │                   │     │           │              │
   │              │ Merge (LinkedHashSet)   │           │              │
   │              │<────┐             │                 │              │
   │              │     │             │                 │              │
   │ Tables       │     │             │                 │              │
   │<─────────────┤     │             │                 │              │
   │              │                   │                 │              │
```

---

## Schema Retrieval Flow

### Get Table Schema (Strategy Pattern)

```
┌──────┐   ┌──────────────┐   ┌──────────┐   ┌──────────────┐   ┌─────────┐
│Athena│   │BaseMetadata  │   │LarkSource│   │Experimental  │   │Glue     │
│      │   │Handler       │   │Provider  │   │Provider      │   │Catalog  │
└──┬───┘   └──────┬───────┘   └────┬─────┘   └──────┬───────┘   └────┬────┘
   │              │                 │                │                │
   │ GetTable     │                 │                │                │
   ├─────────────>│                 │                │                │
   │              │                 │                │                │
   │              │ Try Provider 1  │                │                │
   │              ├────────────────>│                │                │
   │              │                 │                │                │
   │              │                 │ Search in      │                │
   │              │                 │ memory cache   │                │
   │              │                 │<────┐          │                │
   │              │                 │     │          │                │
   │              │                 │ Found!         │                │
   │              │<────────────────┤ Return schema  │                │
   │              │                 │                │                │
   │              │ Add reserved    │                │                │
   │              │ fields          │                │                │
   │              │<────┐           │                │                │
   │              │     │           │                │                │
   │ Schema       │     │           │                │                │
   │<─────────────┤     │           │                │                │
   │              │                 │                │                │

Alternative: Not found in Provider 1

┌──────┐   ┌──────────────┐   ┌──────────┐   ┌──────────────┐   ┌─────────┐
│Athena│   │BaseMetadata  │   │LarkSource│   │Experimental  │   │LarkBase │
│      │   │Handler       │   │Provider  │   │Provider      │   │Service  │
└──┬───┘   └──────┬───────┘   └────┬─────┘   └──────┬───────┘   └────┬────┘
   │              │                 │                │                │
   │ GetTable     │                 │                │                │
   ├─────────────>│                 │                │                │
   │              │                 │                │                │
   │              │ Try Provider 1  │                │                │
   │              ├────────────────>│                │                │
   │              │<────────────────┤ Not found      │                │
   │              │                 │                │                │
   │              │ Try Provider 2  │                │                │
   │              ├────────────────────────────────────>│              │
   │              │                 │                │                │
   │              │                 │                │ Query metadata │
   │              │                 │                │ table          │
   │              │                 │                │<────┐          │
   │              │                 │                │     │          │
   │              │                 │                │ Get base_id,   │
   │              │                 │                │ table_id       │
   │              │                 │                │     │          │
   │              │                 │                │ Fetch schema   │
   │              │                 │                ├────────────────>│
   │              │                 │                │  getTableFields│
   │              │                 │                │<────────────────┤
   │              │                 │                │  field items   │
   │              │                 │                │                │
   │              │                 │                │ Build Arrow    │
   │              │                 │                │ schema         │
   │              │                 │                │<────┐          │
   │              │                 │                │     │          │
   │              │<────────────────────────────────────┤              │
   │              │                 │  schema        │                │
   │              │                 │                │                │
   │              │ Add reserved fields                │                │
   │              │<────┐           │                │                │
   │              │     │           │                │                │
   │ Schema       │     │           │                │                │
   │<─────────────┤     │           │                │                │
   │              │                 │                │                │
```

---

## Partition Planning Flow

### Get Partitions with Filter Translation

```
┌──────┐   ┌──────────────┐   ┌────────────┐   ┌──────────┐   ┌─────────┐
│Athena│   │BaseMetadata  │   │SearchApi   │   │LarkBase  │   │Lark API │
│      │   │Handler       │   │Translator  │   │Service   │   │         │
└──┬───┘   └──────┬───────┘   └─────┬──────┘   └────┬─────┘   └────┬────┘
   │              │                  │               │              │
   │ GetTableLayout                  │               │              │
   │ (with WHERE clause)             │               │              │
   ├─────────────>│                  │               │              │
   │              │                  │               │              │
   │              │ getPartitions()  │               │              │
   │              │<────┐            │               │              │
   │              │     │            │               │              │
   │              │ Resolve partition info           │              │
   │              │ (base_id, table_id, mappings)    │              │
   │              │     │            │               │              │
   │              │ Extract LIMIT    │               │              │
   │              │<────┘            │               │              │
   │              │                  │               │              │
   │              │ Translate filter │               │              │
   │              ├─────────────────>│               │              │
   │              │ WHERE status='active'            │              │
   │              │                  │               │              │
   │              │                  │ Map Athena    │              │
   │              │                  │ field names   │              │
   │              │                  │ to Lark names │              │
   │              │                  │<────┐         │              │
   │              │                  │     │         │              │
   │              │                  │ Build filter  │              │
   │              │                  │ JSON          │              │
   │              │                  │<────┐         │              │
   │              │                  │     │         │              │
   │              │<─────────────────┤     │         │              │
   │              │ filterJson       │     │         │              │
   │              │                  │               │              │
   │              │ Translate ORDER BY                │              │
   │              ├─────────────────>│               │              │
   │              │<─────────────────┤               │              │
   │              │ sortJson         │               │              │
   │              │                  │               │              │
   │              │ Estimate row count               │              │
   │              ├─────────────────────────────────>│              │
   │              │                  │ POST search   │              │
   │              │                  │ page_size=1   ├─────────────>│
   │              │                  │ filter=...    │              │
   │              │                  │               │<─────────────┤
   │              │<─────────────────────────────────┤ total: 5000  │
   │              │                  │               │              │
   │              │ Calculate effective count         │              │
   │              │ (min(total, LIMIT))              │              │
   │              │<────┐            │               │              │
   │              │     │            │               │              │
   │              │ Decide split strategy            │              │
   │              │ If parallel: multiple partitions │              │
   │              │ Else: single partition           │              │
   │              │<────┐            │               │              │
   │              │     │            │               │              │
   │              │ Write partition block            │              │
   │              │ - base_id                        │              │
   │              │ - table_id                       │              │
   │              │ - filterJson                     │              │
   │              │ - sortJson                       │              │
   │              │ - page_size                      │              │
   │              │ - expected_count                 │              │
   │              │<────┐            │               │              │
   │              │     │            │               │              │
   │ Partition    │     │            │               │              │
   │ Block        │     │            │               │              │
   │<─────────────┤     │            │               │              │
   │              │                  │               │              │
```

### Parallel Partition Strategy

```
┌──────┐   ┌──────────────┐
│Athena│   │BaseMetadata  │
│      │   │Handler       │
└──┬───┘   └──────┬───────┘
   │              │
   │ GetTableLayout
   ├─────────────>│
   │              │
   │              │ getPartitions()
   │              │<────┐
   │              │     │
   │              │ Detect $reserved_split_key
   │              │ Total rows: 5000
   │              │ PAGE_SIZE: 500
   │              │     │
   │              │ Calculate splits:
   │              │ Split 1: rows 1-500
   │              │ Split 2: rows 501-1000
   │              │ Split 3: rows 1001-1500
   │              │ ...
   │              │ Split 10: rows 4501-5000
   │              │     │
   │              │ Write 10 partition rows
   │              │ Each with:
   │              │ - split_start_index
   │              │ - split_end_index
   │              │ - is_parallel_split=true
   │              │<────┘
   │              │
   │ 10 Partition │
   │ Rows         │
   │<─────────────┤
   │              │
```

---

## Split Generation Flow

```
┌──────┐   ┌──────────────┐
│Athena│   │BaseMetadata  │
│      │   │Handler       │
└──┬───┘   └──────┬───────┘
   │              │
   │ GetSplits    │
   │ (partition block)
   ├─────────────>│
   │              │
   │              │ doGetSplits()
   │              │<────┐
   │              │     │
   │              │ Read partition block
   │              │ For each partition row:
   │              │   base_id = "..."
   │              │   table_id = "..."
   │              │   filter = {...}
   │              │   sort = [...]
   │              │   page_size = 500
   │              │   expected_count = 5000
   │              │   is_parallel = false
   │              │     │
   │              │ Apply LIMIT optimization
   │              │ If LIMIT < PAGE_SIZE:
   │              │   page_size = LIMIT
   │              │     │
   │              │ Create Split object
   │              │ Copy all properties
   │              │<────┘
   │              │
   │ Split        │
   │ Objects      │
   │<─────────────┤
   │              │
```

---

## Data Reading Flow

### Complete Record Retrieval with Pagination

```
┌──────┐   ┌───────────┐   ┌──────────┐   ┌────────────┐   ┌─────────┐
│Athena│   │BaseRecord │   │Iterator  │   │LarkBase    │   │Lark API │
│      │   │Handler    │   │          │   │Service     │   │         │
└──┬───┘   └─────┬─────┘   └────┬─────┘   └─────┬──────┘   └────┬────┘
   │             │               │               │               │
   │ ReadRecords │               │               │               │
   │ (split)     │               │               │               │
   ├────────────>│               │               │               │
   │             │               │               │               │
   │             │ readWithConstraint()          │               │
   │             │<────┐         │               │               │
   │             │     │         │               │               │
   │             │ Extract split properties      │               │
   │             │ - base_id, table_id           │               │
   │             │ - filter, sort                │               │
   │             │ - page_size, expected_count   │               │
   │             │     │         │               │               │
   │             │ Deserialize   │               │               │
   │             │ field type mapping            │               │
   │             │     │         │               │               │
   │             │ Create RegistererExtractor    │               │
   │             │<────┘         │               │               │
   │             │               │               │               │
   │             │ getIterator() │               │               │
   │             ├──────────────>│               │               │
   │             │               │               │               │
   │             │               │ Initialize    │               │
   │             │               │ - pageToken = null            │
   │             │               │ - hasMore = true              │
   │             │               │<────┐         │               │
   │             │               │     │         │               │
   │             │<──────────────┤     │         │               │
   │             │ Iterator      │     │         │               │
   │             │               │               │               │
   │             │ writeItemsToBlock()           │               │
   │             │<────┐         │               │               │
   │             │     │         │               │               │
   │             │ Build RowWriter               │               │
   │             │ with extractors               │               │
   │             │     │         │               │               │
   │             │ Loop: while hasNext()         │               │
   │             │     │         │               │               │
   │             │     │ hasNext()               │               │
   │             │     ├────────>│               │               │
   │             │     │         │               │               │
   │             │     │         │ Check current page            │
   │             │     │         │ Empty? Fetch next             │
   │             │     │         │               │               │
   │             │     │         │ fetchNextPage()               │
   │             │     │         ├──────────────>│               │
   │             │     │         │               │               │
   │             │     │         │               │ POST search   │
   │             │     │         │               │ {             │
   │             │     │         │               │   pageSize:500│
   │             │     │         │               │   pageToken:.│
   │             │     │         │               │   filter:{...}│
   │             │     │         │               │   sort:[...]  │
   │             │     │         │               │ }             │
   │             │     │         │               ├──────────────>│
   │             │     │         │               │<──────────────┤
   │             │     │         │<──────────────┤               │
   │             │     │         │ Response:     │               │
   │             │     │         │ - items: [500]│               │
   │             │     │         │ - hasMore: true               │
   │             │     │         │ - pageToken: "abc123"         │
   │             │     │         │               │               │
   │             │     │         │ Update state  │               │
   │             │     │         │ - currentPageIterator         │
   │             │     │         │ - pageToken = "abc123"        │
   │             │     │         │ - hasMore = true              │
   │             │     │         │<────┐         │               │
   │             │     │         │     │         │               │
   │             │     │<────────┤ true│         │               │
   │             │     │         │     │         │               │
   │             │     │ next()  │     │         │               │
   │             │     ├────────>│     │         │               │
   │             │     │         │     │         │               │
   │             │     │         │ Get next item from current page
   │             │     │         │ Add reserved fields           │
   │             │     │         │ - $reserved_record_id         │
   │             │     │         │ - $reserved_table_id          │
   │             │     │         │ - $reserved_base_id           │
   │             │     │         │<────┐         │               │
   │             │     │         │     │         │               │
   │             │     │<────────┤ record        │               │
   │             │     │         │     │         │               │
   │             │ Ensure all schema fields present │            │
   │             │ Add default values for missing   │            │
   │             │     │         │               │               │
   │             │ rowWriter.writeRow()             │            │
   │             │ - Extract values with type conversion         │
   │             │ - Write to Arrow vectors         │            │
   │             │     │         │               │               │
   │             │ ... continues for all records ...             │
   │             │     │         │               │               │
   │             │     │ hasNext()               │               │
   │             │     ├────────>│               │               │
   │             │     │         │               │               │
   │             │     │         │ Current page exhausted        │
   │             │     │         │ Fetch next page               │
   │             │     │         ├──────────────>│               │
   │             │     │         │               ├──────────────>│
   │             │     │         │               │<──────────────┤
   │             │     │         │<──────────────┤               │
   │             │     │         │ items: [500]  │               │
   │             │     │         │ hasMore: false│               │
   │             │     │         │ pageToken: "" │               │
   │             │     │         │               │               │
   │             │     │         │ Update:       │               │
   │             │     │         │ hasMore = false               │
   │             │     │         │<────┐         │               │
   │             │     │         │     │         │               │
   │             │     │<────────┤ true│         │               │
   │             │     │         │     │         │               │
   │             │ ... process remaining records ...             │
   │             │     │         │               │               │
   │             │     │ hasNext()               │               │
   │             │     ├────────>│               │               │
   │             │     │<────────┤ false         │               │
   │             │     │         │               │               │
   │             │ Exit loop     │               │               │
   │             │<────┘         │               │               │
   │             │               │               │               │
   │ Arrow       │               │               │               │
   │ Blocks      │               │               │               │
   │<────────────┤               │               │               │
   │             │               │               │               │
```

---

## Filter Pushdown Flow

### SQL WHERE Clause to Lark Filter Translation

```
User: SELECT * FROM table WHERE status = 'active' AND age > 18

┌──────┐   ┌───────────┐   ┌────────────┐   ┌─────────┐
│Athena│   │BaseMetadata│   │SearchApi   │   │Lark API │
│      │   │Handler     │   │Translator  │   │         │
└──┬───┘   └─────┬──────┘   └──────┬─────┘   └────┬────┘
   │             │                  │              │
   │ GetTableLayout                 │              │
   │ constraints: {                 │              │
   │   status: EquatableValueSet(['active'])       │
   │   age: SortedRangeSet(>18)    │              │
   │ }            │                  │              │
   ├────────────>│                  │              │
   │             │                  │              │
   │             │ getPartitions()  │              │
   │             │<────┐            │              │
   │             │     │            │              │
   │             │ translateFilterExpression()     │
   │             ├─────────────────>│              │
   │             │ constraints      │              │
   │             │ fieldMappings    │              │
   │             │                  │              │
   │             │                  │ For each constraint:
   │             │                  │<────┐        │
   │             │                  │     │        │
   │             │                  │ 1. status    │
   │             │                  │   Find mapping:
   │             │                  │   athena: "status"
   │             │                  │   lark: "Status"
   │             │                  │   type: SINGLE_SELECT
   │             │                  │     │        │
   │             │                  │   Check if type allowed
   │             │                  │   for pushdown
   │             │                  │   SINGLE_SELECT → YES
   │             │                  │     │        │
   │             │                  │   Translate EquatableValueSet:
   │             │                  │   {
   │             │                  │     field_name: "Status"
   │             │                  │     operator: "is"
   │             │                  │     value: ["active"]
   │             │                  │   }
   │             │                  │     │        │
   │             │                  │ 2. age       │
   │             │                  │   Find mapping:
   │             │                  │   athena: "age"
   │             │                  │   lark: "Age"
   │             │                  │   type: NUMBER
   │             │                  │     │        │
   │             │                  │   Translate SortedRangeSet:
   │             │                  │   {
   │             │                  │     field_name: "Age"
   │             │                  │     operator: "isGreater"
   │             │                  │     value: ["18"]
   │             │                  │   }
   │             │                  │     │        │
   │             │                  │ Build filter structure:
   │             │                  │ {
   │             │                  │   "conjunction": "and"
   │             │                  │   "conditions": [
   │             │                  │     { field_name: "Status",
   │             │                  │       operator: "is",
   │             │                  │       value: ["active"] },
   │             │                  │     { field_name: "Age",
   │             │                  │       operator: "isGreater",
   │             │                  │       value: ["18"] }
   │             │                  │   ]
   │             │                  │ }
   │             │                  │     │        │
   │             │                  │ Serialize to JSON
   │             │                  │<────┘        │
   │             │                  │              │
   │             │<─────────────────┤              │
   │             │ filterJson       │              │
   │             │                  │              │
   │             │ Store in partition              │
   │             │<────┐            │              │
   │             │     │            │              │
   │             │     ... later during ReadRecords ...
   │             │                  │              │
   │             │                  │ POST search  │
   │             │                  │ {            │
   │             │                  │   filter: {  │
   │             │                  │     "conjunction": "and"
   │             │                  │     "conditions": [...]
   │             │                  │   }          │
   │             │                  │ }            │
   │             │                  ├─────────────>│
   │             │                  │              │
   │             │                  │ Filtering happens
   │             │                  │ on Lark side │
   │             │                  │              │
   │             │                  │<─────────────┤
   │             │                  │ Only matching│
   │             │                  │ records      │
```

---

## Crawler Execution Flow

### Glue Crawler - Table Discovery and Registration

```
┌──────┐   ┌─────────────┐   ┌───────────┐   ┌────────┐   ┌─────┐
│Lambda│   │MainCrawler  │   │LarkBase   │   │LarkBase│   │Glue │
│Invoke│   │Handler      │   │Crawler    │   │Service │   │     │
└──┬───┘   └──────┬──────┘   └─────┬─────┘   └────┬───┘   └──┬──┘
   │              │                 │              │          │
   │ Event: {     │                 │              │          │
   │   handler_type: "lark_base"    │              │          │
   │   larkBaseDataSourceId: "..."  │              │          │
   │   larkTableDataSourceId: "..." │              │          │
   │ }            │                 │              │          │
   ├─────────────>│                 │              │          │
   │              │                 │              │          │
   │              │ Route to specific handler      │          │
   │              ├────────────────>│              │          │
   │              │                 │              │          │
   │              │                 │ handleRequest()         │
   │              │                 │<────┐        │          │
   │              │                 │     │        │          │
   │              │                 │ getLarkDatabases()      │
   │              │                 ├─────────────>│          │
   │              │                 │              │          │
   │              │                 │              │ Get records from
   │              │                 │              │ source table
   │              │                 │              │<────┐    │
   │              │                 │              │     │    │
   │              │                 │<─────────────┤     │    │
   │              │                 │ Database records:  │    │
   │              │                 │ [{id:"base1",      │    │
   │              │                 │   name:"db1"}, ...]│    │
   │              │                 │              │     │    │
   │              │                 │ For each database: │    │
   │              │                 │<────┐        │          │
   │              │                 │     │        │          │
   │              │                 │ Create/verify DB    │    │
   │              │                 │     │        │     ├───>│
   │              │                 │     │        │     │<───┤
   │              │                 │     │        │          │
   │              │                 │ List tables in base     │
   │              │                 ├─────────────>│          │
   │              │                 │              │          │
   │              │                 │              │ GET /apps/{base}/tables
   │              │                 │              │<────┐    │
   │              │                 │              │     │    │
   │              │                 │<─────────────┤     │    │
   │              │                 │ Table list:  │     │    │
   │              │                 │ [{tableId,name}...] │    │
   │              │                 │              │          │
   │              │                 │ For each table:         │
   │              │                 │     │        │          │
   │              │                 │ Get fields   │          │
   │              │                 ├─────────────>│          │
   │              │                 │              │          │
   │              │                 │              │ GET /apps/{base}/tables/{table}/fields
   │              │                 │              │<────┐    │
   │              │                 │              │     │    │
   │              │                 │<─────────────┤     │    │
   │              │                 │ Field list:  │     │    │
   │              │                 │ [{fieldId,   │     │    │
   │              │                 │   fieldName, │     │    │
   │              │                 │   type}...]  │     │    │
   │              │                 │              │          │
   │              │                 │ Build Glue schema       │
   │              │                 │ Map types:              │
   │              │                 │ TEXT → string           │
   │              │                 │ NUMBER → double         │
   │              │                 │ DATE_TIME → bigint      │
   │              │                 │ MULTI_SELECT → array<string>
   │              │                 │ ATTACHMENT → array<struct>
   │              │                 │<────┐        │          │
   │              │                 │     │        │          │
   │              │                 │ Create TableInput       │
   │              │                 │ - name (sanitized)      │
   │              │                 │ - location URI          │
   │              │                 │ - columns               │
   │              │                 │ - parameters            │
   │              │                 │ - column parameters     │
   │              │                 │<────┐        │          │
   │              │                 │     │        │          │
   │              │                 │ Check if exists         │
   │              │                 │     │        │     ├───>│
   │              │                 │     │        │     │ getTable
   │              │                 │     │        │     │<───┤
   │              │                 │     │        │          │
   │              │                 │ If exists:              │
   │              │                 │   Compare schemas       │
   │              │                 │   If changed:           │
   │              │                 │     Update table   ├───>│
   │              │                 │     │        │     │<───┤
   │              │                 │     │        │          │
   │              │                 │ If not exists:          │
   │              │                 │   Create table     ├───>│
   │              │                 │     │        │     │<───┤
   │              │                 │     │        │          │
   │              │                 │ ... next table ...      │
   │              │                 │     │        │          │
   │              │                 │ ... next database ...   │
   │              │                 │<────┘        │          │
   │              │                 │              │          │
   │              │                 │ Build summary           │
   │              │                 │ - databases processed   │
   │              │                 │ - tables created        │
   │              │                 │ - tables updated        │
   │              │                 │ - tables skipped        │
   │              │                 │<────┐        │          │
   │              │                 │     │        │          │
   │              │<────────────────┤     │        │          │
   │              │ Summary         │     │        │          │
   │              │                 │     │        │          │
   │<─────────────┤                 │     │        │          │
   │ Response     │                 │     │        │          │
   │ "Success:    │                 │     │        │          │
   │  3 databases │                 │     │        │          │
   │  15 tables   │                 │     │        │          │
   │  created/updated"              │     │        │          │
```

---

## Summary

These sequence diagrams illustrate the complete data flow through the AWS Athena Lark Base Connector:

1. **Query Execution**: Complete lifecycle from SQL query to results
2. **Metadata Discovery**: How schemas and tables are discovered
3. **Schema Retrieval**: Strategy pattern for schema resolution
4. **Partition Planning**: Filter translation and partition creation
5. **Split Generation**: Converting partitions to execution splits
6. **Data Reading**: Pagination and record retrieval
7. **Filter Pushdown**: SQL constraint translation to Lark API
8. **Crawler**: Automated table discovery and registration

Each diagram shows the interaction between components, data flow, and decision points in the system.
