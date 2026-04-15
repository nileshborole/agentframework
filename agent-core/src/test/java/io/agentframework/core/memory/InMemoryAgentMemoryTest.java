package io.agentframework.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.agentframework.core.memory.AgentMemory.MemoryEntry.Role.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryAgentMemoryTest {

    private InMemoryAgentMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryAgentMemory();
    }

    @Test
    void addUserMessage_appendsToHistory() {
        memory.addUserMessage("s1", "Hello");
        List<AgentMemory.MemoryEntry> history = memory.getHistory("s1");
        assertEquals(1, history.size());
        assertEquals(USER, history.get(0).role());
        assertEquals("Hello", history.get(0).text());
    }

    @Test
    void addAssistantMessage_appendsToHistory() {
        memory.addAssistantMessage("s1", "Hi there");
        assertEquals(ASSISTANT, memory.getHistory("s1").get(0).role());
    }

    @Test
    void addToolResult_appendsWithToolName() {
        memory.addToolResult("s1", "search_web", "Found 10 results");
        AgentMemory.MemoryEntry entry = memory.getHistory("s1").get(0);
        assertEquals(TOOL_RESULT, entry.role());
        assertEquals("search_web", entry.toolName());
        assertEquals("Found 10 results", entry.text());
    }

    @Test
    void getHistory_returnsChronologicalOrder() {
        memory.addUserMessage("s1", "First");
        memory.addAssistantMessage("s1", "Second");
        memory.addToolResult("s1", "tool", "Third");
        List<AgentMemory.MemoryEntry> history = memory.getHistory("s1");
        assertEquals("First", history.get(0).text());
        assertEquals("Second", history.get(1).text());
        assertEquals("Third", history.get(2).text());
    }

    @Test
    void getHistory_returnsEmptyForUnknownSession() {
        assertTrue(memory.getHistory("unknown").isEmpty());
    }

    @Test
    void getRecentHistory_returnsLastN() {
        for (int i = 1; i <= 10; i++) {
            memory.addUserMessage("s1", "msg-" + i);
        }
        List<AgentMemory.MemoryEntry> recent = memory.getRecentHistory("s1", 3);
        assertEquals(3, recent.size());
        assertEquals("msg-8", recent.get(0).text());
        assertEquals("msg-10", recent.get(2).text());
    }

    @Test
    void getRecentHistory_returnsAllWhenSmallerThanMax() {
        memory.addUserMessage("s1", "only one");
        assertEquals(1, memory.getRecentHistory("s1", 10).size());
    }

    @Test
    void clear_removesAllHistory() {
        memory.addUserMessage("s1", "msg");
        memory.clear("s1");
        assertEquals(0, memory.size("s1"));
        assertTrue(memory.getHistory("s1").isEmpty());
    }

    @Test
    void clear_doesNotAffectOtherSessions() {
        memory.addUserMessage("s1", "keep");
        memory.addUserMessage("s2", "remove");
        memory.clear("s2");
        assertEquals(1, memory.size("s1"));
        assertEquals(0, memory.size("s2"));
    }

    @Test
    void size_countsAllEntries() {
        memory.addUserMessage("s1", "u1");
        memory.addAssistantMessage("s1", "a1");
        memory.addToolResult("s1", "t", "r1");
        assertEquals(3, memory.size("s1"));
    }

    @Test
    void sessionIsolation_differentSessionsDontShareHistory() {
        memory.addUserMessage("session-a", "Message A");
        memory.addUserMessage("session-b", "Message B");
        assertEquals(1, memory.size("session-a"));
        assertEquals(1, memory.size("session-b"));
        assertEquals("Message A", memory.getHistory("session-a").get(0).text());
        assertEquals("Message B", memory.getHistory("session-b").get(0).text());
    }

    @Test
    void getHistory_returnsUnmodifiableList() {
        memory.addUserMessage("s1", "msg");
        assertThrows(UnsupportedOperationException.class,
                () -> memory.getHistory("s1").clear());
    }
}