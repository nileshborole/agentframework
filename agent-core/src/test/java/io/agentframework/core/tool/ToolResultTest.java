package io.agentframework.core.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void continueWith_hasContinueSignal() {
        ToolResult result = ToolResult.continueWith("some output");
        assertEquals(ToolSignal.CONTINUE, result.signal());
        assertEquals("some output", result.content());
        assertFalse(result.hasUserPayload());
    }

    @Test
    void pauseForUser_hasPauseSignalAndPayload() {
        Object payload = new Object();
        ToolResult result = ToolResult.pauseForUser("internal summary", payload);
        assertEquals(ToolSignal.PAUSE_FOR_USER, result.signal());
        assertTrue(result.hasUserPayload());
        assertTrue(result.userPayload(Object.class).isPresent());
    }

    @Test
    void terminate_hasTerminateSignal() {
        ToolResult result = ToolResult.terminate("final answer", null);
        assertEquals(ToolSignal.TERMINATE, result.signal());
        assertFalse(result.hasUserPayload());
    }

    @Test
    void error_prependsErrorPrefix() {
        ToolResult result = ToolResult.error("ticker not found");
        assertTrue(result.content().startsWith("ERROR:"));
        assertEquals(ToolSignal.CONTINUE, result.signal());
    }

    @Test
    void userPayload_returnsEmptyForWrongType() {
        ToolResult result = ToolResult.pauseForUser("x", "string payload");
        assertTrue(result.userPayload(String.class).isPresent());
        assertTrue(result.userPayload(Integer.class).isEmpty());
    }

    @Test
    void userPayload_returnsEmptyWhenNull() {
        ToolResult result = ToolResult.continueWith("x");
        assertTrue(result.userPayload(String.class).isEmpty());
    }
}