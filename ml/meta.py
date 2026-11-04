"""Lightweight Roboflow metadata (no image downloads). Writes datasets_report.md. Never prints the key."""
import json, os, sys
from pathlib import Path
import requests

ROOT = Path(__file__).parent
key = os.environ.get("ROBOFLOW_API_KEY")
envf = ROOT / ".env"
if not key and envf.exists():
    for line in envf.read_text().splitlines():
        if line.startswith("ROBOFLOW_API_KEY="):
            key = line.split("=", 1)[1].strip().strip('"\'')
if not key:
    sys.exit("ROBOFLOW_API_KEY missing: set it in env or ml/.env")

DATASETS = {
    "tardata": ("new-workspace-febsz", "tardata"),
    "objectdetection": ("universiti-malaysia-pahang-qcvas", "objectdetection-ngxjp"),
    "instrument_lab": ("universidad-de-los-andes-lzihh", "instrument-lab-detection"),
    "oscilloscope_detection": ("roboticslearning", "oscilloscope-detection"),
    "equipment_data": ("senior-design-yolov7", "equipment-data"),
    "testbw": ("new-workspace-febsz", "testbw"),
    "resistor_detection": ("circuits-project", "resistor-detection-5azes"),
}
out = {}
for name, (ws, proj) in DATASETS.items():
    r = requests.get(f"https://api.roboflow.com/{ws}/{proj}", params={"api_key": key}, timeout=30)
    try:
        j = r.json()
    except Exception:
        j = {"error": r.text[:200]}
    out[name] = {"status": r.status_code, "json": j}
    print(name, r.status_code, list(j.keys())[:8])
json.dump(out, open(ROOT / "datasets_meta_raw.json", "w"), indent=1)
