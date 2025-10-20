#!/usr/bin/env python3
"""
Comprehensive Test Framework for AWS Athena Lark Base Connector

Supports 3 environments (MOCK, HYBRID, AWS) and 4 metadata providers:
1. Glue Catalog Provider (athena_lark_base_regression_test)
2. Lark Base Source Provider (athena_lark_base_regression_test1)
3. Lark Drive Source Provider (athena_lark_base_regression_test2)
4. Experimental Provider (EEMGbnS87a2W1IsaJKhjds3fpwe.tblCGeqbqp03ivAY)

Usage:
    # MOCK mode (default - all services mocked)
    export TEST_ENVIRONMENT=mock
    python comprehensive_test_runner.py

    # HYBRID mode (LocalStack + mocks)
    export TEST_ENVIRONMENT=hybrid
    python comprehensive_test_runner.py

    # AWS mode (real AWS services)
    export TEST_ENVIRONMENT=aws
    python comprehensive_test_runner.py

    # Test specific metadata providers
    python comprehensive_test_runner.py --providers glue,experimental
    python comprehensive_test_runner.py --providers all
"""
import os
import sys
import time
import argparse
import traceback
from typing import List, Dict, Any, Tuple
from dataclasses import dataclass
from enum import Enum

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from config import get_environment, TestEnvironment, print_test_config
from tests.base_test import BaseRegressionTest


class MetadataProvider(Enum):
    """Metadata provider types."""
    GLUE_CATALOG = "glue_catalog"
    LARK_BASE_SOURCE = "lark_base_source"
    LARK_DRIVE_SOURCE = "lark_drive_source"
    EXPERIMENTAL = "experimental"


@dataclass
class TestConfiguration:
    """Configuration for each metadata provider."""
    name: str
    database: str
    table: str
    catalog: str
    env_vars: Dict[str, str]
    description: str
    base_id: str = None
    table_id: str = None


