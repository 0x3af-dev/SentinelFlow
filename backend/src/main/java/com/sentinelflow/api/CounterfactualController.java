package com.sentinelflow.api;

import com.sentinelflow.analytics.counterfactual.CounterfactualFeatureRegistry;
import com.sentinelflow.analytics.counterfactual.CounterfactualService;
import com.sentinelflow.analytics.dto.CounterfactualRequest;
import com.sentinelflow.analytics.dto.CounterfactualResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Counterfactual Engine: re-scores a persisted feature snapshot with the
 * active model after bounded, validated feature edits. Descriptive only; never
 * mutates production lineage.
 */
@RestController
@RequestMapping("/api/counterfactuals")
public class CounterfactualController {

    private final CounterfactualService counterfactualService;
    private final CounterfactualFeatureRegistry featureRegistry;

    public CounterfactualController(CounterfactualService counterfactualService,
                                     CounterfactualFeatureRegistry featureRegistry) {
        this.counterfactualService = counterfactualService;
        this.featureRegistry = featureRegistry;
    }

    /** Supported editable features (name, description, min, max, integral) for the UI. */
    @GetMapping("/features")
    public ResponseEntity<List<CounterfactualFeatureRegistry.FeatureSpec>> features() {
        return ResponseEntity.ok(featureRegistry.list());
    }

    @PostMapping
    public ResponseEntity<CounterfactualResponse> analyze(
            @RequestBody CounterfactualRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(counterfactualService.analyze(request));
    }

    @GetMapping("/transactions/{transactionReference}")
    public ResponseEntity<List<CounterfactualResponse>> listForTransaction(
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(counterfactualService.listCounterfactuals(transactionReference));
    }
}