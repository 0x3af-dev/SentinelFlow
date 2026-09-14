"""Model save/load + metadata for Transaction Risk Model."""
import json
import os
import joblib
from datetime import datetime, timezone

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODELS_DIR, exist_ok=True)

ARTIFACT_PATH = os.path.join(MODELS_DIR, "risk_model.joblib")
META_PATH = os.path.join(MODELS_DIR, "model_metadata.json")


def save_artifact(pipeline, metadata: dict):
    joblib.dump(pipeline, ARTIFACT_PATH)
    metadata["saved_at"] = datetime.now(timezone.utc).isoformat()
    with open(META_PATH, "w") as f:
        json.dump(metadata, f, indent=2)
    return ARTIFACT_PATH


def load_artifact():
    if not os.path.exists(ARTIFACT_PATH):
        return None, None
    pipe = joblib.load(ARTIFACT_PATH)
    meta = {}
    if os.path.exists(META_PATH):
        with open(META_PATH) as f:
            meta = json.load(f)
    return pipe, meta