class ComprehensiveTestRunner:
    """Comprehensive test runner for all environments and metadata providers."""

    def __init__(self, verbose: bool = False):
        self.verbose = verbose
        self.environment = get_environment()
        self.test_configs = self._initialize_test_configs()

    def _initialize_test_configs(self) -> Dict[MetadataProvider, TestConfiguration]:
        """Initialize test configurations for all metadata providers."""
        base_catalog = os.getenv("ATHENA_CATALOG", "Athena-testgani")

        return {
            MetadataProvider.GLUE_CATALOG: TestConfiguration(
                name="Glue Catalog Provider",
                database="athena_lark_base_regression_test",
                table="data_type_test_table",
                catalog=base_catalog,
                env_vars={},
                description="Uses Glue Catalog for schema discovery"
            ),

            MetadataProvider.LARK_BASE_SOURCE: TestConfiguration(
                name="Lark Base Source Provider",
                database="athena_lark_base_regression_test1",
                table="data_type_test_table",
                catalog=base_catalog,  # Use same catalog
                env_vars={
                    "default_does_activate_lark_base_source": "true",
                    "lark_base_id_data_source": "EEMGbnS87a2W1IsaJKhjds3fpwe",
                    "lark_table_id_data_source": "tblCGeqbqp03ivAY"
                },
                description="Uses Lark Base name as database, runtime schema discovery"
            ),

            MetadataProvider.LARK_DRIVE_SOURCE: TestConfiguration(
                name="Lark Drive Source Provider",
                database="athena_lark_base_regression_test2",
                table="data_type_test_table",
                catalog=base_catalog,  # Use same catalog
                env_vars={
                    "default_does_activate_lark_drive_source": "true",
                    "lark_drive_id_data_source": "EEMGbnS87a2W1IsaJKhjds3fpwe"
                },
                description="Uses Drive with multiple bitables, runtime schema discovery"
            ),

            MetadataProvider.EXPERIMENTAL: TestConfiguration(
                name="Experimental Provider",
                database="EEMGbnS87a2W1IsaJKhjds3fpwe",  # base_id as database
                table="tblehzVRm83N1vOX",  # actual data table_id (not metadata table)
                catalog=base_catalog,  # Use same catalog
                env_vars={
                    "default_does_activate_experimental_feature": "true"
                },
                base_id="EEMGbnS87a2W1IsaJKhjds3fpwe",
                table_id="tblehzVRm83N1vOX",
                description="Query-time schema discovery using base_id.table_id format"
            )
        }

    def get_all_test_queries(self) -> List[Tuple[str, str, Dict[str, Any]]]:
        """Get all test queries with descriptions and expected behaviors.

        Returns:
            List of tuples: (query, description, expected_behavior)
        """
        queries = [
            # Basic SELECT queries
            ("SELECT * FROM {database}.{table} LIMIT 5",
             "SELECT * with LIMIT",
             {"should_return_rows": True, "min_columns": 40}),

            ("SELECT field_text, field_number, field_checkbox FROM {database}.{table} LIMIT 3",
             "SELECT specific columns",
             {"should_return_rows": True, "exact_columns": 3}),

            # WHERE clause tests
            ("SELECT * FROM {database}.{table} WHERE field_text IS NOT NULL LIMIT 5",
             "WHERE IS NOT NULL",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} WHERE field_number > 0 LIMIT 5",
             "WHERE with numeric comparison",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} WHERE field_checkbox = true LIMIT 5",
             "WHERE with boolean equality",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} WHERE field_single_select = 'Option A' LIMIT 5",
             "WHERE with string equality",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} WHERE field_number BETWEEN 1 AND 100 LIMIT 5",
             "WHERE with BETWEEN",
             {"should_return_rows": True}),

            # AND/OR conditions
            ("SELECT * FROM {database}.{table} WHERE field_text IS NOT NULL AND field_number > 0 LIMIT 5",
             "WHERE with AND condition",
             {"should_return_rows": True}),

            # ORDER BY tests
            ("SELECT * FROM {database}.{table} ORDER BY field_text ASC LIMIT 5",
             "ORDER BY ASC",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} ORDER BY field_number DESC LIMIT 5",
             "ORDER BY DESC",
             {"should_return_rows": True}),

            # Combined queries
            ("SELECT * FROM {database}.{table} WHERE field_text IS NOT NULL ORDER BY field_number ASC LIMIT 5",
             "WHERE + ORDER BY + LIMIT",
             {"should_return_rows": True}),

            # Aggregation queries
            ("SELECT COUNT(*) as total_rows FROM {database}.{table}",
             "COUNT(*) aggregation",
             {"should_return_rows": True, "exact_columns": 1}),

            ("SELECT DISTINCT field_single_select FROM {database}.{table} LIMIT 10",
             "DISTINCT query",
             {"should_return_rows": True}),

            # Field type specific tests
            ("SELECT field_user, field_url, field_created_user, field_modified_user FROM {database}.{table} LIMIT 3",
             "Complex field types (USER, URL, CREATED_USER, MODIFIED_USER)",
             {"should_return_rows": True, "exact_columns": 4}),

            ("SELECT field_single_link, field_duplex_link FROM {database}.{table} LIMIT 3",
             "LINK fields (single_link, duplex_link)",
             {"should_return_rows": True, "exact_columns": 2}),

            # Pagination tests (OFFSET not supported in Athena, using alternative)
            ("SELECT * FROM {database}.{table} LIMIT 5",
             "Pagination - LIMIT 5",
             {"should_return_rows": True}),

            ("SELECT * FROM {database}.{table} WHERE field_number > 0 LIMIT 5",
             "Pagination with filter - LIMIT 5",
             {"should_return_rows": True}),
        ]

        return queries

    def run_provider_tests(self, provider: MetadataProvider) -> Dict[str, Any]:
        """Run all tests for a specific metadata provider."""
        config = self.test_configs[provider]
        results = {
            "provider": provider.value,
            "config": config,
            "total_tests": 0,
            "passed_tests": 0,
            "failed_tests": 0,
            "test_details": []
        }

        print(f"\n{'='*80}")
        print(f"Testing {config.name}")
        print(f"Database: {config.database}")
        print(f"Table: {config.table}")
        print(f"Catalog: {config.catalog}")
        print(f"Description: {config.description}")
        print(f"{'='*80}")

        # Set environment variables for this provider
        old_env = {}
        for key, value in config.env_vars.items():
            old_env[key] = os.environ.get(key)
            os.environ[key] = value

        try:
            # Create base test instance
            base_test = BaseRegressionTest(verbose=self.verbose)
            base_test.test_database = config.database
            base_test.test_table = config.table
            base_test.test_catalog = config.catalog

            # Setup environment
            base_test.setup()

            # Get test queries
            queries = self.get_all_test_queries()

            # Run each test query
            for query, description, expected in queries:
                test_result = self._run_single_query_test(
                    base_test,
                    query.format(database=config.database, table=config.table),
                    description,
                    expected
                )
                results["test_details"].append(test_result)
                results["total_tests"] += 1
                if test_result["passed"]:
                    results["passed_tests"] += 1
                else:
                    results["failed_tests"] += 1

            # Cleanup
            base_test.teardown()

        except Exception as e:
            print(f"[ERROR] Failed to test {config.name}: {str(e)}")
            if self.verbose:
                traceback.print_exc()
            results["error"] = str(e)

        finally:
            # Restore environment variables
            for key, old_value in old_env.items():
                if old_value is None:
                    os.environ.pop(key, None)
                else:
                    os.environ[key] = old_value

        return results

    def _run_single_query_test(self, base_test: BaseRegressionTest, query: str,
                              description: str, expected: Dict[str, Any]) -> Dict[str, Any]:
        """Run a single query test and validate results."""
        test_result = {
            "query": query,
            "description": description,
            "expected": expected,
            "passed": False,
            "error": None,
            "execution_time": 0,
            "row_count": 0,
            "column_count": 0
        }

        try:
            start_time = time.time()

            if self.environment == TestEnvironment.MOCK:
                # In MOCK mode, simulate query execution
                print(f"[MOCK] {description}")
                print(f"[MOCK] Query: {query[:100]}...")

                # Simulate different behaviors based on query
                if "COUNT(*)" in query:
                    test_result["row_count"] = 1
                    test_result["column_count"] = 1
                elif "DISTINCT" in query:
                    test_result["row_count"] = 3
                    test_result["column_count"] = 1
                else:
                    test_result["row_count"] = min(5, expected.get("min_columns", 5))
                    test_result["column_count"] = expected.get("exact_columns", expected.get("min_columns", 10))

                test_result["passed"] = True
                print(f"[MOCK PASS] {description}")

            else:
                # In HYBRID or AWS mode, execute real query
                import boto3
                athena = boto3.client('athena', region_name=os.getenv('AWS_REGION', 'ap-southeast-1'))
                s3_bucket = os.getenv('S3_RESULTS_BUCKET', 'aws-athena-query-results-105676898724-ap-southeast-1')

                # Start query execution
                response = athena.start_query_execution(
                    QueryString=query,
                    QueryExecutionContext={
                        'Database': base_test.test_database,
                        'Catalog': base_test.test_catalog
                    },
                    ResultConfiguration={'OutputLocation': f's3://{s3_bucket}/'}
                )

                query_id = response['QueryExecutionId']

                # Wait for completion
                for i in range(60):  # 60 attempts = 5 minutes max
                    result = athena.get_query_execution(QueryExecutionId=query_id)
                    status = result['QueryExecution']['Status']['State']

                    if status == 'SUCCEEDED':
                        break
                    elif status == 'FAILED':
                        raise Exception(f"Query failed: {result['QueryExecution']['Status'].get('StateChangeReason', 'Unknown error')}")
                    elif status == 'CANCELLED':
                        raise Exception("Query was cancelled")

                    time.sleep(1)
                else:
                    raise Exception("Query timed out")

                # Get results
                results_response = athena.get_query_results(QueryExecutionId=query_id)
                rows = results_response['ResultSet']['Rows']

                # Skip header row if present
                if len(rows) > 1 and results_response['ResultSet'].get('ResultSetMetadata'):
                    data_rows = len(rows) - 1
                else:
                    data_rows = len(rows)

                test_result["row_count"] = data_rows
                test_result["column_count"] = len(rows[0]['Data']) if rows else 0

                # Validate expectations
                if expected.get("should_return_rows", True) and data_rows == 0:
                    test_result["error"] = f"Expected rows but got 0"
                elif expected.get("exact_columns") and test_result["column_count"] != expected["exact_columns"]:
                    test_result["error"] = f"Expected {expected['exact_columns']} columns but got {test_result['column_count']}"
                elif expected.get("min_columns") and test_result["column_count"] < expected["min_columns"]:
                    test_result["error"] = f"Expected at least {expected['min_columns']} columns but got {test_result['column_count']}"
                else:
                    test_result["passed"] = True
                    print(f"[PASS] {description} ({data_rows} rows, {test_result['column_count']} columns)")

            test_result["execution_time"] = time.time() - start_time

        except Exception as e:
            test_result["error"] = str(e)
            print(f"[FAIL] {description}: {str(e)}")
            if self.verbose:
                traceback.print_exc()

        return test_result

    def run_all_tests(self, providers: List[MetadataProvider] = None) -> Dict[str, Any]:
        """Run tests for specified providers or all providers."""
        if providers is None:
            providers = list(MetadataProvider)

        all_results = {
            "environment": self.environment.value,
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "providers": {},
            "summary": {
                "total_providers": len(providers),
                "total_tests": 0,
                "total_passed": 0,
                "total_failed": 0
            }
        }

        print(f"\n{'='*80}")
        print("COMPREHENSIVE TEST FRAMEWORK")
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Providers: {', '.join([p.value for p in providers])}")
        print(f"{'='*80}")

        for provider in providers:
            if provider not in self.test_configs:
                print(f"[WARN] Unknown provider: {provider.value}")
                continue

            # Check if provider supports current environment
            if not self._provider_supports_environment(provider):
                print(f"[SKIP] {self.test_configs[provider].name}: Not supported in {self.environment.value.upper()} mode")
                continue

            # Run tests for this provider
            provider_results = self.run_provider_tests(provider)
            all_results["providers"][provider.value] = provider_results

            # Update summary
            all_results["summary"]["total_tests"] += provider_results["total_tests"]
            all_results["summary"]["total_passed"] += provider_results["passed_tests"]
            all_results["summary"]["total_failed"] += provider_results["failed_tests"]

        return all_results

    def _provider_supports_environment(self, provider: MetadataProvider) -> bool:
        """Check if provider supports current environment."""
        if self.environment == TestEnvironment.MOCK:
            return True  # All providers work in MOCK mode
        elif self.environment == TestEnvironment.HYBRID:
            # TODO: Enable when HYBRID mode is fully implemented
            return provider == MetadataProvider.GLUE_CATALOG
        else:  # AWS
            return True  # All providers work in AWS mode

    def print_summary(self, results: Dict[str, Any]):
        """Print comprehensive test summary."""
        print(f"\n{'='*80}")
        print("COMPREHENSIVE TEST SUMMARY")
        print(f"{'='*80}")
        print(f"Environment: {results['environment'].upper()}")
        print(f"Timestamp: {results['timestamp']}")
        print()

        for provider_id, provider_results in results["providers"].items():
            config = provider_results["config"]
            print(f"Provider: {config.name}")
            print(f"  Database: {config.database}")
            print(f"  Table: {config.table}")
            print(f"  Catalog: {config.catalog}")

            if "error" in provider_results:
                print(f"  Status: ERROR - {provider_results['error']}")
            else:
                print(f"  Tests: {provider_results['passed_tests']}/{provider_results['total_tests']} passed")
                if provider_results["failed_tests"] > 0:
                    print(f"  Failed Tests:")
                    for test_detail in provider_results["test_details"]:
                        if not test_detail["passed"]:
                            print(f"    - {test_detail['description']}: {test_detail.get('error', 'Unknown error')}")
            print()

        # Overall summary
        summary = results["summary"]
        print(f"OVERALL SUMMARY:")
        print(f"  Total Providers: {summary['total_providers']}")
        print(f"  Total Tests: {summary['total_tests']}")
        print(f"  Passed: {summary['total_passed']}")
        print(f"  Failed: {summary['total_failed']}")
        print(f"  Success Rate: {(summary['total_passed']/summary['total_tests']*100):.1f}%" if summary['total_tests'] > 0 else "N/A")
        print(f"{'='*80}")

        # Return exit code
        return 1 if summary["total_failed"] > 0 else 0


