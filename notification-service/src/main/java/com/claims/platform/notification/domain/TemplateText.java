package com.claims.platform.notification.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code {{name}}} placeholders. Unknown placeholders render empty, so a template edit can't break delivery. */
public final class TemplateText {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9]*)\\s*}}");

    private TemplateText() {
    }

    public static String render(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString().replaceAll(" {2,}", " ").trim();
    }
}
