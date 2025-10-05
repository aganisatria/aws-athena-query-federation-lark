#!/bin/bash

# ==============================================================================
# Athena-Lark-Base Connector - Regression Testing Script
# ==============================================================================
#
# Purpose: Comprehensive regression testing for all Lark Base data types
# Comprehensive regression test suite for Athena Lark Base Connector
# Last Updated: 2025-10-04
#
# This script tests the AWS Athena Query Federation connector for Lark Base
# against all 26 supported data types and various edge cases.
#
# ==============================================================================

set -e  # Exit on error

# Load environment variables from .env if it exists
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
fi

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test configuration
TEST_DATABASE="${TEST_DATABASE:-athena_lark_base_regression_test}"
TEST_TABLE="${TEST_TABLE:-data_type_test_table}"
ATHENA_WORKGROUP="${ATHENA_WORKGROUP:-primary}"
ATHENA_CATALOG="${ATHENA_CATALOG:-your-catalog-name}"  # Federated query catalog
OUTPUT_LOCATION="${OUTPUT_LOCATION:-}"  # Empty = use workgroup default
REGION="${AWS_REGION:-us-east-1}"

# Test results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# ==============================================================================
# Helper Functions
# ==============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1" >&2
    ((PASSED_TESTS++)) || true
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1" >&2
    ((FAILED_TESTS++)) || true
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_skip() {
    echo -e "${YELLOW}[SKIP]${NC} $1" >&2
    ((SKIPPED_TESTS++)) || true
}

# Execute Athena query and return query execution ID
execute_athena_query() {
    local query="$1"
    local query_name="$2"

    log_info "Executing: $query_name"

    # Start query execution
    if [[ -n "$OUTPUT_LOCATION" ]]; then
        execution_id=$(aws athena start-query-execution \
            --query-string "$query" \
            --query-execution-context "Catalog=$ATHENA_CATALOG,Database=$TEST_DATABASE" \
            --result-configuration "OutputLocation=$OUTPUT_LOCATION" \
            --work-group "$ATHENA_WORKGROUP" \
            --region "$REGION" \
            --query 'QueryExecutionId' \
            --output text 2>&1)
    else
        # Use workgroup's default output location
        execution_id=$(aws athena start-query-execution \
            --query-string "$query" \
            --query-execution-context "Catalog=$ATHENA_CATALOG,Database=$TEST_DATABASE" \
            --work-group "$ATHENA_WORKGROUP" \
            --region "$REGION" \
            --query 'QueryExecutionId' \
            --output text 2>&1)
    fi

    if [[ $? -ne 0 ]]; then
        log_error "Failed to start query: $query_name"
        echo "$execution_id" >&2
        return 1
    fi

    echo "$execution_id"
}

# Wait for query to complete and return status
wait_for_query() {
    local execution_id="$1"
    local max_wait=300  # 5 minutes
    local elapsed=0

    while [[ $elapsed -lt $max_wait ]]; do
        status=$(aws athena get-query-execution \
            --query-execution-id "$execution_id" \
            --region "$REGION" \
            --query 'QueryExecution.Status.State' \
            --output text 2>&1)

        case "$status" in
            SUCCEEDED)
                return 0
                ;;
            FAILED|CANCELLED)
                # Get error message
                error=$(aws athena get-query-execution \
                    --query-execution-id "$execution_id" \
                    --region "$REGION" \
                    --query 'QueryExecution.Status.StateChangeReason' \
                    --output text 2>&1)
                log_error "Query failed: $error"
                return 1
                ;;
            QUEUED|RUNNING)
                sleep 2
                ((elapsed+=2))
                ;;
            *)
                log_error "Unknown status: $status"
                return 1
                ;;
        esac
    done

    log_error "Query timeout after ${max_wait}s"
    return 1
}

# Get query results
get_query_results() {
    local execution_id="$1"

    aws athena get-query-results \
        --query-execution-id "$execution_id" \
        --region "$REGION" \
        --output json
}

# Run a test query
run_test_query() {
    local query="$1"
    local test_name="$2"
    local expected_result="$3"  # Optional: validate result if provided

    ((TOTAL_TESTS++)) || true

    execution_id=$(execute_athena_query "$query" "$test_name")
    if [[ $? -ne 0 ]]; then
        log_error "$test_name"
        return 1
    fi

    if wait_for_query "$execution_id"; then
        results=$(get_query_results "$execution_id")

        # If expected result provided, validate
        if [[ -n "$expected_result" ]]; then
            if echo "$results" | grep -q "$expected_result"; then
                log_success "$test_name"
                return 0
            else
                log_error "$test_name - Result mismatch"
                echo "Expected: $expected_result"
                echo "Got: $results"
                return 1
            fi
        else
            log_success "$test_name"
            return 0
        fi
    else
        log_error "$test_name"
        return 1
    fi
}

