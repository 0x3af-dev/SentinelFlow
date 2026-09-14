"""Training pipeline for Transaction Risk Model."""
import os
import numpy as np
import pandas as pd
from sklearn.pipeline import Pipeline
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier, GradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, roc_auc_score
from sklearn.model_selection import train_test_split

from features import add_derived_features, build_preprocessor, get_feature_columns
from model import save_artifact

TARGET = "is_fraud"
MODEL_VERSION = os.environ.get("MODEL_VERSION", "v1")
FEATURE_SCHEMA_VERSION = "fs-v1"


def generate_synthetic_data(n_samples: int = 10000) -> pd.DataFrame:
    """Generate synthetic training data for transaction risk model."""
    np.random.seed(42)
    
    data = []
    for i in range(n_samples):
        # Base transaction
        is_fraud = np.random.random() < 0.15  # 15% fraud rate
        
        if is_fraud:
            # Fraud patterns
            amount = np.random.lognormal(10, 1.5)  # Higher amounts
            hour = np.random.choice([22,23,0,1,2,3,4,5])  # Night hours
            txn_10m = np.random.poisson(5)
            txn_1h = np.random.poisson(15)
            txn_24h = np.random.poisson(60)
            is_new_dev = np.random.random() < 0.4
            is_new_loc = np.random.random() < 0.3
            account_age = np.random.exponential(30)
            device_user_count = np.random.poisson(2) + 1
        else:
            # Normal patterns
            amount = np.random.lognormal(7, 1)
            hour = np.random.randint(6, 22)
            txn_10m = np.random.poisson(0.5)
            txn_1h = np.random.poisson(2)
            txn_24h = np.random.poisson(10)
            is_new_dev = np.random.random() < 0.05
            is_new_loc = np.random.random() < 0.05
            account_age = np.random.exponential(365)
            device_user_count = 1
        
        # Clamp values
        amount = max(1, min(amount, 100000))
        txn_10m = min(txn_10m, 20)
        txn_1h = min(txn_1h, 50)
        txn_24h = min(txn_24h, 200)
        account_age = min(account_age, 2000)
        device_user_count = min(device_user_count, 10)
        
        row = {
            "transaction_amount": round(amount, 2),
            "transaction_hour": hour,
            "transaction_day_of_week": np.random.randint(0, 7),
            "user_transaction_count_24h": np.random.poisson(5),
            "user_transaction_count_7d": np.random.poisson(20),
            "user_transaction_count_30d": np.random.poisson(80),
            "user_total_volume_24h": round(np.random.lognormal(8, 1), 2),
            "user_total_volume_7d": round(np.random.lognormal(9, 1), 2),
            "user_total_volume_30d": round(np.random.lognormal(10, 1), 2),
            "user_avg_transaction_amount": round(np.random.lognormal(7, 0.5), 2),
            "user_account_age_days": round(account_age),
            "transactions_last_10_minutes": txn_10m,
            "transactions_last_1_hour": txn_1h,
            "transactions_last_24_hours": txn_24h,
            "is_new_device": int(is_new_dev),
            "device_transaction_count": np.random.poisson(10),
            "device_user_count": device_user_count,
            "device_age_days": np.random.exponential(180),
            "is_new_location": int(is_new_loc),
            "location_transaction_count": np.random.poisson(5),
            "location_age_days": np.random.exponential(180),
            "merchant_transaction_count": np.random.poisson(100),
            "merchant_category_frequency": np.random.random(),
            "channel": np.random.choice(["ONLINE", "POS", "ATM", "MOBILE"]),
            "transaction_type": np.random.choice(["PURCHASE", "WITHDRAWAL", "TRANSFER", "PAYMENT"]),
            "merchant_category": np.random.choice(["GROCERY", "ELECTRONICS", "APPAREL", "FUEL", "RESTAURANT", "TRAVEL", "ENTERTAINMENT"]),
            "merchant_country": "IN",
            "device_type": np.random.choice(["MOBILE", "DESKTOP", "TABLET"]),
            "device_platform": np.random.choice(["ANDROID", "IOS", "WEB"]),
            "user_country": "IN",
            "user_region": np.random.choice(["Karnataka", "Maharashtra", "Delhi", "Tamil Nadu", "Telangana"]),
            "is_fraud": int(is_fraud),
        }
        data.append(row)
    
    return pd.DataFrame(data)


