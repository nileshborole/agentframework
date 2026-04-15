package io.agentframework.core.runner;

import io.agentframework.core.AgentState;
import io.agentframework.core.result.AgentRunResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates multiple {@link AgentRunner} instances to accomplish
 * tasks that exceed a single agent's scope.
 *
 * <p>Orchestrators implement higher-level patterns from the course:
 * <ul>
 *   <li><b>Supervisor</b> — delegates subtasks to specialised runners</li>
 *   <li><b>Sequential pipeline</b> — chains runners, passing {@link AgentHandoff}s</li>
 *   <li><b>Parallel fan-out</b> — runs multiple agents concurrently</li>
 * </ul>
 *
 * <p>Domain implementations extend {@code AbstractAgentOrchestrator}
 * which provides parallel execution utilities, handoff builders,
 * and partial-failure handling.
 *
 * <p>Example supervisor implementation:
 * <pre>
 * public class ResearchOrchestrator extends AbstractAgentOrchestrator&lt;ResearchData&gt; {
 *     {@literal @}Override
 *     public AgentRunResult orchestrate(AgentState&lt;ResearchData&gt; state,
 *                                        String userGoal,
 *                                        AgentRunConfig config) {
 *         List&lt;String&gt; subtasks = decompose(userGoal);
 *         Map&lt;String, AgentRunResult&gt; results = runParallel(subtasks, config);
 *         return aggregate(state, userGoal, results);
 *     }
 * }
 * </pre>
 *
 * @param <S> the domain state type
 */
public interface AgentOrchestrator<S> {

    /**
     * Orchestrates one or more agent runs to fulfil the user's goal.
     *
     * <p>Always returns — never throws. Partial failures from sub-agents
     * are captured in individual {@link AgentRunResult}s and factored
     * into the final result.
     *
     * @param state     the orchestrator's own session state
     * @param userGoal  the high-level goal to accomplish
     * @param config    run-time limits applied to each sub-agent run
     * @return the final aggregated result
     */
    AgentRunResult orchestrate(AgentState<S> state,
                               String userGoal,
                               AgentRunConfig config);

    /**
     * Orchestrates with default configuration.
     */
    default AgentRunResult orchestrate(AgentState<S> state, String userGoal) {
        return orchestrate(state, userGoal, AgentRunConfig.defaults());
    }

    /**
     * Orchestrates asynchronously. Default wraps {@link #orchestrate}.
     */
    default CompletableFuture<AgentRunResult> orchestrateAsync(AgentState<S> state,
                                                               String userGoal,
                                                               AgentRunConfig config) {
        return CompletableFuture.supplyAsync(() -> orchestrate(state, userGoal, config));
    }

    /**
     * Returns the name of this orchestrator, used in logs and traces.
     */
    String orchestratorName();

    /**
     * Returns the sub-agents this orchestrator can delegate to,
     * keyed by their runner names. Used for auto-wiring and introspection.
     */
    Map<String, AgentRunner<?>> subAgents();

    /**
     * Returns the runners registered under this orchestrator
     * as an ordered list for sequential pipelines.
     */
    default List<AgentRunner<?>> subAgentList() {
        return List.copyOf(subAgents().values());
    }
}