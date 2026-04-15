package io.agentframework.core.observer;

import io.agentframework.core.AgentState;
import io.agentframework.core.result.AgentRunResult;

/**
 * Observability hook for agent lifecycle events.
 *
 * <p>Implement this interface to capture tracing, metrics, and audit data
 * at every stage of an agent run. The framework calls each method at the
 * appropriate lifecycle point — implementations must be non-blocking and
 * must never throw exceptions (swallow and log internally).
 *
 * <p>Multiple observers can be composed via {@link CompositeAgentObserver}.
 *
 * <p>Framework-provided implementations (in integration modules):
 * <ul>
 *   <li>{@code Slf4jAgentObserver} — structured JSON logging (agent-core)</li>
 *   <li>{@code MicrometerAgentObserver} — Micrometer metrics + OTel spans (agent-spring-ai)</li>
 * </ul>
 *
 * <p>Example custom observer:
 * <pre>
 * public class AuditObserver implements AgentObserver {
 *     {@literal @}Override
 *     public void onRunComplete(AgentState&lt;?&gt; state, AgentRunResult result) {
 *         auditLog.record(state.sessionId(), result.outcome(), result.totalTokens());
 *     }
 * }
 * </pre>
 */
public interface AgentObserver {

    /**
     * Called immediately before the agent loop begins processing user input.
     *
     * @param state       the current agent state at run start
     * @param userInput   the user message that triggered this run
     * @param traceId     unique identifier for this run's trace
     */
    default void onRunStart(AgentState<?> state, String userInput, String traceId) {}

    /**
     * Called after every step (LLM call or tool execution) completes.
     *
     * @param state the agent state after this step
     * @param step  the trace record for this step
     */
    default void onStep(AgentState<?> state, StepTrace step) {}

    /**
     * Called when the agent loop completes — success or failure.
     * This is always called, even if the run failed.
     *
     * @param state  the final agent state
     * @param result the complete run result with outcome and telemetry
     */
    default void onRunComplete(AgentState<?> state, AgentRunResult result) {}

    /**
     * Called when a phase transition occurs within a run.
     *
     * @param state     the agent state
     * @param fromPhase the phase being left
     * @param toPhase   the phase being entered
     */
    default void onPhaseTransition(AgentState<?> state,
                                   String fromPhase, String toPhase) {}

    /**
     * No-op observer. Use as a safe null-object default.
     */
    AgentObserver NOOP = new AgentObserver() {};
}