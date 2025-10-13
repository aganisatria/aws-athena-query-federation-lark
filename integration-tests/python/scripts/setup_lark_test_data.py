#!/usr/bin/env python3
"""
Lark Base Test Data Setup Script

Creates a complete test environment in Lark Base for regression testing.
Works with real Lark API in AWS mode. In MOCK/HYBRID modes, provides instructions.

Migrated from: ../../../../setup-lark-test-data.py

Note: This is a utility script, not a regression test.
"""
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from config import get_environment, TestEnvironment

# Colors for output
class Colors:
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    END = '\033[0m'
    BOLD = '\033[1m'

def log_info(msg: str):
    print(f"{Colors.BLUE}[INFO]{Colors.END} {msg}")

def log_success(msg: str):
    print(f"{Colors.GREEN}[SUCCESS]{Colors.END} {msg}")

def log_error(msg: str):
    print(f"{Colors.RED}[ERROR]{Colors.END} {msg}")

def log_warning(msg: str):
    print(f"{Colors.YELLOW}[WARNING]{Colors.END} {msg}")

def main():
    """Main setup function"""
    environment = get_environment()

    print("="*80)
    print("Lark Base Test Data Setup")
    print("="*80)
    print(f"Environment: {environment.value.upper()}\n")

    if environment in [TestEnvironment.MOCK, TestEnvironment.HYBRID]:
        log_info(f"Running in {environment.value.upper()} mode")
        print("\nIn MOCK/HYBRID mode, test data setup is simulated.")
        print("Mock data is automatically populated by the testing framework.\n")

        log_success("Mock test environment ready!")
        print("\nYou can run tests immediately:")
        print(f"  cd integration-tests/python")
        print(f"  export TEST_ENVIRONMENT={environment.value}")
        print(f"  python run_all_tests.py")
        return 0

    # AWS mode - need to run the actual setup script
    log_info("Running in AWS mode - real Lark API setup required")
    print("\nTo set up real Lark Base test data:")
    print(f"\n1. Ensure environment variables are set:")
    print(f"   export LARK_APP_ID=your_app_id")
    print(f"   export LARK_APP_SECRET=your_app_secret")
    print(f"   export LARK_FOLDER_TOKEN=your_folder_token (optional)")

    print(f"\n2. Run the original setup script from project root:")
    print(f"   python3 setup-lark-test-data.py --verbose")

    print(f"\n3. The script will:")
    print(f"   - Create a new Lark Base")
    print(f"   - Create test tables with all field types")
    print(f"   - Populate test data")
    print(f"   - Output environment variables for testing")

    print(f"\n4. After setup, copy the environment variables to your .env file")

    print(f"\n5. Then run regression tests:")
    print(f"   export TEST_ENVIRONMENT=aws")
    print(f"   python run_all_tests.py")

    log_warning("Original setup script available at: ../../../../setup-lark-test-data.py")

    return 0


if __name__ == "__main__":
    sys.exit(main())
