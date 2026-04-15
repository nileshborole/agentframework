package io.agentframework.core.observer;

import io.agentframework.core.AgentState;
import io.agentframework.core.result.AgentRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Structured logging observer using SLF4J.
 *
 * <p>Emits one log line per lifecycle event with key=value pairs
 * compatible with Logstash, Splunk, and Datadog log parsers.
 * Zero additional dependencies beyond SLF4J.
 *
 * <p>This is the default observer bundled with {@code agent-core}.
 * For Micrometer metrics and OpenTelemetry spans, use
 * {@code MicrometerAgentObserver} from the {@code agent-spring-ai} module.
 */
public class Slf4jAgentObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger(Slf4jAgentObserver.class);

    @Override
    public void onRunStart(AgentState<?> state, String userInput, String traceId) {
        log.info("event=AGENT_RUN_START sessionId={} traceId={} phase={} inputLen={}",
                state.sessionId(),
                traceId,
                state.currentPhase().id(),
                userInput != null ? userInput.length() : 0);
    }

    @Override
    public void onStep(AgentState<?> state, StepTrace step) {
        if (step.isLlmCall()) {
            log.info("event=LLM_CALL sessionId={} traceId={} step={} "
                            + "inputTokens={} outputTokens={} durationMs={} success={}",
                    step.sessionId(), step.traceId(), step.stepNumber(),
                    step.inputTokens(), step.outputTokens(),
                    step.duration().toMillis(), step.success());
        } else if (step.isToolCall()) {
            if (step.success()) {
                log.info("event=TOOL_CALL sessionId={} traceId={} step={} "
                                + "tool={} durationMs={} success=true",
                        step.sessionId(), step.traceId(), step.stepNumber(),
                        step.name(), step.duration().toMillis());
            } else {
                log.warn("event=TOOL_CALL_FAILED sessionId={} traceId={} step={} "
                                + "tool={} durationMs={} error={}",
                        step.sessionId(), step.traceId(), step.stepNumber(),
                        step.name(), step.duration().toMillis(), step.errorMessage());
            }
        }
    }

    @Override
    public void onRunComplete(AgentState<?> state, AgentRunResult result) {
        if (result.isSuccess()) {
            log.info("event=AGENT_RUN_COMPLETE sessionId={} outcome={} "
                            + "steps={} totalTokens={} durationMs={} costUsd={}",
                    result.sessionId(), result.outcome(),
                    result.stepsExecuted(), result.totalTokens(),
                    result.duration().toMillis(),
                    String.format("%.4f", result.estimatedCostUsd()));
        } else {
            log.warn("event=AGENT_RUN_FAILED sessionId={} outcome={} "
                            + "reason={} steps={} totalTokens={} durationMs={}",
                    result.sessionId(), result.outcome(),
                    Optional.ofNullable(result.failureReason()).filter(r -> !r.isBlank()).orElse("unknown"),
                    result.stepsExecuted(), result.totalTokens(),
                    result.duration().toMillis());
        }
    }

    @Override
    public void onPhaseTransition(AgentState<?> state,
                                  String fromPhase, String toPhase) {
        log.info("event=PHASE_TRANSITION sessionId={} from={} to={}",
                state.sessionId(), fromPhase, toPhase);
    }
}