"""
Base test class for regression tests.

Provides:
- Environment-aware setup (MOCK/HYBRID/AWS)
- AWS client factory
- Test result tracking
- Logging helpers
"""
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from clients import AWSClientFactory
from config import get_environment, TestEnvironment, TEST_DATABASE, TEST_TABLE


class BaseRegressionTest:
    """Base class for all regression tests."""

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.environment = get_environment()
        self.factory = AWSClientFactory()

        # Test configuration from config.py
        self.test_database = TEST_DATABASE
        self.test_table = TEST_TABLE
        self.test_catalog = os.getenv("ATHENA_CATALOG", "Athena-testgani")

        # Test results tracking
        self.test_results = {"total": 0, "passed": 0, "failed": 0, "warnings": 0}

    def setup(self):
        """Setup test environment based on mode."""
        setup_funcs = {
            TestEnvironment.MOCK: self._setup_mock,
            TestEnvironment.HYBRID: self._setup_hybrid,
            TestEnvironment.AWS: self._setup_aws
        }
        setup_funcs.get(self.environment, lambda: None)()

    def _setup_mock(self):
        """Setup MOCK mode - create in-memory Glue database and table."""
        self.log_info("Setting up MOCK environment")
        mock_glue = self.factory.get_mock_glue_client()

        # Create database
        try:
            mock_glue.get_database(Name=self.test_database)
        except Exception:
            mock_glue.create_database(DatabaseInput={
                "Name": self.test_database,
                "Description": "Test database"
            })

        # Create table
        try:
            mock_glue.get_table(DatabaseName=self.test_database, Name=self.test_table)
        except Exception:
            self._create_mock_table(mock_glue)

    def _setup_hybrid(self):
        """Setup HYBRID mode - LocalStack + Mocks."""
        self.log_info("Setting up HYBRID environment (LocalStack + Mocks)")
        # Use same mock Glue setup (Glue not in LocalStack Community)
        self._setup_mock()
        # TODO: Deploy Lambda to LocalStack when implementing HYBRID tests

    def _setup_aws(self):
        """Setup AWS mode - verify infrastructure exists."""
        self.log_info("Using AWS environment")
        glue = self.factory.create_glue_client()
        try:
            glue.get_database(Name=self.test_database)
            self.log_info(f"Database '{self.test_database}' found")
        except Exception as e:
            self.log_warning(f"Database '{self.test_database}' not found: {e}")

    def _create_mock_table(self, glue_client):
        """Create mock table with minimal schema for testing."""
        # Minimal schema for MOCK mode testing
        # Real AWS table has 48 fields, but for mocks we only need common ones
        glue_client.create_simple_table(
            db_name=self.test_database,
            table_name=self.test_table,
            columns=[
                {"Name": "field_text", "Type": "string"},
                {"Name": "field_number", "Type": "decimal"},
                {"Name": "field_checkbox", "Type": "boolean"},
                {"Name": "field_single_select", "Type": "string"},
                {"Name": "field_multi_select", "Type": "array<string>"},
                {"Name": "field_date_time", "Type": "timestamp"},
            ],
            parameters={"larkBaseId": "mock_base", "larkTableId": "mock_table"}
        )

    def teardown(self):
        """Cleanup test environment."""
        self.factory.cleanup()

    # Logging helpers
    def log_info(self, msg: str):
        """Log info (only if verbose)."""
        if self.verbose:
            print(f"[INFO] {msg}")

    def log_success(self, msg: str):
        """Log test success."""
        print(f"[PASS] {msg}")
        self.test_results["passed"] += 1
        self.test_results["total"] += 1

    def log_error(self, msg: str):
        """Log test failure."""
        print(f"[FAIL] {msg}")
        self.test_results["failed"] += 1
        self.test_results["total"] += 1

    def log_warning(self, msg: str):
        """Log warning."""
        print(f"[WARN] {msg}")
        self.test_results["warnings"] += 1

    def print_summary(self):
        """Print test execution summary."""
        print("\n" + "=" * 80)
        print("TEST SUMMARY")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Total:    {self.test_results['total']}")
        print(f"Passed:   {self.test_results['passed']}")
        print(f"Failed:   {self.test_results['failed']}")
        print(f"Warnings: {self.test_results['warnings']}")

        if self.test_results['failed'] == 0:
            print("\n✓ All tests passed!")
        else:
            print(f"\n✗ {self.test_results['failed']} test(s) failed")
        print("=" * 80)
