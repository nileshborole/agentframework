package io.agentframework.core.tool;

import io.agentframework.core.AgentPhase;
import io.agentframework.core.AgentState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic input container passed to every {@link AgentTool} on execution.
 *
 * <p>Tools receive the full {@link AgentState} through a typed accessor
 * rather than a concrete domain class — keeping tools decoupled from
 * specific domain state implementations.
 *
 * @param <S> the domain state type carried in the session
 */
public final class ToolContext<S> {

    private final AgentState<S> state;
    private final Map<String, Object> input;
    private final List<String> missingFields;

    public ToolContext(AgentState<S> state,
                       Map<String, Object> input,
                       List<String> missingFields) {
        this.state = state;
        this.input  = input != null ? Map.copyOf(input) : Map.of();
        this.missingFields = missingFields != null
                ? Collections.unmodifiableList(missingFields)
                : List.of();
    }

    // ── Session accessors ─────────────────────────────────────────────────

    /** The unique session identifier. */
    public String sessionId() { return state.sessionId(); }

    /** The current execution phase. */
    public AgentPhase currentPhase() { return state.currentPhase(); }

    /** The domain-specific data for this session. May be null. */
    public S domainData() { return state.domainData(); }

    /** The full agent state — for tools that need deeper access. */
    public AgentState<S> state() { return state; }

    // ── Input accessors ───────────────────────────────────────────────────

    /** The parameters the LLM passed when calling this tool. */
    public Map<String, Object> input() { return input; }

    /**
     * Retrieves a required input parameter as a String.
     *
     * @throws IllegalArgumentException if the key is absent or not a String
     */
    public String requireString(String key) {
        Object v = input.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required input: " + key);
        return v.toString();
    }

    /**
     * Retrieves an optional input parameter as a String.
     */
    public Optional<String> optionalString(String key) {
        Object v = input.get(key);
        return Optional.ofNullable(v).map(Object::toString);
    }

    /**
     * Retrieves an optional integer input parameter.
     */
    public Optional<Integer> optionalInt(String key) {
        Object v = input.get(key);
        if (v == null) return Optional.empty();
        if (v instanceof Number n) return Optional.of(n.intValue());
        try { return Optional.of(Integer.parseInt(v.toString())); }
        catch (NumberFormatException e) { return Optional.empty(); }
    }

    // ── Completeness accessors ────────────────────────────────────────────

    /** Fields the agent has determined are still missing from the session. */
    public List<String> missingFields() { return missingFields; }

    /** True if there are any missing fields for the current phase. */
    public boolean hasMissingFields() { return !missingFields.isEmpty(); }

    /** True if a specific field is in the missing list. */
    public boolean isMissing(String fieldName) { return missingFields.contains(fieldName); }
}