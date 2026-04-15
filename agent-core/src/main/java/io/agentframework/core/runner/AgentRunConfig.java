package io.agentframework.core.runner;

import java.time.Duration;

/**
 * Immutable configuration controlling the bounds and behaviour of a
 * single agent run.
 *
 * <p>All safety limits are defined here — max steps, token budget,
 * wall-clock timeout, and confidence thresholds. These values are
 * read from {@code AgentConfig} by the runner at execution time,
 * but can be overridden per-run for testing or special cases.
 */
public record AgentRunConfig(
        int maxSteps,
        int maxTokenBudget,
        Duration wallClockTimeout,
        double minConfidenceThreshold,
        boolean stopOnToolPause
) {

    /**
     * Conservative defaults suitable for most interactive chat agents.
     * Derived from the values proven in the Yodeki trip planning system:
     * 15 steps, 100k tokens, 5-minute timeout, 0.5 confidence floor.
     */
    public static AgentRunConfig defaults() {
        return new AgentRunConfig(15, 100_000, Duration.ofMinutes(5), 0.5, true);
    }

    /**
     * Tighter limits for low-latency or cost-sensitive scenarios.
     */
    public static AgentRunConfig conservative() {
        return new AgentRunConfig(8, 50_000, Duration.ofMinutes(2), 0.6, true);
    }

    /**
     * Relaxed limits for long-running research or batch tasks.
     */
    public static AgentRunConfig extended() {
        return new AgentRunConfig(30, 500_000, Duration.ofMinutes(20), 0.3, false);
    }

    /**
     * Returns a copy with the given max steps.
     */
    public AgentRunConfig withMaxSteps(int maxSteps) {
        return new AgentRunConfig(maxSteps, maxTokenBudget,
                wallClockTimeout, minConfidenceThreshold, stopOnToolPause);
    }

    /**
     * Returns a copy with the given confidence threshold.
     */
    public AgentRunConfig withConfidenceThreshold(double threshold) {
        return new AgentRunConfig(maxSteps, maxTokenBudget,
                wallClockTimeout, threshold, stopOnToolPause);
    }
}