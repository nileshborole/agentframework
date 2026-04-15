package io.agentframework.core.runner;

import io.agentframework.core.*;
import io.agentframework.core.TestFixtures.*;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.result.AgentResult;
import io.agentframework.core.result.AgentRunOutcome;
import io.agentframework.core.result.AgentRunResult;
import io.agentframework.core.tool.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the abstract loop mechanics without any real LLM.
 *
 * <p>A stub runner returns pre-configured decisions so we can verify
 * every branch of the loop: RESPOND, CALL_TOOL (continue / pause / terminate),
 * TRANSITION_PHASE, max steps, low confidence, and error handling.
 */
class AbstractAgentRunnerTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Builds a runner whose decisions come from a fixed queue.
     * Once the queue is exhausted, subsequent calls return RESPOND with "done".
     */
    private StubRunner runner(ToolRegistry registry, AgentDecision... decisions) {
        return new StubRunner(registry, new ArrayDeque<>(List.of(decisions)));
    }

    private ToolRegistry emptyRegistry() {
        return new ToolRegistry();
    }

    private ToolRegistry registryWith(AgentTool... tools) {
        return new ToolRegistry(List.of(tools));
    }

    private AgentDecision respond(String text) {
        return new SimpleDecision(new StepType.Respond(), text, 0.9);
    }

    private AgentDecision callTool(String name, Map<String, Object> input) {
        return new SimpleDecision(new StepType.CallTool(name, input), null, 0.9);
    }

    private AgentDecision transition(TestPhase phase) {
        return new SimpleDecision(new StepType.TransitionPhase(phase), null, 0.9);
    }

    private AgentDecision lowConfidence() {
        return new SimpleDecision(new StepType.Respond(), "uncertain", 0.1);
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void respond_returnsSuccessWithReply() {
        var state  = TestFixtures.state("s1");
        var result = runner(emptyRegistry(), respond("Hello user"))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertTrue(result.isSuccess());
        assertEquals("Hello user", result.output());
        assertEquals(AgentRunOutcome.SUCCESS, result.outcome());
    }

    @Test
    void callTool_continue_loopsAndResponds() {
        var tool = silentTool("greet", ToolResult.continueWith("tool result"));
        var state = TestFixtures.state("s1");
        var result = runner(registryWith(tool),
                callTool("greet", Map.of()),
                respond("After tool: done"))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertTrue(result.isSuccess());
        assertEquals("After tool: done", result.output());
    }

    @Test
    void callTool_pauseForUser_stopsLoop() {
        var tool = silentTool("ask_user",
                ToolResult.pauseForUser("asking user", "user-payload"));
        var state = TestFixtures.state("s1");
        var result = runner(registryWith(tool), callTool("ask_user", Map.of()))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertTrue(result.isSuccess());
        assertEquals(1, result.stepsExecuted());
    }

    @Test
    void callTool_terminate_stopsLoopImmediately() {
        var tool = silentTool("final_tool",
                ToolResult.terminate("final answer", null));
        var state = TestFixtures.state("s1");
        var result = runner(registryWith(tool), callTool("final_tool", Map.of()))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertTrue(result.isSuccess());
        assertEquals("final answer", result.output());
    }

    @Test
    void unknownTool_returnsErrorObservationAndLoopContinues() {
        var state = TestFixtures.state("s1");
        var result = runner(emptyRegistry(),
                callTool("nonexistent_tool", Map.of()),
                respond("recovered"))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertTrue(result.isSuccess());
        assertEquals("recovered", result.output());
    }

    @Test
    void transitionPhase_updatesStateAndContinues() {
        var state = TestFixtures.state("s1", TestPhase.INITIAL);
        runner(emptyRegistry(),
                transition(TestPhase.PROCESSING),
                respond("now in processing"))
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertEquals(TestPhase.PROCESSING, state.currentPhase());
    }

    @Test
    void lowConfidence_returnsFallbackAndSucceeds() {
        var state  = TestFixtures.state("s1");
        var result = runner(emptyRegistry(), lowConfidence())
                .run(state, "hi", List.of(),
                        AgentRunConfig.defaults().withConfidenceThreshold(0.5));

        assertTrue(result.isSuccess());
        assertTrue(result.output().contains("fallback"));
    }

    @Test
    void maxSteps_returnsStepLimitOutcome() {
        // All decisions are CALL_TOOL on an unknown tool — loop keeps going
        AgentDecision[] infiniteTools = new AgentDecision[5];
        Arrays.fill(infiniteTools, callTool("nonexistent", Map.of()));

        var state  = TestFixtures.state("s1");
        var config = AgentRunConfig.defaults().withMaxSteps(3);
        var result = runner(emptyRegistry(), infiniteTools)
                .run(state, "hi", List.of(), config);

        assertFalse(result.isSuccess());
        assertEquals(AgentRunOutcome.STEP_LIMIT_REACHED, result.outcome());
        assertEquals(3, result.stepsExecuted());
    }

    @Test
    void emptyContext_returnsErrorImmediately() {
        var state  = TestFixtures.state("s1");
        var result = new EmptyContextRunner(emptyRegistry())
                .run(state, "hi", List.of(), AgentRunConfig.defaults());

        assertFalse(result.isSuccess());
        assertEquals(AgentRunOutcome.ERROR, result.outcome());
    }

    // ── Stub implementations ──────────────────────────────────────────────

    record SimpleDecision(StepType stepType, String reply, double confidence)
            implements AgentDecision {}

    static AgentTool silentTool(String name, ToolResult result) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public List<ToolInputSpec> inputs() { return List.of(); }
            @Override public ToolResult execute(ToolContext<?> ctx) { return result; }
        };
    }

    static class StubRunner extends AbstractAgentRunner<TestDomainData, String> {

        private final Queue<AgentDecision> decisions;

        StubRunner(ToolRegistry registry, Queue<AgentDecision> decisions) {
            super(registry, AgentObserver.NOOP);
            this.decisions = decisions;
        }

        @Override
        public String runnerName() { return "stub-runner"; }

        @Override
        protected List<String> buildContext(AgentState<TestDomainData> state,
                                            String userInput,
                                            List<String> missingFields) {
            return List.of("system prompt", "user: " + userInput);
        }

        @Override
        protected AgentResult<String> callLlm(AgentState<TestDomainData> state,
                                              List<String> context,
                                              String traceId, int step) {
            return AgentResult.success("raw-llm-response-step-" + step);
        }

        @Override
        protected AgentResult<AgentDecision> parseDecision(String rawResponse) {
            AgentDecision next = decisions.poll();
            if (next == null) next = new SimpleDecision(new StepType.Respond(), "done", 1.0);
            return AgentResult.success(next);
        }

        @Override
        protected String buildFallbackOutput(AgentState<TestDomainData> state, String reason) {
            return "fallback: " + reason;
        }
    }

    static class EmptyContextRunner extends AbstractAgentRunner<TestDomainData, String> {

        EmptyContextRunner(ToolRegistry registry) {
            super(registry, AgentObserver.NOOP);
        }

        @Override public String runnerName() { return "empty-context-runner"; }

        @Override
        protected List<String> buildContext(AgentState<TestDomainData> state,
                                            String userInput,
                                            List<String> missingFields) {
            return List.of(); // Returns empty — should trigger immediate error
        }

        @Override
        protected AgentResult<String> callLlm(AgentState<TestDomainData> state,
                                              List<String> context,
                                              String traceId, int step) {
            return AgentResult.success("should not reach here");
        }

        @Override
        protected AgentResult<AgentDecision> parseDecision(String raw) {
            return AgentResult.success(new SimpleDecision(
                    new StepType.Respond(), "done", 1.0));
        }

        @Override
        protected String buildFallbackOutput(AgentState<TestDomainData> state, String reason) {
            return "fallback";
        }
    }
}