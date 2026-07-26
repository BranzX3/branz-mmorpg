package com.branz.mmorpg.api.character;

public record ClassCompatibilityResult(boolean compatible, String reason) {
    public ClassCompatibilityResult { reason = reason == null ? "" : reason; }
    public static ClassCompatibilityResult allowed() { return new ClassCompatibilityResult(true, ""); }
    public static ClassCompatibilityResult denied(String reason) { return new ClassCompatibilityResult(false, reason); }
}
