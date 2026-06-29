package io.github.doum.deathgates.model;

import java.util.Arrays;

public enum TargetKind {
    MATERIAL("material"),
    RESULT("result"),
    RECIPE("recipe");

    private final String id;

    TargetKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static TargetKind fromId(String id) {
        return Arrays.stream(values())
                .filter(kind -> kind.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown target kind: " + id));
    }
}
