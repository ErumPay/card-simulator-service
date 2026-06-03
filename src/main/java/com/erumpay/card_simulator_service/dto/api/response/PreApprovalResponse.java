package com.erumpay.card_simulator_service.dto.api.response;

import com.erumpay.card_simulator_service.entity.SimulatorPreApproval.PreApprovalStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PreApprovalResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("pg_txn_id") Long pgTxnId,
        @JsonProperty("pre_approval_status") PreApprovalStatus preApprovalStatus,
        @JsonProperty("pre_approval_id") Long preApprovalId,
        @JsonProperty("pre_approval_number") String preApprovalNumber,
        @JsonProperty("pre_approved_at") String preApprovedAt,
        @JsonProperty("approved_amount") Long approvedAmount,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
