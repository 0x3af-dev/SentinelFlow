# ML Service — Transaction Risk

## Service
Python FastAPI service at `ml-risk-service/` (port 8001), separate from Java backend.

**Responsibility:**
- Model loading, feature validation, inference, prediction generation, metadata
- Does NOT own business policy, final decision, rules, evidence, audit

**Endpoints:**
- `GET /health` → `{status, model_loaded, model_name, model_version, feature_schema_version, model_type}`
- `POST /predict` → `PredictResponse` with `risk_score 0..1, prediction, risk_factors, model_metadata`

## Model
- **Name:** `risk-model`, **Version:** `v1`, **Feature Schema:** `fs-v1`
- **Algorithm:** LogisticRegression (selected via training pipeline; also tried RandomForest, HistGradientBoosting, GradientBoosting, XGBoost)
- **Training Data:** Synthetic (20000 rows, 15.14% fraud, clearly labeled as synthetic, NOT real fraud performance)
  - Fraud patterns: high amount, night hours (22-5), high velocity (10m/1h/24h), new device/location, low account age, high device user count
  - Normal patterns: normal amount, day hours, low velocity, known device/location, mature account
- **Metrics (synthetic, not real):**
  - Baseline: accuracy 0.8487, AUC 0.5
  - LogisticRegression: accuracy 1.0, precision 1.0, recall 1.0, F1 1.0, AUC 1.0 (overly perfect due to separable synthetic data, documented as synthetic)
  - RandomForest: same perfect scores
  - HistGradientBoosting: accuracy 0.9997, AUC 0.9989
  - Test AUC: 1.0
  - **Note:** Synthetic performance is not real-world; model is intentionally simple and reproducible for Phase 2 lineage testing

## Feature Schema (fs-v1)
23 numeric: transaction_amount, transaction_hour, transaction_day_of_week, user_transaction_count_24h/7d/30d, user_total_volume_24h/7d/30d, user_avg_transaction_amount, user_account_age_days, transactions_last_10_minutes/1_hour/24_hours, is_new_device, device_transaction_count, device_user_count, device_age_days, is_new_location, location_transaction_count, location_age_days, merchant_transaction_count, merchant_category_frequency
8 categorical: channel, transaction_type, merchant_category, merchant_country, device_type, device_platform, user_country, user_region

## Training
```bash
cd ml-risk-service
python train.py  # generates synthetic, trains, evaluates, saves to models/risk_model.joblib + model_metadata.json
```
- Reproducible via `np.random.seed(42)`, stratified train/val/test 70/15/15
- Saves artifact with metadata: model_name, model_version, feature_schema, algorithm, dataset_size, fraud_rate, metrics_table, best, test_auc, saved_at

## Inference Contract
**Request:** all features as JSON (see `features.py` `get_feature_columns()`)
**Response:**
```json
{
  "model_name": "risk-model",
  "model_version": "v1",
  "feature_schema_version": "fs-v1",
  "risk_score": 0.72,
  "prediction": "MEDIUM",
  "risk_factors": [{"factor_type":"NEW_DEVICE","description":"...","severity":"HIGH","source":"MODEL_FEATURE","details":{}}],
  "model_metadata": {"risk_score":0.72,"contributing_features":[...],"model_type":"gradient_boosting"},
  "inference_latency_ms": 5,
  "inference_timestamp": "2026-09-14T00:00:00Z"
}
```
- Validates feature schema, model version, score 0..1
- Rejects incompatible feature schema with 400
- Returns structured risk factors (NEW_DEVICE, HIGH_VELOCITY, etc.) with severity and details, language "contributed to model risk" not "caused fraud"

## Risk Score
- Normalized 0.0 (lowest) → 1.0 (highest modeled risk)
- Not claimed as calibrated probability unless calibration performed (Phase 2 uses risk score terminology)

## Explainability
- Simple model-specific explanation: contributing features with observed values
- No SHAP required in Phase 2

## Health
- `model_loaded` indicates artifact availability
- No secrets exposed

## Failure
- If ML unavailable, Java client throws `MlInferenceException`, pipeline marks FAILED, no fabricated 0.0

## Lineage
- Every RiskScore references ModelVersion (risk-model/v1) and FeatureSnapshot (fs-v1)
- DecisionRecord references RiskScore + DecisionPolicy
