"""
Mock AWS Systems Manager (SSM) Parameter Store client for testing.
"""
from typing import Dict, List, Any
from datetime import datetime


class MockSSMClient:
    """Mock implementation of AWS SSM Parameter Store client."""

    def __init__(self):
        self.parameters = {}

    def get_parameter(self, Name: str, WithDecryption: bool = True) -> Dict[str, Any]:
        """Get parameter by name."""
        if Name not in self.parameters:
            raise Exception(f"ParameterNotFound: Parameter {Name} not found")

        param = self.parameters[Name]
        value = param["Value"]

        # Mask SecureString if WithDecryption is False
        if not WithDecryption and param["Type"] == "SecureString":
            value = "****"

        return {
            "Parameter": {
                "Name": Name,
                "Value": value,
                "Type": param["Type"],
                "LastModifiedDate": param["LastModifiedDate"],
                "Version": param["Version"],
                "ARN": f"arn:aws:ssm:us-east-1:123456789012:parameter{Name}"
            }
        }

    def get_parameters_by_path(
        self,
        Path: str,
        Recursive: bool = False,
        WithDecryption: bool = True
    ) -> Dict[str, Any]:
        """Get parameters by path."""
        matching = []

        for name, param in self.parameters.items():
            if Recursive:
                matches = name.startswith(Path)
            else:
                # Non-recursive: only direct children
                if not name.startswith(Path):
                    continue
                remainder = name[len(Path):]
                if remainder.startswith("/"):
                    remainder = remainder[1:]
                matches = "/" not in remainder

            if matches:
                value = param["Value"]
                if not WithDecryption and param["Type"] == "SecureString":
                    value = "****"

                matching.append({
                    "Name": name,
                    "Value": value,
                    "Type": param["Type"],
                    "LastModifiedDate": param["LastModifiedDate"],
                    "Version": param["Version"],
                    "ARN": f"arn:aws:ssm:us-east-1:123456789012:parameter{name}"
                })

        return {"Parameters": matching}

    def put_parameter(
        self,
        Name: str,
        Value: str,
        Type: str = "String",
        Description: str = ""
    ) -> Dict[str, Any]:
        """Create or update a parameter."""
        existing = self.parameters.get(Name)
        version = existing["Version"] + 1 if existing else 1

        self.parameters[Name] = {
            "Value": Value,
            "Type": Type,
            "Description": Description,
            "LastModifiedDate": datetime.now(),
            "Version": version
        }

        return {"Version": version}

    def delete_parameter(self, Name: str) -> Dict[str, Any]:
        """Delete a parameter."""
        if Name not in self.parameters:
            raise Exception(f"ParameterNotFound: Parameter {Name} not found")

        del self.parameters[Name]
        return {}

    def populate_default_test_parameters(self):
        """Pre-populate common test parameters."""
        self.put_parameter("/lark/app_id", "test_app_id")
        self.put_parameter("/lark/app_secret", "test_app_secret", Type="SecureString")
        self.put_parameter("/lark/base/data_source_id", "test_base_id")
        self.put_parameter("/lark/table/data_source_id", "test_table_id")
        self.put_parameter("/athena/catalog", "test_catalog")
        self.put_parameter("/athena/workgroup", "primary")
        self.put_parameter("/features/enable_parallel_split", "true")
        self.put_parameter("/features/enable_debug_logging", "false")

    def clear_all(self):
        """Clear all parameters."""
        self.parameters.clear()
