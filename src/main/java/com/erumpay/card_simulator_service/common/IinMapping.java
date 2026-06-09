package com.erumpay.card_simulator_service.common;

import java.util.Map;

public class IinMapping {

    private IinMapping() {}

    public enum CardForm { CREDIT, CHECK }

    // 80~89 mockBin 규약 밖의 실물 카드 BIN(앞 6자리) -> 카드사 매핑.
    // billing-key-service IinMapping 과 동일 내용을 유지해야 한다.
    private static final Map<String, CardCompany> PROMOTED_BIN_TO_COMPANY = Map.of(
        "527289", CardCompany.KB
    );

    // mockBin 규약: xx0000~xx0499 = 신용, xx0500~xx0999 = 체크 (CardProductCatalog 일관 적용)
    public static CardForm resolveCardForm(String cardNumber) {
        if (cardNumber == null) {
            return CardForm.CREDIT;
        }
        String normalized = cardNumber.replaceAll("\\D", "");
        if (normalized.length() < 6) {
            return CardForm.CREDIT;
        }
        int suffix = Integer.parseInt(normalized.substring(2, 6));
        return suffix < 500 ? CardForm.CREDIT : CardForm.CHECK;
    }

    public static CardCompany findByCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return CardCompany.UNKNOWN;
        }
        String normalized = cardNumber.replaceAll("\\D", "");
        if (normalized.length() < 2) {
            return CardCompany.UNKNOWN;
        }
        if (normalized.length() >= 6) {
            CardCompany promoted = PROMOTED_BIN_TO_COMPANY.get(normalized.substring(0, 6));
            if (promoted != null) {
                return promoted;
            }
        }
        return switch (normalized.substring(0, 2)) {
            case "80" -> CardCompany.SAMSUNG;
            case "81" -> CardCompany.SHINHAN;
            case "82" -> CardCompany.HYUNDAI;
            case "83" -> CardCompany.KB;
            case "84" -> CardCompany.LOTTE;
            case "85" -> CardCompany.WOORI;
            case "86" -> CardCompany.HANA;
            case "87" -> CardCompany.NH;
            case "88" -> CardCompany.IBK;
            case "89" -> resolveOtherSeries(normalized);
            default -> CardCompany.UNKNOWN;
        };
    }

    // 89 시리즈는 단일 카드사가 아니라 여러 발급사가 섞여 있어, 앞 2자리로는 구분되지 않는다.
    // 6자리 mockBin 단위로 발급사를 매핑한다(미등록 89 BIN은 UNKNOWN).
    private static CardCompany resolveOtherSeries(String normalized) {
        if (normalized.length() < 6) {
            return CardCompany.UNKNOWN;
        }
        return switch (normalized.substring(0, 6)) {
            case "890000" -> CardCompany.BC;
            case "890500" -> CardCompany.KG_FINANCIAL;
            case "890501", "890502", "890503", "890504", "890505" -> CardCompany.MG_COMMUNITY;
            case "890506" -> CardCompany.OK_CASHBAG;
            case "890507" -> CardCompany.NAVERPAY;
            case "890508" -> CardCompany.PAYCO;
            case "890509", "890510", "890511", "890512", "890513", "890514" -> CardCompany.EPOST;
            case "890515" -> CardCompany.JEJU_BANK;
            case "890516", "890517", "890518" -> CardCompany.KAKAO_BANK;
            case "890519", "890520" -> CardCompany.K_BANK;
            case "890521" -> CardCompany.TOSS_BANK;
            default -> CardCompany.UNKNOWN;
        };
    }
}
