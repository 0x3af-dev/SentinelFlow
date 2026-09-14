"""
Feature engineering for Transaction Risk Model.
Builds a single sklearn ColumnTransformer + pipeline that is
saved with the model so train/inference share identical preprocessing.
"""
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.impute import SimpleImputer
import pandas as pd
import numpy as np

FEATURE_SCHEMA_VERSION = "fs-v1"

# Feature columns that the model expects
NUMERIC_FEATURES = [
    "transaction_amount",
    "transaction_hour",
    "transaction_day_of_week",
    "user_transaction_count_24h",
    "user_transaction_count_7d",
    "user_transaction_count_30d",
    "user_total_volume_24h",
    "user_total_volume_7d",
    "user_total_volume_30d",
    "user_avg_transaction_amount",
    "user_account_age_days",
    "transactions_last_10_minutes",
    "transactions_last_1_hour",
    "transactions_last_24_hours",
    "is_new_device",
    "device_transaction_count",
    "device_user_count",
    "device_age_days",
    "is_new_location",
    "location_transaction_count",
    "location_age_days",
    "merchant_transaction_count",
    "merchant_category_frequency",
]

CATEGORICAL_FEATURES = [
    "channel",
    "transaction_type",
    "merchant_category",
    "merchant_country",
    "device_type",
    "device_platform",
    "user_country",
    "user_region",
]


def add_derived_features(df: pd.DataFrame) -> pd.DataFrame:
    """Add derived features from raw transaction/enrichment data."""
    df = df.copy()
    
    # Ensure numeric columns exist
    for c in NUMERIC_FEATURES:
        if c not in df.columns:
            df[c] = 0
        df[c] = pd.to_numeric(df[c], errors="coerce").fillna(0)
    
    # Ensure categorical columns exist
    for c in CATEGORICAL_FEATURES:
        if c not in df.columns:
            df[c] = "UNKNOWN"
        df[c] = df[c].fillna("UNKNOWN").astype(str)
    
    # Derived features
    # Transaction time features
    if "transaction_timestamp" in df.columns:
        df["transaction_timestamp"] = pd.to_datetime(df["transaction_timestamp"], errors="coerce")
        df["transaction_hour"] = df["transaction_timestamp"].dt.hour.fillna(12).astype(int)
        df["transaction_day_of_week"] = df["transaction_timestamp"].dt.dayofweek.fillna(0).astype(int)
    else:
        if "transaction_hour" not in df.columns:
            df["transaction_hour"] = 12
        if "transaction_day_of_week" not in df.columns:
            df["transaction_day_of_week"] = 0
    
    # Account age in days
    if "user_created_at" in df.columns:
        df["user_created_at"] = pd.to_datetime(df["user_created_at"], errors="coerce")
        ref_time = df["transaction_timestamp"] if "transaction_timestamp" in df.columns else pd.Timestamp.now(tz="UTC")
        df["user_account_age_days"] = (ref_time - df["user_created_at"]).dt.total_seconds() / 86400
        df["user_account_age_days"] = df["user_account_age_days"].fillna(0).clip(lower=0)
    elif "user_account_age_days" not in df.columns:
        df["user_account_age_days"] = 0
    
    # Device age in days
    if "device_first_seen_at" in df.columns:
        df["device_first_seen_at"] = pd.to_datetime(df["device_first_seen_at"], errors="coerce")
        ref_time = df["transaction_timestamp"] if "transaction_timestamp" in df.columns else pd.Timestamp.now(tz="UTC")
        df["device_age_days"] = (ref_time - df["device_first_seen_at"]).dt.total_seconds() / 86400
        df["device_age_days"] = df["device_age_days"].fillna(0).clip(lower=0)
    elif "device_age_days" not in df.columns:
        df["device_age_days"] = 0
    
    # Location age in days
    if "location_first_seen_at" in df.columns:
        df["location_first_seen_at"] = pd.to_datetime(df["location_first_seen_at"], errors="coerce")
        ref_time = df["transaction_timestamp"] if "transaction_timestamp" in df.columns else pd.Timestamp.now(tz="UTC")
        df["location_age_days"] = (ref_time - df["location_first_seen_at"]).dt.total_seconds() / 86400
        df["location_age_days"] = df["location_age_days"].fillna(0).clip(lower=0)
    elif "location_age_days" not in df.columns:
        df["location_age_days"] = 0
    
    # Boolean to int conversion
    for bool_col in ["is_new_device", "is_new_location"]:
        if bool_col in df.columns:
            df[bool_col] = df[bool_col].astype(int)
    
    return df


def build_preprocessor():
    """Build the sklearn ColumnTransformer for preprocessing."""
    numeric_pipe = Pipeline(steps=[
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
    ])
    categorical_pipe = Pipeline(steps=[
        ("imputer", SimpleImputer(strategy="constant", fill_value="UNKNOWN")),
        ("onehot", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
    ])
    pre = ColumnTransformer(
        transformers=[
            ("num", numeric_pipe, NUMERIC_FEATURES),
            ("cat", categorical_pipe, CATEGORICAL_FEATURES),
        ],
        remainder="drop",
        sparse_threshold=0,
    )
    return pre


def get_feature_columns():
    """Return list of all feature columns the model expects."""
    return NUMERIC_FEATURES + CATEGORICAL_FEATURES


def validate_features(features: dict) -> tuple[bool, list[str]]:
    """Validate that required features are present."""
    missing = []
    for f in NUMERIC_FEATURES + CATEGORICAL_FEATURES:
        if f not in features:
            missing.append(f)
    return len(missing) == 0, missing