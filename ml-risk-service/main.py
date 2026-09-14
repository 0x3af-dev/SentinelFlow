"""FastAPI ML service for Transaction Risk Scoring: /health and /predict."""
import os
from typing import Optional, List, Dict, Any
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from dotenv import load_dotenv

load_dotenv()

from features import add_derived_features, build_preprocessor, get_feature_columns, validate_features, FEATURE_SCHEMA_VERSION
from model import load_artifact
from risk_factors import build_risk_factors, get_model_explanation

app = FastAPI(title="SentinelFlow Transaction Risk ML Service")

PIPE, META = None, {}


@app.on_event("startup")
def _load():
    global PIPE, META
    PIPE, META = load_artifact()
    if PIPE is None:
        print("WARNING: no model artifact found. Train first (python train.py). /predict will 503.")


class FeatureRequest(BaseModel):
    """Input features for risk prediction."""
    transaction_amount: float
    transaction_hour: Optional[int] = 12
    transaction_day_of_week: Optional[int] = 0
    transaction_timestamp: Optional[str] = None
    user_transaction_count_24h: Optional[int] = 0
    user_transaction_count_7d: Optional[int] = 0
    user_transaction_count_30d: Optional[int] = 0
    user_total_volume_24h: Optional[float] = 0
    user_total_volume_7d: Optional[float] = 0
    user_total_volume_30d: Optional[float] = 0
    user_avg_transaction_amount: Optional[float] = 0
    user_account_age_days: Optional[float] = 0
    user_created_at: Optional[str] = None
    transactions_last_10_minutes: Optional[int] = 0
    transactions_last_1_hour: Optional[int] = 0
    transactions_last_24_hours: Optional[int] = 0
    is_new_device: Optional[int] = 0
    device_transaction_count: Optional[int] = 0
    device_user_count: Optional[int] = 1
    device_age_days: Optional[float] = 0
    device_first_seen_at: Optional[str] = None
    is_new_location: Optional[int] = 0
    location_transaction_count: Optional[int] = 0
    location_age_days: Optional[float] = 0
    location_first_seen_at: Optional[str] = None
    merchant_transaction_count: Optional[int] = 0
    merchant_category_frequency: Optional[float] = 0
    channel: Optional[str] = "ONLINE"
    transaction_type: Optional[str] = "PURCHASE"
    merchant_category: Optional[str] = "OTHER"
    merchant_country: Optional[str] = "IN"
    device_type: Optional[str] = "UNKNOWN"
    device_platform: Optional[str] = "UNKNOWN"
    user_country: Optional[str] = "IN"
    user_region: Optional[str] = "UNKNOWN"


class RiskFactorResponse(BaseModel):
    factor_type: str
    description: str
    severity: str
    source: str
    details: Dict[str, Any]


class PredictResponse(BaseModel):
    model_name: str
    model_version: str
    feature_schema_version: str
    risk_score: float
    prediction: str
    risk_factors: List[RiskFactorResponse]
    model_metadata: Dict[str, Any]
    inference_latency_ms: int
    inference_timestamp: str


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_name: str
    model_version: str
    feature_schema_version: str
    model_type: str


@app.get("/health", response_model=HealthResponse)
def health():
    return HealthResponse(
        status="ok" if PIPE is not None else "degraded",
        model_loaded=PIPE is not None,
        model_name=META.get("model_name", "risk-model"),
        model_version=META.get("model_version", "none"),
        feature_schema_version=META.get("feature_schema_version", FEATURE_SCHEMA_VERSION),
        model_type=META.get("model_type", "none"),
    )


@app.post("/predict", response_model=PredictResponse)
def predict(req: FeatureRequest):
    import time
    start_time = time.perf_counter()
    
    if PIPE is None:
        raise HTTPException(status_code=503, detail="Model not loaded. Train first.")
    
    # Convert request to dict
    features = req.model_dump()
    
    # Validate features
    valid, missing = validate_features(features)
    if not valid:
        raise HTTPException(status_code=400, detail=f"Missing required features: {missing}")
    
    # Add derived features
    df = pd.DataFrame([features])
    df = add_derived_features(df)
    
    # Get feature columns in correct order
    feature_cols = get_feature_columns()
    X = df[feature_cols]
    
    # Predict
    try:
        prob = float(PIPE.predict_proba(X)[0, 1])
        pred_label = "HIGH" if prob >= 0.85 else ("MEDIUM" if prob >= 0.5 else "LOW")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Inference failed: {e}")
    
    latency_ms = int((time.perf_counter() - start_time) * 1000)
    
    # Build risk factors
    risk_factors = build_risk_factors(features, prob)
    
    # Model explanation
    explanation = get_model_explanation(features, prob)
    
    # Prepare response
    response = PredictResponse(
        model_name=META.get("model_name", "risk-model"),
        model_version=META.get("model_version", "v1"),
        feature_schema_version=META.get("feature_schema_version", FEATURE_SCHEMA_VERSION),
        risk_score=round(prob, 4),
        prediction=pred_label,
        risk_factors=[RiskFactorResponse(**rf) for rf in risk_factors],
        model_metadata=explanation,
        inference_latency_ms=latency_ms,
        inference_timestamp=pd.Timestamp.now(tz="UTC").isoformat(),
    )
    
    return response


if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", "8001"))
    uvicorn.run(app, host="0.0.0.0", port=port)