package io.agentframework.core.result;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Complete result of a finished agent run — success or failure, with
 * full telemetry for observability.
 *
 * <p>Returned by {@code AgentRunner.run()} to callers. Callers should
 * always check {@code outcome()} before accessing {@code output()}.
 */
public record AgentRunResult(
        String sessionId,
        AgentRunOutcome outcome,
        String output,
        String failureReason,
        int stepsExecuted,
        int totalInputTokens,
        int totalOutputTokens,
        Instant startedAt,
        Instant completedAt
) {

    /** True if the run completed successfully. */
    public boolean isSuccess() {
        return outcome == AgentRunOutcome.SUCCESS;
    }

    /** The output, if present. Empty on failure. */
    public Optional<String> outputIfPresent() {
        return Optional.ofNullable(output);
    }

    /** Total wall-clock duration of the run. */
    public Duration duration() {
        return Duration.between(startedAt, completedAt);
    }

    /** Estimated cost in USD at claude-opus-4 pricing ($15/1M input, $75/1M output). */
    public double estimatedCostUsd() {
        return (totalInputTokens / 1_000_000.0 * 15.0)
                + (totalOutputTokens / 1_000_000.0 * 75.0);
    }

    /** Total tokens used across all LLM calls in this run. */
    public int totalTokens() {
        return totalInputTokens + totalOutputTokens;
    }

    // ── Factory methods ───────────────────────────────────────────────────

    public static AgentRunResult success(String sessionId, String output,
                                         int steps, int inputTokens, int outputTokens,
                                         Instant start, Instant end) {
        return new AgentRunResult(sessionId, AgentRunOutcome.SUCCESS, output, null,
                steps, inputTokens, outputTokens, start, end);
    }

    public static AgentRunResult failure(String sessionId, AgentRunOutcome outcome,
                                         String reason, int steps,
                                         int inputTokens, int outputTokens,
                                         Instant start, Instant end) {
        return new AgentRunResult(sessionId, outcome, null, reason,
                steps, inputTokens, outputTokens, start, end);
    }
}