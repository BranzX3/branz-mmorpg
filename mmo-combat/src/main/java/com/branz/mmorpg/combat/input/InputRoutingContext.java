package com.branz.mmorpg.combat.input;

import java.util.Objects;
import java.util.Set;

public record InputRoutingContext(Set<SemanticInput> legalNow, boolean bufferWindowOpen) {
    public InputRoutingContext {
        legalNow = Set.copyOf(Objects.requireNonNull(legalNow, "legalNow"));
    }

    public static InputRoutingContext legal(Set<SemanticInput> legalNow) {
        return new InputRoutingContext(legalNow, false);
    }
}
