package io.agentframework.advisor;

import io.agentframework.core.AgentState;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable per-call context passed through the entire advisor chain.
 *
 * <p>Advisors use this to share data with each other without coupling
 * to concrete types. It carries:
 * <ul>
 *   <li>The current {@link AgentState} for state-reading advisors</li>
 *   <li>Typed parameters set by the runner before the call</li>
 *   <li>A mutable attribute map for advisors to communicate</li>
 * </ul>
 *
 * <p>Replaces the Spring AI {@code .advisors(a -> a.param(KEY, value))}
 * pattern with a typed, framework-agnostic equivalent.
 *
 * <p>Example — runner sets parameters before calling LLM:
 * <pre>
 * AdvisorContext ctx = AdvisorContext.builder()
 *     .state(agentState)
 *     .param(ConfidenceGuardAdvisor.THRESHOLD_KEY, 0.5)
 *     .param(ObservabilityAdvisor.PROMPT_NAME_KEY, "plan_step_0")
 *     .build();
 * </pre>
 *
 * <p>Example — advisor reads and writes attributes:
 * <pre>
 * // In ConfidenceGuardAdvisor:
 * boolean lowConfidence = detectLowConfidence(response);
 * context.setAttribute(LOW_CONFIDENCE_KEY, lowConfidence);
 *
 * // In the runner, after the chain:
 * boolean low = context.attribute(LOW_CONFIDENCE_KEY, Boolean.class)
 *                      .orElse(false);
 * </pre>
 */
public final class AdvisorContext {

    private final AgentState<?> state;
    private final Map<String, Object> params;
    private final Map<String, Object> attributes;
    private final String traceId;
    private final int stepNumber;

    private AdvisorContext(Builder builder) {
        this.state      = builder.state;
        this.params     = Map.copyOf(builder.params);
        this.attributes = new ConcurrentHashMap<>(builder.initialAttributes);
        this.traceId    = builder.traceId;
        this.stepNumber = builder.stepNumber;
    }

    // ── State ─────────────────────────────────────────────────────────────

    /** The current agent session state. May be null for non-session calls. */
    public AgentState<?> state() { return state; }

    /** Session ID, or "unknown" if state is null. */
    public String sessionId() {
        return state != null ? state.sessionId() : "unknown";
    }

    /** Current phase ID, or "unknown" if state is null. */
    public String phaseId() {
        return state != null ? state.currentPhase().id() : "unknown";
    }

    // ── Params (read-only, set by runner) ─────────────────────────────────

    /**
     * Retrieves a typed parameter set by the runner.
     *
     * @param key  the parameter key
     * @param type the expected type
     */
    public <T> Optional<T> param(String key, Class<T> type) {
        Object v = params.get(key);
        if (v == null || !type.isInstance(v)) return Optional.empty();
        return Optional.of(type.cast(v));
    }

    /** Retrieves a String parameter. */
    public String paramString(String key, String defaultValue) {
        return param(key, String.class).orElse(defaultValue);
    }

    /** Retrieves a double parameter. */
    public double paramDouble(String key, double defaultValue) {
        return param(key, Double.class).orElse(
                param(key, Number.class).map(Number::doubleValue).orElse(defaultValue));
    }

    // ── Attributes (mutable, advisor-to-advisor communication) ────────────

    /**
     * Sets a mutable attribute for downstream advisors to read.
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Retrieves a typed attribute set by an upstream advisor.
     */
    public <T> Optional<T> attribute(String key, Class<T> type) {
        Object v = attributes.get(key);
        if (v == null || !type.isInstance(v)) return Optional.empty();
        return Optional.of(type.cast(v));
    }

    // ── Tracing ───────────────────────────────────────────────────────────

    /** Unique identifier for the current agent run's trace. */
    public String traceId() { return traceId; }

    /** The step number within the current agent run (0-indexed). */
    public int stepNumber() { return stepNumber; }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private AgentState<?> state;
        private final Map<String, Object> params = new ConcurrentHashMap<>();
        private final Map<String, Object> initialAttributes = new ConcurrentHashMap<>();
        private String traceId = "unknown";
        private int stepNumber = 0;

        public Builder state(AgentState<?> state) {
            this.state = state; return this;
        }
        public Builder param(String key, Object value) {
            this.params.put(key, value); return this;
        }
        public Builder params(Map<String, Object> params) {
            this.params.putAll(params); return this;
        }
        public Builder traceId(String traceId) {
            this.traceId = traceId; return this;
        }
        public Builder stepNumber(int stepNumber) {
            this.stepNumber = stepNumber; return this;
        }
        public AdvisorContext build() { return new AdvisorContext(this); }
    }
}