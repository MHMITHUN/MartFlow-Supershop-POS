package com.martflow.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response records for returns. */
public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record RequestedLine(int lineNo, BigDecimal quantity, String reason) {
    }

    public record ReturnRequest(List<RequestedLine> lines, String refundChannel) {
    }

    public record ReturnLineResponse(int lineNo, String name, BigDecimal quantity, String reason) {
    }

    public record ReturnResponse(
            String id,
            String receiptNo,
            LocalDateTime at,
            String cashier,
            List<ReturnLineResponse> lines,
            BigDecimal refundAmount,
            String refundChannel,
            String refundTransactionId) {
    }
}
