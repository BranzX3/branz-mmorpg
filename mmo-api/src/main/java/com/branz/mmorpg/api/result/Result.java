package com.branz.mmorpg.api.result;

import java.util.Objects;
import java.util.function.Function;

/** Explicit success/failure result used at module and provider boundaries. */
public sealed interface Result<T, E extends ErrorCode> permits Result.Success, Result.Failure {

    boolean isSuccess();

    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    static <T, E extends ErrorCode> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E extends ErrorCode> Result<T, E> failure(E error, String detail) {
        return new Failure<>(error, detail);
    }

    record Success<T, E extends ErrorCode>(T value) implements Result<T, E> {
        public Success {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return Result.success(mapper.apply(value));
        }
    }

    record Failure<T, E extends ErrorCode>(E error, String detail) implements Result<T, E> {
        public Failure {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return Result.failure(error, detail);
        }
    }
}
