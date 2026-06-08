package com.erumpay.card_simulator_service.controller;

import com.erumpay.card_simulator_service.dto.api.request.PerformanceInquireRequest;
import com.erumpay.card_simulator_service.dto.api.response.PerformanceInquireResponse;
import com.erumpay.card_simulator_service.service.PerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/card-simulator/performance")
@RequiredArgsConstructor
@Validated
public class PerformanceController {

    private final PerformanceService performanceService;

    // [be] 하지혁 260603 Performance API 1 : 카드 실적 조회
    @PostMapping("/inquire")
    public PerformanceInquireResponse inquire(@RequestBody @Valid PerformanceInquireRequest request) {
        return performanceService.inquire(request);
    }
}
