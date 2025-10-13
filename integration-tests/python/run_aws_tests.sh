#!/bin/bash
#
# Run integration tests in AWS mode
#
# This script loads environment variables from ../../.env and runs tests

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/../.."

# Load environment variables from .env
if [ -f "$PROJECT_ROOT/.env" ]; then
    echo "Loading environment variables from .env..."
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
else
    echo "Error: .env file not found at $PROJECT_ROOT/.env"
    exit 1
fi

# Set test environment to AWS
export TEST_ENVIRONMENT=aws

# Print configuration
echo "========================================"
echo "AWS Test Configuration"
echo "========================================"
echo "AWS Region: $AWS_REGION"
echo "Athena Catalog: $ATHENA_CATALOG"
echo "Test Database: $TEST_DATABASE"
echo "Test Table: $TEST_TABLE"
echo "Athena Workgroup: $ATHENA_WORKGROUP"
echo "Crawler Lambda: $GLUE_CRAWLER_LAMBDA_NAME"
echo "========================================"
echo

# Run tests
if [ "$#" -eq 0 ]; then
    # Run all tests
    python "$SCRIPT_DIR/run_all_tests.py" --verbose
else
    # Run specific test
    python "$@"
fi