# ==============================================================================
# Pre-flight Checks
# ==============================================================================

preflight_checks() {
    log_info "Running pre-flight checks..."

    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI not found. Please install it first."
        exit 1
    fi

    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        log_error "AWS credentials not configured properly."
        exit 1
    fi

    # Verify Athena workgroup exists
    if ! aws athena get-work-group --work-group "$ATHENA_WORKGROUP" --region "$REGION" &> /dev/null; then
        log_warning "Workgroup '$ATHENA_WORKGROUP' not found. Using default."
        ATHENA_WORKGROUP="primary"
    fi

    log_success "Pre-flight checks completed"
}

# ==============================================================================
# Test Setup
# ==============================================================================

print_test_setup_instructions() {
    cat <<EOF

${BLUE}==============================================================================
REGRESSION TEST SETUP INSTRUCTIONS
==============================================================================${NC}

Before running these tests, you need to create a test Lark Base with the
following structure. This table should contain sample data for all supported
data types.

${YELLOW}Test Table Name:${NC} data_type_test_table
${YELLOW}Required Fields:${NC}

${GREEN}1. Primitive String Types:${NC}
   - field_text (TEXT)
   - field_barcode (BARCODE)
   - field_single_select (SINGLE_SELECT)
   - field_phone (PHONE)
   - field_email (EMAIL)
   - field_auto_number (AUTO_NUMBER)

${GREEN}2. Numeric Types:${NC}
   - field_number (NUMBER)
   - field_progress (PROGRESS)
   - field_currency (CURRENCY)
   - field_rating (RATING)

${GREEN}3. Boolean Type:${NC}
   - field_checkbox (CHECKBOX)

${GREEN}4. Date/Time Types:${NC}
   - field_date_time (DATE_TIME)
   - field_created_time (CREATED_TIME)
   - field_modified_time (MODIFIED_TIME)

${GREEN}5. Array Types:${NC}
   - field_multi_select (MULTI_SELECT)
   - field_user (USER)
   - field_group_chat (GROUP_CHAT)
   - field_attachment (ATTACHMENT)

${GREEN}6. Link Types:${NC}
   - field_single_link (SINGLE_LINK)
   - field_duplex_link (DUPLEX_LINK)

${GREEN}7. Struct Types:${NC}
   - field_url (URL)
   - field_location (LOCATION)
   - field_created_user (CREATED_USER)
   - field_modified_user (MODIFIED_USER)

${GREEN}8. Complex Types:${NC}
   - field_formula (FORMULA) - should compute from another field
   - field_lookup (LOOKUP) - should reference another table

${YELLOW}Sample Data Requirements:${NC}
- At least 10 rows of test data
- Include edge cases:
  * NULL/empty values
  * Maximum length strings
  * Boundary numeric values (0, negative, very large)
  * Past, present, and future dates
  * Empty arrays
  * Complex formulas (text, number, date results)
  * Multi-level lookups

${YELLOW}AWS Configuration:${NC}
1. Deploy the Lark Base connector Lambda function
2. Register the connector with Athena:
   aws athena create-data-catalog \\
     --name lark_base_catalog \\
     --type LAMBDA \\
     --parameters function=arn:aws:lambda:REGION:ACCOUNT:function:FUNCTION_NAME

3. Run the Glue crawler to create catalog metadata (if using Glue integration)

4. Set environment variables:
   export TEST_DATABASE="lark_base_test"
   export TEST_TABLE="data_type_test_table"
   export OUTPUT_LOCATION="s3://your-bucket/athena-results/"
   export AWS_REGION="us-east-1"

${BLUE}==============================================================================${NC}

EOF
}

# ==============================================================================
# Test Categories
# ==============================================================================

# Category 1: Basic Data Type Reading Tests
test_basic_data_types() {
    log_info "==== Testing Basic Data Types ===="

    # Test 1: TEXT field
    run_test_query \
        "SELECT field_text FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read TEXT field"

    # Test 2: BARCODE field
    run_test_query \
        "SELECT field_barcode FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read BARCODE field"

    # Test 3: SINGLE_SELECT field
    run_test_query \
        "SELECT field_single_select FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read SINGLE_SELECT field"

    # Test 4: PHONE field
    run_test_query \
        "SELECT field_phone FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read PHONE field"

    # Test 5: EMAIL field
    run_test_query \
        "SELECT field_email FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read EMAIL field"
}

