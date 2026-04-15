package io.agentframework.core.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process, thread-safe implementation of {@link AgentMemory}.
 *
 * <p>Suitable for:
 * <ul>
 *   <li>Unit and integration tests</li>
 *   <li>Single-node deployments where memory need not survive restarts</li>
 *   <li>Short-lived agent sessions (e.g. single HTTP request)</li>
 * </ul>
 *
 * <p>Not suitable for distributed deployments or sessions that must
 * survive process restarts — use {@code JdbcAgentMemory} or
 * {@code RedisAgentMemory} from the integration modules instead.
 */
public class InMemoryAgentMemory implements AgentMemory {

    private final Map<String, List<MemoryEntry>> store = new ConcurrentHashMap<>();

    @Override
    public void addUserMessage(String sessionId, String text) {
        append(sessionId, MemoryEntry.user(text));
    }

    @Override
    public void addAssistantMessage(String sessionId, String text) {
        append(sessionId, MemoryEntry.assistant(text));
    }

    @Override
    public void addToolResult(String sessionId, String toolName, String result) {
        append(sessionId, MemoryEntry.toolResult(toolName, result));
    }

    @Override
    public List<MemoryEntry> getHistory(String sessionId) {
        return Collections.unmodifiableList(
                store.getOrDefault(sessionId, List.of()));
    }

    @Override
    public List<MemoryEntry> getRecentHistory(String sessionId, int maxEntries) {
        List<MemoryEntry> all = store.getOrDefault(sessionId, List.of());
        if (all.size() <= maxEntries) {
            return Collections.unmodifiableList(all);
        }
        return Collections.unmodifiableList(
                all.subList(all.size() - maxEntries, all.size()));
    }

    @Override
    public void clear(String sessionId) {
        store.remove(sessionId);
    }

    @Override
    public int size(String sessionId) {
        return store.getOrDefault(sessionId, List.of()).size();
    }

    private void append(String sessionId, MemoryEntry entry) {
        store.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                .add(entry);
    }
}