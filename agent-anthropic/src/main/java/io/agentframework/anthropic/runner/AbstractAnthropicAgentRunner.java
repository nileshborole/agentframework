package io.agentframework.anthropic.runner;

import io.agentframework.anthropic.llm.AnthropicLlmClient;
import io.agentframework.core.AgentState;
import io.agentframework.core.context.ContextBuilderSelector;
import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.result.AgentResult;
import io.agentframework.core.runner.AbstractAgentRunner;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.llm.*;

import java.util.List;

/**
 * Base runner for agents using the Anthropic SDK directly.
 *
 * <p>Extends {@link AbstractAgentRunner} with {@link LlmMessage} as the message type.
 * Handles context building delegation, LLM invocation via {@link AnthropicLlmClient},
 * and token usage tracking.
 *
 * <p>Domain runners extend this class and implement only:
 * <ul>
 *   <li>{@link #parseDecision} — parse raw LLM response into a typed decision</li>
 *   <li>{@link #buildFallbackOutput} — produce a safe fallback string</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * public class TripPlannerRunner extends AbstractAnthropicAgentRunner&lt;TripData&gt; {
 *     public TripPlannerRunner(AnthropicLlmClient client,
 *                               ContextBuilderSelector&lt;TripData, LlmMessage&gt; selector,
 *                               ToolRegistry tools, AgentMemory memory) {
 *         super(client, selector, tools, memory, AgentObserver.NOOP);
 *     }
 *
 *     {@literal @}Override
 *     protected AgentResult&lt;AgentDecision&gt; parseDecision(String raw) { ... }
 *
 *     {@literal @}Override
 *     protected String buildFallbackOutput(AgentState&lt;TripData&gt; state, String reason) { ... }
 *
 *     {@literal @}Override
 *     public String runnerName() { return "trip-planner"; }
 * }
 * </pre>
 *
 * @param <S> the domain state type
 */
public abstract class AbstractAnthropicAgentRunner<S> extends AbstractAgentRunner<S, LlmMessage> {

    private final AnthropicLlmClient llmClient;
    private final ContextBuilderSelector<S, LlmMessage> contextSelector;
    private final AgentMemory memory;

    /**
     * Token usage from the most recent LLM call, stored per-thread.
     * Index 0 = input tokens, index 1 = output tokens.
     */
    private final ThreadLocal<int[]> lastTokenUsage = ThreadLocal.withInitial(() -> new int[]{0, 0});

    /**
     * Creates a new Anthropic agent runner.
     *
     * @param llmClient       the Anthropic LLM client
     * @param contextSelector selects the appropriate context builder per phase
     * @param toolRegistry    available tools
     * @param memory          conversation memory
     * @param observer        observability hook
     */
    protected AbstractAnthropicAgentRunner(AnthropicLlmClient llmClient,
                                            ContextBuilderSelector<S, LlmMessage> contextSelector,
                                            ToolRegistry toolRegistry,
                                            AgentMemory memory,
                                            AgentObserver observer) {
        super(toolRegistry, observer);
        this.llmClient = llmClient;
        this.contextSelector = contextSelector;
        this.memory = memory;
    }

    // ── AbstractAgentRunner hooks ────────────────────────────────────────

    @Override
    protected List<LlmMessage> buildContext(AgentState<S> state, String userInput,
                                             List<String> missingFields) {
        return contextSelector.select(state).build(state, userInput, missingFields);
    }

    @Override
    protected AgentResult<String> callLlm(AgentState<S> state, List<LlmMessage> context,
                                           String traceId, int step) {
        LlmClientConfig config = llmClientConfig(state, step);
        LlmRequest request = LlmRequest.of(runnerName(), context, config);

        AgentResult<LlmResponse> result = llmClient.call(request);
        if (result.isFailure()) {
            return AgentResult.failure(result.failureReason().orElse("LLM call failed"));
        }

        LlmResponse response = result.value().orElseThrow();
        lastTokenUsage.set(new int[]{response.inputTokens(), response.outputTokens()});

        return AgentResult.success(response.content());
    }

    @Override
    protected int[] tokenUsage(String rawResponse) {
        return lastTokenUsage.get();
    }

    @Override
    protected void addToolObservation(AgentState<S> state, String toolName, String result) {
        memory.addToolResult(state.sessionId(), toolName, result);
    }

    // ── Configurable hook ────────────────────────────────────────────────

    /**
     * Returns the LLM client config for the current state and step.
     *
     * <p>Override to vary model, temperature, or max tokens by phase.
     * Default returns {@link LlmClientConfig#forStructuredOutput()}.
     *
     * @param state the current agent state
     * @param step  the current step number
     * @return the LLM config to use
     */
    protected LlmClientConfig llmClientConfig(AgentState<S> state, int step) {
        return LlmClientConfig.forStructuredOutput();
    }
}