"""
Test configuration for integration tests.

Supports three test modes:
- MOCK: All services mocked in-memory (fastest, no infrastructure)
- HYBRID: LocalStack Community (Lambda, S3) + Mocks (Glue, Athena, Secrets, SSM)
- AWS: Real AWS services (slowest, requires AWS credentials)

Usage:
    export TEST_ENVIRONMENT=mock    # or hybrid, or aws
    python -m pytest tests/
"""
import os
from enum import Enum
from typing import Dict, Any


class TestEnvironment(Enum):
    """Test environment modes."""
    MOCK = "mock"
    HYBRID = "hybrid"
    AWS = "aws"


def get_environment() -> TestEnvironment:
    """
    Get test environment from TEST_ENVIRONMENT variable.

    Returns:
        TestEnvironment enum value (defaults to MOCK)
    """
    env = os.getenv("TEST_ENVIRONMENT", "mock").lower()

    try:
        return TestEnvironment(env)
    except ValueError:
        print(f"Warning: Invalid TEST_ENVIRONMENT '{env}', defaulting to MOCK")
        return TestEnvironment.MOCK


def get_aws_config(service: str = None) -> Dict[str, Any]:
    """
    Get AWS client configuration based on test environment.

    Args:
        service: AWS service name (optional)

    Returns:
        Dictionary of boto3 client configuration
    """
    env = get_environment()

    if env == TestEnvironment.AWS:
        # Real AWS configuration
        config = {
            "region_name": os.getenv("AWS_REGION", "us-east-1")
        }
    elif env == TestEnvironment.HYBRID:
        # LocalStack configuration (only for Lambda and S3)
        if service in ["lambda", "s3", "logs", "sts", "iam"]:
            config = {
                "endpoint_url": os.getenv("LOCALSTACK_ENDPOINT", "http://localhost:4566"),
                "region_name": "us-east-1",
                "aws_access_key_id": "test",
                "aws_secret_access_key": "test"
            }
        else:
            # Other services use mocks in HYBRID mode
            config = {"use_mock": True}
    else:
        # MOCK mode - all services mocked
        config = {"use_mock": True}

    return config


def get_lark_api_base_url() -> str:
    """
    Get Lark API base URL based on test environment.

    Returns:
        Lark API base URL (real or WireMock)
    """
    env = get_environment()

    if env == TestEnvironment.AWS:
        # Real Lark API
        return "https://open.larksuite.com"
    else:
        # WireMock for MOCK and HYBRID
        return os.getenv("WIREMOCK_URL", "http://localhost:8080")


def get_metadata_provider() -> str:
    """
    Get metadata provider from METADATA_PROVIDER environment variable.

    Returns:
        Metadata provider string (defaults to "glue")
    """
    return os.getenv("METADATA_PROVIDER", "glue").lower()


def get_sam_template_path() -> str:
    """
    Get SAM template path based on metadata provider.

    Returns:
        Path to the SAM template file
    """
    provider = get_metadata_provider()
    if provider == "source":
        return "../../../athena-lark-base/athena-larkbase-source-provider.yaml"
    elif provider == "experimental":
        return "../../../athena-lark-base/athena-larkbase-experimental-provider.yaml"
    else:  # glue
        return "../../../athena-lark-base/athena-larkbase-package.yaml"


def get_athena_catalog_name(provider: str) -> str:
    """
    Get Athena catalog name based on metadata provider.

    Returns:
        Unique Athena catalog name
    """
    base_catalog = os.getenv("ATHENA_CATALOG", "athena-lark-base-test")
    return f"{base_catalog}-{provider}"


# Test data configuration
TEST_DATABASE = os.getenv("TEST_DATABASE", "athena_lark_base_regression_test")
TEST_TABLE = os.getenv("TEST_TABLE", "data_type_test_table")

# Lark test configuration
LARK_APP_ID = os.getenv("LARK_APP_ID", "test_app_id")
LARK_APP_SECRET = os.getenv("LARK_APP_SECRET", "test_app_secret")
LARK_BASE_TOKEN = os.getenv("LARK_BASE_APP_TOKEN", "test_base_token")
LARK_TABLE_ID = os.getenv("LARK_BASE_TABLE_ID", "test_table_id")

# Lambda configuration
LAMBDA_FUNCTION_NAME = os.getenv("LAMBDA_FUNCTION_NAME", "test-athena-lark-connector")
CRAWLER_FUNCTION_NAME = os.getenv("GLUE_CRAWLER_LAMBDA_NAME", "test-lark-crawler")

# S3 configuration
S3_BUCKET = os.getenv("S3_BUCKET", "test-lambda-deployment")
S3_RESULTS_BUCKET = os.getenv("S3_RESULTS_BUCKET", "test-athena-results")


def print_test_config():
    """Print current test configuration."""
    env = get_environment()
    provider = get_metadata_provider()
    catalog_name = get_athena_catalog_name(provider)

    print("=" * 80)
    print("Test Configuration")
    print("=" * 80)
    print(f"Environment: {env.value.upper()}")
    print(f"Metadata Provider: {provider.upper()}")
    print(f"AWS Region: {os.getenv('AWS_REGION', 'us-east-1')}")

    if env == TestEnvironment.HYBRID:
        print(f"LocalStack Endpoint: {os.getenv('LOCALSTACK_ENDPOINT', 'http://localhost:4566')}")
        print(f"WireMock URL: {get_lark_api_base_url()}")
    elif env == TestEnvironment.MOCK:
        print(f"Lark API Mock: {get_lark_api_base_url()}")

    print(f"\nTest Data:")
    print(f"  Database: {TEST_DATABASE}")
    print(f"  Table: {TEST_TABLE}")
    print(f"  Catalog: {catalog_name}")
    print("=" * 80)


if __name__ == "__main__":
    print_test_config()