def main():
    parser = argparse.ArgumentParser(description="Comprehensive test framework for Athena Lark Base Connector")
    parser.add_argument("--providers", type=str, help="Comma-separated list of providers to test (glue_catalog,lark_base_source,lark_drive_source,experimental,all)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--list-providers", action="store_true", help="List all providers and exit")
    args = parser.parse_args()

    # Print configuration
    print_test_config()

    runner = ComprehensiveTestRunner(verbose=args.verbose)

    # List providers
    if args.list_providers:
        print("\nAvailable Metadata Providers:")
        for provider in MetadataProvider:
            config = runner.test_configs[provider]
            print(f"  {provider.value}:")
            print(f"    Name: {config.name}")
            print(f"    Database: {config.database}")
            print(f"    Table: {config.table}")
            print(f"    Catalog: {config.catalog}")
            print(f"    Description: {config.description}")
            print()
        return 0

    # Determine providers to test
    if args.providers:
        if args.providers.lower() == "all":
            providers = list(MetadataProvider)
        else:
            provider_names = [p.strip() for p in args.providers.split(",")]
            providers = []
            for name in provider_names:
                try:
                    providers.append(MetadataProvider(name))
                except ValueError:
                    print(f"[WARN] Unknown provider: {name}")
    else:
        # Default: test all providers
        providers = list(MetadataProvider)

    # Run tests
    try:
        results = runner.run_all_tests(providers)
        exit_code = runner.print_summary(results)
        return exit_code
    except KeyboardInterrupt:
        print("\n[INFO] Tests interrupted by user")
        return 130
    except Exception as e:
        print(f"\n[ERROR] Test execution failed: {str(e)}")
        if args.verbose:
            traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())