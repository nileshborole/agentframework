package io.agentframework.core.state;

import io.agentframework.core.AgentPhase;
import io.agentframework.core.AgentState;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default thread-safe implementation of {@link AgentState}.
 *
 * <p>All state mutations are synchronized to ensure consistency when
 * the agent loop and observers access state concurrently.
 *
 * <p>Use factory methods instead of constructors:
 * <pre>
 * // New session
 * var state = DefaultAgentState.newSession(MyPhase.INITIAL, new MyData());
 *
 * // Restore from persistence
 * var state = DefaultAgentState.restore(sessionId, MyPhase.PLANNING, data, createdAt, metadata);
 * </pre>
 *
 * @param <S> the domain data type
 */
public class DefaultAgentState<S> implements AgentState<S> {

    private final String sessionId;
    private volatile AgentPhase currentPhase;
    private volatile S domainData;
    private final Instant createdAt;
    private volatile Instant lastUpdatedAt;
    private final ConcurrentHashMap<String, Object> metadata;

    private DefaultAgentState(String sessionId, AgentPhase currentPhase, S domainData,
                              Instant createdAt, Map<String, Object> metadata) {
        this.sessionId = sessionId;
        this.currentPhase = currentPhase;
        this.domainData = domainData;
        this.createdAt = createdAt;
        this.lastUpdatedAt = createdAt;
        this.metadata = new ConcurrentHashMap<>(metadata != null ? metadata : Map.of());
    }

    // ── Factory methods ──────────────────────────────────────────────────

    /**
     * Creates a new session with a generated UUID and the current timestamp.
     *
     * @param initialPhase the starting phase
     * @param domainData   the initial domain data
     * @param <S>          the domain data type
     * @return a new agent state
     */
    public static <S> DefaultAgentState<S> newSession(AgentPhase initialPhase, S domainData) {
        return newSession(initialPhase, domainData, Map.of());
    }

    /**
     * Creates a new session with a generated UUID, current timestamp, and initial metadata.
     *
     * @param initialPhase the starting phase
     * @param domainData   the initial domain data
     * @param metadata     initial metadata entries
     * @param <S>          the domain data type
     * @return a new agent state
     */
    public static <S> DefaultAgentState<S> newSession(AgentPhase initialPhase, S domainData,
                                                       Map<String, Object> metadata) {
        return new DefaultAgentState<>(
                UUID.randomUUID().toString(),
                initialPhase,
                domainData,
                Instant.now(),
                metadata
        );
    }

    /**
     * Restores a previously persisted session.
     *
     * @param sessionId    the existing session ID
     * @param currentPhase the phase to restore to
     * @param domainData   the domain data to restore
     * @param createdAt    the original creation timestamp
     * @param metadata     the metadata to restore
     * @param <S>          the domain data type
     * @return a restored agent state
     */
    public static <S> DefaultAgentState<S> restore(String sessionId, AgentPhase currentPhase,
                                                    S domainData, Instant createdAt,
                                                    Map<String, Object> metadata) {
        return new DefaultAgentState<>(sessionId, currentPhase, domainData, createdAt, metadata);
    }

    /**
     * Restores a minimal session (no domain data, no metadata).
     *
     * @param sessionId    the existing session ID
     * @param currentPhase the phase to restore to
     * @param <S>          the domain data type
     * @return a restored agent state with null domain data
     */
    public static <S> DefaultAgentState<S> restore(String sessionId, AgentPhase currentPhase) {
        return new DefaultAgentState<>(sessionId, currentPhase, null, Instant.now(), Map.of());
    }

    // ── AgentState contract ──────────────────────────────────────────────

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public AgentPhase currentPhase() {
        return currentPhase;
    }

    @Override
    public synchronized void transitionTo(AgentPhase phase) {
        if (phase == null) {
            throw new IllegalArgumentException("Target phase must not be null");
        }
        if (currentPhase.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot transition from terminal phase '" + currentPhase.id() + "'");
        }
        this.currentPhase = phase;
        this.lastUpdatedAt = Instant.now();
    }

    @Override
    public S domainData() {
        return domainData;
    }

    @Override
    public synchronized void updateDomainData(S data) {
        this.domainData = data;
        this.lastUpdatedAt = Instant.now();
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    @Override
    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public <T> Optional<T> metadataValue(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    // ── Mutable metadata operations ──────────────────────────────────────

    /**
     * Adds or replaces a metadata entry.
     *
     * @param key   the metadata key
     * @param value the metadata value
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Removes a metadata entry.
     *
     * @param key the metadata key to remove
     */
    public void removeMetadata(String key) {
        metadata.remove(key);
        this.lastUpdatedAt = Instant.now();
    }
}