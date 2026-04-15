package io.agentframework.core.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central registry of all {@link AgentTool} instances available to an agent.
 *
 * <p>Framework-agnostic by design. Works without Spring by calling
 * {@link #register(AgentTool)} manually. With Spring, the
 * {@code agent-spring} module provides an auto-configuration that
 * injects all {@code AgentTool} beans automatically.
 *
 * <p>Thread-safe — can be shared across concurrent agent runs.
 */
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /** Creates an empty registry. Tools must be registered manually. */
    public ToolRegistry() {}

    /**
     * Creates a pre-populated registry from a collection of tools.
     * Duplicate names cause the last registration to win — a warning is logged.
     */
    public ToolRegistry(Collection<AgentTool> toolList) {
        toolList.forEach(this::register);
    }

    // ── Registration ──────────────────────────────────────────────────────

    /**
     * Registers a tool. If a tool with the same name already exists,
     * it is replaced and a warning is logged.
     */
    public void register(AgentTool tool) {
        Objects.requireNonNull(tool, "Tool must not be null");
        Objects.requireNonNull(tool.name(), "Tool name must not be null");
        if (tools.containsKey(tool.name())) {
            log.warn("[ToolRegistry] Replacing existing tool: {}", tool.name());
        }
        tools.put(tool.name(), tool);
        log.debug("[ToolRegistry] Registered tool: {}", tool.name());
    }

    /** Removes a tool by name. No-op if the tool does not exist. */
    public void unregister(String name) {
        tools.remove(name);
    }

    // ── Lookup ────────────────────────────────────────────────────────────

    /**
     * Returns the tool registered under the given name, or empty if absent.
     */
    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Returns all registered tools as an unmodifiable list.
     */
    public List<AgentTool> all() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /**
     * Returns the set of registered tool names.
     */
    public Set<String> toolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Returns true if a tool with the given name is registered.
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * Builds the full tool definition block for injection into LLM prompts.
     * Each tool's {@link AgentTool#toolDefinition()} is concatenated with
     * a blank-line separator.
     */
    public String buildToolDefinitionBlock() {
        return tools.values().stream()
                .map(AgentTool::toolDefinition)
                .collect(Collectors.joining("\n"));
    }
}