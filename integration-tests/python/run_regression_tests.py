#!/usr/bin/env python3
"""
Master Regression Test Runner

Runs all regression tests for AWS Athena Lark Base Connector.
Supports three test modes: MOCK, HYBRID, AWS

Usage:
    # MOCK mode (default, 100% free)
    export TEST_ENVIRONMENT=mock
    python run_regression_tests.py

    # HYBRID mode (LocalStack Community, 100% free)
    export TEST_ENVIRONMENT=hybrid
    python run_regression_tests.py

    # AWS mode (real AWS, minimal cost with Free Tier)
    export TEST_ENVIRONMENT=aws
    python run_regression_tests.py

    # Run specific tests only
    python run_regression_tests.py --tests glue_crawler,pushdown

    # Verbose output
    python run_regression_tests.py --verbose
"""
import sys
import os
import argparse
import subprocess
from typing import List, Dict
import time

# Add current directory to path
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from config import get_environment, TestEnvironment, print_test_config


# Available regression tests
AVAILABLE_TESTS = {
    "glue_crawler": {
        "name": "Glue Crawler Test",
        "script": "tests/regression/test_glue_crawler.py",
        "description": "Tests Glue crawler Lambda function",
        "modes": ["mock", "hybrid", "aws"]
    },
    # TODO: Add more tests as they are migrated
    # "pushdown": {
    #     "name": "Pushdown Predicates Test",
    #     "script": "tests/regression/test_pushdown.py",
    #     "description": "Tests filter pushdown and sorting",
    #     "modes": ["mock", "hybrid", "aws"]
    # },
}


class RegressionTestRunner:
    """Master regression test runner."""

    def __init__(self, verbose: bool = False, tests: List[str] = None):
        self.verbose = verbose
        self.environment = get_environment()
        self.tests_to_run = tests or list(AVAILABLE_TESTS.keys())

        self.results = {
            "total": 0,
            "passed": 0,
            "failed": 0,
            "skipped": 0
        }

    def run_all_tests(self) -> int:
        """Run all selected regression tests."""
        print("=" * 80)
        print("AWS Athena Lark Base Connector - Regression Test Suite")
        print("=" * 80)
        print_test_config()
        print()

        start_time = time.time()

        for test_key in self.tests_to_run:
            if test_key not in AVAILABLE_TESTS:
                print(f"[WARN] Unknown test: {test_key}, skipping")
                continue

            test_config = AVAILABLE_TESTS[test_key]

            # Check if test supports current environment
            if self.environment.value not in test_config["modes"]:
                print(f"\n[SKIP] {test_config['name']}: Not supported in {self.environment.value.upper()} mode")
                self.results["skipped"] += 1
                self.results["total"] += 1
                continue

            # Run the test
            success = self._run_test(test_key, test_config)

            self.results["total"] += 1
            if success:
                self.results["passed"] += 1
            else:
                self.results["failed"] += 1

        elapsed_time = time.time() - start_time

        # Print summary
        self._print_summary(elapsed_time)

        # Return exit code
        return 0 if self.results["failed"] == 0 else 1

    def _run_test(self, test_key: str, test_config: Dict) -> bool:
        """Run a single test."""
        print(f"\n{'=' * 80}")
        print(f"Running: {test_config['name']}")
        print(f"Description: {test_config['description']}")
        print(f"{'=' * 80}\n")

        script_path = os.path.join(os.path.dirname(__file__), test_config['script'])

        if not os.path.exists(script_path):
            print(f"[ERROR] Test script not found: {script_path}")
            return False

        # Build command
        cmd = [sys.executable, script_path]
        if self.verbose:
            cmd.append("--verbose")

        # Set environment variables
        env = os.environ.copy()
        env["TEST_ENVIRONMENT"] = self.environment.value

        try:
            # Run the test
            result = subprocess.run(
                cmd,
                env=env,
                capture_output=False,  # Show output in real-time
                check=False
            )

            success = result.returncode == 0

            if success:
                print(f"\n[PASS] {test_config['name']} completed successfully")
            else:
                print(f"\n[FAIL] {test_config['name']} failed with exit code {result.returncode}")

            return success

        except Exception as e:
            print(f"\n[ERROR] Failed to run {test_config['name']}: {str(e)}")
            return False

    def _print_summary(self, elapsed_time: float):
        """Print test suite summary."""
        print("\n" + "=" * 80)
        print("REGRESSION TEST SUITE SUMMARY")
        print("=" * 80)
        print(f"Environment: {self.environment.value.upper()}")
        print(f"Total Tests: {self.results['total']}")
        print(f"Passed: {self.results['passed']}")
        print(f"Failed: {self.results['failed']}")
        print(f"Skipped: {self.results['skipped']}")
        print(f"Time: {elapsed_time:.2f} seconds")
        print("=" * 80)

        if self.results['failed'] == 0:
            print("\n✓ All regression tests passed!")
        else:
            print(f"\n✗ {self.results['failed']} test(s) failed")

        # Print next steps based on environment
        self._print_next_steps()

    def _print_next_steps(self):
        """Print recommended next steps based on environment."""
        print("\n" + "=" * 80)
        print("NEXT STEPS")
        print("=" * 80)

        if self.environment == TestEnvironment.MOCK:
            print("MOCK mode testing complete!")
            print("\nTo test with LocalStack (HYBRID mode):")
            print("  1. Start LocalStack:")
            print("     cd integration-tests/src/main/resources/localstack")
            print("     docker-compose up -d")
            print("  2. Run tests:")
            print("     export TEST_ENVIRONMENT=hybrid")
            print("     python run_regression_tests.py")

        elif self.environment == TestEnvironment.HYBRID:
            print("HYBRID mode testing complete!")
            print("\nTo test against real AWS:")
            print("  export TEST_ENVIRONMENT=aws")
            print("  python run_regression_tests.py")
            print("\nNote: AWS testing will incur minimal costs (~$0.03/month with Free Tier)")

        elif self.environment == TestEnvironment.AWS:
            print("AWS mode testing complete!")
            print("\nYour connector is validated against real AWS infrastructure.")
            print("Consider running MOCK/HYBRID tests locally to save costs:")
            print("  export TEST_ENVIRONMENT=mock")
            print("  python run_regression_tests.py")

        print("=" * 80)


