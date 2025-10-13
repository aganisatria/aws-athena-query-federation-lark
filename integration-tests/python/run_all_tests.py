#!/usr/bin/env python3
"""
Master Test Runner - Runs ALL Tests (New + Legacy)

This script runs:
1. New regression tests in integration-tests/python/tests/regression/
2. Legacy tests from project root (until fully migrated)

Usage:
    # MOCK mode (default - query validation only, no AWS)
    export TEST_ENVIRONMENT=mock
    python run_all_tests.py

    # HYBRID mode (LocalStack + mocks)
    export TEST_ENVIRONMENT=hybrid
    python run_all_tests.py

    # AWS mode (real AWS services)
    export TEST_ENVIRONMENT=aws
    python run_all_tests.py
"""
import sys
import os
import subprocess
import argparse

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from config import get_environment, TestEnvironment, print_test_config

# Project root directory
PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))

# All available tests
ALL_TESTS = {
    # Migrated tests (in integration-tests module)
    "glue_crawler": {
        "name": "Glue Crawler Test",
        "script": "integration-tests/python/tests/regression/test_glue_crawler.py",
        "migrated": True,
        "modes": ["mock", "hybrid", "aws"]
    },
    "pushdown": {
        "name": "Pushdown Predicates Test",
        "script": "integration-tests/python/tests/regression/test_pushdown.py",
        "migrated": True,
        "modes": ["mock", "aws"]  # Athena not in LocalStack Community
    },
    "search_api_filters": {
        "name": "Search API Filters Test",
        "script": "integration-tests/python/tests/regression/test_search_api_filters.py",
        "migrated": True,
        "modes": ["mock", "hybrid", "aws"]
    },
    "api_comparison": {
        "name": "API Comparison Test",
        "script": "integration-tests/python/tests/regression/test_api_comparison.py",
        "migrated": True,
        "modes": ["mock", "hybrid", "aws"]
    },
    "json_filters": {
        "name": "JSON Filters Test",
        "script": "integration-tests/python/tests/regression/test_json_filters.py",
        "migrated": True,
        "modes": ["mock", "hybrid", "aws"]
    },
    "like_pushdown": {
        "name": "LIKE Pushdown Test",
        "script": "integration-tests/python/tests/regression/test_like_pushdown.py",
        "migrated": True,
        "modes": ["mock", "aws"]  # Athena not in LocalStack Community
    },
    "all_pushdown_filters": {
        "name": "All Pushdown Filters Test",
        "script": "integration-tests/python/tests/regression/test_all_pushdown_filters.py",
        "migrated": True,
        "modes": ["mock", "aws"]  # Athena not in LocalStack Community
    },
}


def run_test(test_key, test_config, verbose=False):
    """Run a single test"""
    print(f"\n{'='*80}")
    print(f"Running: {test_config['name']}")
    if not test_config['migrated']:
        print(f"⚠️  Legacy test (not yet migrated)")
    if 'note' in test_config:
        print(f"Note: {test_config['note']}")
    print(f"{'='*80}\n")

    # Build script path
    if test_config['migrated']:
        script_path = os.path.join(PROJECT_ROOT, test_config['script'])
    else:
        script_path = os.path.join(PROJECT_ROOT, test_config['script'])

    if not os.path.exists(script_path):
        print(f"[SKIP] Test script not found: {script_path}")
        return True  # Don't count as failure

    # Build command
    cmd = [sys.executable, script_path]
    if verbose:
        cmd.append("--verbose")

    # Set environment
    env = os.environ.copy()
    env["TEST_ENVIRONMENT"] = get_environment().value

    try:
        result = subprocess.run(cmd, env=env, capture_output=False, check=False)
        success = result.returncode == 0

        if success:
            print(f"\n[PASS] {test_config['name']} completed successfully")
        else:
            print(f"\n[FAIL] {test_config['name']} failed with exit code {result.returncode}")

        return success

    except Exception as e:
        print(f"\n[ERROR] Failed to run {test_config['name']}: {str(e)}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Run all regression tests (migrated + legacy)")
    parser.add_argument("--tests", type=str, help="Comma-separated list of tests to run")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    parser.add_argument("--migrated-only", action="store_true", help="Run only migrated tests")
    parser.add_argument("--legacy-only", action="store_true", help="Run only legacy tests")
    parser.add_argument("--list", action="store_true", help="List all tests and exit")
    args = parser.parse_args()

    environment = get_environment()

    # List tests
    if args.list:
        print("Available tests:")
        print()
        for key, config in ALL_TESTS.items():
            status = "✅ Migrated" if config['migrated'] else "⏳ Legacy (needs migration)"
            print(f"  {key}:")
            print(f"    Name: {config['name']}")
            print(f"    Status: {status}")
            print(f"    Modes: {', '.join(config['modes'])}")
            if 'note' in config:
                print(f"    Note: {config['note']}")
            print()
        return 0

    # Print configuration
    print("="*80)
    print("AWS Athena Lark Base Connector - Complete Test Suite")
    print("="*80)
    print_test_config()
    print()

    # Determine which tests to run
    if args.tests:
        tests_to_run = [t.strip() for t in args.tests.split(",")]
    else:
        tests_to_run = list(ALL_TESTS.keys())

    # Filter by migration status
    if args.migrated_only:
        tests_to_run = [t for t in tests_to_run if ALL_TESTS[t]['migrated']]
    elif args.legacy_only:
        tests_to_run = [t for t in tests_to_run if not ALL_TESTS[t]['migrated']]

    # Run tests
    results = {"total": 0, "passed": 0, "failed": 0, "skipped": 0}

    for test_key in tests_to_run:
        if test_key not in ALL_TESTS:
            print(f"[WARN] Unknown test: {test_key}")
            continue

        test_config = ALL_TESTS[test_key]

        # Check if test supports current environment
        if environment.value not in test_config['modes']:
            print(f"\n[SKIP] {test_config['name']}: Not supported in {environment.value.upper()} mode")
            results['skipped'] += 1
            results['total'] += 1
            continue

        # Run test
        success = run_test(test_key, test_config, args.verbose)
        results['total'] += 1
        if success:
            results['passed'] += 1
        else:
            results['failed'] += 1

    # Print summary
    print("\n" + "="*80)
    print("COMPLETE TEST SUITE SUMMARY")
    print("="*80)
    print(f"Environment: {environment.value.upper()}")
    print(f"Total: {results['total']}")
    print(f"Passed: {results['passed']}")
    print(f"Failed: {results['failed']}")
    print(f"Skipped: {results['skipped']}")
    print("="*80)

    if results['failed'] == 0:
        print("\n✓ All tests passed!")
    else:
        print(f"\n✗ {results['failed']} test(s) failed")

    # Print migration status
    migrated_count = sum(1 for t in ALL_TESTS.values() if t['migrated'])
    total_count = len(ALL_TESTS)
    print(f"\nMigration Progress: {migrated_count}/{total_count} tests migrated")

    if migrated_count < total_count:
        print("\nLegacy tests remaining:")
        for key, config in ALL_TESTS.items():
            if not config['migrated']:
                print(f"  - {key}: {config['name']}")
        print("\nSee MIGRATION_GUIDE.md for migration instructions")
    else:
        print("\n✅ All tests have been migrated to the new framework!")
        print("You can now run all tests in MOCK, HYBRID, or AWS modes.")

    return 1 if results['failed'] > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
