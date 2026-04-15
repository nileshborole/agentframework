package io.agentframework.springai.llm;

import io.agentframework.core.AgentDecision;
import io.agentframework.core.AgentState;
import io.agentframework.core.context.ContextBuilderSelector;
import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.result.AgentResult;
import io.agentframework.core.runner.AbstractAgentRunner;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.llm.LlmClientConfig;
import io.agentframework.llm.LlmMessage;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring AI-flavoured extension of {@link AbstractAgentRunner}.
 *
 * <p>Wires together:
 * <ul>
 *   <li>A {@link SpringAiLlmClient} for all LLM calls</li>
 *   <li>A {@link ContextBuilderSelector} that picks the right Spring AI
 *       {@link io.agentframework.springai.context.SpringAiContextBuilder} per step</li>
 *   <li>An {@link AgentMemory} for reading conversation history into context
 *       and writing observations after tool calls</li>
 * </ul>
 *
 * <p>Domain runners extend this and implement only:
 * <ul>
 *   <li>{@link #parseDecision} — parse raw JSON → typed {@link AgentDecision}</li>
 *   <li>{@link #buildFallbackOutput} — safe fallback string</li>
 *   <li>{@link #runnerName} — identity string for logs and metrics</li>
 * </ul>
 *
 * <p>Yodeki migration example — how {@code TripAgentSupervisor} becomes:
 * <pre>
 * {@literal @}Component
 * public class TripAgentRunner
 *         extends AbstractSpringAiAgentRunner&lt;TripAttributes&gt; {
 *
 *     private final ObjectMapper mapper;
 *
 *     public TripAgentRunner(
 *             SpringAiLlmClient llmClient,
 *             ContextBuilderSelector&lt;TripAttributes, Message&gt; contextSelector,
 *             ToolRegistry toolRegistry,
 *             AgentMemory memory,
 *             AgentObserver observer,
 *             ObjectMapper mapper) {
 *         super(llmClient, contextSelector, toolRegistry, memory, observer);
 *         this.mapper = mapper;
 *     }
 *
 *     {@literal @}Override
 *     public String runnerName() { return "trip-agent-runner"; }
 *
 *     {@literal @}Override
 *     protected AgentResult&lt;AgentDecision&gt; parseDecision(String raw) {
 *         try {
 *             TripPlannerDecision d = mapper.readValue(raw, TripPlannerDecision.class);
 *             return AgentResult.success(d);
 *         } catch (Exception e) {
 *             return AgentResult.failure("Parse failed: " + e.getMessage());
 *         }
 *     }
 *
 *     {@literal @}Override
 *     protected String buildFallbackOutput(AgentState&lt;TripAttributes&gt; state,
 *                                           String reason) {
 *         return "Could you clarify that?";
 *     }
 * }
 * </pre>
 *
 * @param <S> the domain state type
 */
public abstract class AbstractSpringAiAgentRunner<S>
        extends AbstractAgentRunner<S, Message> {

    private final SpringAiLlmClient llmClient;
    private final ContextBuilderSelector<S, Message> contextSelector;
    private final AgentMemory memory;

    // Per-run token accumulator shared between callLlm and tokenUsage
    private final ThreadLocal<int[]> lastTokenUsage = ThreadLocal.withInitial(() -> new int[]{0, 0});

    protected AbstractSpringAiAgentRunner(
            SpringAiLlmClient llmClient,
            ContextBuilderSelector<S, Message> contextSelector,
            ToolRegistry toolRegistry,
            AgentMemory memory,
            AgentObserver observer) {
        super(toolRegistry, observer);
        this.llmClient       = llmClient;
        this.contextSelector = contextSelector;
        this.memory          = memory;
    }

    // ── AbstractAgentRunner hooks ─────────────────────────────────────────

    @Override
    protected List<Message> buildContext(AgentState<S> state,
                                         String userInput,
                                         List<String> missingFields) {
        return contextSelector.select(state)
                .build(state, userInput, missingFields);
    }

    @Override
    protected AgentResult<String> callLlm(AgentState<S> state,
                                          List<Message> context,
                                          String traceId,
                                          int step) {
        // Convert Spring AI Messages to framework LlmMessages
        List<LlmMessage> llmMessages = context.stream()
                .map(this::toLlmMessage)
                .toList();

        LlmRequest request = LlmRequest.of(
                runnerName() + "_step_" + step,
                llmMessages,
                llmClientConfig(state, step));

        AgentResult<LlmResponse> result = llmClient.call(request);

        if (result.isSuccess()) {
            LlmResponse response = result.value().get();
            // Capture token usage for tokenUsage() override
            lastTokenUsage.set(new int[]{
                    response.inputTokens(),
                    response.outputTokens()
            });
            return AgentResult.success(response.content());
        }

        return result.map(LlmResponse::content);
    }

    @Override
    protected int[] tokenUsage(String rawResponse) {
        return lastTokenUsage.get();
    }

    @Override
    protected void addToolObservation(AgentState<S> state,
                                      String toolName, String result) {
        // Write tool result into conversation memory so it appears in next step's context
        memory.addToolResult(state.sessionId(), toolName, result);
    }

    // ── Overrideable hooks ────────────────────────────────────────────────

    /**
     * Returns the {@link LlmClientConfig} for a given step.
     *
     * <p>Default: structured output mode (low temperature, JSON mode enabled).
     * Override to vary config by phase, step number, or state.
     */
    protected LlmClientConfig llmClientConfig(AgentState<S> state, int step) {
        return LlmClientConfig.forStructuredOutput();
    }

    // ── Type translation ──────────────────────────────────────────────────

    private LlmMessage toLlmMessage(Message springMessage) {
        return switch (springMessage.getMessageType()) {
            case SYSTEM    -> LlmMessage.system(springMessage.getText());
            case ASSISTANT -> LlmMessage.assistant(springMessage.getText());
            case TOOL      -> LlmMessage.toolResult(springMessage.getText());
            default        -> LlmMessage.user(springMessage.getText());
        };
    }
}