# Category 2: Numeric Data Types
test_numeric_data_types() {
    log_info "==== Testing Numeric Data Types ===="

    # Test 6: NUMBER field
    run_test_query \
        "SELECT field_number FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read NUMBER field"

    # Test 7: PROGRESS field
    run_test_query \
        "SELECT field_progress FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read PROGRESS field"

    # Test 8: CURRENCY field
    run_test_query \
        "SELECT field_currency FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read CURRENCY field"

    # Test 9: RATING field
    run_test_query \
        "SELECT field_rating FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read RATING field"

    # Test 10: Numeric aggregations
    run_test_query \
        "SELECT AVG(field_number), SUM(field_currency), MAX(field_rating) FROM \"$TEST_DATABASE\".\"$TEST_TABLE\"" \
        "Numeric aggregations"
}

# Category 3: Boolean and Date Types
test_boolean_and_date_types() {
    log_info "==== Testing Boolean and Date Types ===="

    # Test 11: CHECKBOX field
    run_test_query \
        "SELECT field_checkbox FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read CHECKBOX field"

    # Test 12: DATE_TIME field
    run_test_query \
        "SELECT field_date_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read DATE_TIME field"

    # Test 13: CREATED_TIME field
    run_test_query \
        "SELECT field_created_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read CREATED_TIME field"

    # Test 14: MODIFIED_TIME field
    run_test_query \
        "SELECT field_modified_time FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read MODIFIED_TIME field"

    # Test 15: Date functions
    run_test_query \
        "SELECT date_format(field_date_time, '%Y-%m-%d') FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Date formatting"
}

# Category 4: Array/List Types
test_array_types() {
    log_info "==== Testing Array Types ===="

    # Test 16: MULTI_SELECT field
    run_test_query \
        "SELECT field_multi_select FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read MULTI_SELECT array"

    # Test 17: USER field
    run_test_query \
        "SELECT field_user FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read USER array"

    # Test 18: GROUP_CHAT field
    run_test_query \
        "SELECT field_group_chat FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read GROUP_CHAT array"

    # Test 19: ATTACHMENT field
    run_test_query \
        "SELECT field_attachment FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read ATTACHMENT array"

    # Test 20: Array size
    run_test_query \
        "SELECT cardinality(field_multi_select) as array_size FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Array cardinality function"
}

# Category 5: Struct Types
test_struct_types() {
    log_info "==== Testing Struct Types ===="

    # Test 21: URL field
    run_test_query \
        "SELECT field_url FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read URL struct"

    # Test 22: URL field nested access
    run_test_query \
        "SELECT field_url.link, field_url.text FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Access URL struct fields"

    # Test 23: LOCATION field
    run_test_query \
        "SELECT field_location FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read LOCATION struct"

    # Test 24: LOCATION nested access
    run_test_query \
        "SELECT field_location.address, field_location.full_address FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Access LOCATION struct fields"

    # Test 25: CREATED_USER field
    run_test_query \
        "SELECT field_created_user.name, field_created_user.email FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Access CREATED_USER struct fields"
}

# Category 6: Complex Types (Formula, Lookup, Links)
test_complex_types() {
    log_info "==== Testing Complex Types ===="

    # Test 26: FORMULA field
    run_test_query \
        "SELECT field_formula FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read FORMULA field"

    # Test 27: LOOKUP field
    run_test_query \
        "SELECT field_lookup FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read LOOKUP field"

    # Test 28: SINGLE_LINK field
    run_test_query \
        "SELECT field_single_link FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read SINGLE_LINK field"

    # Test 29: DUPLEX_LINK field
    run_test_query \
        "SELECT field_duplex_link FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read DUPLEX_LINK field"
}

# Category 7: Filter Pushdown Tests
test_filter_pushdown() {
    log_info "==== Testing Filter Pushdown ===="

    # Test 30: TEXT equality filter
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_text = 'test_value'" \
        "TEXT equality filter (pushdown)"

    # Test 31: NUMBER range filter
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_number > 100 AND field_number <= 1000" \
        "NUMBER range filter (pushdown)"

    # Test 32: CHECKBOX filter
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_checkbox = true" \
        "CHECKBOX true filter (pushdown)"

    # Test 33: CHECKBOX IS NULL
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_checkbox IS NULL" \
        "CHECKBOX IS NULL filter (pushdown converts to false)"

    # Test 34: SINGLE_SELECT IN filter
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_single_select IN ('option1', 'option2')" \
        "SINGLE_SELECT IN filter (pushdown)"

    # Test 35: TEXT NOT EQUAL filter
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_text != 'excluded_value'" \
        "TEXT NOT EQUAL filter (pushdown)"

    # Test 36: Combined filters
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_number > 50 AND field_checkbox = true" \
        "Combined AND filters (pushdown)"
}

