package io.agentframework.core.tool;

import java.util.Optional;

/**
 * Output of a single tool execution.
 *
 * <p>Contains the content fed back to the LLM as an observation, a signal
 * controlling the loop's next action, and an optional user-facing payload
 * that the application maps to its own message types.
 *
 * <p>The framework never inspects {@code userPayload} — it belongs entirely
 * to the application layer. The application's output handler receives it
 * and decides how to present it (WebSocket broadcast, REST response, etc.).
 */
public record ToolResult(
        String content,
        ToolSignal signal,
        Object userPayload
) {

    /**
     * Silent result — loop continues, no user-facing output.
     *
     * @param content the observation text fed back to the LLM
     */
    public static ToolResult continueWith(String content) {
        return new ToolResult(content, ToolSignal.CONTINUE, null);
    }

    /**
     * User-facing result — loop pauses for human input.
     *
     * @param content     the observation text fed back to the LLM
     * @param userPayload the payload delivered to the application's output handler
     */
    public static ToolResult pauseForUser(String content, Object userPayload) {
        return new ToolResult(content, ToolSignal.PAUSE_FOR_USER, userPayload);
    }

    /**
     * Terminating result — loop stops, tool result is the final answer.
     *
     * @param content     the final answer text
     * @param userPayload optional payload for the application's output handler
     */
    public static ToolResult terminate(String content, Object userPayload) {
        return new ToolResult(content, ToolSignal.TERMINATE, userPayload);
    }

    /**
     * Error result — loop continues with error context so the LLM can recover.
     *
     * @param errorMessage descriptive error for the LLM to act on
     */
    public static ToolResult error(String errorMessage) {
        return new ToolResult("ERROR: " + errorMessage, ToolSignal.CONTINUE, null);
    }

    /** Whether this result carries a user-facing payload. */
    public boolean hasUserPayload() { return userPayload != null; }

    /** The user payload cast to the expected type. */
    public <T> Optional<T> userPayload(Class<T> type) {
        if (userPayload == null || !type.isInstance(userPayload)) return Optional.empty();
        return Optional.of(type.cast(userPayload));
    }
}