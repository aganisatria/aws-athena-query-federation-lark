"""
Example integration test demonstrating the testing framework.

This test works in all three modes:
- MOCK: Uses in-memory mock Glue client
- HYBRID: Uses in-memory mock Glue client (Glue not in LocalStack Community)
- AWS: Uses real AWS Glue Data Catalog
"""
import sys
import os
import pytest

# Add parent directory to path for imports
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))

from clients import AWSClientFactory
from config import TestEnvironment, get_environment


@pytest.fixture
def client_factory():
    """Create AWS client factory."""
    factory = AWSClientFactory()
    yield factory
    factory.cleanup()


@pytest.fixture
def glue_client(client_factory):
    """Create Glue client with pre-populated test data."""
    glue = client_factory.create_glue_client()

    # Pre-populate test data only if using mocks
    if get_environment() != TestEnvironment.AWS:
        mock_glue = client_factory.get_mock_glue_client()

        # Create test database
        mock_glue.create_database(DatabaseInput={
            "Name": "test_database",
            "Description": "Test database for integration tests"
        })

        # Create test table
        mock_glue.create_simple_table(
            db_name="test_database",
            table_name="test_table",
            columns=[
                {"Name": "field_text", "Type": "string"},
                {"Name": "field_number", "Type": "decimal"},
                {"Name": "field_checkbox", "Type": "boolean"}
            ],
            parameters={
                "larkBaseId": "test_base_123",
                "larkTableId": "test_table_456"
            }
        )

    return glue


def test_get_database(glue_client):
    """Test getting a database from Glue catalog."""
    # Act
    response = glue_client.get_database(Name="test_database")

    # Assert
    assert "Database" in response
    database = response["Database"]
    assert database["Name"] == "test_database"
    assert database["Description"] == "Test database for integration tests"


def test_get_table(glue_client):
    """Test getting a table from Glue catalog."""
    # Act
    response = glue_client.get_table(
        DatabaseName="test_database",
        Name="test_table"
    )

    # Assert
    assert "Table" in response
    table = response["Table"]
    assert table["Name"] == "test_table"
    assert table["DatabaseName"] == "test_database"

    # Check columns
    columns = table["StorageDescriptor"]["Columns"]
    assert len(columns) == 3
    column_names = [col["Name"] for col in columns]
    assert set(column_names) == {"field_text", "field_number", "field_checkbox"}

    # Check parameters
    parameters = table["Parameters"]
    assert parameters["larkBaseId"] == "test_base_123"
    assert parameters["larkTableId"] == "test_table_456"


def test_table_metadata(glue_client):
    """Test reading Lark metadata from table parameters."""
    # Arrange
    response = glue_client.get_table(
        DatabaseName="test_database",
        Name="test_table"
    )
    table = response["Table"]

    # Act - Simulate reading Lark metadata from table parameters
    lark_base_id = table["Parameters"]["larkBaseId"]
    lark_table_id = table["Parameters"]["larkTableId"]

    # Assert
    assert lark_base_id == "test_base_123"
    assert lark_table_id == "test_table_456"


def test_list_tables(glue_client):
    """Test listing tables in a database."""
    # Act
    response = glue_client.get_tables(DatabaseName="test_database")

    # Assert
    assert "TableList" in response
    tables = response["TableList"]
    assert len(tables) >= 1
    table_names = [t["Name"] for t in tables]
    assert "test_table" in table_names


if __name__ == "__main__":
    # Run tests with pytest
    pytest.main([__file__, "-v"])