# Category 8: Sort and Limit Pushdown Tests
test_sort_and_limit_pushdown() {
    log_info "==== Testing Sort and Limit Pushdown ===="

    # Test 37: ORDER BY single field
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" ORDER BY field_number DESC LIMIT 10" \
        "ORDER BY + LIMIT (Top-N pushdown)"

    # Test 38: LIMIT only
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "LIMIT only (pushdown)"

    # Test 39: ORDER BY multiple fields
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" ORDER BY field_created_time DESC, field_text ASC LIMIT 10" \
        "Multi-field ORDER BY + LIMIT"
}

# Category 9: NULL Handling Tests
test_null_handling() {
    log_info "==== Testing NULL Handling ===="

    # Test 40: TEXT IS NULL
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_text IS NULL" \
        "TEXT IS NULL check"

    # Test 41: TEXT IS NOT NULL (should add empty string check)
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_text IS NOT NULL" \
        "TEXT IS NOT NULL check"

    # Test 42: NUMBER IS NULL
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_number IS NULL" \
        "NUMBER IS NULL check"

    # Test 43: COALESCE with default values
    run_test_query \
        "SELECT COALESCE(field_text, 'default_value') FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "COALESCE for NULL handling"
}

# Category 10: Edge Cases and Stress Tests
test_edge_cases() {
    log_info "==== Testing Edge Cases ===="

    # Test 44: Empty array handling
    run_test_query \
        "SELECT field_multi_select FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE cardinality(field_multi_select) = 0" \
        "Empty array handling"

    # Test 45: Large result set (pagination)
    run_test_query \
        "SELECT COUNT(*) as total_rows FROM \"$TEST_DATABASE\".\"$TEST_TABLE\"" \
        "Count all rows (pagination test)"

    # Test 46: Complex nested access
    run_test_query \
        "SELECT field_user[1].email, field_attachment[1].name FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Array element access"

    # Test 47: All fields together
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 1" \
        "Read all fields in single query"

    # Test 48: Reserved fields
    run_test_query \
        "SELECT \\$reserved_record_id, \\$reserved_table_id, \\$reserved_base_id FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read reserved system fields"
}

# Category 11: Data Type Conversion Tests
test_data_type_conversions() {
    log_info "==== Testing Data Type Conversions ===="

    # Test 49: CAST operations
    run_test_query \
        "SELECT CAST(field_number AS VARCHAR) FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "CAST number to string"

    # Test 50: Date to string conversion
    run_test_query \
        "SELECT CAST(field_date_time AS VARCHAR) FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "CAST date to string"

    # Test 51: Boolean to integer
    run_test_query \
        "SELECT CAST(field_checkbox AS INTEGER) FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "CAST boolean to integer"
}

# Category 12: Additional Fields Tests (User-Added Fields)
test_additional_fields() {
    log_info "==== Testing Additional User-Added Fields ===="

    # Test 49: Additional CURRENCY fields
    run_test_query \
        "SELECT field_currency_2, field_currency_3 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional CURRENCY fields"

    # Test 50: Additional DATE_TIME fields
    run_test_query \
        "SELECT field_date_time_2, field_date_time_3, field_date_time_4 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional DATE_TIME fields (2-4)"

    # Test 51: More DATE_TIME fields
    run_test_query \
        "SELECT field_date_time_5, field_date_time_6, field_date_time_7 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional DATE_TIME fields (5-7)"

    # Test 52: Final DATE_TIME fields
    run_test_query \
        "SELECT field_date_time_8, field_date_time_9 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional DATE_TIME fields (8-9)"

    # Test 53: Additional PROGRESS fields
    run_test_query \
        "SELECT field_progress_2, field_progress_3, field_progress_4 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional PROGRESS fields"

    # Test 54: Additional RATING field
    run_test_query \
        "SELECT field_rating_2 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional RATING field"

    # Test 55: Additional GROUP_CHAT field
    run_test_query \
        "SELECT field_group_chat_2 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional GROUP_CHAT field"

    # Test 56: Additional FORMULA fields (various types)
    run_test_query \
        "SELECT field_formula_2, field_formula_3 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional FORMULA fields (text/timestamp)"

    # Test 57: More FORMULA fields (numeric)
    run_test_query \
        "SELECT field_formula_4, field_formula_5, field_formula_6 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional FORMULA fields (decimal)"

    # Test 58: Final FORMULA field (rating type)
    run_test_query \
        "SELECT field_formula_7 FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read additional FORMULA field (tinyint)"

    # Test 59: Record ID field
    run_test_query \
        "SELECT record_id FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Read record_id field"

    # Test 60: Aggregations on additional numeric fields
    run_test_query \
        "SELECT AVG(field_currency_2), SUM(field_progress_2), MAX(field_rating_2) FROM \"$TEST_DATABASE\".\"$TEST_TABLE\"" \
        "Aggregations on additional numeric fields"

    # Test 61: Date functions on additional timestamp fields
    run_test_query \
        "SELECT date_format(field_date_time_2, '%Y-%m-%d'), date_format(field_formula_3, '%Y-%m-%d') FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" LIMIT 5" \
        "Date formatting on additional timestamp fields"

    # Test 62: Filter on additional fields
    run_test_query \
        "SELECT * FROM \"$TEST_DATABASE\".\"$TEST_TABLE\" WHERE field_progress_2 > 0.5 AND field_rating_2 >= 3" \
        "Filter on additional numeric fields"
}

