package io.agentframework.config.spi;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persistent configuration storage.
 *
 * <p>Implementations back {@link io.agentframework.config.impl.JdbcAgentConfig}
 * with a database or other durable store. The framework ships no concrete
 * implementation — domain applications provide one for their persistence layer.
 *
 * <p>Example JDBC implementation:
 * <pre>
 * public class PostgresConfigStore implements AgentConfigStore {
 *     private final JdbcTemplate jdbc;
 *     // ... implement all methods with SQL queries
 * }
 * </pre>
 *
 * @see io.agentframework.config.impl.JdbcAgentConfig
 */
public interface AgentConfigStore {

    /**
     * Loads all configuration entries from the store.
     *
     * @return all config entries, never null
     */
    List<ConfigEntry> loadAll();

    /**
     * Finds a single configuration entry by key.
     *
     * @param key the configuration key
     * @return the entry if found
     */
    Optional<ConfigEntry> findByKey(String key);

    /**
     * Persists an updated value for the given key.
     *
     * @param key       the configuration key
     * @param newValue  the new value
     * @param updatedBy who performed the update
     */
    void update(String key, String newValue, String updatedBy);

    /**
     * Inserts a history record for audit purposes.
     *
     * @param entry the history entry to insert
     */
    void insertHistory(ConfigHistoryEntry entry);

    /**
     * Retrieves change history for a given configuration key.
     *
     * @param key the configuration key
     * @return history entries, newest first
     */
    List<ConfigHistoryEntry> findHistoryByKey(String key);

    /**
     * Retrieves a specific history entry by its ID.
     *
     * @param historyId the history entry ID
     * @return the history entry if found
     */
    Optional<ConfigHistoryEntry> findHistoryById(long historyId);

    // ── Nested records ───────────────────────────────────────────────────

    /**
     * A single configuration entry with metadata for validation.
     *
     * @param key       the configuration key
     * @param value     the current value
     * @param dataType  the data type (DECIMAL, INTEGER, BOOLEAN, STRING)
     * @param label     human-readable label
     * @param minValue  minimum allowed value (nullable)
     * @param maxValue  maximum allowed value (nullable)
     */
    record ConfigEntry(String key, String value, String dataType,
                       String label, String minValue, String maxValue) {
    }

    /**
     * An audit record of a configuration change.
     *
     * @param id        unique history entry ID
     * @param configKey the configuration key that changed
     * @param oldValue  the previous value
     * @param newValue  the new value
     * @param note      optional note about the change
     * @param changedBy who made the change
     * @param changedAt epoch millis when the change occurred
     */
    record ConfigHistoryEntry(long id, String configKey, String oldValue,
                              String newValue, String note, String changedBy,
                              long changedAt) {

        /**
         * Creates a new history entry with auto-generated timestamp.
         *
         * @param key      the config key
         * @param oldValue previous value
         * @param newValue new value
         * @param note     change note
         * @param changedBy who changed it
         * @return a new history entry with id=0 and current timestamp
         */
        public static ConfigHistoryEntry of(String key, String oldValue, String newValue,
                                            String note, String changedBy) {
            return new ConfigHistoryEntry(0, key, oldValue, newValue, note,
                    changedBy, System.currentTimeMillis());
        }
    }
}