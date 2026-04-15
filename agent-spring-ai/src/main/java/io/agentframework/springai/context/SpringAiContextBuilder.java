package io.agentframework.springai.context;

import io.agentframework.core.AgentState;
import io.agentframework.core.context.ContextBuilder;
import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.tool.ToolRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Base {@link ContextBuilder} for Spring AI domains.
 *
 * <p>Implements the common prompt assembly pattern used across both
 * {@code TripContextBuilder} and {@code ExploreContextBuilder} in
 * the Yodeki codebase — but as a reusable, domain-agnostic base class.
 *
 * <p>Assembly order (matches what you already have):
 * <ol>
 *   <li>System prompt(s) — from {@link #buildSystemPrompts}</li>
 *   <li>Phase-specific instructions — from {@link #buildPhaseInstructions}</li>
 *   <li>Tool definitions block — auto-built from {@link ToolRegistry}</li>
 *   <li>Domain context messages — from {@link #buildDomainContext}</li>
 *   <li>Missing fields hint — from {@link #buildMissingFieldsHint}</li>
 *   <li>Conversation memory — from {@link AgentMemory}, reversed (newest last)</li>
 * </ol>
 *
 * <p>Domain context builders extend this and implement the abstract methods.
 * They never need to handle memory or tool injection manually.
 *
 * <p>Example domain implementation:
 * <pre>
 * {@literal @}Component
 * public class TripPlanContextBuilder
 *         extends SpringAiContextBuilder&lt;TripAttributes&gt; {
 *
 *     public TripPlanContextBuilder(ToolRegistry tools, AgentMemory memory) {
 *         super(tools, memory);
 *     }
 *
 *     {@literal @}Override
 *     protected List&lt;String&gt; buildSystemPrompts(AgentState&lt;TripAttributes&gt; state) {
 *         return List.of(PLAN_SYSTEM_PROMPT);
 *     }
 *
 *     {@literal @}Override
 *     protected List&lt;String&gt; buildDomainContext(AgentState&lt;TripAttributes&gt; state,
 *                                                 String userInput) {
 *         TripAttributes attrs = state.domainData();
 *         return List.of("Phase: " + state.currentPhase().id(),
 *                        "Attributes: " + toJson(attrs));
 *     }
 *
 *     {@literal @}Override
 *     public boolean appliesTo(AgentState&lt;TripAttributes&gt; state) {
 *         return state.currentPhase().id().equals("PLAN");
 *     }
 * }
 * </pre>
 *
 * @param <S> the domain state type
 */
public abstract class SpringAiContextBuilder<S>
        implements ContextBuilder<S, Message> {

    protected final ToolRegistry toolRegistry;
    protected final AgentMemory memory;

    protected SpringAiContextBuilder(ToolRegistry toolRegistry, AgentMemory memory) {
        this.toolRegistry = toolRegistry;
        this.memory       = memory;
    }

    // ── ContextBuilder ────────────────────────────────────────────────────

    @Override
    public final List<Message> build(AgentState<S> state,
                                     String userInput,
                                     List<String> missingFields) {
        List<Message> context = new ArrayList<>();

        // 1. System prompts
        buildSystemPrompts(state).forEach(p ->
                context.add(new SystemMessage(p)));

        // 2. Phase-specific instructions
        buildPhaseInstructions(state).forEach(p ->
                context.add(new SystemMessage(p)));

        // 3. Tool definitions (only if tools are registered)
        if (!toolRegistry.all().isEmpty()) {
            String toolBlock = toolRegistry.buildToolDefinitionBlock();
            if (!toolBlock.isBlank()) {
                context.add(new SystemMessage("Available tools:\n" + toolBlock));
            }
        }

        // 4. Domain-specific context (attributes, scenario, etc.)
        buildDomainContext(state, userInput).forEach(c ->
                context.add(new SystemMessage(c)));

        // 5. Missing fields hint
        String missingHint = buildMissingFieldsHint(state, missingFields);
        if (missingHint != null && !missingHint.isBlank()) {
            context.add(new SystemMessage(missingHint));
        }

        // 6. Conversation memory — reversed (oldest→newest = natural reading order)
        List<AgentMemory.MemoryEntry> history =
                memory.getRecentHistory(state.sessionId(), maxMemoryEntries());
        List<AgentMemory.MemoryEntry> ordered = new ArrayList<>(history);
        // history is already oldest-first from InMemoryAgentMemory — just add
        for (AgentMemory.MemoryEntry entry : ordered) {
            context.add(toSpringMessage(entry));
        }

        return context;
    }

    // ── Abstract hooks ────────────────────────────────────────────────────

    /**
     * Returns the core system prompt(s) for this context builder.
     * These define the agent's role, capabilities, and output format.
     *
     * @param state current agent state
     * @return non-null list of system prompt strings (may be empty)
     */
    protected abstract List<String> buildSystemPrompts(AgentState<S> state);

    /**
     * Returns phase-specific or scenario-specific instructions.
     * Injected after the core system prompt.
     *
     * <p>Default: empty list. Override to add scenario-aware instructions
     * (e.g. VAGUE / NOT_FROZEN / FROZEN in the Yodeki Explore phase).
     */
    protected List<String> buildPhaseInstructions(AgentState<S> state) {
        return List.of();
    }

    /**
     * Returns domain-specific context messages injected after tool definitions.
     * Typically includes: serialised domain state, scenario subtype, certainty level.
     *
     * <p>These replace the manual TripAttributes / scenarioSubtype / certaintyLevel
     * blocks that were duplicated in both Yodeki context builders.
     *
     * @param state     current agent state
     * @param userInput the user's message for this turn
     * @return list of context strings (each becomes a SystemMessage)
     */
    protected List<String> buildDomainContext(AgentState<S> state, String userInput) {
        return List.of();
    }

    /**
     * Returns a hint about missing fields for the agent to gather.
     * Return null or blank to omit this section.
     *
     * <p>Default: generates "Missing: [field1, field2]" if list is non-empty.
     */
    protected String buildMissingFieldsHint(AgentState<S> state,
                                            List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) return null;
        return "Still missing (gather naturally, not as interrogation): " + missingFields;
    }

    /**
     * Maximum number of memory entries to include in context.
     * Override to tighten or relax the memory window.
     * Default: 20 (last 20 messages).
     */
    protected int maxMemoryEntries() {
        return 20;
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Message toSpringMessage(AgentMemory.MemoryEntry entry) {
        return switch (entry.role()) {
            case USER        -> new UserMessage(entry.text());
            case ASSISTANT   -> new AssistantMessage(entry.text());
            case TOOL_RESULT -> new UserMessage(
                    "[Tool: " + entry.toolName() + "]\n" + entry.text());
        };
    }
}