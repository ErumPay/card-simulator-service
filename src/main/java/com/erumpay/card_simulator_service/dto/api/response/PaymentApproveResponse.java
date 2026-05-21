package com.erumpay.card_simulator_service.dto.api.response;

import com.erumpay.card_simulator_service.entity.SimulatorPaymentHistory.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PaymentApproveResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("pg_txn_id") Long pgTxnId,
        @JsonProperty("payment_status") PaymentStatus paymentStatus,
        @JsonProperty("approval_number") String approvalNumber,
        @JsonProperty("approved_at") String approvedAt,
        @JsonProperty("approved_amount") Long approvedAmount,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