def main():
    parser = argparse.ArgumentParser(
        description="Run regression tests for AWS Athena Lark Base Connector",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Run all tests in MOCK mode (default, free)
  python run_regression_tests.py

  # Run all tests in HYBRID mode (LocalStack, free)
  export TEST_ENVIRONMENT=hybrid
  python run_regression_tests.py

  # Run all tests in AWS mode (real AWS)
  export TEST_ENVIRONMENT=aws
  python run_regression_tests.py

  # Run specific tests only
  python run_regression_tests.py --tests glue_crawler,pushdown

  # Verbose output
  python run_regression_tests.py --verbose

Available tests:
""" + "\n".join([f"  - {key}: {config['description']}"
                 for key, config in AVAILABLE_TESTS.items()])
    )

    parser.add_argument(
        "--tests",
        type=str,
        help="Comma-separated list of tests to run (default: all)"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Verbose output"
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="List available tests and exit"
    )

    args = parser.parse_args()

    # List tests and exit
    if args.list:
        print("Available regression tests:")
        print()
        for key, config in AVAILABLE_TESTS.items():
            print(f"  {key}:")
            print(f"    Name: {config['name']}")
            print(f"    Description: {config['description']}")
            print(f"    Modes: {', '.join(config['modes'])}")
            print()
        sys.exit(0)

    # Parse tests to run
    tests_to_run = None
    if args.tests:
        tests_to_run = [t.strip() for t in args.tests.split(",")]

    # Run tests
    runner = RegressionTestRunner(verbose=args.verbose, tests=tests_to_run)
    exit_code = runner.run_all_tests()

    sys.exit(exit_code)


if __name__ == "__main__":
    main()
