package com.lankapay.transfer.web.dto;

import com.lankapay.transfer.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of reconciliation query on a transfer")
public record ReconcileResultResponse(
        Long transferId,
        String reference,
        TransactionStatus previousStatus,
        TransactionStatus resolvedStatus,
        boolean resolved,
        String message
) {}
