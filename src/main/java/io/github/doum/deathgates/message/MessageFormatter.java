package io.github.doum.deathgates.message;

import java.util.Map;
import java.util.Objects;

public final class MessageFormatter {
    private static final String[] SUPPORTED_PLACEHOLDERS = {
        "player", "operation", "target", "required", "actual", "count"
    };

    private MessageFormatter() {}

    public static String format(String template, Map<String, String> values) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");

        String rendered = template;
        for (String placeholder : SUPPORTED_PLACEHOLDERS) {
            if (values.containsKey(placeholder)) {
                rendered = rendered.replace(
                        "{" + placeholder + "}",
                        Objects.toString(values.get(placeholder), ""));
            }
        }
        return rendered;
    }
}
