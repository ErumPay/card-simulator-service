package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.PreApprovalCancelRequest;
import com.erumpay.card_simulator_service.dto.PreApprovalCancelResponse;
import com.erumpay.card_simulator_service.dto.PreApprovalInquireRequest;
import com.erumpay.card_simulator_service.dto.PreApprovalInquireResponse;
import com.erumpay.card_simulator_service.dto.PreApprovalRequest;
import com.erumpay.card_simulator_service.dto.PreApprovalResponse;
import com.erumpay.card_simulator_service.service.PreApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-simulator/pre-approval")
@RequiredArgsConstructor
@Validated
public class PreApprovalController {

    private final PreApprovalService preApprovalService;

    @PostMapping("/request")
    public PreApprovalResponse request(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PreApprovalRequest request) {
        return preApprovalService.request(idempotencyKey, request);
    }

    @PostMapping("/cancel")
    public PreApprovalCancelResponse cancel(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PreApprovalCancelRequest request) {
        return preApprovalService.cancel(idempotencyKey, request);
    }

    @PostMapping("/inquire")
    public PreApprovalInquireResponse inquire(@RequestBody @Valid PreApprovalInquireRequest request) {
        return preApprovalService.inquire(request);
    }
}
