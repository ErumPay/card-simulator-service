package com.erumpay.card_simulator_service.common;

public class IinMapping {

    private IinMapping() {}

    public static CardCompany findByCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return CardCompany.UNKNOWN;
        }
        String normalized = cardNumber.replaceAll("\\D", "");
        if (normalized.length() < 2) {
            return CardCompany.UNKNOWN;
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
            case "89" -> CardCompany.OTHER;
            default -> CardCompany.UNKNOWN;
        };
    }
}
