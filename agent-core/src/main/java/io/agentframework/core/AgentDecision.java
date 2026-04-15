package io.agentframework.core;

/**
 * Structured output produced by an LLM call within the agent loop.
 *
 * <p>Every domain's LLM response is mapped to an {@code AgentDecision}
 * before the framework routes on it. This ensures the generic loop
 * never parses raw LLM text directly.
 *
 * <p>Domains implement this interface in their decision models:
 * <pre>
 * public record TripPlannerDecision(
 *     StepType stepType,
 *     String reply,
 *     double confidence,
 *     String tripPersona      // domain-specific extra field
 * ) implements AgentDecision {}
 * </pre>
 */
public interface AgentDecision {

    /**
     * What the agent loop should do next. Never null.
     * The framework switches on this to route execution.
     */
    StepType stepType();

    /**
     * The text reply to return to the user, if any.
     * May be null for silent tool calls.
     */
    String reply();

    /**
     * LLM confidence in this decision, in range [0.0, 1.0].
     * Used by {@code ConfidenceGuardAdvisor} to trigger fallbacks.
     * Return 1.0 if the domain does not use confidence scoring.
     */
    double confidence();

    /**
     * Whether this decision has a non-blank reply.
     */
    default boolean hasReply() {
        return reply() != null && !reply().isBlank();
    }

    /**
     * Whether confidence meets a given threshold.
     *
     * @param threshold minimum acceptable confidence [0.0, 1.0]
     */
    default boolean isConfident(double threshold) {
        return confidence() >= threshold;
    }
}