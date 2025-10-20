#!/bin/bash
#
# Comprehensive Test Runner Script for AWS Athena Lark Base Connector
#
# This script runs comprehensive tests across 3 environments and 4 metadata providers.
#
# Usage:
#     # Run all tests in all environments
#     ./run_comprehensive_tests.sh --all
#
#     # Run tests in specific environment
#     ./run_comprehensive_tests.sh --env mock
#     ./run_comprehensive_tests.sh --env aws
#
#     # Run specific metadata providers
#     ./run_comprehensive_tests.sh --providers glue_catalog,experimental
#
#     # Fix Glue crawler before running tests
#     ./run_comprehensive_tests.sh --fix-crawler --env aws
#
#     # Verbose output
#     ./run_comprehensive_tests.sh --verbose
#
# Examples:
#     # Quick mock test
#     ./run_comprehensive_tests.sh --env mock --verbose
#
#     # Full AWS testing with crawler fix
#     ./run_comprehensive_tests.sh --fix-crawler --env aws --providers all --verbose
#
#     # Test only experimental provider in AWS
#     ./run_comprehensive_tests.sh --env aws --providers experimental --verbose
#
set -e

# Default values
ENVIRONMENT="mock"
PROVIDERS="all"
FIX_CRAWLER=false
VERBOSE=false
HELP=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

print_usage() {
    cat << EOF
Comprehensive Test Runner for AWS Athena Lark Base Connector

USAGE:
    $0 [OPTIONS]

OPTIONS:
    --env ENVIRONMENT       Test environment: mock, hybrid, aws (default: mock)
    --providers PROVIDERS   Comma-separated list: glue_catalog,lark_base_source,lark_drive_source,experimental,all (default: all)
    --fix-crawler          Fix Glue crawler metadata mapping before testing (AWS mode only)
    --verbose, -v          Verbose output
    --help, -h             Show this help message

ENVIRONMENTS:
    mock      - All services mocked in-memory (fastest, no AWS credentials needed)
    hybrid    - LocalStack + mocks (requires LocalStack running)
    aws       - Real AWS services (requires AWS credentials)

METADATA PROVIDERS:
    glue_catalog      - Glue Catalog Provider (athena_lark_base_regression_test)
    lark_base_source  - Lark Base Source Provider (athena_lark_base_regression_test1)
    lark_drive_source - Lark Drive Source Provider (athena_lark_base_regression_test2)
    experimental      - Experimental Provider (EEMGbnS87a2W1IsaJKhjds3fpwe.tblCGeqbqp03ivAY)

EXAMPLES:
    # Quick mock test
    $0 --env mock --verbose

    # Full AWS testing with crawler fix
    $0 --fix-crawler --env aws --providers all --verbose

    # Test specific providers
    $0 --env aws --providers glue_catalog,experimental --verbose

EOF
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

validate_environment() {
    local env=$1
    case $env in
        mock|hybrid|aws)
            return 0
            ;;
        *)
            log_error "Invalid environment: $env"
            log_error "Valid environments: mock, hybrid, aws"
            return 1
            ;;
    esac
}

validate_providers() {
    local providers=$1
    IFS=',' read -ra PROVIDER_ARRAY <<< "$providers"

    for provider in "${PROVIDER_ARRAY[@]}"; do
        provider=$(echo "$provider" | xargs)  # trim whitespace
        case $provider in
            glue_catalog|lark_base_source|lark_drive_source|experimental|all)
                ;;
            *)
                log_error "Invalid provider: $provider"
                log_error "Valid providers: glue_catalog, lark_base_source, lark_drive_source, experimental, all"
                return 1
                ;;
        esac
    done
}

check_dependencies() {
    log_info "Checking dependencies..."

    # Check Python
    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 is required"
        return 1
    fi

    # Check AWS CLI for AWS/HYBRID modes
    if [[ "$ENVIRONMENT" == "aws" || "$ENVIRONMENT" == "hybrid" ]]; then
        if ! command -v aws &> /dev/null; then
            log_error "AWS CLI is required for $ENVIRONMENT mode"
            return 1
        fi

        # Check AWS credentials
        if ! aws sts get-caller-identity &> /dev/null; then
            log_error "AWS credentials not configured properly"
            return 1
        fi
        log_success "AWS credentials verified"
    fi

    # Check LocalStack for HYBRID mode
    if [[ "$ENVIRONMENT" == "hybrid" ]]; then
        if ! command -v localstack &> /dev/null; then
            log_warning "LocalStack not found in PATH"
            log_warning "Make sure LocalStack is running: localstack start"
        fi
    fi

    # Check Python packages
    log_info "Checking Python packages..."
    if ! python3 -c "import boto3, botocore" &> /dev/null; then
        log_error "Required Python packages not found"
        log_info "Install with: pip install boto3 botocore"
        return 1
    fi

    log_success "Dependencies verified"
}

