package com.claims.platform.notification.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** A customer's notification settings. In-app is always on; other channels need opt-in and an address. */
public record Preference(String userId, String language, String email, boolean emailEnabled, String phone,
                         boolean smsEnabled, boolean pushEnabled) {
    public static final List<String> LANGUAGES = List.of("en", "zh", "zh-Hant", "ms", "th", "id");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9 ]{7,20}");

    public Preference {
        Objects.requireNonNull(userId, "userId");
        language = language == null || language.isBlank() ? "en" : language;
        if (!LANGUAGES.contains(language)) {
            throw new IllegalArgumentException("language must be one of " + LANGUAGES);
        }
        if (emailEnabled && (email == null || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException("a valid e-mail address is needed to enable e-mail");
        }
        if (smsEnabled && (phone == null || !PHONE.matcher(phone).matches())) {
            throw new IllegalArgumentException("a valid phone number is needed to enable SMS");
        }
    }

    public static Preference defaults(String userId) {
        return new Preference(userId, "en", null, false, null, false, false);
    }

    /** Channel and address for each channel this customer receives. */
    public List<Delivery> deliveries() {
        List<Delivery> deliveries = new ArrayList<>();
        deliveries.add(new Delivery(Channel.IN_APP, userId));
        if (emailEnabled) {
            deliveries.add(new Delivery(Channel.EMAIL, email));
        }
        if (smsEnabled) {
            deliveries.add(new Delivery(Channel.SMS, phone));
        }
        if (pushEnabled) {
            deliveries.add(new Delivery(Channel.PUSH, userId));
        }
        return deliveries;
    }

    public record Delivery(Channel channel, String address) {
    }
}
