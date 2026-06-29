package io.github.doum.deathgates.model;

import java.util.Arrays;

public enum OperationType {
    BLOCK_BREAK("block-break"),
    BLOCK_PLACE("block-place"),
    CRAFT_ITEM("craft-item");

    private final String id;

    OperationType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static OperationType fromId(String id) {
        return Arrays.stream(values())
                .filter(type -> type.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation id: " + id));
    }
}
