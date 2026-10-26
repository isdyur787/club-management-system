import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__)))

try:
    from scripts.ingest_test_data import ingest_all
    print("=== Starting data ingestion ===", flush=True)
    ingest_all()
    print("=== Data ingestion complete ===", flush=True)
except Exception as e:
    import traceback
    traceback.print_exc()
