"""
Mock AWS Secrets Manager client for testing.
"""
from typing import Dict, Any
import json


class MockSecretsManagerClient:
    """Mock implementation of AWS Secrets Manager client."""

    def __init__(self):
        self.secrets = {}

    def get_secret_value(self, SecretId: str) -> Dict[str, Any]:
        """Get secret value by ID."""
        if SecretId not in self.secrets:
            raise Exception(f"ResourceNotFoundException: Secret {SecretId} not found")

        value = self.secrets[SecretId]
        return {
            "ARN": f"arn:aws:secretsmanager:us-east-1:123456789012:secret:{SecretId}",
            "Name": SecretId,
            "SecretString": value if isinstance(value, str) else None,
            "SecretBinary": value if isinstance(value, bytes) else None
        }

    def create_secret(self, Name: str, SecretString: str = None, SecretBinary: bytes = None) -> Dict[str, Any]:
        """Create a new secret."""
        value = SecretString if SecretString else SecretBinary
        self.secrets[Name] = value
        return {
            "ARN": f"arn:aws:secretsmanager:us-east-1:123456789012:secret:{Name}",
            "Name": Name
        }

    def put_secret_value(self, SecretId: str, SecretString: str = None, SecretBinary: bytes = None) -> Dict[str, Any]:
        """Update secret value."""
        value = SecretString if SecretString else SecretBinary
        self.secrets[SecretId] = value
        return {
            "ARN": f"arn:aws:secretsmanager:us-east-1:123456789012:secret:{SecretId}",
            "Name": SecretId
        }

    def delete_secret(self, SecretId: str) -> Dict[str, Any]:
        """Delete a secret."""
        if SecretId not in self.secrets:
            raise Exception(f"ResourceNotFoundException: Secret {SecretId} not found")

        del self.secrets[SecretId]
        return {
            "ARN": f"arn:aws:secretsmanager:us-east-1:123456789012:secret:{SecretId}",
            "Name": SecretId
        }

    def put_secret(self, name: str, value: str):
        """Convenience method to store a secret."""
        self.secrets[name] = value

    def populate_default_test_secrets(self):
        """Pre-populate common test secrets."""
        self.put_secret("lark-app-credentials",
                       json.dumps({"app_id": "test_app_id", "app_secret": "test_app_secret"}))
        self.put_secret("lark-app-id", "test_app_id")
        self.put_secret("lark-app-secret", "test_app_secret")

    def clear_all(self):
        """Clear all secrets."""
        self.secrets.clear()
