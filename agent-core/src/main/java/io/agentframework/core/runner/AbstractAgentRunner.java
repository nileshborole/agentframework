package io.agentframework.core.runner;

import io.agentframework.core.AgentDecision;
import io.agentframework.core.AgentState;
import io.agentframework.core.StepType;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.observer.StepTrace;
import io.agentframework.core.result.AgentResult;
import io.agentframework.core.result.AgentRunOutcome;
import io.agentframework.core.result.AgentRunResult;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.core.tool.ToolResult;
import io.agentframework.core.tool.ToolSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generic ReAct agent loop that domain runners extend.
 *
 * <p>Handles all framework mechanics so domains implement only three methods:
 * <ol>
 *   <li>{@link #callLlm} — invoke the LLM with current context</li>
 *   <li>{@link #parseDecision} — parse raw LLM response into a typed decision</li>
 *   <li>{@link #buildFallbackOutput} — produce a safe fallback string on failure</li>
 * </ol>
 *
 * <p>The loop follows the ReAct pattern taught in Topic 3:
 * <pre>
 * while steps &lt; maxSteps AND tokens &lt; budget AND time &lt; timeout:
 *   context = contextBuilder.build(state, userInput, missing)
 *   response = callLlm(context)               ← domain implements
 *   decision = parseDecision(response)        ← domain implements
 *   if confidence &lt; threshold → return fallback
 *   switch decision.stepType():
 *     RESPOND         → send reply → DONE
 *     CALL_TOOL       → execute tool → if PAUSE_FOR_USER → DONE
 *     TRANSITION_PHASE→ update phase → continue loop
 *     CUSTOM          → handleCustomStep() → domain decides
 * </pre>
 *
 * <p>Every step is traced via {@link AgentObserver} for full observability.
 *
 * @param <S> the domain state type
 * @param <M> the LLM message type used by the domain's context builder
 */
public abstract class AbstractAgentRunner<S, M> implements AgentRunner<S> {

    private static final Logger log = LoggerFactory.getLogger(AbstractAgentRunner.class);

    protected final ToolRegistry toolRegistry;
    protected final AgentObserver observer;

    protected AbstractAgentRunner(ToolRegistry toolRegistry, AgentObserver observer) {
        this.toolRegistry = toolRegistry;
        this.observer     = observer != null ? observer : AgentObserver.NOOP;
    }

    // ── AgentRunner contract ──────────────────────────────────────────────

    @Override
    public final AgentRunResult run(AgentState<S> state,
                                    String userInput,
                                    List<String> missingFields,
                                    AgentRunConfig config) {
        String traceId    = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        AtomicInteger inputTokens  = new AtomicInteger(0);
        AtomicInteger outputTokens = new AtomicInteger(0);
        int stepsExecuted = 0;

        observer.onRunStart(state, userInput, traceId);

        try {
            for (int step = 0; step < config.maxSteps(); step++) {
                stepsExecuted = step + 1;

                // ── Build context ─────────────────────────────────────────
                List<M> context = buildContext(state, userInput, missingFields);
                if (context == null || context.isEmpty()) {
                    log.warn("[{}] Empty context from builder at step {}. "
                            + "Returning fallback.", runnerName(), step);
                    return failure(state, traceId, AgentRunOutcome.ERROR,
                            "Context builder returned empty context",
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                // ── Call LLM ──────────────────────────────────────────────
                Instant stepStart = Instant.now();
                AgentResult<String> llmResult = callLlm(state, context, traceId, step);
                Duration stepDuration = Duration.between(stepStart, Instant.now());

                if (llmResult.isFailure()) {
                    log.warn("[{}] LLM call failed at step {}: {}",
                            runnerName(), step,
                            llmResult.failureReason().orElse("unknown"));
                    observer.onStep(state, StepTrace.llmCall(
                            traceId, state.sessionId(), step,
                            "", false, 0, 0, stepStart, stepDuration));
                    return failure(state, traceId, AgentRunOutcome.ERROR,
                            llmResult.failureReason().orElse("LLM call failed"),
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                String rawResponse = llmResult.value().get();

                // Track tokens (domain provides via tokenUsage())
                int[] tokens = tokenUsage(rawResponse);
                inputTokens.addAndGet(tokens[0]);
                outputTokens.addAndGet(tokens[1]);

                observer.onStep(state, StepTrace.llmCall(
                        traceId, state.sessionId(), step,
                        rawResponse, true,
                        tokens[0], tokens[1], stepStart, stepDuration));

                // ── Token budget check ────────────────────────────────────
                if (inputTokens.get() + outputTokens.get() >= config.maxTokenBudget()) {
                    log.warn("[{}] Token budget exceeded at step {}. Budget: {}, Used: {}",
                            runnerName(), step, config.maxTokenBudget(),
                            inputTokens.get() + outputTokens.get());
                    return failure(state, traceId, AgentRunOutcome.STEP_LIMIT_REACHED,
                            "Token budget exhausted",
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                // ── Parse decision ────────────────────────────────────────
                AgentResult<AgentDecision> decisionResult = parseDecision(rawResponse);
                if (decisionResult.isFailure()) {
                    log.warn("[{}] Decision parsing failed at step {}: {}",
                            runnerName(), step,
                            decisionResult.failureReason().orElse("parse error"));
                    return failure(state, traceId, AgentRunOutcome.ERROR,
                            decisionResult.failureReason().orElse("Decision parsing failed"),
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                AgentDecision decision = decisionResult.value().get();

                // ── Confidence guard ──────────────────────────────────────
                if (!decision.isConfident(config.minConfidenceThreshold())) {
                    log.info("[{}] Low confidence at step {}: {} < {}. Triggering fallback.",
                            runnerName(), step, decision.confidence(),
                            config.minConfidenceThreshold());
                    String fallback = buildFallbackOutput(state,
                            "Low confidence: " + decision.confidence());
                    sendOutput(state, fallback, OutputType.FALLBACK);
                    return success(state, traceId, fallback,
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                // ── Route on StepType ─────────────────────────────────────
                StepType stepType = decision.stepType();

                if (stepType instanceof StepType.Respond) {
                    String reply = decision.reply();
                    sendOutput(state, reply, OutputType.ASSISTANT);
                    return success(state, traceId, reply,
                            stepsExecuted, inputTokens, outputTokens, startTime);
                }

                if (stepType instanceof StepType.TransitionPhase tp) {
                    String fromPhase = state.currentPhase().id();
                    state.transitionTo(tp.targetPhase());
                    observer.onPhaseTransition(state, fromPhase, tp.targetPhase().id());
                    observer.onStep(state, StepTrace.phaseTransition(
                            traceId, state.sessionId(), step,
                            fromPhase, tp.targetPhase().id(),
                            stepStart, Duration.between(stepStart, Instant.now())));
                    if (tp.hasReply()) {
                        sendOutput(state, tp.reply(), OutputType.ASSISTANT);
                    }
                    continue; // Loop continues in new phase
                }

                if (stepType instanceof StepType.CallTool ct) {
                    if (ct.hasPreReply()) {
                        sendOutput(state, ct.preReply(), OutputType.ASSISTANT);
                    }

                    Instant toolStart = Instant.now();
                    ToolResult toolResult = executeTool(state, ct, missingFields);
                    Duration toolDuration = Duration.between(toolStart, Instant.now());

                    boolean toolSuccess = !toolResult.content().startsWith("ERROR");
                    observer.onStep(state, StepTrace.toolCall(
                            traceId, state.sessionId(), step,
                            ct.toolName(), ct.toolInput(),
                            toolResult.content(), toolSuccess,
                            toolSuccess ? null : toolResult.content(),
                            toolStart, toolDuration));

                    // Tool result fed back as observation
                    addToolObservation(state, ct.toolName(), toolResult.content());

                    if (toolResult.signal() == ToolSignal.PAUSE_FOR_USER) {
                        sendUserPayload(state, toolResult.userPayload());
                        return success(state, traceId,
                                toolResult.content(),
                                stepsExecuted, inputTokens, outputTokens, startTime);
                    }

                    if (toolResult.signal() == ToolSignal.TERMINATE) {
                        if (toolResult.hasUserPayload()) {
                            sendUserPayload(state, toolResult.userPayload());
                        }
                        return success(state, traceId,
                                toolResult.content(),
                                stepsExecuted, inputTokens, outputTokens, startTime);
                    }

                    continue; // CONTINUE — loop for next step
                }

                if (stepType instanceof StepType.Custom custom) {
                    AgentRunResult customResult = handleCustomStep(
                            state, decision, custom, missingFields,
                            traceId, stepsExecuted, inputTokens, outputTokens, startTime);
                    if (customResult != null) return customResult;
                    continue;
                }
            }

            // Fell through max steps
            log.warn("[{}] Max steps ({}) reached for session {}.",
                    runnerName(), config.maxSteps(), state.sessionId());
            return failure(state, traceId, AgentRunOutcome.STEP_LIMIT_REACHED,
                    "Max steps reached: " + config.maxSteps(),
                    stepsExecuted, inputTokens, outputTokens, startTime);

        } catch (Exception e) {
            log.error("[{}] Unexpected error in agent loop for session {}",
                    runnerName(), state.sessionId(), e);
            return failure(state, traceId, AgentRunOutcome.ERROR,
                    "Unexpected error: " + e.getMessage(),
                    stepsExecuted, inputTokens, outputTokens, startTime);
        } finally {
            AgentRunResult finalResult = buildFinalResult(state, traceId,
                    stepsExecuted, inputTokens, outputTokens, startTime);
            observer.onRunComplete(state, finalResult);
        }
    }

    // ── Abstract methods — domain must implement ──────────────────────────

    /**
     * Builds the LLM prompt context for the current step.
     *
     * <p>Delegates to the domain's {@code ContextBuilder} or
     * {@code ContextBuilderSelector}. Returns null or empty list
     * to trigger an immediate fallback.
     */
    protected abstract List<M> buildContext(AgentState<S> state,
                                            String userInput,
                                            List<String> missingFields);

    /**
     * Calls the LLM with the given context and returns the raw response text.
     *
     * <p>Must not throw — return {@code AgentResult.failure(...)} on errors.
     *
     * @param state    current agent state
     * @param context  the prompt messages built by {@link #buildContext}
     * @param traceId  trace identifier for observability
     * @param step     current step number (0-indexed)
     */
    protected abstract AgentResult<String> callLlm(AgentState<S> state,
                                                   List<M> context,
                                                   String traceId,
                                                   int step);

    /**
     * Parses the raw LLM response text into a typed {@link AgentDecision}.
     *
     * <p>Must not throw — return {@code AgentResult.failure(...)} if parsing fails.
     *
     * @param rawResponse the raw LLM output string
     */
    protected abstract AgentResult<AgentDecision> parseDecision(String rawResponse);

    /**
     * Returns a safe fallback output string for low-confidence or error cases.
     *
     * @param state  current agent state
     * @param reason why the fallback was triggered
     */
    protected abstract String buildFallbackOutput(AgentState<S> state, String reason);

    // ── Optional hooks — domains override as needed ───────────────────────

    /**
     * Returns token counts [inputTokens, outputTokens] from a raw LLM response.
     *
     * <p>Default: returns [0, 0]. Override to extract real token counts from
     * the provider-specific response object that your {@code callLlm} encodes
     * into the raw response string, or track them via a shared counter.
     */
    protected int[] tokenUsage(String rawResponse) {
        return new int[]{0, 0};
    }

    /**
     * Sends a text output to the user. Called for RESPOND, FALLBACK, and
     * pre-tool replies.
     *
     * <p>Default: logs the output. Override to write to WebSocket, REST
     * response, message queue, etc.
     */
    protected void sendOutput(AgentState<S> state, String output, OutputType type) {
        log.debug("[{}] session={} type={} output={}",
                runnerName(), state.sessionId(), type,
                output != null ? output.substring(0, Math.min(100, output.length())) : "null");
    }

    /**
     * Delivers a tool's user-facing payload to the application's output handler.
     *
     * <p>Default: logs the payload class name. Override to broadcast via
     * WebSocket, push to a queue, etc.
     */
    protected void sendUserPayload(AgentState<S> state, Object payload) {
        log.debug("[{}] session={} tool payload: {}",
                runnerName(), state.sessionId(),
                payload != null ? payload.getClass().getSimpleName() : "null");
    }

    /**
     * Adds a tool observation back into the agent's context for the next step.
     *
     * <p>Default: no-op. Override to append to your conversation memory or
     * message history so the LLM sees the tool result on the next call.
     */
    protected void addToolObservation(AgentState<S> state,
                                      String toolName, String result) {}

    /**
     * Handles a {@link StepType.Custom} step. Return a non-null
     * {@link AgentRunResult} to terminate the loop, or null to continue.
     *
     * <p>Default: logs and continues.
     */
    protected AgentRunResult handleCustomStep(AgentState<S> state,
                                              AgentDecision decision,
                                              StepType.Custom custom,
                                              List<String> missingFields,
                                              String traceId,
                                              int stepsExecuted,
                                              AtomicInteger inputTokens,
                                              AtomicInteger outputTokens,
                                              Instant startTime) {
        log.debug("[{}] Unhandled custom step type: {}. Continuing loop.",
                runnerName(), custom.typeId());
        return null;
    }

    // ── Tool execution ────────────────────────────────────────────────────

    private ToolResult executeTool(AgentState<S> state,
                                   StepType.CallTool ct,
                                   List<String> missingFields) {
        return toolRegistry.find(ct.toolName())
                .map(tool -> {
                    try {
                        var ctx = new io.agentframework.core.tool.ToolContext<>(
                                state, ct.toolInput(), missingFields);
                        return tool.execute(ctx);
                    } catch (Exception e) {
                        log.error("[{}] Tool '{}' threw unexpectedly",
                                runnerName(), ct.toolName(), e);
                        return io.agentframework.core.tool.ToolResult.error(
                                "Tool threw: " + e.getMessage());
                    }
                })
                .orElseGet(() -> {
                    log.warn("[{}] Tool not found: {}", runnerName(), ct.toolName());
                    return io.agentframework.core.tool.ToolResult.error(
                            "Tool not found: " + ct.toolName()
                                    + ". Available: " + toolRegistry.toolNames());
                });
    }

    // ── Result helpers ────────────────────────────────────────────────────

    private AgentRunResult success(AgentState<S> state, String traceId,
                                   String output, int steps,
                                   AtomicInteger in, AtomicInteger out,
                                   Instant start) {
        return AgentRunResult.success(state.sessionId(), output,
                steps, in.get(), out.get(), start, Instant.now());
    }

    private AgentRunResult failure(AgentState<S> state, String traceId,
                                   AgentRunOutcome outcome, String reason,
                                   int steps, AtomicInteger in,
                                   AtomicInteger out, Instant start) {
        return AgentRunResult.failure(state.sessionId(), outcome, reason,
                steps, in.get(), out.get(), start, Instant.now());
    }

    private AgentRunResult buildFinalResult(AgentState<S> state, String traceId,
                                            int steps, AtomicInteger in,
                                            AtomicInteger out, Instant start) {
        return AgentRunResult.success(state.sessionId(), null,
                steps, in.get(), out.get(), start, Instant.now());
    }

    /** Output type passed to {@link #sendOutput} for routing. */
    public enum OutputType {
        ASSISTANT, SYSTEM, FALLBACK
    }
}