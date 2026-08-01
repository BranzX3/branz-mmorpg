package com.branz.mmorpg.lifeskills.node;

import java.util.Objects;

public record ResourceNodeOperation(ResourceNodeOperationKind kind, String signature) {
    public ResourceNodeOperation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(signature, "signature");
        if (signature.isBlank()) {
            throw new IllegalArgumentException("node operation signature must not be blank");
        }
    }
}
