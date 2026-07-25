package com.branz.mmorpg.api.error;

import java.util.Objects;

/** Unchecked failure carrying a structured {@link ErrorCode}. */
public class MMOException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public MMOException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public MMOException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }

    @Override
    public String getMessage() {
        return code + ": " + super.getMessage();
    }
}
