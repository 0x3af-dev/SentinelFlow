package com.sentinelflow.api;

import com.sentinelflow.observability.Md;
import com.sentinelflow.pipeline.TransactionIntelligencePipeline;
import com.sentinelflow.shared.dto.PipelineResult;
import com.sentinelflow.transaction.TransactionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public gateway for the investigation workspace demo narrative: runs the
 * existing pipeline for a transaction. Thin facade — no decision logic is
 * duplicated here; all steps still happen in TransactionIntelligencePipeline.
 * Database availability failures surface cleanly as 503 DEPENDENCY_UNAVAILABLE
 * so the investigation workspace can distinguish infrastructure outages from
 * application logic errors.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionProcessController {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessController.class);

    private final TransactionRepository transactionRepository;
    private final TransactionIntelligencePipeline pipeline;

    public TransactionProcessController(TransactionRepository transactionRepository,
                                        TransactionIntelligencePipeline pipeline) {
        this.transactionRepository = transactionRepository;
        this.pipeline = pipeline;
    }

    @PostMapping("/{transactionReference}/process")
    public ResponseEntity<?> process(@PathVariable String transactionReference) {
        String correlationId = UUID.randomUUID().toString();
        return Md.run(Md.of(Md.OP_PIPELINE, correlationId, transactionReference), () -> {
            try {
                var transaction = transactionRepository.findByTransactionReference(transactionReference);
                if (transaction.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                            "code", "NOT_FOUND",
                            "message", "Transaction not found: " + transactionReference,
                            "details", Map.of("transactionReference", transactionReference)));
                }
                log.info("Public process request for transaction: {}", transactionReference);
                PipelineResult result = pipeline.process(transactionReference);
                return ResponseEntity.ok(result);
            } catch (DataAccessException e) {
                log.error("Database unavailable for transaction {}: {}", transactionReference, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "code", "DEPENDENCY_UNAVAILABLE",
                        "message", "A required dependency is unavailable. Please try again later.",
                        "details", Map.of("transactionReference", transactionReference)));
            } catch (TransactionIntelligencePipeline.PipelineException e) {
                log.error("Pipeline failed for transaction {}: {}", transactionReference, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "code", "ML_UNAVAILABLE",
                        "message", "The transaction pipeline could not complete this request.",
                        "details", Map.of(
                                "transactionReference", transactionReference,
                                "pipelineMessage", e.getMessage(),
                                "at", Instant.now().toString())));
            }
        });
    }
}