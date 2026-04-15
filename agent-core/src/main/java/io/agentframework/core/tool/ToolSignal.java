package io.agentframework.core.tool;

/**
 * Signal returned by a tool to control what the agent loop does next.
 *
 * <p>Replaces the boolean {@code hasMessage()} pattern with an explicit,
 * extensible signal type. The framework acts on the signal; the tool
 * does not need to know about the broadcast mechanism.
 */
public enum ToolSignal {

    /**
     * Tool executed silently. The agent loop continues to the next step.
     * The tool result is added to the LLM context as an observation.
     */
    CONTINUE,

    /**
     * Tool produced a user-facing response. The agent loop pauses
     * and waits for the next human input before continuing.
     * Used for human-in-the-loop interactions.
     */
    PAUSE_FOR_USER,

    /**
     * Tool result is the final answer. The agent loop terminates
     * immediately with this result as the output.
     */
    TERMINATE
}