package io.agentframework.core;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Generic container for the running state of an agent session.
 *
 * <p>{@code S} is the domain-specific state type (e.g. {@code TripAttributes},
 * {@code SupportTicket}). The framework carries it opaquely — it never
 * inspects or mutates the domain data directly.
 *
 * <p>Implementations are expected to be mutable during a single agent run
 * and persisted externally between runs (DB, Redis, etc.).
 *
 * @param <S> the domain state type
 */
public interface AgentState<S> {

    /**
     * Unique identifier for this agent session. Stable across multiple
     * turns (e.g. a chat session ID or ticket ID).
     */
    String sessionId();

    /**
     * The current execution phase. Never null — starts at the domain's
     * initial phase and transitions as the agent progresses.
     */
    AgentPhase currentPhase();

    /**
     * Transitions the session to a new phase.
     *
     * @param phase the target phase — must not be null
     * @throws IllegalArgumentException if the transition is invalid
     *         for this domain's phase model
     */
    void transitionTo(AgentPhase phase);

    /**
     * The domain-specific data for this session. May be null if the
     * session has no accumulated data yet (e.g. very first turn).
     */
    S domainData();

    /**
     * Replaces the domain data. Called after attribute extraction,
     * state merging, or any domain-level mutation.
     */
    void updateDomainData(S data);

    /**
     * When this session was created. Used for TTL and audit.
     */
    Instant createdAt();

    /**
     * When this session was last updated. Updated by the framework
     * after every agent run.
     */
    Instant lastUpdatedAt();

    /**
     * Arbitrary key-value metadata for cross-cutting concerns.
     * Not interpreted by the framework — available to tools, advisors,
     * and observers as a side-channel.
     *
     * <p>Example uses: user locale, feature flags, A/B test cohort.
     */
    Map<String, Object> metadata();

    /**
     * Retrieves a typed metadata value by key.
     *
     * @param key  the metadata key
     * @param type the expected type
     * @return an Optional containing the value, or empty if absent or wrong type
     */
    default <T> Optional<T> metadataValue(String key, Class<T> type) {
        Object value = metadata().get(key);
        if (value == null) return Optional.empty();
        if (!type.isInstance(value)) return Optional.empty();
        return Optional.of(type.cast(value));
    }
}