def evaluate_model(name, model, Xt, yt, Xv, yv):
    pt = model.predict(Xv)
    try:
        prob = model.predict_proba(Xv)[:, 1]
        auc = roc_auc_score(yv, prob)
    except Exception:
        auc = 0.0
    
    return {
        "model": name,
        "val_accuracy": round(float(accuracy_score(yv, pt)), 4),
        "val_precision": round(float(precision_score(yv, pt, zero_division=0)), 4),
        "val_recall": round(float(recall_score(yv, pt, zero_division=0)), 4),
        "val_f1": round(float(f1_score(yv, pt, zero_division=0)), 4),
        "val_auc": round(float(auc), 4),
    }


def main():
    print(f"Training Transaction Risk Model {MODEL_VERSION} with feature schema {FEATURE_SCHEMA_VERSION}")
    
    # Generate synthetic data
    df = generate_synthetic_data(20000)
    print(f"Generated {len(df)} synthetic samples, fraud rate: {df[TARGET].mean():.2%}")
    
    df = add_derived_features(df)
    feature_cols = get_feature_columns()
    
    y = df[TARGET]
    X = df[feature_cols]
    
    # Split
    Xt, Xr, yt, yr = train_test_split(X, y, test_size=0.3, random_state=42, stratify=y)
    Xv, Xte, yv, yte = train_test_split(Xr, yr, test_size=0.5, random_state=42, stratify=yr)
    
    pre = build_preprocessor()
    
    candidates = {
        "Baseline": DummyClassifier(strategy="prior", random_state=42),
        "LogisticRegression": LogisticRegression(max_iter=1000, random_state=42, class_weight="balanced"),
        "RandomForest": RandomForestClassifier(n_estimators=200, max_depth=10, min_samples_leaf=5, n_jobs=-1, random_state=42, class_weight="balanced"),
        "HistGradientBoosting": HistGradientBoostingClassifier(max_iter=300, learning_rate=0.05, max_leaf_nodes=31, min_samples_leaf=20, random_state=42, class_weight="balanced"),
        "GradientBoosting": GradientBoostingClassifier(n_estimators=200, learning_rate=0.05, max_depth=5, random_state=42),
    }
    
    try:
        import xgboost
        from xgboost import XGBClassifier
        candidates["XGBoost"] = XGBClassifier(n_estimators=300, max_depth=6, learning_rate=0.05, subsample=0.9, colsample_bytree=0.9, n_jobs=-1, random_state=42, eval_metric="logloss")
    except Exception:
        pass
    
    results = []
    pipes = {}
    for name, reg in candidates.items():
        pipe = Pipeline([("pre", pre), ("reg", reg)])
        pipe.fit(Xt, yt)
        r = evaluate_model(name, pipe, Xt, yt, Xv, yv)
        results.append(r)
        pipes[name] = pipe
        print(r)
    
    # Select best by AUC (excluding baseline)
    non_base = [r for r in results if not r["model"].startswith("Baseline")]
    best_row = max(non_base, key=lambda r: r["val_auc"]) if non_base else max(results, key=lambda r: r["val_auc"])
    best_name = best_row["model"]
    best_pipe = pipes[best_name]
    
    # Test metrics
    ptest = best_pipe.predict(Xte)
    try:
        prob_test = best_pipe.predict_proba(Xte)[:, 1]
        test_auc = roc_auc_score(yte, prob_test)
    except Exception:
        test_auc = 0.0
    
    print("\n===== SUMMARY =====")
    print(f"Dataset: synthetic  Samples: {len(df)}")
    print(f"Best model: {best_name}  val_AUC={best_row['val_auc']}  test_AUC={round(test_auc,4)}")
    
    meta = {
        "model_name": "risk-model",
        "model_version": MODEL_VERSION,
        "feature_schema_version": FEATURE_SCHEMA_VERSION,
        "model_type": best_name,
        "algorithm": best_name.lower().replace("gradient_boosting", "gradient_boosted_trees"),
        "data_source": "synthetic",
        "dataset_size": len(df),
        "fraud_rate": float(df[TARGET].mean()),
        "metrics_table": results,
        "best": best_row,
        "test_auc": round(test_auc, 4),
    }
    save_artifact(best_pipe, meta)
    print(f"Saved to models/risk_model.joblib + model_metadata.json")


if __name__ == "__main__":
    main()