fix_glue_crawler() {
    log_info "Fixing Glue crawler metadata mapping..."

    if [[ "$ENVIRONMENT" != "aws" ]]; then
        log_error "Glue crawler fix is only available in AWS mode"
        return 1
    fi

    export TEST_ENVIRONMENT=aws

    if [[ "$VERBOSE" == "true" ]]; then
        python3 fix_glue_crawler.py
    else
        python3 fix_glue_crawler.py 2>&1 | grep -E "(✅|❌|⚠️|🔧|📋|ERROR|WARNING|INFO)" || true
    fi

    local exit_code=$?
    if [[ $exit_code -eq 0 ]]; then
        log_success "Glue crawler fix completed successfully"
    else
        log_error "Glue crawler fix failed"
        return 1
    fi
}

run_comprehensive_tests() {
    log_info "Running comprehensive tests..."
    log_info "Environment: $ENVIRONMENT"
    log_info "Providers: $PROVIDERS"

    export TEST_ENVIRONMENT="$ENVIRONMENT"

    local cmd_args=""
    if [[ "$VERBOSE" == "true" ]]; then
        cmd_args="--verbose"
    fi

    if [[ "$PROVIDERS" != "all" ]]; then
        cmd_args="$cmd_args --providers $PROVIDERS"
    fi

    log_info "Command: python3 comprehensive_test_runner.py $cmd_args"

    if python3 comprehensive_test_runner.py $cmd_args; then
        log_success "Comprehensive tests completed successfully"
        return 0
    else
        log_error "Comprehensive tests failed"
        return 1
    fi
}

run_quick_validation() {
    log_info "Running quick validation tests..."

    # Run a subset of critical tests for quick validation
    local critical_providers="glue_catalog"

    if [[ "$ENVIRONMENT" == "aws" ]]; then
        critical_providers="glue_catalog,experimental"
    fi

    export TEST_ENVIRONMENT="$ENVIRONMENT"

    local cmd_args="--providers $critical_providers"
    if [[ "$VERBOSE" == "true" ]]; then
        cmd_args="$cmd_args --verbose"
    fi

    if python3 comprehensive_test_runner.py $cmd_args; then
        log_success "Quick validation passed"
        return 0
    else
        log_error "Quick validation failed"
        return 1
    fi
}

cleanup() {
    log_info "Cleaning up temporary files..."
    # Remove any temporary files or logs if needed
    rm -f .test_temp_*
}

main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --env)
                ENVIRONMENT="$2"
                shift 2
                ;;
            --providers)
                PROVIDERS="$2"
                shift 2
                ;;
            --fix-crawler)
                FIX_CRAWLER=true
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --help|-h)
                HELP=true
                shift
                ;;
            --all)
                ENVIRONMENT="aws"
                PROVIDERS="all"
                FIX_CRAWLER=true
                VERBOSE=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done

    # Show help
    if [[ "$HELP" == "true" ]]; then
        print_usage
        exit 0
    fi

    # Print configuration
    echo "=============================================================================="
    echo "COMPREHENSIVE TEST RUNNER"
    echo "=============================================================================="
    echo "Environment: $ENVIRONMENT"
    echo "Providers: $PROVIDERS"
    echo "Fix Crawler: $FIX_CRAWLER"
    echo "Verbose: $VERBOSE"
    echo "=============================================================================="

    # Validate inputs
    validate_environment "$ENVIRONMENT" || exit 1
    validate_providers "$PROVIDERS" || exit 1

    # Check dependencies
    check_dependencies || exit 1

    # Setup cleanup trap
    trap cleanup EXIT

    # Step 1: Fix Glue crawler if requested (AWS mode only)
    if [[ "$FIX_CRAWLER" == "true" ]]; then
        fix_glue_crawler || exit 1
        echo ""
    fi

    # Step 2: Run comprehensive tests
    if run_comprehensive_tests; then
        log_success "All tests completed successfully!"

        # Step 3: Run quick validation to ensure everything still works
        echo ""
        log_info "Running final validation..."
        if run_quick_validation; then
            log_success "Final validation passed!"
            echo ""
            log_success "🎉 ALL TESTS PASSED! 🎉"
            echo ""
            echo "Test Results Summary:"
            echo "  - Environment: $ENVIRONMENT"
            echo "  - Providers: $PROVIDERS"
            echo "  - Status: PASSED"
            echo ""
            echo "Your AWS Athena Lark Base Connector is working correctly!"
        else
            log_warning "Final validation failed, but main tests passed"
            exit 1
        fi
    else
        log_error "Tests failed!"
        exit 1
    fi
}

# Run main function
main "$@"