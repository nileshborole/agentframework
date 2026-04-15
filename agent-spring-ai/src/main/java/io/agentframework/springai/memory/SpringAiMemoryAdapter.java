package io.agentframework.springai.memory;

import io.agentframework.core.memory.AgentMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapts a Spring AI {@link ChatMemory} to the framework's {@link AgentMemory} interface.
 *
 * <p>Tool results are stored as {@link UserMessage} with a {@code [Tool: name]} prefix
 * to distinguish them from regular user messages. This prefix is used for round-trip
 * parsing when reading history back.
 *
 * <p>Example usage:
 * <pre>
 * ChatMemory springMemory = MessageWindowChatMemory.builder().build();
 * AgentMemory memory = new SpringAiMemoryAdapter(springMemory);
 * memory.addUserMessage("session-1", "Hello");
 * memory.addToolResult("session-1", "search", "Found 3 results");
 * </pre>
 *
 * @see AgentMemory
 * @see ChatMemory
 */
public class SpringAiMemoryAdapter implements AgentMemory {

    private static final String TOOL_PREFIX = "[Tool: ";
    private static final String TOOL_SUFFIX = "]\n";

    private final ChatMemory chatMemory;

    /**
     * Creates an adapter wrapping the given Spring AI ChatMemory.
     *
     * @param chatMemory the Spring AI memory implementation to wrap
     */
    public SpringAiMemoryAdapter(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public void addUserMessage(String sessionId, String text) {
        chatMemory.add(sessionId, new UserMessage(text));
    }

    @Override
    public void addAssistantMessage(String sessionId, String text) {
        chatMemory.add(sessionId, new AssistantMessage(text));
    }

    @Override
    public void addToolResult(String sessionId, String toolName, String result) {
        // Store as UserMessage with [Tool: name] prefix for round-trip detection
        String content = TOOL_PREFIX + toolName + TOOL_SUFFIX + result;
        chatMemory.add(sessionId, new UserMessage(content));
    }

    @Override
    public List<MemoryEntry> getHistory(String sessionId) {
        List<Message> messages = chatMemory.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<MemoryEntry> entries = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            entries.add(toMemoryEntry(msg));
        }
        return Collections.unmodifiableList(entries);
    }

    @Override
    public List<MemoryEntry> getRecentHistory(String sessionId, int maxEntries) {
        List<MemoryEntry> all = getHistory(sessionId);
        if (all.size() <= maxEntries) {
            return all;
        }
        return Collections.unmodifiableList(all.subList(all.size() - maxEntries, all.size()));
    }

    @Override
    public void clear(String sessionId) {
        chatMemory.clear(sessionId);
    }

    @Override
    public int size(String sessionId) {
        List<Message> messages = chatMemory.get(sessionId);
        return messages != null ? messages.size() : 0;
    }

    /**
     * Converts a Spring AI {@link Message} back to a framework {@link MemoryEntry}.
     *
     * <p>Detects tool results by checking for the {@code [Tool: name]} prefix on
     * user messages.
     */
    private MemoryEntry toMemoryEntry(Message message) {
        MessageType type = message.getMessageType();
        String text = message.getText();

        if (type == MessageType.ASSISTANT) {
            return MemoryEntry.assistant(text);
        }

        if (type == MessageType.USER && text != null && text.startsWith(TOOL_PREFIX)) {
            // Parse tool name and result from prefixed format
            int suffixIdx = text.indexOf(TOOL_SUFFIX);
            if (suffixIdx > TOOL_PREFIX.length()) {
                String toolName = text.substring(TOOL_PREFIX.length(), suffixIdx);
                String result = text.substring(suffixIdx + TOOL_SUFFIX.length());
                return MemoryEntry.toolResult(toolName, result);
            }
        }

        // SYSTEM messages and regular USER messages
        return MemoryEntry.user(text != null ? text : "");
    }
}