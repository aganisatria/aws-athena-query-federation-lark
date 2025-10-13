"""
AWS client wrappers with mock support for integration testing.
"""
from .aws_client import AWSClientFactory
from .mock_glue import MockGlueClient
from .mock_secrets import MockSecretsManagerClient
from .mock_ssm import MockSSMClient

__all__ = [
    'AWSClientFactory',
    'MockGlueClient',
    'MockSecretsManagerClient',
    'MockSSMClient',
]
