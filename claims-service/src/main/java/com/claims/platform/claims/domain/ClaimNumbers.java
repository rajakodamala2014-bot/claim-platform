package com.claims.platform.claims.domain;

public final class ClaimNumbers {
    private ClaimNumbers() {
    }

    public static String format(String market, String lineOfBusiness, int year, long sequence) {
        String code = switch (lineOfBusiness) {
            case "MOTOR" -> "MTR";
            case "PROPERTY" -> "PRP";
            default -> throw new IllegalArgumentException("Unsupported line of business " + lineOfBusiness);
        };
        return "%s-%s-%d-%06d".formatted(market, code, year, sequence);
    }
}
