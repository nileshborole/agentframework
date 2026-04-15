package io.agentframework.core.runner;

import io.agentframework.core.AgentState;
import io.agentframework.core.result.AgentRunResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The primary execution contract for a single agent — the generic,
 * domain-agnostic agent loop.
 *
 * <p>{@code S} is the domain state type. The framework provides the
 * loop mechanics (step counting, token tracking, error boundaries,
 * observability hooks); the domain provides the LLM client, context
 * builder, decision parser, and tools.
 *
 * <p>Implementations follow the ReAct pattern taught in the course:
 * <ol>
 *   <li>Build prompt context from state + memory</li>
 *   <li>Call LLM → parse into {@code AgentDecision}</li>
 *   <li>Route on {@code StepType}: respond, call tool, or transition phase</li>
 *   <li>Record step trace via {@code AgentObserver}</li>
 *   <li>Repeat up to {@code maxSteps}</li>
 * </ol>
 *
 * <p>Domain implementations extend {@code AbstractAgentRunner} rather than
 * implementing this interface directly — the abstract class handles all
 * the boilerplate (step counting, token budget, timeout, observer calls)
 * leaving domains to implement only:
 * <ul>
 *   <li>{@code callLlm(state, context)} — LLM invocation</li>
 *   <li>{@code parseDecision(rawResponse)} — structured output parsing</li>
 *   <li>{@code buildFallbackResult(state, reason)} — error response text</li>
 * </ul>
 *
 * @param <S> the domain state type
 */
public interface AgentRunner<S> {

    /**
     * Executes the agent loop synchronously for one user turn.
     *
     * <p>Always returns — never throws. All errors are captured in
     * {@link AgentRunResult} with an appropriate {@code AgentRunOutcome}.
     *
     * @param state        the current agent session state (may be mutated during run)
     * @param userInput    the user's message for this turn
     * @param missingFields domain-computed list of fields still needed at this phase
     * @param config       run-time limits and thresholds for this execution
     * @return the complete result of this run
     */
    AgentRunResult run(AgentState<S> state,
                       String userInput,
                       List<String> missingFields,
                       AgentRunConfig config);

    /**
     * Executes the agent loop with default configuration.
     *
     * @param state        the current agent session state
     * @param userInput    the user's message for this turn
     * @param missingFields domain-computed list of fields still needed
     * @return the complete result of this run
     */
    default AgentRunResult run(AgentState<S> state,
                               String userInput,
                               List<String> missingFields) {
        return run(state, userInput, missingFields, AgentRunConfig.defaults());
    }

    /**
     * Executes the agent loop asynchronously.
     *
     * <p>Default implementation wraps {@link #run} in a
     * {@code CompletableFuture}. Override for reactive or virtual-thread
     * implementations.
     *
     * @param state        the current agent session state
     * @param userInput    the user's message for this turn
     * @param missingFields domain-computed list of fields still needed
     * @param config       run-time limits and thresholds
     * @return a future that completes with the run result
     */
    default CompletableFuture<AgentRunResult> runAsync(AgentState<S> state,
                                                       String userInput,
                                                       List<String> missingFields,
                                                       AgentRunConfig config) {
        return CompletableFuture.supplyAsync(
                () -> run(state, userInput, missingFields, config));
    }

    /**
     * Returns the unique name of this runner. Used in logs and traces
     * to identify which runner handled a given session.
     *
     * <p>Example: {@code "trip-planning-runner"}, {@code "support-runner"}
     */
    String runnerName();
}