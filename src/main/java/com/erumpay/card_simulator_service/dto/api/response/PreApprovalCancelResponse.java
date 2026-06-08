package com.erumpay.card_simulator_service.dto.api.response;

import com.erumpay.card_simulator_service.entity.SimulatorPreApproval.PreApprovalStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// [be] 하지혁 260603 가승인 취소 Response DTO
@Builder
public record PreApprovalCancelResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("pg_txn_id") Long pgTxnId,
        @JsonProperty("pre_approval_status") PreApprovalStatus preApprovalStatus,
        @JsonProperty("pre_approval_number") String preApprovalNumber,
        @JsonProperty("cancelled_at") String cancelledAt,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
