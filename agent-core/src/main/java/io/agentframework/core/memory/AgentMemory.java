package io.agentframework.core.memory;

import java.time.Instant;
import java.util.List;

/**
 * Conversation memory for an agent session.
 *
 * <p>Stores the ordered history of messages exchanged during a session.
 * The framework uses this to build LLM context on every turn without
 * tying itself to a specific memory provider (Spring AI ChatMemory,
 * Redis, JDBC, in-memory, etc.).
 *
 * <p>Implementations must be thread-safe when shared across turns
 * of the same session.
 */
public interface AgentMemory {

    /**
     * Appends a user message to this session's history.
     *
     * @param sessionId the session to append to
     * @param text      the user's message text
     */
    void addUserMessage(String sessionId, String text);

    /**
     * Appends an assistant (LLM) message to this session's history.
     *
     * @param sessionId the session to append to
     * @param text      the assistant's message text
     */
    void addAssistantMessage(String sessionId, String text);

    /**
     * Appends a tool result observation to this session's history.
     *
     * @param sessionId the session to append to
     * @param toolName  the name of the tool that produced the result
     * @param result    the tool result text
     */
    void addToolResult(String sessionId, String toolName, String result);

    /**
     * Retrieves the full message history for a session in chronological order
     * (oldest first).
     *
     * @param sessionId the session to retrieve
     * @return unmodifiable ordered list of memory entries; empty if no history
     */
    List<MemoryEntry> getHistory(String sessionId);

    /**
     * Retrieves the last {@code maxEntries} messages for a session.
     * Useful for context window management.
     *
     * @param sessionId  the session to retrieve
     * @param maxEntries maximum number of entries to return
     * @return unmodifiable ordered list, newest entries last
     */
    List<MemoryEntry> getRecentHistory(String sessionId, int maxEntries);

    /**
     * Clears all history for a session.
     *
     * @param sessionId the session to clear
     */
    void clear(String sessionId);

    /**
     * Returns the total number of entries in a session's history.
     *
     * @param sessionId the session to check
     */
    int size(String sessionId);

    /**
     * A single entry in the conversation history.
     *
     * @param role      the speaker role
     * @param text      the message content
     * @param toolName  the tool name for TOOL_RESULT entries; null otherwise
     * @param timestamp when this entry was recorded
     */
    record MemoryEntry(
            Role role,
            String text,
            String toolName,
            Instant timestamp
    ) {
        public enum Role { USER, ASSISTANT, TOOL_RESULT }

        public static MemoryEntry user(String text) {
            return new MemoryEntry(Role.USER, text, null, Instant.now());
        }

        public static MemoryEntry assistant(String text) {
            return new MemoryEntry(Role.ASSISTANT, text, null, Instant.now());
        }

        public static MemoryEntry toolResult(String toolName, String result) {
            return new MemoryEntry(Role.TOOL_RESULT, result, toolName, Instant.now());
        }

        public boolean isUser() { return role == Role.USER; }
        public boolean isAssistant() { return role == Role.ASSISTANT; }
        public boolean isToolResult() { return role == Role.TOOL_RESULT; }
    }
}