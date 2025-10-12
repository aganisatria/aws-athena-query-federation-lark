# Future Improvements and Migration Plan

## 1. Checkstyle Compliance (Deferred)

### Status
Currently skipped for development velocity. Must be addressed before upstream contribution.

### Action Required
Run checkstyle and fix all violations:
```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home" mvn checkstyle:check -Dcheckstyle.consoleOutput=true
```

### Common Issues to Fix
- Javadoc missing or incomplete
- Line length exceeds 120 characters
- Import ordering
- Whitespace issues
- Magic numbers without explanation

### Reference
- Upstream checkstyle config: https://github.com/awslabs/aws-athena-query-federation/blob/master/checkstyle.xml
- All other connectors pass checkstyle validation

---

## 2. SDK Upgrade Plan (v2025.8.1 → v2025.37.1)

### Current Versions
- **Project version**: 2022.47.1
- **Current SDK**: 2025.8.1 (athena-lark-base/pom.xml:33)
- **Target SDK**: 2025.37.1

### Upgrade Advantages

#### Security & Stability
- Zookeeper CVE fixes (updated to 3.9.4)
- Critical dependency updates (Protobuf, AWS SDK, Elasticsearch)
- Multiple bug fixes and stability improvements

#### New Features
- Substrait query plan support (better query optimization)
- OAuth improvements for DataLake Gen2
- Pagination support for large datasets
- Cross-account CloudWatch metrics

#### Performance
- Arrow library improvements with timestamp timezone support
- Performance enhancements across connectors
- Better credential handling

### Estimated Effort: 4-8 hours

#### Step 1: Update Dependencies (15 min)
```xml
<!-- In athena-lark-base/pom.xml -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-athena-federation-sdk</artifactId>
    <version>2025.37.1</version>
    <classifier>withdep</classifier>
</dependency>
```

#### Step 2: Build & Compile (1-2 hours)
```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home" mvn clean package -Dcheckstyle.skip=true
```
- Check for compilation errors
- Review deprecation warnings
- Update any breaking changes

#### Step 3: Run Test Suite (1-2 hours)
```bash
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home" mvn test -Dcheckstyle.skip=true
```
- Verify all unit tests pass
- Check for any behavioral changes
- Pay special attention to timestamp handling (Arrow upgrade)

#### Step 4: Integration Testing (2-4 hours)
```bash
# Deploy to test environment
make deploy-module MODULE=athena-lark-base

# Run regression tests
./regression-test-plan.sh
```
- Test Lark connector functionality
- Verify authentication flows
- Test Search API filters
- Check timestamp/date queries
- Monitor Lambda logs for errors

#### Step 5: Verify (1 hour)
- Review CloudWatch logs
- Check query performance
- Validate all field types
- Test edge cases

### Release Notes Between Versions
Key changes from v2025.8.1 to v2025.37.1:
- 29 minor version increments
- No major breaking changes mentioned
- Primarily dependency updates and incremental improvements

### References
- Release v2025.37.1: https://github.com/awslabs/aws-athena-query-federation/releases/tag/v2025.37.1
- Release v2025.8.1: https://github.com/awslabs/aws-athena-query-federation/releases/tag/v2025.8.1
- All releases: https://github.com/awslabs/aws-athena-query-federation/releases

---

## 3. Upstream Contribution Plan

### Prerequisites
1. ✅ Code complete and tested
2. ⏳ Checkstyle compliance (see section 1)
3. ⏳ SDK upgrade to latest version (see section 2)
4. ⏳ Documentation complete (README, DEPLOYMENT_GUIDE, etc.)
5. ⏳ CDK deployment templates (if required)

### Repository Structure for Upstream
```
aws-athena-query-federation/
├── athena-lark/
│   ├── src/main/java/com/amazonaws/athena/connectors/lark/
│   ├── src/test/java/com/amazonaws/athena/connectors/lark/
│   ├── pom.xml
│   └── README.md
└── glue-lark-crawler/ (if separate, or combined)
```

### Key Documentation Needed
1. **README.md** - Setup and usage guide
2. **Connector capabilities** - Supported features, limitations
3. **Authentication guide** - Lark API credentials setup
4. **Field type mappings** - Lark Base types to Athena types
5. **Performance tuning** - Best practices, optimization tips
6. **Troubleshooting guide** - Common issues and solutions

### Upstream PR Checklist
- [ ] Fork awslabs/aws-athena-query-federation
- [ ] Create feature branch: `feature/add-lark-connector`
- [ ] Add connector code under `athena-lark/` directory
- [ ] Update root pom.xml to include new module
- [ ] Add comprehensive README.md
- [ ] Pass all checkstyle validations
- [ ] Achieve >80% test coverage
- [ ] Add connector to main repository README
- [ ] Create PR with detailed description
- [ ] Respond to maintainer feedback

### PR Description Template
```markdown
## Description
Add support for Lark Base as a federated query source in Amazon Athena.

## Motivation
Lark Base is a collaborative database platform widely used in enterprises. This connector enables querying Lark Base data directly from Athena without ETL.

## Features
- Full support for Lark Base field types
- Search API integration with filter pushdown
- Checkbox, text, number, date, formula field support
- Predicate pushdown optimization
- Configurable debug logging

## Testing
- 200+ unit tests with >90% coverage
- Comprehensive integration tests
- Field tested in production environment

## Documentation
- Complete README with setup instructions
- Field type mapping documentation
- Troubleshooting guide

## Breaking Changes
None - this is a new connector.
```

---

## Current Status Summary

### ✅ Completed
1. **Test Coverage Improvements**
   - athena-lark-base: 90%+ coverage on most classes
   - glue-lark-base-crawler: 90%+ coverage
   - 200+ unit tests added
   - JaCoCo reporting configured

2. **Code Quality**
   - Dependency injection for testability
   - Comprehensive edge case testing
   - Mock-based unit tests
   - Property-based tests with jqwik

3. **Major Features**
   - Search API migration (from deprecated List API)
   - Filter pushdown implementation
   - Configurable debug logging
   - Timestamp/date query fixes

4. **POM.xml Alignment**
   - JaCoCo version updated to 0.8.13 (matches upstream)
   - Removed redundant plugin declarations from child POMs
   - mockito-inline for static mocking
   - @{argLine} for JaCoCo integration

### ⏳ Deferred (documented here)
1. Checkstyle compliance
2. SDK upgrade to v2025.37.1
3. Upstream contribution preparation

### 📝 Next Steps
1. Create PR for test coverage improvements
2. Address checkstyle when ready for upstream
3. Schedule SDK upgrade and testing
4. Prepare documentation for upstream contribution
