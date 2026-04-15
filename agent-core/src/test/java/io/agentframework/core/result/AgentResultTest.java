package io.agentframework.core.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentResultTest {

    @Test
    void success_isSuccessAndHasValue() {
        AgentResult<String> result = AgentResult.success("hello");
        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals("hello", result.value().get());
        assertTrue(result.failureReason().isEmpty());
        assertTrue(result.cause().isEmpty());
    }

    @Test
    void failure_withReason_isFailureWithNoValue() {
        AgentResult<String> result = AgentResult.failure("network error");
        assertFalse(result.isSuccess());
        assertTrue(result.isFailure());
        assertTrue(result.value().isEmpty());
        assertEquals("network error", result.failureReason().get());
    }

    @Test
    void failure_withCause_capturesBoth() {
        RuntimeException ex = new RuntimeException("timeout");
        AgentResult<String> result = AgentResult.failure("call failed", ex);
        assertEquals("call failed", result.failureReason().get());
        assertSame(ex, result.cause().get());
    }

    @Test
    void failure_fromThrowable_usesMessage() {
        AgentResult<String> result = AgentResult.failure(new IllegalStateException("bad state"));
        assertEquals("bad state", result.failureReason().get());
    }

    @Test
    void map_transformsSuccessValue() {
        AgentResult<String> result = AgentResult.success("42");
        AgentResult<Integer> mapped = result.map(Integer::parseInt);
        assertTrue(mapped.isSuccess());
        assertEquals(42, mapped.value().get());
    }

    @Test
    void map_passesFailureThrough() {
        AgentResult<String> failure = AgentResult.failure("original error");
        AgentResult<Integer> mapped = failure.map(Integer::parseInt);
        assertTrue(mapped.isFailure());
        assertEquals("original error", mapped.failureReason().get());
    }

    @Test
    void orElse_returnsValueOnSuccess() {
        AgentResult<String> result = AgentResult.success("actual");
        assertEquals("actual", result.orElse("fallback"));
    }

    @Test
    void orElse_returnsFallbackOnFailure() {
        AgentResult<String> result = AgentResult.failure("error");
        assertEquals("fallback", result.orElse("fallback"));
    }

    @Test
    void success_withNullValue_valueIsEmpty() {
        AgentResult<String> result = AgentResult.success(null);
        assertTrue(result.value().isEmpty());
    }
}