# ==============================================================================
# Main Test Execution
# ==============================================================================

main() {
    echo "[DEBUG] main() function started" >&2
    echo ""
    echo "================================================================================"
    echo "  Athena-Lark-Base Connector - Regression Test Suite"
    echo "================================================================================"
    echo ""
    echo "Configuration:"
    echo "  Catalog: $ATHENA_CATALOG"
    echo "  Database: $TEST_DATABASE"
    echo "  Table: $TEST_TABLE"
    echo "  Workgroup: $ATHENA_WORKGROUP"
    echo "  Region: $REGION"
    echo "  Output Location: $OUTPUT_LOCATION"
    echo ""
    echo "================================================================================"
    echo ""

    # Check if setup instructions should be shown
    if [[ "$1" == "--setup" ]]; then
        print_test_setup_instructions
        exit 0
    fi

    # Confirm before running
    read -p "Do you want to proceed with the tests? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_warning "Tests cancelled by user"
        exit 0
    fi

    # Run pre-flight checks
    echo "[DEBUG] About to run preflight_checks" >&2
    preflight_checks
    echo "[DEBUG] Finished preflight_checks" >&2

    # Start testing
    echo "[DEBUG] About to set start_time" >&2
    local start_time=$(date +%s)
    echo "[DEBUG] start_time=$start_time" >&2

    # Run all test categories
    echo "[DEBUG] Starting test_basic_data_types..."
    test_basic_data_types
    echo "[DEBUG] Finished test_basic_data_types"
    test_numeric_data_types
    test_boolean_and_date_types
    test_array_types
    test_struct_types
    test_complex_types
    test_filter_pushdown
    test_sort_and_limit_pushdown
    test_null_handling
    test_edge_cases
    test_data_type_conversions
    test_additional_fields

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # Print summary
    echo ""
    echo "================================================================================"
    echo "  Test Summary"
    echo "================================================================================"
    echo "  Total Tests: $TOTAL_TESTS"
    echo -e "  ${GREEN}Passed: $PASSED_TESTS${NC}"
    echo -e "  ${RED}Failed: $FAILED_TESTS${NC}"
    echo -e "  ${YELLOW}Skipped: $SKIPPED_TESTS${NC}"
    echo "  Duration: ${duration}s"
    echo "================================================================================"
    echo ""

    if [[ $FAILED_TESTS -eq 0 ]]; then
        log_success "All tests passed!"
        exit 0
    else
        log_error "$FAILED_TESTS test(s) failed"
        exit 1
    fi
}

# ==============================================================================
# Script Entry Point
# ==============================================================================

# Handle command line arguments
case "${1:-}" in
    --setup)
        print_test_setup_instructions
        ;;
    --help|-h)
        cat <<EOF
Usage: $0 [OPTIONS]

Regression test suite for Athena-Lark-Base connector

Options:
  --setup       Show test setup instructions
  --help, -h    Show this help message

Environment Variables:
  TEST_DATABASE        Athena database name (default: lark_base_test)
  TEST_TABLE           Test table name (default: data_type_test_table)
  ATHENA_WORKGROUP     Athena workgroup (default: primary)
  OUTPUT_LOCATION      S3 path for query results (required)
  AWS_REGION           AWS region (default: us-east-1)

Examples:
  # Show setup instructions
  $0 --setup

  # Run tests with custom configuration
  TEST_DATABASE=my_db TEST_TABLE=my_table OUTPUT_LOCATION=s3://my-bucket/ $0

EOF
        ;;
    *)
        main "$@"
        ;;
esac
