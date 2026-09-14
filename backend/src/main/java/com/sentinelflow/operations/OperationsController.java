package com.sentinelflow.operations;

import com.sentinelflow.observability.Md;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operational endpoints backing the System Health UI. These inspect
 * probes, meters and aggregates; none of them read or write event payloads and
 * none of them mutate state (no replay, no re-drive, no delete).
 */
@RestController
@RequestMapping("/api/operations")
public class OperationsController {

    private final OperationalService operationalService;

    public OperationsController(OperationalService operationalService) {
        this.operationalService = operationalService;
    }

    @GetMapping("/summary")
    public OperationalSummaryResponse summary() {
        return Md.run(Md.of(Md.OP_OPERATIONS, null, null), operationalService::summary);
    }

    @GetMapping("/dlq")
    public DlqResponse dlq() {
        return Md.run(Md.of(Md.OP_OPERATIONS, null, null), operationalService::dlq);
    }

    @GetMapping("/outbox")
    public OutboxResponse outbox() {
        return Md.run(Md.of(Md.OP_OPERATIONS, null, null), operationalService::outbox);
    }

    @GetMapping("/attempts")
    public AttemptSummaryResponse attempts() {
        return Md.run(Md.of(Md.OP_OPERATIONS, null, null), operationalService::attempts);
    }

    @GetMapping("/integrity")
    public DataIntegrityService.IntegrityResponse integrity() {
        return Md.run(Md.of(Md.OP_OPERATIONS, null, null), operationalService::integrity);
    }

    @GetMapping
    public Map<String, String> index() {
        return Map.of("status", "available", "endpoints", "/summary,/dlq,/outbox,/attempts,/integrity");
    }
}