package io.agentframework.example.runner;

import io.agentframework.core.AgentDecision;
import io.agentframework.core.AgentState;
import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.result.AgentResult;
import io.agentframework.core.runner.AbstractAgentRunner;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.example.domain.WeatherData;
import io.agentframework.llm.LlmClient;
import io.agentframework.llm.LlmClientConfig;
import io.agentframework.llm.LlmMessage;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * The weather assistant agent runner.
 *
 * <p>This class extends {@link AbstractAgentRunner} and implements the four
 * required abstract methods that the framework calls during the ReAct loop:
 *
 * <ol>
 *   <li>{@link #buildContext} — assembles the LLM prompt messages</li>
 *   <li>{@link #callLlm} — sends the context to the LLM and gets a response</li>
 *   <li>{@link #parseDecision} — converts the raw LLM text into a typed decision</li>
 *   <li>{@link #buildFallbackOutput} — creates a safe response when confidence is low</li>
 * </ol>
 *
 * <p>The framework handles the entire ReAct loop, tool dispatch, phase transitions,
 * token budgeting, and observability. You only write domain logic.
 *
 * <h3>Type Parameters</h3>
 * <ul>
 *   <li>{@code S = WeatherData} — the domain data type stored in AgentState</li>
 *   <li>{@code M = String} — the message type used for LLM context (simple strings here;
 *       in production with Spring AI you'd use {@code Message}, with Anthropic you'd use
 *       {@code LlmMessage})</li>
 * </ul>
 */
public class WeatherAgentRunner extends AbstractAgentRunner<WeatherData, String> {

    private final LlmClient llmClient;
    private final AgentMemory memory;
    private final WeatherDecisionParser decisionParser;

    // Thread-local storage for token usage from the last LLM call
    private final ThreadLocal<int[]> lastTokenUsage = ThreadLocal.withInitial(() -> new int[]{0, 0});

    /**
     * Creates a new weather agent runner.
     *
     * @param llmClient    the LLM client to use for generating responses
     * @param toolRegistry registry of available tools
     * @param memory       conversation memory
     * @param observer     agent observer for logging/metrics
     */
    public WeatherAgentRunner(LlmClient llmClient,
                              ToolRegistry toolRegistry,
                              AgentMemory memory,
                              AgentObserver observer) {
        // Pass toolRegistry and observer to the framework's AbstractAgentRunner
        super(toolRegistry, observer);
        this.llmClient = llmClient;
        this.memory = memory;
        this.decisionParser = new WeatherDecisionParser();
    }

    @Override
    public String runnerName() {
        return "weather-agent";
    }

    // ─── REQUIRED: Build the LLM context ────────────────────────────────────

    /**
     * Assembles the list of context messages sent to the LLM.
     *
     * <p>This is called at the start of every loop iteration. The framework
     * uses whatever you return here as input to {@link #callLlm}.
     *
     * <p>Typical context includes:
     * <ul>
     *   <li>System prompt with personality and instructions</li>
     *   <li>Available tool definitions (from ToolRegistry)</li>
     *   <li>Conversation history (from AgentMemory)</li>
     *   <li>The current user input</li>
     *   <li>Any missing field hints</li>
     * </ul>
     */
    @Override
    protected List<String> buildContext(AgentState<WeatherData> state,
                                        String userInput,
                                        List<String> missingFields) {
        List<String> context = new ArrayList<>();

        // 1. System prompt
        context.add("""
                You are a helpful weather assistant. You can look up current weather
                and forecasts for any city in the world.
                
                When the user asks about weather, use the appropriate tool.
                When you have the information, respond to the user.
                
                Respond in this exact format:
                ACTION: RESPOND | CALL_TOOL | END
                CONFIDENCE: 0.0-1.0
                TOOL: tool_name (only if ACTION=CALL_TOOL)
                TOOL_INPUT: key=value (only if ACTION=CALL_TOOL)
                REPLY: Your message to the user
                """);

        // 2. Available tools (the framework's ToolRegistry formats them)
        String toolBlock = getToolRegistry().buildToolDefinitionBlock();
        if (!toolBlock.isBlank()) {
            context.add("Available tools:\n" + toolBlock);
        }

        // 3. Conversation history
        var history = memory.getRecentHistory(state.sessionId(), 20);
        for (var entry : history) {
            context.add("[" + entry.role() + "] " + entry.text());
        }

        // 4. Current user input
        context.add("[USER] " + userInput);

        // 5. Missing fields hint
        if (missingFields != null && !missingFields.isEmpty()) {
            context.add("NOTE: Still missing information: " + String.join(", ", missingFields));
        }

        return context;
    }

    // ─── REQUIRED: Call the LLM ─────────────────────────────────────────────

    /**
     * Sends the assembled context to the LLM and returns the raw response.
     *
     * <p>The framework calls this after {@link #buildContext}. You translate
     * your context format into whatever the LLM client expects, make the call,
     * and return the raw text.
     */
    @Override
    protected AgentResult<String> callLlm(AgentState<WeatherData> state,
                                           List<String> context,
                                           String traceId,
                                           int step) {
        // Build LlmMessage list from our string context
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(context.get(0))); // system prompt
        for (int i = 1; i < context.size(); i++) {
            String msg = context.get(i);
            if (msg.startsWith("[USER]") || msg.startsWith("[TOOL_RESULT]")) {
                messages.add(LlmMessage.user(msg));
            } else if (msg.startsWith("[ASSISTANT]")) {
                messages.add(LlmMessage.assistant(msg));
            } else {
                messages.add(LlmMessage.system(msg));
            }
        }

        // Create LLM request
        LlmRequest request = LlmRequest.of("weather-prompt", messages,
                LlmClientConfig.forStructuredOutput());

        // Call the LLM
        AgentResult<LlmResponse> result = llmClient.call(request);

        if (result.isFailure()) {
            return AgentResult.failure(result.failureReason().orElse("LLM call failed"));
        }

        LlmResponse response = result.value().orElse(null);
        if (response == null || !response.hasContent()) {
            return AgentResult.failure("Empty LLM response");
        }

        // Store token usage for the framework to read via tokenUsage()
        lastTokenUsage.set(new int[]{response.inputTokens(), response.outputTokens()});

        return AgentResult.success(response.content());
    }

    // ─── REQUIRED: Parse LLM response into a decision ───────────────────────

    /**
     * Parses the raw LLM response text into a typed {@link AgentDecision}.
     *
     * <p>The framework calls this after {@link #callLlm}. The decision tells
     * the framework what to do next: respond to user, call a tool, transition
     * phase, or execute custom logic.
     */
    @Override
    protected AgentResult<AgentDecision> parseDecision(String rawResponse) {
        return decisionParser.parse(rawResponse);
    }

    // ─── REQUIRED: Build fallback output ────────────────────────────────────

    /**
     * Creates a safe fallback response when confidence is below threshold.
     *
     * <p>Called when the LLM's decision confidence is lower than
     * {@code AgentRunConfig.minConfidenceThreshold()}.
     */
    @Override
    protected String buildFallbackOutput(AgentState<WeatherData> state, String reason) {
        return "I'm not confident enough to answer that. Could you rephrase your question? (Reason: " + reason + ")";
    }

    // ─── OPTIONAL HOOKS ─────────────────────────────────────────────────────

    /**
     * Returns token usage from the last LLM call.
     * The framework uses this for budget tracking and observability.
     */
    @Override
    protected int[] tokenUsage(String rawResponse) {
        return lastTokenUsage.get();
    }

    /**
     * Called when the framework wants to send output to the user.
     * Override this to integrate with your UI/API layer.
     */
    @Override
    protected void sendOutput(AgentState<WeatherData> state, String output, OutputType type) {
        System.out.println("[" + type + "] " + output);
        // Also store in memory so the next turn has context
        memory.addAssistantMessage(state.sessionId(), output);
    }

    /**
     * Called after a tool produces a result.
     * Stores the observation in memory so the LLM sees it on the next iteration.
     */
    @Override
    protected void addToolObservation(AgentState<WeatherData> state, String toolName, String result) {
        memory.addToolResult(state.sessionId(), toolName, result);
    }

    // Expose toolRegistry for buildContext
    private ToolRegistry getToolRegistry() {
        // Access via the field inherited from AbstractAgentRunner
        // We store a reference since the parent's field may be protected
        return toolRegistry;
    }
}