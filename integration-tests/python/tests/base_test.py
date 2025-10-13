"""
Base test class for regression tests.

Provides common functionality for all regression tests including:
- Environment-aware AWS client creation
- Test data setup/teardown
- Lark API mocking in MOCK/HYBRID modes
"""
import sys
import os
from typing import Any

# Add parent directory to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from clients import AWSClientFactory
from config import (
    get_environment,
    TestEnvironment,
    TEST_DATABASE,
    TEST_TABLE,
    TEST_CATALOG,
    LARK_APP_ID,
    LARK_APP_SECRET,
    LARK_BASE_TOKEN,
    LARK_TABLE_ID
)


class BaseRegressionTest:
    """
    Base class for regression tests.

    Provides environment-aware setup and teardown.
    """

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.environment = get_environment()
        self.factory = AWSClientFactory()

        # Test configuration
        self.test_database = TEST_DATABASE
        self.test_table = TEST_TABLE
        self.test_catalog = TEST_CATALOG
        self.lark_app_id = LARK_APP_ID
        self.lark_app_secret = LARK_APP_SECRET
        self.lark_base_token = LARK_BASE_TOKEN
        self.lark_table_id = LARK_TABLE_ID

        # Test results
        self.test_results = {
            "total": 0,
            "passed": 0,
            "failed": 0,
            "warnings": 0
        }

    def setup(self):
        """Setup test environment."""
        if self.environment == TestEnvironment.MOCK:
            self._setup_mock_environment()
        elif self.environment == TestEnvironment.HYBRID:
            self._setup_hybrid_environment()
        elif self.environment == TestEnvironment.AWS:
            self._setup_aws_environment()

    def _setup_mock_environment(self):
        """Setup MOCK mode environment."""
        if self.verbose:
            print(f"[MOCK] Setting up mock environment")

        # Pre-populate mock Glue with test database and table
        mock_glue = self.factory.get_mock_glue_client()

        # Create test database if it doesn't exist
        try:
            mock_glue.get_database(Name=self.test_database)
        except Exception:
            mock_glue.create_database(DatabaseInput={
                "Name": self.test_database,
                "Description": "Test database for regression tests"
            })

        # Create test table if it doesn't exist
        try:
            mock_glue.get_table(
                DatabaseName=self.test_database,
                Name=self.test_table
            )
        except Exception:
            self._create_test_table(mock_glue)

    def _setup_hybrid_environment(self):
        """Setup HYBRID mode environment."""
        if self.verbose:
            print(f"[HYBRID] Setting up hybrid environment (LocalStack + Mocks)")

        # Same as MOCK for Glue (not available in LocalStack Community)
        self._setup_mock_environment()

        # TODO: Deploy Lambda to LocalStack if needed

    def _setup_aws_environment(self):
        """Setup AWS mode environment."""
        if self.verbose:
            print(f"[AWS] Using real AWS environment")

        # In AWS mode, assume infrastructure is already set up
        # Just verify it exists
        glue = self.factory.create_glue_client()

        try:
            glue.get_database(Name=self.test_database)
            if self.verbose:
                print(f"[AWS] Database '{self.test_database}' exists")
        except Exception as e:
            print(f"[AWS] Warning: Database '{self.test_database}' not found: {e}")

    def _create_test_table(self, glue_client):
        """Create test table with standard schema."""
        glue_client.create_simple_table(
            db_name=self.test_database,
            table_name=self.test_table,
            columns=[
                {"Name": "field_text", "Type": "string"},
                {"Name": "field_barcode", "Type": "string"},
                {"Name": "field_single_select", "Type": "string"},
                {"Name": "field_phone", "Type": "string"},
                {"Name": "field_email", "Type": "string"},
                {"Name": "field_auto_number", "Type": "string"},
                {"Name": "field_number", "Type": "decimal"},
                {"Name": "field_progress", "Type": "decimal"},
                {"Name": "field_currency", "Type": "decimal"},
                {"Name": "field_rating", "Type": "tinyint"},
                {"Name": "field_checkbox", "Type": "boolean"},
                {"Name": "field_date_time", "Type": "timestamp"},
                {"Name": "field_created_time", "Type": "timestamp"},
                {"Name": "field_modified_time", "Type": "timestamp"},
                {"Name": "field_multi_select", "Type": "array<string>"},
            ],
            parameters={
                "larkBaseId": self.lark_base_token,
                "larkTableId": self.lark_table_id,
                "larkBaseDataSourceId": self.lark_base_token,
                "larkTableDataSourceId": self.lark_table_id,
                "crawlingMethod": "test"
            }
        )

    def teardown(self):
        """Cleanup test environment."""
        self.factory.cleanup()

    def log_info(self, msg: str):
        """Log info message."""
        if self.verbose:
            print(f"[INFO] {msg}")

    def log_success(self, msg: str):
        """Log success message."""
        print(f"[PASS] {msg}")
        self.test_results["passed"] += 1
        self.test_results["total"] += 1

    def log_error(self, msg: str):
        """Log error message."""
        print(f"[FAIL] {msg}")
        self.test_results["failed"] += 1
        self.test_results["total"] += 1

    def log_warning(self, msg: str):
        """Log warning message."""
        print(f"[WARN] {msg}")
        self.test_results["warnings"] += 1

    def print_summary(self):
        """Print test summary."""
        print("\n" + "=" * 80)
        print("Test Summary")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Total Tests: {self.test_results['total']}")
        print(f"Passed: {self.test_results['passed']}")
        print(f"Failed: {self.test_results['failed']}")
        print(f"Warnings: {self.test_results['warnings']}")

        if self.test_results['failed'] == 0:
            print("\n✓ All tests passed!")
        else:
            print(f"\n✗ {self.test_results['failed']} test(s) failed")

        print("=" * 80)
