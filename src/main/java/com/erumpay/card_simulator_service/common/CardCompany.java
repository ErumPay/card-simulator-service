package com.erumpay.card_simulator_service.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum CardCompany {
    SAMSUNG("삼성카드"),
    SHINHAN("신한카드"),
    HYUNDAI("현대카드"),
    KB("KB국민카드"),
    LOTTE("롯데카드"),
    WOORI("우리카드"),
    HANA("하나카드"),
    NH("NH농협카드"),
    IBK("IBK기업은행"),
    BC("BC카드"),
    KG_FINANCIAL("KG파이낸셜"),
    MG_COMMUNITY("MG새마을금고"),
    OK_CASHBAG("OK캐쉬백"),
    NAVERPAY("네이버페이"),
    PAYCO("NHN페이코"),
    EPOST("우체국"),
    JEJU_BANK("제주은행"),
    KAKAO_BANK("카카오뱅크"),
    K_BANK("케이뱅크"),
    TOSS_BANK("토스뱅크"),
    UNKNOWN("미식별 카드사");

    @JsonValue
    private final String displayName;

    private static final Map<String, CardCompany> BY_DISPLAY_NAME =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(c -> c.displayName, c -> c));

    @JsonCreator
    public static CardCompany fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return BY_DISPLAY_NAME.getOrDefault(displayName, UNKNOWN);
    }
}
