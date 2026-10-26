import sys, os, traceback, faulthandler
faulthandler.enable()

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.dirname(os.path.abspath(__file__)))

def my_excepthook(type, value, tb):
    print(f"UNHANDLED: {type.__name__}: {value}", flush=True)
    traceback.print_exception(type, value, tb)

sys.excepthook = my_excepthook

print("Step 1: importing module...", flush=True)
import scripts.ingest_test_data
print("Step 2: imported OK", flush=True)
