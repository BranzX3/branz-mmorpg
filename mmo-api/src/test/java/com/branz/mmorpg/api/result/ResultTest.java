package com.branz.mmorpg.api.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultTest {
    private enum TestError implements ErrorCode {
        FAILED;

        @Override
        public String code() {
            return name();
        }
    }

    @Test
    void mapsSuccessWithoutLosingErrorType() {
        Result<Integer, TestError> result = Result.success(21);

        Result<String, TestError> mapped = result.map(value -> Integer.toString(value * 2));

        assertTrue(mapped.isSuccess());
        assertEquals("42", ((Result.Success<String, TestError>) mapped).value());
    }

    @Test
    void mappingFailurePreservesCodeAndDetail() {
        Result<Integer, TestError> result = Result.failure(TestError.FAILED, "expected");

        Result<String, TestError> mapped = result.map(Object::toString);

        assertFalse(mapped.isSuccess());
        Result.Failure<String, TestError> failure = (Result.Failure<String, TestError>) mapped;
        assertEquals(TestError.FAILED, failure.error());
        assertEquals("expected", failure.detail());
    }
}
