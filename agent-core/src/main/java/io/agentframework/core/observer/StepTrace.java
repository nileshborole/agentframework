package io.agentframework.core.observer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of a single step within an agent run.
 *
 * <p>Every LLM call and every tool execution within a run produces a
 * {@code StepTrace}. The {@link AgentObserver} receives these as they
 * occur, enabling real-time tracing alongside post-run analysis.
 */
public record StepTrace(
        String traceId,
        String sessionId,
        int stepNumber,
        StepKind kind,
        String name,
        Map<String, Object> input,
        String output,
        boolean success,
        String errorMessage,
        int inputTokens,
        int outputTokens,
        Instant startedAt,
        Duration duration
) {

    /**
     * The kind of step that produced this trace.
     */
    public enum StepKind {
        /** An LLM call (model inference). */
        LLM_CALL,
        /** A tool execution. */
        TOOL_CALL,
        /** A phase transition. */
        PHASE_TRANSITION,
        /** An agent handoff to a sub-agent. */
        AGENT_HANDOFF
    }

    // ── Factory methods ───────────────────────────────────────────────────

    public static StepTrace llmCall(String traceId, String sessionId, int step,
                                    String output, boolean success,
                                    int inputTokens, int outputTokens,
                                    Instant start, Duration duration) {
        return new StepTrace(traceId, sessionId, step, StepKind.LLM_CALL,
                "llm_call", Map.of(), output, success, null,
                inputTokens, outputTokens, start, duration);
    }

    public static StepTrace toolCall(String traceId, String sessionId, int step,
                                     String toolName, Map<String, Object> input,
                                     String result, boolean success, String errorMsg,
                                     Instant start, Duration duration) {
        return new StepTrace(traceId, sessionId, step, StepKind.TOOL_CALL,
                toolName, Map.copyOf(input), result, success, errorMsg,
                0, 0, start, duration);
    }

    public static StepTrace phaseTransition(String traceId, String sessionId, int step,
                                            String fromPhase, String toPhase,
                                            Instant start, Duration duration) {
        return new StepTrace(traceId, sessionId, step, StepKind.PHASE_TRANSITION,
                "phase_transition",
                Map.of("from", fromPhase, "to", toPhase),
                toPhase, true, null, 0, 0, start, duration);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Optional<String> errorMessageIfPresent() {
        return Optional.ofNullable(errorMessage);
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public boolean isLlmCall() { return kind == StepKind.LLM_CALL; }
    public boolean isToolCall() { return kind == StepKind.TOOL_CALL; }
}