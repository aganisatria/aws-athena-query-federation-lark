#!/bin/bash

# ==============================================================================
# Timestamp/Date Regression Tests
# ==============================================================================
# Purpose: Targeted regression testing for timestamp/date type fixes
# ==============================================================================

set -e

# Load environment from .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test configuration
TEST_DATABASE="${TEST_DATABASE:-athena_lark_base_regression_test}"
TEST_TABLE="${TEST_TABLE:-data_type_test_table}"
ATHENA_WORKGROUP="${ATHENA_WORKGROUP:-poweruser}"
ATHENA_CATALOG="${ATHENA_CATALOG:-your-catalog-name}"
OUTPUT_LOCATION="${OUTPUT_LOCATION:-}"
REGION="${AWS_REGION:-ap-southeast-1}"

# Test results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASSED_TESTS++)) || true; }
log_error() { echo -e "${RED}[FAIL]${NC} $1"; ((FAILED_TESTS++)) || true; }

execute_athena_query() {
    local query="$1"
    local query_name="$2"

    log_info "Executing: $query_name" >&2

    if [[ -n "$OUTPUT_LOCATION" ]]; then
        execution_id=$(aws athena start-query-execution \
            --query-string "$query" \
            --query-execution-context "Catalog=$ATHENA_CATALOG,Database=$TEST_DATABASE" \
            --result-configuration "OutputLocation=$OUTPUT_LOCATION" \
            --work-group "$ATHENA_WORKGROUP" \
            --region "$REGION" \
            --query 'QueryExecutionId' \
            --output text)
    else
        execution_id=$(aws athena start-query-execution \
            --query-string "$query" \
            --query-execution-context "Catalog=$ATHENA_CATALOG,Database=$TEST_DATABASE" \
            --work-group "$ATHENA_WORKGROUP" \
            --region "$REGION" \
            --query 'QueryExecutionId' \
            --output text)
    fi

    echo "$execution_id"
}

wait_for_query() {
    local execution_id="$1"
    local max_wait=300
    local elapsed=0

    while [[ $elapsed -lt $max_wait ]]; do
        status=$(aws athena get-query-execution \
            --query-execution-id "$execution_id" \
            --region "$REGION" \
            --query 'QueryExecution.Status.State' \
            --output text)

        case "$status" in
            SUCCEEDED) return 0 ;;
            FAILED|CANCELLED)
                error=$(aws athena get-query-execution \
                    --query-execution-id "$execution_id" \
                    --region "$REGION" \
                    --query 'QueryExecution.Status.StateChangeReason' \
                    --output text)
                log_error "Query failed: $error" >&2
                return 1
                ;;
            QUEUED|RUNNING)
                sleep 2
                ((elapsed+=2))
                ;;
            *)
                log_error "Unknown status: $status" >&2
                return 1
                ;;
        esac
    done

    log_error "Query timeout after ${max_wait}s" >&2
    return 1
}

get_query_results() {
    local execution_id="$1"
    aws athena get-query-results \
        --query-execution-id "$execution_id" \
        --region "$REGION" \
        --output json
}

run_test_query() {
    local query="$1"
    local test_name="$2"

    ((TOTAL_TESTS++)) || true

    execution_id=$(execute_athena_query "$query" "$test_name")
    if [[ $? -ne 0 ]]; then
        log_error "$test_name - Query execution failed"
        return 1
    fi

    if wait_for_query "$execution_id"; then
        results=$(get_query_results "$execution_id")

        # Log results
        echo "Results:"
        echo "$results" | jq -r '.ResultSet.Rows[] | .Data | map(.VarCharValue // "NULL") | @csv'

        log_success "$test_name"
        return 0
    else
        log_error "$test_name - Query execution failed or timed out"
        return 1
    fi
}

# ==============================================================================
# Timestamp/Date Tests
# ==============================================================================

test_timestamp_fields() {
    log_info "==== Testing Timestamp/Date Fields ===="

    # Test 1: DATE_TIME field - Read values
    run_test_query \
        "SELECT field_date_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 10" \
        "Test 1: Read DATE_TIME field (10 rows)"

    # Test 2: CREATED_TIME field
    run_test_query \
        "SELECT field_created_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 2: Read CREATED_TIME field"

    # Test 3: MODIFIED_TIME field
    run_test_query \
        "SELECT field_modified_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 3: Read MODIFIED_TIME field"

    # Test 4: Additional DATE_TIME fields (field_date_time_2 through field_date_time_9)
    run_test_query \
        "SELECT field_date_time_2, field_date_time_3, field_date_time_4 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 4: Read additional DATE_TIME fields (2-4)"

    run_test_query \
        "SELECT field_date_time_5, field_date_time_6, field_date_time_7 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 5: Read additional DATE_TIME fields (5-7)"

    run_test_query \
        "SELECT field_date_time_8, field_date_time_9 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 6: Read additional DATE_TIME fields (8-9)"

    # Test 7: Date formatting
    run_test_query \
        "SELECT date_format(field_date_time, '%Y-%m-%d %H:%i:%s') as formatted_date FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 7: Date formatting on DATE_TIME"

    # Test 8: Formula timestamp fields
    run_test_query \
        "SELECT field_formula_3, date_format(field_formula_3, '%Y-%m-%d') as formatted_formula FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Test 8: Formula timestamp field (field_formula_3)"

    # Test 9: Range check - Past dates (1995)
    run_test_query \
        "SELECT field_date_time, date_format(field_date_time, '%Y') as year FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE date_format(field_date_time, '%Y') = '1995' LIMIT 5" \
        "Test 9: Past dates (1995) verification"

    # Test 10: Range check - Future dates (2035)
    run_test_query \
        "SELECT field_date_time, date_format(field_date_time, '%Y') as year FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE date_format(field_date_time, '%Y') = '2035' LIMIT 5" \
        "Test 10: Future dates (2035) verification"

    # Test 11: Timestamp comparisons
    run_test_query \
        "SELECT COUNT(*) as count, MIN(field_date_time) as min_date, MAX(field_date_time) as max_date FROM \"$TEST_DATABASE\".\"$TEST_TABLE\"" \
        "Test 11: Timestamp aggregations (min/max)"

    # Test 12: All timestamp fields together
    run_test_query \
        "SELECT field_date_time, field_created_time, field_modified_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 3" \
        "Test 12: All main timestamp fields together"
}

# ==============================================================================
# Main Execution
# ==============================================================================

echo "================================================================================"
echo "  Timestamp/Date Regression Tests"
echo "================================================================================"
echo ""
echo "Configuration:"
echo "  Catalog: $ATHENA_CATALOG"
echo "  Database: $TEST_DATABASE"
echo "  Table: $TEST_TABLE"
echo "  Workgroup: $ATHENA_WORKGROUP"
echo "  Region: $REGION"
echo ""
echo "================================================================================"
echo ""

# Run tests
test_timestamp_fields

# Print summary
echo ""
echo "================================================================================"
echo "  Test Summary"
echo "================================================================================"
echo "  Total Tests: $TOTAL_TESTS"
echo -e "  ${GREEN}Passed: $PASSED_TESTS${NC}"
echo -e "  ${RED}Failed: $FAILED_TESTS${NC}"
echo "================================================================================"
echo ""

if [[ $FAILED_TESTS -eq 0 ]]; then
    log_success "All timestamp/date tests passed!"
    exit 0
else
    log_error "$FAILED_TESTS timestamp/date test(s) failed"
    exit 1
fi
