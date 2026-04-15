package io.agentframework.config.impl;

import io.agentframework.config.AgentConfig;
import io.agentframework.config.spi.AgentConfigStore;
import io.agentframework.config.spi.AgentConfigStore.ConfigEntry;
import io.agentframework.config.spi.AgentConfigStore.ConfigHistoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link AgentConfig} backed by a persistent {@link AgentConfigStore}.
 *
 * <p>Maintains an in-memory cache that is populated on construction via
 * {@link #reload()} and can be refreshed periodically via {@link #startAutoReload}.
 *
 * <p>All updates are validated against the {@link ConfigEntry} metadata
 * (data type, min/max bounds) before being persisted. Every change is
 * recorded as a {@link ConfigHistoryEntry} for full audit trail.
 *
 * <p>Example usage:
 * <pre>
 * AgentConfigStore store = new PostgresConfigStore(dataSource);
 * JdbcAgentConfig config = new JdbcAgentConfig(store);
 * config.startAutoReload(60); // refresh every 60s
 *
 * int maxSteps = config.maxStepsPerTurn();
 * config.update("agent.max_steps_per_turn", "20", "admin");
 * config.rollback("agent.max_steps_per_turn", historyId, "admin");
 * </pre>
 *
 * @see AgentConfigStore
 */
public class JdbcAgentConfig implements AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentConfig.class);

    private final AgentConfigStore store;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConfigEntry> entryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean initialLoadDone = new AtomicBoolean(false);

    private volatile ScheduledExecutorService scheduler;

    /**
     * Creates a new JdbcAgentConfig and performs the initial load.
     *
     * @param store the persistent config store
     * @throws RuntimeException if the initial load fails
     */
    public JdbcAgentConfig(AgentConfigStore store) {
        this.store = store;
        reload(); // initial load — throws on failure
    }

    // ── AgentConfig contract ─────────────────────────────────────────────

    @Override
    public String getString(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void update(String key, String newValue, String updatedBy) {
        ConfigEntry entry = entryCache.get(key);
        if (entry != null) {
            validate(entry, newValue);
        }

        String oldValue = cache.get(key);

        // Record history
        ConfigHistoryEntry history = ConfigHistoryEntry.of(
                key, oldValue, newValue, "Updated via API", updatedBy);
        store.insertHistory(history);

        // Persist
        store.update(key, newValue, updatedBy);

        // Update cache atomically
        cache.put(key, newValue);
        if (entry != null) {
            entryCache.put(key, new ConfigEntry(
                    entry.key(), newValue, entry.dataType(),
                    entry.label(), entry.minValue(), entry.maxValue()));
        }

        log.info("Config updated: key={}, oldValue={}, newValue={}, updatedBy={}",
                key, oldValue, newValue, updatedBy);
    }

    // ── Reload ───────────────────────────────────────────────────────────

    /**
     * Reloads all configuration from the persistent store into the cache.
     *
     * <p>On initial load failure, throws an exception. On subsequent reload
     * failures, logs the error and keeps the stale cache intact.
     *
     * @throws RuntimeException if the initial load fails
     */
    public void reload() {
        try {
            List<ConfigEntry> entries = store.loadAll();
            ConcurrentHashMap<String, String> newCache = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, ConfigEntry> newEntryCache = new ConcurrentHashMap<>();

            for (ConfigEntry entry : entries) {
                newCache.put(entry.key(), entry.value());
                newEntryCache.put(entry.key(), entry);
            }

            cache.clear();
            cache.putAll(newCache);
            entryCache.clear();
            entryCache.putAll(newEntryCache);

            initialLoadDone.set(true);
            log.debug("Config reloaded: {} entries", entries.size());

        } catch (Exception e) {
            if (!initialLoadDone.get()) {
                throw new RuntimeException("Initial config load failed", e);
            }
            // After initial load, keep stale cache on failure
            log.error("Config reload failed — keeping stale cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Starts a daemon thread that periodically reloads configuration.
     *
     * @param intervalSeconds reload interval in seconds (minimum 10)
     */
    public void startAutoReload(long intervalSeconds) {
        long interval = Math.max(intervalSeconds, 10);
        stopAutoReload();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-config-reload");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::reload, interval, interval, TimeUnit.SECONDS);
        log.info("Auto-reload started with interval={}s", interval);
    }

    /**
     * Stops the auto-reload daemon if running.
     */
    public void stopAutoReload() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("Auto-reload stopped");
        }
    }

    // ── Rollback ─────────────────────────────────────────────────────────

    /**
     * Rolls back a configuration key to its value at a specific history entry.
     *
     * @param key       the configuration key to rollback
     * @param historyId the history entry ID to restore from
     * @param rolledBy  who performed the rollback
     * @throws IllegalArgumentException if the history entry is not found
     */
    public void rollback(String key, long historyId, String rolledBy) {
        ConfigHistoryEntry history = store.findHistoryById(historyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "History entry not found: " + historyId));

        update(key, history.oldValue(), rolledBy);
        log.info("Config rolled back: key={}, restoredValue={}, historyId={}, rolledBy={}",
                key, history.oldValue(), historyId, rolledBy);
    }

    // ── Validation ───────────────────────────────────────────────────────

    /**
     * Validates a new value against the config entry metadata.
     *
     * <p>Protected so subclasses can add custom validation (e.g., enum checking).
     *
     * @param entry    the config entry with type and bounds metadata
     * @param newValue the proposed new value
     * @throws IllegalArgumentException if validation fails
     */
    protected void validate(ConfigEntry entry, String newValue) {
        if (newValue == null || newValue.isBlank()) {
            throw new IllegalArgumentException("Value must not be blank for key: " + entry.key());
        }

        String dataType = entry.dataType();
        if (dataType == null) return;

        switch (dataType.toUpperCase()) {
            case "INTEGER" -> {
                int val;
                try {
                    val = Integer.parseInt(newValue);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Value must be an integer for key: " + entry.key());
                }
                checkBounds(entry, val);
            }
            case "DECIMAL" -> {
                double val;
                try {
                    val = Double.parseDouble(newValue);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Value must be a decimal for key: " + entry.key());
                }
                checkBounds(entry, val);
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(newValue) && !"false".equalsIgnoreCase(newValue)) {
                    throw new IllegalArgumentException(
                            "Value must be 'true' or 'false' for key: " + entry.key());
                }
            }
            case "STRING" -> { /* no special validation */ }
            default -> log.warn("Unknown dataType '{}' for key '{}' — skipping validation",
                    dataType, entry.key());
        }
    }

    private void checkBounds(ConfigEntry entry, double val) {
        if (entry.minValue() != null && !entry.minValue().isBlank()) {
            double min = Double.parseDouble(entry.minValue());
            if (val < min) {
                throw new IllegalArgumentException(
                        "Value " + val + " is below minimum " + min + " for key: " + entry.key());
            }
        }
        if (entry.maxValue() != null && !entry.maxValue().isBlank()) {
            double max = Double.parseDouble(entry.maxValue());
            if (val > max) {
                throw new IllegalArgumentException(
                        "Value " + val + " exceeds maximum " + max + " for key: " + entry.key());
            }
        }
    }

    // ── Cache info ───────────────────────────────────────────────────────

    /**
     * Returns the number of entries currently in the cache.
     *
     * @return cache size
     */
    public int cacheSize() {
        return cache.size();
    }
}