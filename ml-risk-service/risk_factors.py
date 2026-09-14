"""Build structured risk factors from model predictions and feature values."""

from typing import Any

# Risk factor definitions with thresholds and descriptions
RISK_FACTOR_DEFINITIONS = {
    "HIGH_AMOUNT": {
        "feature": "transaction_amount",
        "threshold": 50000,
        "description": "Transaction amount significantly exceeds typical range",
        "severity": "HIGH",
    },
    "ELEVATED_AMOUNT": {
        "feature": "transaction_amount",
        "threshold": 10000,
        "description": "Transaction amount is above typical range",
        "severity": "MEDIUM",
    },
    "UNUSUAL_HOUR": {
        "feature": "transaction_hour",
        "threshold": (22, 5),  # 10 PM to 5 AM
        "description": "Transaction occurred during unusual hours",
        "severity": "MEDIUM",
    },
    "HIGH_VELOCITY_10M": {
        "feature": "transactions_last_10_minutes",
        "threshold": 3,
        "description": "High transaction velocity in last 10 minutes",
        "severity": "HIGH",
    },
    "HIGH_VELOCITY_1H": {
        "feature": "transactions_last_1_hour",
        "threshold": 10,
        "description": "High transaction velocity in last hour",
        "severity": "HIGH",
    },
    "HIGH_VELOCITY_24H": {
        "feature": "transactions_last_24_hours",
        "threshold": 50,
        "description": "High transaction velocity in last 24 hours",
        "severity": "MEDIUM",
    },
    "NEW_DEVICE": {
        "feature": "is_new_device",
        "threshold": 1,
        "description": "Transaction from a device not previously associated with user",
        "severity": "HIGH",
    },
    "NEW_LOCATION": {
        "feature": "is_new_location",
        "threshold": 1,
        "description": "Transaction from a location not previously associated with user",
        "severity": "HIGH",
    },
    "LOW_ACCOUNT_AGE": {
        "feature": "user_account_age_days",
        "threshold": 30,
        "description": "User account is relatively new",
        "severity": "MEDIUM",
    },
    "HIGH_DEVICE_USER_COUNT": {
        "feature": "device_user_count",
        "threshold": 3,
        "description": "Device has been used by multiple users",
        "severity": "MEDIUM",
    },
    "UNUSUAL_MERCHANT_CATEGORY": {
        "feature": "merchant_category_frequency",
        "threshold": 0.1,
        "description": "Transaction at merchant category rarely used by user",
        "severity": "LOW",
    },
}


def build_risk_factors(features: dict[str, Any], risk_score: float) -> list[dict[str, Any]]:
    """Build structured risk factors based on feature values."""
    risk_factors = []
    
    for factor_id, definition in RISK_FACTOR_DEFINITIONS.items():
        feature_name = definition["feature"]
        threshold = definition["threshold"]
        
        if feature_name not in features:
            continue
            
        value = features[feature_name]
        triggered = False
        details = {"feature": feature_name, "observed_value": value}
        
        if isinstance(threshold, tuple):
            # Range check (e.g., unusual hour)
            low, high = threshold
            if feature_name == "transaction_hour":
                # Night hours: 22-23 and 0-5
                if value >= low or value <= high:
                    triggered = True
                    details["threshold_range"] = f"{low}-23, 0-{high}"
        elif isinstance(threshold, (int, float)):
            # Simple threshold check
            if value >= threshold:
                triggered = True
                details["threshold"] = threshold
        elif threshold == 1 and feature_name in ["is_new_device", "is_new_location"]:
            # Boolean flags
            if value == 1:
                triggered = True
                details["threshold"] = 1
        
        if triggered:
            risk_factors.append({
                "factor_type": factor_id,
                "description": definition["description"],
                "severity": definition["severity"],
                "source": "MODEL_FEATURE",
                "details": details,
            })
    
    # Add model-based risk level factor
    if risk_score >= 0.85:
        risk_factors.append({
            "factor_type": "MODEL_HIGH_RISK",
            "description": "ML model predicts high fraud probability",
            "severity": "HIGH",
            "source": "MODEL_PREDICTION",
            "details": {"risk_score": risk_score, "threshold": 0.85},
        })
    elif risk_score >= 0.5:
        risk_factors.append({
            "factor_type": "MODEL_ELEVATED_RISK",
            "description": "ML model predicts elevated fraud probability",
            "severity": "MEDIUM",
            "source": "MODEL_PREDICTION",
            "details": {"risk_score": risk_score, "threshold": 0.5},
        })
    
    return risk_factors


def get_model_explanation(features: dict[str, Any], risk_score: float) -> dict[str, Any]:
    """Generate basic model explanation."""
    contributing_features = []
    
    # Check which features likely contributed most
    feature_importance = {
        "transaction_amount": features.get("transaction_amount", 0),
        "is_new_device": features.get("is_new_device", 0),
        "is_new_location": features.get("is_new_location", 0),
        "transactions_last_1_hour": features.get("transactions_last_1_hour", 0),
        "transactions_last_10_minutes": features.get("transactions_last_10_minutes", 0),
        "user_account_age_days": features.get("user_account_age_days", 0),
    }
    
    for feat, value in feature_importance.items():
        if feat in ["is_new_device", "is_new_location"] and value == 1:
            contributing_features.append({
                "feature": feat,
                "observed": value,
                "contribution": "elevated_risk",
            })
        elif feat == "transaction_amount" and value > 10000:
            contributing_features.append({
                "feature": feat,
                "observed": value,
                "contribution": "elevated_risk",
            })
        elif feat in ["transactions_last_1_hour", "transactions_last_10_minutes"] and value > 5:
            contributing_features.append({
                "feature": feat,
                "observed": value,
                "contribution": "elevated_risk",
            })
        elif feat == "user_account_age_days" and value < 30:
            contributing_features.append({
                "feature": feat,
                "observed": value,
                "contribution": "elevated_risk",
            })
    
    return {
        "risk_score": risk_score,
        "contributing_features": contributing_features,
        "model_type": "gradient_boosting",
        "feature_schema_version": "fs-v1",
    }