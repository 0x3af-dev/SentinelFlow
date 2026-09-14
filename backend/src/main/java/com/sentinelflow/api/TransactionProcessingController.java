package com.sentinelflow.api;

import com.sentinelflow.pipeline.TransactionIntelligencePipeline;
import com.sentinelflow.shared.dto.PipelineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/transactions")
public class TransactionProcessingController {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingController.class);

    private final TransactionIntelligencePipeline pipeline;

    public TransactionProcessingController(TransactionIntelligencePipeline pipeline) {
        this.pipeline = pipeline;
    }

    @PostMapping("/{transactionReference}/process")
    public ResponseEntity<PipelineResult> processTransaction(@PathVariable String transactionReference) {
        log.info("Received request to process transaction: {}", transactionReference);
        
        try {
            PipelineResult result = pipeline.process(transactionReference);
            return ResponseEntity.ok(result);
        } catch (TransactionIntelligencePipeline.PipelineException e) {
            log.error("Pipeline failed for transaction {}: {}", transactionReference, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}