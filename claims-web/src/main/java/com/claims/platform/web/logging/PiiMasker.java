package com.claims.platform.web.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks personal data that may slip into log messages: e-mail addresses, international phone numbers and
 * long digit runs (phone numbers, card/ID numbers). Claim numbers such as {@code SG-MTR-2026-000451} and
 * UUIDs are deliberately left intact because they are needed for support and contain no PII.
 */
public final class PiiMasker {
    private static final Pattern EMAIL = Pattern.compile(
            "([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile(
            "(?<![\\w+-])\\+\\d{1,3}[ ]?\\d{3,6}[ ]?\\d{2,6}(?![\\w-])");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<![\\w-])\\d{8,19}(?![\\w-])");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = EMAIL.matcher(text).replaceAll("$1***@$2");
        masked = replaceKeepingTail(INTERNATIONAL_PHONE.matcher(masked));
        return replaceKeepingTail(LONG_DIGITS.matcher(masked));
    }

    private static String replaceKeepingTail(Matcher matcher) {
        return matcher.replaceAll(match -> {
            String digits = match.group().replaceAll("\\D", "");
            return Matcher.quoteReplacement("****" + digits.substring(digits.length() - 2));
        });
    }
}
