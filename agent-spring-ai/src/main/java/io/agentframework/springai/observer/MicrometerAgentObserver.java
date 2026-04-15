package io.agentframework.springai.observer;

import io.agentframework.core.AgentState;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.observer.StepTrace;
import io.agentframework.core.result.AgentRunResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * {@link AgentObserver} that publishes agent lifecycle events as Micrometer metrics.
 *
 * <p>Enriches Spring AI's existing auto-configured metrics ({@code gen_ai.client.*})
 * with agent-level semantics. Never replaces them — sits alongside them.
 *
 * <p>Published metrics:
 * <pre>
 * agent.run.started        [counter]  — sessionId, runnerName, phase
 * agent.run.completed      [counter]  — sessionId, runnerName, outcome
 * agent.run.duration       [timer]    — runnerName, outcome         (milliseconds)
 * agent.run.steps          [timer]    — runnerName                  (step count as value)
 * agent.run.tokens         [counter]  — runnerName, tokenType(input|output)
 * agent.run.cost_usd       [counter]  — runnerName                  (estimated cost)
 * agent.tool.call          [counter]  — runnerName, toolName, success(true|false)
 * agent.tool.duration      [timer]    — runnerName, toolName
 * agent.phase.transition   [counter]  — runnerName, from, to
 * </pre>
 *
 * <p>All metrics use the {@code agent.} prefix to avoid collision with
 * Spring AI's {@code gen_ai.} namespace.
 *
 * <p>Usage — wire in Spring context:
 * <pre>
 * {@literal @}Bean
 * public AgentObserver agentObserver(MeterRegistry meterRegistry) {
 *     return new MicrometerAgentObserver(meterRegistry, "trip-planning-runner");
 * }
 * </pre>
 *
 * <p>Combine with {@code Slf4jAgentObserver} via {@code CompositeAgentObserver}
 * to get both structured logs and Micrometer metrics:
 * <pre>
 * {@literal @}Bean
 * public AgentObserver agentObserver(MeterRegistry registry) {
 *     return new CompositeAgentObserver(List.of(
 *         new Slf4jAgentObserver(),
 *         new MicrometerAgentObserver(registry, "trip-planning-runner")
 *     ));
 * }
 * </pre>
 */
public class MicrometerAgentObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger(MicrometerAgentObserver.class);

    // ── Metric name constants ─────────────────────────────────────────────
    public static final String METRIC_RUN_STARTED      = "agent.run.started";
    public static final String METRIC_RUN_COMPLETED    = "agent.run.completed";
    public static final String METRIC_RUN_DURATION     = "agent.run.duration";
    public static final String METRIC_RUN_STEPS        = "agent.run.steps";
    public static final String METRIC_RUN_TOKENS       = "agent.run.tokens";
    public static final String METRIC_RUN_COST         = "agent.run.cost_usd";
    public static final String METRIC_TOOL_CALL        = "agent.tool.call";
    public static final String METRIC_TOOL_DURATION    = "agent.tool.duration";
    public static final String METRIC_PHASE_TRANSITION = "agent.phase.transition";

    // ── Tag name constants ────────────────────────────────────────────────
    public static final String TAG_RUNNER   = "runner";
    public static final String TAG_OUTCOME  = "outcome";
    public static final String TAG_PHASE    = "phase";
    public static final String TAG_TOOL     = "tool";
    public static final String TAG_SUCCESS  = "success";
    public static final String TAG_FROM     = "from";
    public static final String TAG_TO       = "to";
    public static final String TAG_TOKEN_TYPE = "token_type";

    private final MeterRegistry registry;
    private final String runnerName;

    public MicrometerAgentObserver(MeterRegistry registry, String runnerName) {
        this.registry   = registry;
        this.runnerName = runnerName;
    }

    // ── AgentObserver ─────────────────────────────────────────────────────

    @Override
    public void onRunStart(AgentState<?> state, String userInput, String traceId) {
        safely(() ->
                Counter.builder(METRIC_RUN_STARTED)
                        .description("Agent runs started")
                        .tag(TAG_RUNNER, runnerName)
                        .tag(TAG_PHASE,  state.currentPhase().id())
                        .register(registry)
                        .increment()
        );
    }

    @Override
    public void onRunComplete(AgentState<?> state, AgentRunResult result) {
        safely(() -> {
            String outcome = result.outcome().name();

            // Run completed counter
            Counter.builder(METRIC_RUN_COMPLETED)
                    .description("Agent runs completed, by outcome")
                    .tag(TAG_RUNNER,  runnerName)
                    .tag(TAG_OUTCOME, outcome)
                    .register(registry)
                    .increment();

            // Wall-clock duration
            Timer.builder(METRIC_RUN_DURATION)
                    .description("Agent run wall-clock duration")
                    .tag(TAG_RUNNER,  runnerName)
                    .tag(TAG_OUTCOME, outcome)
                    .register(registry)
                    .record(result.duration().toMillis(), TimeUnit.MILLISECONDS);

            // Steps used (recorded as a timer so we get p50/p95/p99)
            Timer.builder(METRIC_RUN_STEPS)
                    .description("Steps executed per agent run")
                    .tag(TAG_RUNNER, runnerName)
                    .register(registry)
                    .record(result.stepsExecuted(), TimeUnit.MILLISECONDS);

            // Token counts
            Counter.builder(METRIC_RUN_TOKENS)
                    .description("LLM tokens consumed by agent runs")
                    .tag(TAG_RUNNER,     runnerName)
                    .tag(TAG_TOKEN_TYPE, "input")
                    .register(registry)
                    .increment(result.totalInputTokens());

            Counter.builder(METRIC_RUN_TOKENS)
                    .description("LLM tokens consumed by agent runs")
                    .tag(TAG_RUNNER,     runnerName)
                    .tag(TAG_TOKEN_TYPE, "output")
                    .register(registry)
                    .increment(result.totalOutputTokens());

            // Estimated cost
            Counter.builder(METRIC_RUN_COST)
                    .description("Estimated LLM cost per agent run in USD")
                    .tag(TAG_RUNNER, runnerName)
                    .register(registry)
                    .increment(result.estimatedCostUsd());
        });
    }

    @Override
    public void onStep(AgentState<?> state, StepTrace step) {
        safely(() -> {
            if (step.isToolCall()) {
                // Tool call counter (success/failure)
                Counter.builder(METRIC_TOOL_CALL)
                        .description("Agent tool calls")
                        .tag(TAG_RUNNER,  runnerName)
                        .tag(TAG_TOOL,    step.name())
                        .tag(TAG_SUCCESS, String.valueOf(step.success()))
                        .register(registry)
                        .increment();

                // Tool call duration
                Timer.builder(METRIC_TOOL_DURATION)
                        .description("Agent tool execution duration")
                        .tag(TAG_RUNNER, runnerName)
                        .tag(TAG_TOOL,   step.name())
                        .register(registry)
                        .record(step.duration().toMillis(), TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override
    public void onPhaseTransition(AgentState<?> state,
                                  String fromPhase, String toPhase) {
        safely(() ->
                Counter.builder(METRIC_PHASE_TRANSITION)
                        .description("Agent phase transitions")
                        .tag(TAG_RUNNER, runnerName)
                        .tag(TAG_FROM,   fromPhase)
                        .tag(TAG_TO,     toPhase)
                        .register(registry)
                        .increment()
        );
    }

    // ── Safety wrapper ────────────────────────────────────────────────────

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            // Micrometer errors must never break the agent loop
            log.warn("[MicrometerAgentObserver] Metric recording failed: {}", e.getMessage());
        }
    }
}