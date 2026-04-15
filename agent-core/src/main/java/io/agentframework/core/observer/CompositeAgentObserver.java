package io.agentframework.core.observer;

import io.agentframework.core.AgentState;
import io.agentframework.core.result.AgentRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Composite observer that delegates to multiple {@link AgentObserver}
 * instances, isolating each from failures in others.
 *
 * <p>Use this when you need both Micrometer metrics and structured logging:
 * <pre>
 * AgentObserver observer = new CompositeAgentObserver(List.of(
 *     new Slf4jAgentObserver(),
 *     new MicrometerAgentObserver(meterRegistry)
 * ));
 * </pre>
 *
 * <p>If any individual observer throws, the exception is caught, logged
 * at WARN level, and the remaining observers continue to execute.
 * This ensures one broken observer never silences another.
 */
public final class CompositeAgentObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger(CompositeAgentObserver.class);

    private final List<AgentObserver> observers;

    public CompositeAgentObserver(List<AgentObserver> observers) {
        this.observers = List.copyOf(observers);
    }

    @Override
    public void onRunStart(AgentState<?> state, String userInput, String traceId) {
        observers.forEach(o -> safely(o, "onRunStart",
                () -> o.onRunStart(state, userInput, traceId)));
    }

    @Override
    public void onStep(AgentState<?> state, StepTrace step) {
        observers.forEach(o -> safely(o, "onStep",
                () -> o.onStep(state, step)));
    }

    @Override
    public void onRunComplete(AgentState<?> state, AgentRunResult result) {
        observers.forEach(o -> safely(o, "onRunComplete",
                () -> o.onRunComplete(state, result)));
    }

    @Override
    public void onPhaseTransition(AgentState<?> state,
                                  String fromPhase, String toPhase) {
        observers.forEach(o -> safely(o, "onPhaseTransition",
                () -> o.onPhaseTransition(state, fromPhase, toPhase)));
    }

    private void safely(AgentObserver observer, String method, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[CompositeAgentObserver] Observer {} threw on {}: {}",
                    observer.getClass().getSimpleName(), method, e.getMessage(), e);
        }
    }
}