package com.sentinelflow.api;

import com.sentinelflow.analytics.dto.DecisionReplayResponse;
import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.analytics.replay.DecisionReplayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Decision Replay: reconstructs the persisted decision lineage of one
 * transaction as read-only evidence. No ML inference is performed.
 */
@RestController
@RequestMapping("/api/transactions")
public class DecisionReplayController {

    private final DecisionReplayService replayService;

    public DecisionReplayController(DecisionReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/{transactionReference}/decision-replay")
    public ResponseEntity<DecisionReplayResponse> replay(
            @PathVariable String transactionReference) {
        return ResponseEntity.ok(replayService.replay(transactionReference));
    }
}