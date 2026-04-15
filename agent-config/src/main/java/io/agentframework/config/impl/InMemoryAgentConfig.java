package io.agentframework.config.impl;

import io.agentframework.config.AgentConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AgentConfig} backed by a {@code Map}.
 *
 * <p>Primary uses:
 * <ul>
 *   <li>Unit and integration tests — populate with specific values per test</li>
 *   <li>Simple applications that configure via code rather than a database</li>
 * </ul>
 *
 * <p>Supports live updates via {@link #update} — useful for testing
 * config changes without a database.
 *
 * <p>Example test usage:
 * <pre>
 * AgentConfig config = InMemoryAgentConfig.of(Map.of(
 *     "agent.max_steps_per_turn", "5",
 *     "agent.min_confidence_threshold", "0.7",
 *     "agent.tool.enabled.search_web", "false"
 * ));
 * </pre>
 */
public class InMemoryAgentConfig implements AgentConfig {

    private final Map<String, String> store;

    public InMemoryAgentConfig() {
        this.store = new ConcurrentHashMap<>();
    }

    public InMemoryAgentConfig(Map<String, String> initialValues) {
        this.store = new ConcurrentHashMap<>(initialValues);
    }

    // ── Factory methods ───────────────────────────────────────────────────

    /** Creates a config pre-populated with the given key-value pairs. */
    public static InMemoryAgentConfig of(Map<String, String> values) {
        return new InMemoryAgentConfig(values);
    }

    /** Creates an empty config — all framework defaults apply. */
    public static InMemoryAgentConfig defaults() {
        return new InMemoryAgentConfig();
    }

    /** Creates a config from pairs: key, value, key, value, ... */
    public static InMemoryAgentConfig of(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be pairs");
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return new InMemoryAgentConfig(map);
    }

    // ── AgentConfig ───────────────────────────────────────────────────────

    @Override
    public String getString(String key, String defaultValue) {
        return store.getOrDefault(key, defaultValue);
    }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void update(String key, String value, String updatedBy) {
        store.put(key, value);
    }

    // ── Convenience ───────────────────────────────────────────────────────

    /** Sets a value directly (no updatedBy required). For test setup. */
    public InMemoryAgentConfig set(String key, String value) {
        store.put(key, value);
        return this;
    }

    /** Returns the number of entries in the config store. */
    public int size() { return store.size(); }
}