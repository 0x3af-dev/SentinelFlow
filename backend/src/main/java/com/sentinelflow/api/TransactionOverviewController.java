package com.sentinelflow.api;

import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.decision.DecisionRecordRepository;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.dto.TransactionOverview;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only transaction lookup for the investigation UI. Distinguishes "not
 * found", "found but not yet processed through the intelligence pipeline", and
 * "decided" so the frontend can present the right next action.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionOverviewController {

    private final TransactionRepository transactions;
    private final DecisionRecordRepository decisions;

    public TransactionOverviewController(TransactionRepository transactions,
                                         DecisionRecordRepository decisions) {
        this.transactions = transactions;
        this.decisions = decisions;
    }

    @GetMapping("/{transactionReference}")
    public ResponseEntity<TransactionOverview> overview(@PathVariable String transactionReference) {
        Transaction txn = transactions.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new AnalyticsNotFoundException("Transaction not found: " + transactionReference));
        boolean decided = decisions.findFirstByTransactionIdOrderByCreatedAtDesc(txn.getId()).isPresent();
        return ResponseEntity.ok(new TransactionOverview(
                txn.getTransactionReference(), txn.getAmount(), txn.getCurrency(),
                txn.getChannel(), txn.getTransactionType(), txn.getStatus().name(),
                txn.getTransactionTimestamp(), txn.getCreatedAt(), decided));
    }
}