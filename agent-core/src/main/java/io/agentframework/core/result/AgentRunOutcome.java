package io.agentframework.core.result;

/**
 * Enumerates the possible terminal outcomes of a complete agent run.
 * Captured in traces, logs, and metrics for observability.
 */
public enum AgentRunOutcome {

    /** The agent completed its task and produced a final answer. */
    SUCCESS,

    /** The agent reached the maximum step limit without a final answer. */
    STEP_LIMIT_REACHED,

    /** The agent run exceeded the configured wall-clock timeout. */
    TIMEOUT,

    /** The LLM response did not meet the minimum confidence threshold. */
    LOW_CONFIDENCE,

    /** A tool returned an unrecoverable error and the agent could not continue. */
    TOOL_FAILURE,

    /** An unexpected exception terminated the run. */
    ERROR,

    /** The agent output failed post-run validation checks. */
    VALIDATION_FAILED
}