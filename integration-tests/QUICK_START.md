# Quick Start: Verify Zero-Cost Testing Setup

**Time to complete: 5 minutes**

This guide verifies your zero-cost testing infrastructure is working correctly.

---

## Step 1: Verify Java Build (1 minute)

```bash
cd integration-tests

# Compile Java tests
JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home" \
  mvn clean compile -Dcheckstyle.skip=true

# Expected output:
# [INFO] BUILD SUCCESS
```

✅ **Success:** Java framework compiled successfully

---

## Step 2: Verify Python Setup (1 minute)

```bash
cd integration-tests/python

# Install dependencies
pip install -r requirements.txt

# Verify configuration
python -c "import config; config.print_test_config()"

# Expected output:
# Environment: MOCK
# AWS Region: us-east-1
# Lark API Mock: http://localhost:8080
```

✅ **Success:** Python framework configured correctly

---

## Step 3: Run Example Tests in MOCK Mode (1 minute)

```bash
cd integration-tests/python

# Run example integration test
export TEST_ENVIRONMENT=mock
python tests/integration/test_glue_operations.py -v

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** MOCK mode tests pass instantly

**Cost so far: $0** ✅

---

## Step 4: Run Regression Test in MOCK Mode (1 minute)

```bash
cd integration-tests/python

# Run migrated regression test
export TEST_ENVIRONMENT=mock
python tests/regression/test_glue_crawler.py --verbose

# Expected: All tests pass, < 5 seconds
```

✅ **Success:** Regression tests work in MOCK mode

**Cost so far: $0** ✅

---

## Step 5: Start LocalStack Community (1 minute)

```bash
cd integration-tests/src/main/resources/localstack

# Start LocalStack + WireMock
docker-compose up -d

# Wait for services to start
sleep 10

# Verify LocalStack is healthy
curl http://localhost:4566/_localstack/health

# Expected:
# {"services": {"lambda": "running", "s3": "running", ...}}

# Verify WireMock is healthy
curl http://localhost:8080/__admin/health

# Expected: 200 OK
```

✅ **Success:** LocalStack Community is running

**Cost so far: $0** ✅

---

## Step 6: Run Test in HYBRID Mode (Optional)

```bash
cd integration-tests/python

# Run test with LocalStack
export TEST_ENVIRONMENT=hybrid
python tests/integration/test_glue_operations.py -v

# Expected: Tests pass, uses LocalStack for Lambda/S3
```

✅ **Success:** HYBRID mode works with LocalStack

**Cost so far: $0** ✅

---

## Step 7: Run Master Test Runner (30 seconds)

```bash
cd integration-tests/python

# Run all regression tests
export TEST_ENVIRONMENT=mock
python run_regression_tests.py

# Expected:
# ================================================================================
# AWS Athena Lark Base Connector - Regression Test Suite
# ================================================================================
# Environment: MOCK
# ...
# ✓ All regression tests passed!
```

✅ **Success:** Master test runner works

**Total cost: $0** 🎉

---

## Verification Checklist

Mark each item as you complete it:

- [ ] Java framework compiles successfully
- [ ] Python configuration loads correctly
- [ ] MOCK mode tests pass instantly (< 5 seconds)
- [ ] Regression tests work in MOCK mode
- [ ] LocalStack Community starts successfully
- [ ] HYBRID mode tests work with LocalStack (optional)
- [ ] Master test runner executes all tests

---

## What You Just Achieved

✅ **Zero-cost testing infrastructure** is working
✅ **MOCK mode** tests run instantly for free
✅ **HYBRID mode** ready for integration testing (free)
✅ **Master test runner** can run all tests

**Total time spent:** 5 minutes
**Total cost:** $0

---

## Next Steps

### For Daily Development

```bash
# Default to MOCK mode in your shell config
echo 'export TEST_ENVIRONMENT=mock' >> ~/.zshrc
source ~/.zshrc

# Run tests before every commit
cd integration-tests/python
python run_regression_tests.py
```

### For Integration Testing

```bash
# Keep LocalStack running during development
cd integration-tests/src/main/resources/localstack
docker-compose up -d

# Run HYBRID tests when needed
export TEST_ENVIRONMENT=hybrid
python run_regression_tests.py
```

### For Production Validation (Optional)

```bash
# Once per week, validate against real AWS
export TEST_ENVIRONMENT=aws
export AWS_REGION=ap-southeast-1
python run_regression_tests.py

# Cost: ~$0.0075 per run = ~$0.03/month
```

---

## Troubleshooting

### Java build fails

```bash
# Check Java version
java -version
# Expected: java version "17.x.x"

# Set JAVA_HOME
export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.2.jdk/Contents/Home"
```

### Python imports fail

```bash
# Install dependencies
cd integration-tests/python
pip install -r requirements.txt

# Add to PYTHONPATH
export PYTHONPATH=$PWD:$PYTHONPATH
```

### LocalStack won't start

```bash
# Check Docker
docker ps

# View logs
docker logs lark-connector-localstack

# Reset
cd integration-tests/src/main/resources/localstack
docker-compose down -v
docker-compose up -d
```

### Tests fail in MOCK mode

```bash
# Verify environment
echo $TEST_ENVIRONMENT
# Expected: mock

# Run with verbose output
python tests/integration/test_glue_operations.py -v
```

---

## Success! What Now?

You've successfully set up **zero-cost testing infrastructure**. Here's what you can do:

1. **Read the guides:**
   - `README.md` - Comprehensive usage guide
   - `ZERO_COST_TESTING.md` - Zero-cost strategies
   - `MIGRATION_GUIDE.md` - How to migrate existing tests
   - `FINAL_SUMMARY.md` - Complete overview

2. **Migrate your tests:**
   - Start with `test-pushdown-predicates.py`
   - Follow `MIGRATION_GUIDE.md`
   - One test at a time

3. **Set up CI/CD:**
   - Use GitHub Actions with MOCK mode
   - Free unlimited testing!

4. **Train your team:**
   - Share this Quick Start guide
   - Demo the three-tier testing strategy

---

## Congratulations! 🎉

You now have:
- ✅ Zero-cost local testing ($0/month)
- ✅ 10x faster test execution
- ✅ Multi-environment support (MOCK/HYBRID/AWS)
- ✅ LocalStack Community integration (free)
- ✅ Production-ready test framework

**Welcome to truly free cloud testing!**

---

**Questions?** See:
- Main README: `integration-tests/README.md`
- Support: `FINAL_SUMMARY.md`
- GitHub Issues: https://github.com/aganisatria/aws-athena-query-federation-lark/issues
