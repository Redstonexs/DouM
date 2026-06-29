package io.github.doum.deathgates.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record TargetKey(TargetKind kind, String namespace, String key) {
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9/._-]+");

    public TargetKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        requireMatches("namespace", namespace, NAMESPACE_PATTERN);
        requireMatches("key", key, KEY_PATTERN);
    }

    public static TargetKey parse(String rawKey) {
        Objects.requireNonNull(rawKey, "rawKey");
        if (!rawKey.equals(rawKey.trim())) {
            throw new IllegalArgumentException("Target key must not contain leading or trailing whitespace: " + rawKey);
        }

        String[] parts = rawKey.split(":", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Target key must use '<kind>:<namespace>:<key>' format: " + rawKey);
        }

        return new TargetKey(TargetKind.fromId(parts[0]), parts[1], parts[2]);
    }

    public String asConfigKey() {
        return kind.id() + ":" + namespace + ":" + key;
    }

    @Override
    public String toString() {
        return asConfigKey();
    }

    private static void requireMatches(String field, String value, Pattern pattern) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Target key " + field + " has invalid value: " + value);
        }
    }
}
