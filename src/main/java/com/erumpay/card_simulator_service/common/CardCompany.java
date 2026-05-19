package com.erumpay.card_simulator_service.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CardCompany {
    SHINHAN("신한카드"),
    SAMSUNG("삼성카드"),
    HYUNDAI("현대카드"),
    KB("국민카드"),
    LOTTE("롯데카드"),
    WOORI("우리카드"),
    HANA("하나카드"),
    NH("NH농협카드"),
    BC("BC카드"),
    UNKNOWN("미식별 카드사");

    private final String displayName;
}
