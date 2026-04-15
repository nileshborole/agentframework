package io.agentframework.core.runner;

import io.agentframework.core.AgentState;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.result.AgentRunOutcome;
import io.agentframework.core.result.AgentRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Base class for multi-agent orchestration patterns.
 *
 * <p>Provides ready-made implementations of the parallel fan-out and
 * sequential pipeline patterns from Topics 6-9. Domains extend this
 * and implement only {@link #orchestrate}.
 *
 * <p>Example supervisor:
 * <pre>
 * public class ResearchSupervisor extends AbstractAgentOrchestrator&lt;ResearchData&gt; {
 *
 *     public ResearchSupervisor(Map&lt;String, AgentRunner&lt;?&gt;&gt; runners,
 *                                AgentObserver observer) {
 *         super(runners, observer);
 *     }
 *
 *     {@literal @}Override
 *     public AgentRunResult orchestrate(AgentState&lt;ResearchData&gt; state,
 *                                        String goal, AgentRunConfig config) {
 *         List&lt;String&gt; subtasks = decompose(goal);
 *         Map&lt;String, AgentRunResult&gt; results = runParallel(subtasks, config);
 *         return aggregateResults(state, goal, results, config);
 *     }
 *
 *     {@literal @}Override
 *     public String orchestratorName() { return "research-supervisor"; }
 * }
 * </pre>
 *
 * @param <S> the domain state type of the orchestrator itself
 */
public abstract class AbstractAgentOrchestrator<S> implements AgentOrchestrator<S> {

    private static final Logger log = LoggerFactory.getLogger(AbstractAgentOrchestrator.class);

    private final Map<String, AgentRunner<?>> subAgentMap;
    protected final AgentObserver observer;
    private final ExecutorService executor;

    protected AbstractAgentOrchestrator(Map<String, AgentRunner<?>> subAgents,
                                        AgentObserver observer) {
        this.subAgentMap = Collections.unmodifiableMap(new LinkedHashMap<>(subAgents));
        this.observer    = observer != null ? observer : AgentObserver.NOOP;
        this.executor    = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Map<String, AgentRunner<?>> subAgents() {
        return subAgentMap;
    }

    // ── Parallel fan-out ──────────────────────────────────────────────────

    /**
     * Runs multiple subtasks in parallel, one per sub-agent call.
     *
     * <p>Each subtask is matched to a sub-agent via a {@link SubtaskRouter}.
     * Results are collected regardless of individual failures —
     * failed sub-agents return an error {@link AgentRunResult}.
     *
     * @param subtasks list of task descriptions
     * @param router   selects which runner handles each subtask
     * @param config   run config applied to each sub-agent
     * @param timeout  max wait time for the entire parallel batch
     * @return map of subtask → result (never null, always same size as subtasks)
     */
    protected <T> Map<String, AgentRunResult> runParallel(
            List<String> subtasks,
            SubtaskRouter<T> router,
            AgentRunConfig config,
            Duration timeout) {

        Map<String, CompletableFuture<AgentRunResult>> futures = new LinkedHashMap<>();

        for (String task : subtasks) {
            CompletableFuture<AgentRunResult> future = CompletableFuture
                    .supplyAsync(() -> {
                        SubtaskAssignment<T> assignment = router.assign(task);
                        if (assignment == null) {
                            return AgentRunResult.failure(
                                    "orchestrator", AgentRunOutcome.ERROR,
                                    "No runner found for task: " + task,
                                    0, 0, 0, Instant.now(), Instant.now());
                        }
                        log.info("[{}] Parallel task → {}: {}",
                                orchestratorName(), assignment.runnerName(), task);
                        return assignment.runner().run(
                                assignment.state(), task,
                                assignment.missingFields(), config);
                    }, executor)
                    .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .exceptionally(ex -> AgentRunResult.failure(
                            "orchestrator",
                            ex instanceof TimeoutException
                                    ? AgentRunOutcome.TIMEOUT
                                    : AgentRunOutcome.ERROR,
                            ex.getMessage(),
                            0, 0, 0, Instant.now(), Instant.now()));

            futures.put(task, future);
        }

        // Gather all results — never blocks indefinitely
        Map<String, AgentRunResult> results = new LinkedHashMap<>();
        futures.forEach((task, future) -> {
            try {
                results.put(task, future.join());
            } catch (Exception e) {
                results.put(task, AgentRunResult.failure(
                        "orchestrator", AgentRunOutcome.ERROR,
                        "Join failed: " + e.getMessage(),
                        0, 0, 0, Instant.now(), Instant.now()));
            }
        });

        long passed = results.values().stream().filter(AgentRunResult::isSuccess).count();
        log.info("[{}] Parallel batch complete: {}/{} succeeded",
                orchestratorName(), passed, subtasks.size());

        return results;
    }

    // ── Sequential pipeline ───────────────────────────────────────────────

    /**
     * Runs sub-agents sequentially, passing the output of each as a
     * {@link AgentHandoff} to the next.
     *
     * <p>If any stage fails and {@code abortOnFailure} is true, the pipeline
     * stops and returns the failure. Otherwise failed stages are noted in the
     * handoff and the pipeline continues with available data.
     *
     * @param stages        ordered list of pipeline stages
     * @param config        run config applied to each stage
     * @param abortOnFailure whether to stop the pipeline on the first failure
     * @return result of the final stage, or the first failure
     */
    protected AgentRunResult runSequential(
            List<PipelineStage<S>> stages,
            AgentState<S> orchestratorState,
            String userGoal,
            AgentRunConfig config,
            boolean abortOnFailure) {

        AgentHandoff.Builder handoff = AgentHandoff.builder()
                .sessionId(orchestratorState.sessionId())
                .fromAgent(orchestratorName())
                .context(userGoal);

        AgentRunResult lastResult = null;

        for (PipelineStage<S> stage : stages) {
            handoff.toAgent(stage.runnerName());
            String taskPrompt = stage.buildTask(orchestratorState, handoff.build());

            log.info("[{}] Pipeline stage → {}: {}",
                    orchestratorName(), stage.runnerName(),
                    taskPrompt.substring(0, Math.min(80, taskPrompt.length())));

            lastResult = stage.runner().run(
                    stage.state(orchestratorState),
                    taskPrompt,
                    stage.missingFields(orchestratorState),
                    config);

            if (lastResult.isSuccess()) {
                handoff.finding(stage.runnerName(),
                        lastResult.outputIfPresent().orElse(""));
            } else {
                log.warn("[{}] Pipeline stage '{}' failed: {}",
                        orchestratorName(), stage.runnerName(),
                        Optional.ofNullable(lastResult.failureReason()).filter(r -> !r.isBlank()).orElse("unknown"));
                if (abortOnFailure) return lastResult;
                handoff.finding(stage.runnerName(),
                        "ERROR: " + Optional.ofNullable(lastResult.failureReason()).filter(r -> !r.isBlank()).orElse("failed"));
            }
        }

        return lastResult != null ? lastResult
                : AgentRunResult.failure(orchestratorState.sessionId(),
                AgentRunOutcome.ERROR, "No pipeline stages executed",
                0, 0, 0, Instant.now(), Instant.now());
    }

    // ── Result summarisation ──────────────────────────────────────────────

    /**
     * Separates parallel results into successes and failures, for
     * aggregation logic in domain orchestrators.
     */
    protected Map<String, String> successOutputs(Map<String, AgentRunResult> results) {
        return results.entrySet().stream()
                .filter(e -> e.getValue().isSuccess())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().outputIfPresent().orElse("")));
    }

    protected Map<String, String> failureReasons(Map<String, AgentRunResult> results) {
        return results.entrySet().stream()
                .filter(e -> !e.getValue().isSuccess())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Optional.ofNullable(e.getValue().failureReason())
                                .filter(r -> !r.isBlank()).orElse("unknown")));
    }

    // ── Supporting types ──────────────────────────────────────────────────

    /**
     * Routes a subtask string to the runner that should handle it.
     */
    @FunctionalInterface
    public interface SubtaskRouter<T> {
        SubtaskAssignment<T> assign(String task);
    }

    /**
     * The result of routing a subtask to a specific runner.
     */
    public record SubtaskAssignment<T>(
            String runnerName,
            AgentRunner<T> runner,
            AgentState<T> state,
            List<String> missingFields
    ) {}

    /**
     * A single stage in a sequential pipeline.
     */
    public interface PipelineStage<S> {
        String runnerName();
        AgentRunner<S> runner();
        AgentState<S> state(AgentState<S> orchestratorState);
        String buildTask(AgentState<S> orchestratorState, AgentHandoff priorHandoff);
        default List<String> missingFields(AgentState<S> orchestratorState) {
            return List.of();
        }
    }

    /** Alias for Duration — avoids importing java.time in callers. */
    protected static java.time.Duration timeout(long seconds) {
        return java.time.Duration.ofSeconds(seconds);
    }
}