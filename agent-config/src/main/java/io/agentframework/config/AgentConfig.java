package io.agentframework.config;

import java.util.List;
import java.util.Optional;

/**
 * Typed, live-reloadable configuration for the agent framework.
 *
 * <p>Generic replacement for your domain-coupled {@code AgentConfigService}.
 * The framework reads its own keys ({@code maxStepsPerTurn},
 * {@code minConfidence}, etc.) through this interface. Domains add their
 * own typed accessor methods in subinterfaces or delegating classes.
 *
 * <p>Implementations must be thread-safe — config may be read from
 * concurrent agent runs simultaneously.
 *
 * <p>Provided implementations:
 * <ul>
 *   <li>{@code InMemoryAgentConfig} — for tests; populated from a {@code Map}</li>
 *   <li>{@code PropertiesAgentConfig} — backed by {@code application.properties}</li>
 *   <li>{@code JdbcAgentConfig} — backed by a database with live reload (mirrors
 *       your existing {@code AgentConfigService} + {@code AgentConfigDao})</li>
 * </ul>
 */
public interface AgentConfig {

    // ── Raw accessors ─────────────────────────────────────────────────────

    /**
     * Retrieves a raw string config value by key.
     *
     * @param key          the config key
     * @param defaultValue fallback if the key is absent
     */
    String getString(String key, String defaultValue);

    /**
     * Retrieves a raw string config value, returning empty if absent.
     */
    Optional<String> getString(String key);

    // ── Typed accessors ───────────────────────────────────────────────────

    default int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    default boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
    }

    default List<String> getList(String key, List<String> defaultValue) {
        Optional<String> raw = getString(key);
        if (raw.isEmpty()) return defaultValue;
        return List.of(raw.get().split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ── Framework keys — used internally by the framework ─────────────────

    /** Maximum agent loop steps per user turn. Default: 15. */
    default int maxStepsPerTurn() {
        return getInt("agent.max_steps_per_turn", 15);
    }

    /** Maximum tokens consumed per agent run. Default: 100,000. */
    default int maxTokenBudget() {
        return getInt("agent.max_token_budget", 100_000);
    }

    /** Minimum confidence threshold below which fallback is triggered. Default: 0.5. */
    default double minConfidenceThreshold() {
        return getDouble("agent.min_confidence_threshold", 0.5);
    }

    /** Whether a named tool is enabled. Default: true. */
    default boolean isToolEnabled(String toolName) {
        return getBoolean("agent.tool.enabled." + toolName.replace("-", "_"), true);
    }

    /** Maximum concurrent LLM calls (rate limiting). Default: 10. */
    default int maxConcurrentLlmCalls() {
        return getInt("agent.max_concurrent_llm_calls", 10);
    }

    // ── Mutation (optional — not all implementations support this) ────────

    /**
     * Updates a config value at runtime.
     *
     * @param key      the config key to update
     * @param value    the new value
     * @param updatedBy identifier of who made the change (for audit)
     * @throws UnsupportedOperationException if this implementation is read-only
     */
    default void update(String key, String value, String updatedBy) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support live updates");
    }
}