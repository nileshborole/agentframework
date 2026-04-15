package io.agentframework.core.tool;

import io.agentframework.core.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ── Registration ──────────────────────────────────────────────────────

    @Test
    void register_addsToolByName() {
        registry.register(echoTool("greet"));
        assertTrue(registry.contains("greet"));
        assertEquals(1, registry.toolNames().size());
    }

    @Test
    void register_replacesExistingToolWithSameName() {
        registry.register(echoTool("greet"));
        AgentTool updated = resultTool("greet", "updated-output");
        registry.register(updated);

        Optional<AgentTool> found = registry.find("greet");
        assertTrue(found.isPresent());
        ToolContext<?> ctx = new ToolContext<>(
                TestFixtures.state("s1"), null, null);
        assertEquals("updated-output", found.get().execute(ctx).content());
    }

    @Test
    void bulkConstructor_registersAll() {
        registry = new ToolRegistry(List.of(
                echoTool("a"), echoTool("b"), echoTool("c")));
        assertEquals(3, registry.toolNames().size());
    }

    // ── Lookup ────────────────────────────────────────────────────────────

    @Test
    void find_returnsEmptyForUnknownName() {
        assertTrue(registry.find("nonexistent").isEmpty());
    }

    @Test
    void find_returnsToolByExactName() {
        registry.register(echoTool("search_web"));
        assertTrue(registry.find("search_web").isPresent());
        assertTrue(registry.find("Search_Web").isEmpty()); // case-sensitive
    }

    @Test
    void unregister_removesTool() {
        registry.register(echoTool("temp"));
        registry.unregister("temp");
        assertFalse(registry.contains("temp"));
    }

    @Test
    void all_returnsUnmodifiableList() {
        registry.register(echoTool("a"));
        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().add(echoTool("b")));
    }

    // ── Tool definition block ─────────────────────────────────────────────

    @Test
    void buildToolDefinitionBlock_containsAllToolNames() {
        registry.register(echoTool("search_web"));
        registry.register(echoTool("get_stock_price"));
        String block = registry.buildToolDefinitionBlock();
        assertTrue(block.contains("search_web"));
        assertTrue(block.contains("get_stock_price"));
    }

    // ── Tool execution stubs ──────────────────────────────────────────────

    private static AgentTool echoTool(String name) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "Echo tool: " + name; }
            @Override public List<ToolInputSpec> inputs() { return List.of(); }
            @Override public ToolResult execute(ToolContext<?> ctx) {
                return ToolResult.continueWith("echo:" + name);
            }
        };
    }

    private static AgentTool resultTool(String name, String output) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "Result tool"; }
            @Override public List<ToolInputSpec> inputs() { return List.of(); }
            @Override public ToolResult execute(ToolContext<?> ctx) {
                return ToolResult.continueWith(output);
            }
        };
    }
}