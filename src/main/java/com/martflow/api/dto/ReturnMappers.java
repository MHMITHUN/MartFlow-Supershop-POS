package com.martflow.api.dto;

import com.martflow.returns.ReturnLine;
import com.martflow.returns.SaleReturn;

/** Maps returns to their API responses. */
public final class ReturnMappers {

    private ReturnMappers() {
    }

    public static ReturnDtos.ReturnResponse toResponse(SaleReturn r) {
        return new ReturnDtos.ReturnResponse(
                r.getId(),
                r.getReceiptNo(),
                r.getAt(),
                r.getCashierUsername(),
                r.getLines().stream()
                        .map(l -> new ReturnDtos.ReturnLineResponse(
                                l.saleLineNo(), l.name(), l.quantity(), l.reason()))
                        .toList(),
                r.getRefundAmount(),
                r.getRefundChannel(),
                r.getRefundTransactionId());
    }
}
