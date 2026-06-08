package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.api.request.PreApprovalCancelRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalCancelResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalCaptureRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalCaptureResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalInquireResponse;
import com.erumpay.card_simulator_service.dto.api.request.PreApprovalRequest;
import com.erumpay.card_simulator_service.dto.api.response.PreApprovalResponse;
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

    // [be] 하지혁 260603 PreApproval API 1 : 가승인 요청
    @PostMapping("/request")
    public PreApprovalResponse request(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PreApprovalRequest request) {
        return preApprovalService.request(idempotencyKey, request);
    }

    // [be] 하지혁 260603 PreApproval API 2 : 가승인 취소
    @PostMapping("/cancel")
    public PreApprovalCancelResponse cancel(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PreApprovalCancelRequest request) {
        return preApprovalService.cancel(idempotencyKey, request);
    }

    // [be] 하지혁 260603 PreApproval API 3 : 가승인 조회
    @PostMapping("/inquire")
    public PreApprovalInquireResponse inquire(@RequestBody @Valid PreApprovalInquireRequest request) {
        return preApprovalService.inquire(request);
    }

    // [be] 하지혁 260604 PreApproval API 4 : 가승인 캡쳐 (가승인 → 결제 확정)
    @PostMapping("/capture")
    public PreApprovalCaptureResponse capture(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid PreApprovalCaptureRequest request) {
        return preApprovalService.capture(idempotencyKey, request);
    }
}
