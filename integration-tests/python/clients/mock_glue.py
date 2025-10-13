"""
Mock AWS Glue client for testing.
Provides in-memory storage for databases and tables.
"""
from typing import Dict, List, Any
from datetime import datetime


class MockGlueClient:
    """
    Mock implementation of AWS Glue client.

    Stores databases and tables in memory and provides
    basic CRUD operations compatible with boto3 Glue API.
    """

    def __init__(self):
        self.databases = {}
        self.tables = {}

    def get_database(self, Name: str) -> Dict[str, Any]:
        """Get database by name."""
        if Name not in self.databases:
            raise Exception(f"EntityNotFoundException: Database {Name} not found")

        return {"Database": self.databases[Name]}

    def get_databases(self, **kwargs) -> Dict[str, Any]:
        """List all databases."""
        return {"DatabaseList": list(self.databases.values())}

    def create_database(self, DatabaseInput: Dict[str, Any]) -> Dict[str, Any]:
        """Create a new database."""
        name = DatabaseInput["Name"]
        self.databases[name] = {
            "Name": name,
            "Description": DatabaseInput.get("Description", ""),
            "LocationUri": DatabaseInput.get("LocationUri", ""),
            "Parameters": DatabaseInput.get("Parameters", {}),
            "CreateTime": datetime.now()
        }
        self.tables[name] = {}
        return {}

    def delete_database(self, Name: str) -> Dict[str, Any]:
        """Delete a database."""
        if Name not in self.databases:
            raise Exception(f"EntityNotFoundException: Database {Name} not found")

        del self.databases[Name]
        del self.tables[Name]
        return {}

    def get_table(self, DatabaseName: str, Name: str) -> Dict[str, Any]:
        """Get table by name."""
        if DatabaseName not in self.tables:
            raise Exception(f"EntityNotFoundException: Database {DatabaseName} not found")

        if Name not in self.tables[DatabaseName]:
            raise Exception(f"EntityNotFoundException: Table {Name} not found in {DatabaseName}")

        return {"Table": self.tables[DatabaseName][Name]}

    def get_tables(self, DatabaseName: str, **kwargs) -> Dict[str, Any]:
        """List tables in a database."""
        if DatabaseName not in self.tables:
            raise Exception(f"EntityNotFoundException: Database {DatabaseName} not found")

        return {"TableList": list(self.tables[DatabaseName].values())}

    def create_table(self, DatabaseName: str, TableInput: Dict[str, Any]) -> Dict[str, Any]:
        """Create a new table."""
        if DatabaseName not in self.tables:
            raise Exception(f"EntityNotFoundException: Database {DatabaseName} not found")

        name = TableInput["Name"]
        self.tables[DatabaseName][name] = {
            "Name": name,
            "DatabaseName": DatabaseName,
            "Description": TableInput.get("Description", ""),
            "Owner": TableInput.get("Owner", ""),
            "CreateTime": datetime.now(),
            "UpdateTime": datetime.now(),
            "StorageDescriptor": TableInput.get("StorageDescriptor", {}),
            "PartitionKeys": TableInput.get("PartitionKeys", []),
            "Parameters": TableInput.get("Parameters", {}),
            "TableType": TableInput.get("TableType", "EXTERNAL_TABLE")
        }
        return {}

    def update_table(self, DatabaseName: str, TableInput: Dict[str, Any]) -> Dict[str, Any]:
        """Update an existing table."""
        if DatabaseName not in self.tables:
            raise Exception(f"EntityNotFoundException: Database {DatabaseName} not found")

        name = TableInput["Name"]
        if name not in self.tables[DatabaseName]:
            raise Exception(f"EntityNotFoundException: Table {name} not found")

        existing = self.tables[DatabaseName][name]
        create_time = existing.get("CreateTime", datetime.now())

        self.tables[DatabaseName][name] = {
            "Name": name,
            "DatabaseName": DatabaseName,
            "Description": TableInput.get("Description", ""),
            "Owner": TableInput.get("Owner", ""),
            "CreateTime": create_time,
            "UpdateTime": datetime.now(),
            "StorageDescriptor": TableInput.get("StorageDescriptor", {}),
            "PartitionKeys": TableInput.get("PartitionKeys", []),
            "Parameters": TableInput.get("Parameters", {}),
            "TableType": TableInput.get("TableType", "EXTERNAL_TABLE")
        }
        return {}

    def delete_table(self, DatabaseName: str, Name: str) -> Dict[str, Any]:
        """Delete a table."""
        if DatabaseName not in self.tables:
            raise Exception(f"EntityNotFoundException: Database {DatabaseName} not found")

        if Name not in self.tables[DatabaseName]:
            raise Exception(f"EntityNotFoundException: Table {Name} not found")

        del self.tables[DatabaseName][Name]
        return {}

    def create_simple_table(
        self,
        db_name: str,
        table_name: str,
        columns: List[Dict[str, str]],
        parameters: Dict[str, str] = None
    ):
        """Helper method to create a simple table for testing."""
        table_input = {
            "Name": table_name,
            "StorageDescriptor": {
                "Columns": columns
            },
            "Parameters": parameters or {}
        }
        return self.create_table(DatabaseName=db_name, TableInput=table_input)

    def clear_all(self):
        """Clear all data (for cleanup between tests)."""
        self.databases.clear()
        self.tables.clear()
