"""
AWS client factory for integration tests.

Creates appropriate clients based on test environment:
- MOCK: Returns mock clients
- HYBRID: Returns LocalStack clients (Lambda, S3) + mocks (Glue, Secrets, SSM)
- AWS: Returns real AWS clients
"""
import boto3
from typing import Any
from config import get_environment, get_aws_config, TestEnvironment
from .mock_glue import MockGlueClient
from .mock_secrets import MockSecretsManagerClient
from .mock_ssm import MockSSMClient


class AWSClientFactory:
    """
    Factory for creating AWS clients based on test environment.

    Usage:
        factory = AWSClientFactory()
        glue = factory.create_glue_client()
        lambda_client = factory.create_lambda_client()
    """

    def __init__(self):
        self.environment = get_environment()

        # Initialize mock clients (reused across calls)
        self._mock_glue = None
        self._mock_secrets = None
        self._mock_ssm = None

    def create_glue_client(self) -> Any:
        """
        Create Glue client.
        - MOCK/HYBRID: Returns mock Glue
        - AWS: Returns real boto3 Glue client
        """
        if self.environment == TestEnvironment.AWS:
            config = get_aws_config("glue")
            return boto3.client("glue", **config)

        # MOCK or HYBRID - use mock Glue (not available in LocalStack Community)
        if self._mock_glue is None:
            self._mock_glue = MockGlueClient()
        return self._mock_glue

    def create_secrets_manager_client(self) -> Any:
        """
        Create Secrets Manager client.
        - MOCK/HYBRID: Returns mock Secrets Manager
        - AWS: Returns real boto3 Secrets Manager client
        """
        if self.environment == TestEnvironment.AWS:
            config = get_aws_config("secretsmanager")
            return boto3.client("secretsmanager", **config)

        # MOCK or HYBRID - use mock (not available in LocalStack Community)
        if self._mock_secrets is None:
            self._mock_secrets = MockSecretsManagerClient()
            self._mock_secrets.populate_default_test_secrets()
        return self._mock_secrets

    def create_ssm_client(self) -> Any:
        """
        Create SSM client.
        - MOCK/HYBRID: Returns mock SSM
        - AWS: Returns real boto3 SSM client
        """
        if self.environment == TestEnvironment.AWS:
            config = get_aws_config("ssm")
            return boto3.client("ssm", **config)

        # MOCK or HYBRID - use mock (not available in LocalStack Community)
        if self._mock_ssm is None:
            self._mock_ssm = MockSSMClient()
            self._mock_ssm.populate_default_test_parameters()
        return self._mock_ssm

    def create_lambda_client(self) -> Any:
        """
        Create Lambda client.
        - MOCK: Raises error (test handlers directly)
        - HYBRID: Returns LocalStack Lambda client
        - AWS: Returns real boto3 Lambda client
        """
        config = get_aws_config("lambda")

        if config.get("use_mock"):
            raise ValueError(
                "Lambda client not supported in MOCK mode. "
                "Test handlers directly or use HYBRID/AWS mode."
            )

        return boto3.client("lambda", **config)

    def create_s3_client(self) -> Any:
        """
        Create S3 client.
        - MOCK: Raises error
        - HYBRID: Returns LocalStack S3 client
        - AWS: Returns real boto3 S3 client
        """
        config = get_aws_config("s3")

        if config.get("use_mock"):
            raise ValueError(
                "S3 client not supported in MOCK mode. "
                "Use HYBRID/AWS mode."
            )

        return boto3.client("s3", **config)

    def create_athena_client(self) -> Any:
        """
        Create Athena client.
        - MOCK: Returns mock (if available)
        - HYBRID: Returns mock (Athena not in LocalStack Community)
        - AWS: Returns real boto3 Athena client
        """
        if self.environment == TestEnvironment.AWS:
            config = get_aws_config("athena")
            return boto3.client("athena", **config)

        raise ValueError(
            "Athena client not supported in MOCK/HYBRID mode. "
            "Use AWS mode or test connector logic directly."
        )

    def create_logs_client(self) -> Any:
        """
        Create CloudWatch Logs client.
        - MOCK/HYBRID: Returns LocalStack or raises error
        - AWS: Returns real boto3 CloudWatch Logs client
        """
        config = get_aws_config("logs")

        if config.get("use_mock"):
            raise ValueError(
                "CloudWatch Logs client not supported in MOCK mode. "
                "Use HYBRID/AWS mode."
            )

        return boto3.client("logs", **config)

    def get_mock_glue_client(self) -> MockGlueClient:
        """Get mock Glue client for pre-populating test data."""
        if self.environment == TestEnvironment.AWS:
            raise ValueError("Mock Glue client only available in MOCK/HYBRID mode")

        if self._mock_glue is None:
            self._mock_glue = MockGlueClient()
        return self._mock_glue

    def get_mock_secrets_manager_client(self) -> MockSecretsManagerClient:
        """Get mock Secrets Manager for pre-populating test secrets."""
        if self.environment == TestEnvironment.AWS:
            raise ValueError("Mock Secrets Manager only available in MOCK/HYBRID mode")

        if self._mock_secrets is None:
            self._mock_secrets = MockSecretsManagerClient()
            self._mock_secrets.populate_default_test_secrets()
        return self._mock_secrets

    def get_mock_ssm_client(self) -> MockSSMClient:
        """Get mock SSM client for pre-populating test parameters."""
        if self.environment == TestEnvironment.AWS:
            raise ValueError("Mock SSM client only available in MOCK/HYBRID mode")

        if self._mock_ssm is None:
            self._mock_ssm = MockSSMClient()
            self._mock_ssm.populate_default_test_parameters()
        return self._mock_ssm

    def cleanup(self):
        """Clean up all mock clients."""
        if self._mock_glue:
            self._mock_glue.clear_all()
        if self._mock_secrets:
            self._mock_secrets.clear_all()
        if self._mock_ssm:
            self._mock_ssm.clear_all()
