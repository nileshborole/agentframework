package io.agentframework.core;

/**
 * Marker interface for agent execution phases.
 *
 * <p>Domains define their own phase enums by implementing this interface.
 * The framework routes the agent loop based on phases without knowing
 * their concrete values.
 *
 * <p>Example domain implementation:
 * <pre>
 * public enum TripPhase implements AgentPhase {
 *     DISCOVERY, PLAN, ITINERARY, BOOK;
 *
 *     {@literal @}Override public String id() { return name(); }
 *     {@literal @}Override public boolean isTerminal() { return this == BOOK; }
 * }
 * </pre>
 */
public interface AgentPhase {

    /**
     * Stable string identifier for this phase. Used in logging, tracing,
     * config keys, and serialisation. Must be unique within a domain.
     */
    String id();

    /**
     * Whether this phase represents a terminal state — no further
     * agent loop iterations should occur once this phase is reached.
     * Default: false.
     */
    default boolean isTerminal() {
        return false;
    }
}