package io.agentframework.advisor;

import io.agentframework.advisor.chain.AdvisorChain;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvisorChainTest {

    private static final LlmRequest DUMMY_REQUEST =
            LlmRequest.of("test", List.of(LlmMessage.user("hello")));

    private static final AdvisorContext DUMMY_CONTEXT =
            AdvisorContext.builder().traceId("trace-1").stepNumber(0).build();

    // ── Ordering ──────────────────────────────────────────────────────────

    @Test
    void advisors_executeInOrderAscending() {
        List<Integer> executionOrder = new ArrayList<>();
        var chain = new AdvisorChain(List.of(
                recordingAdvisor(30, executionOrder),
                recordingAdvisor(10, executionOrder),
                recordingAdvisor(20, executionOrder)
        ), successLlmClient("response"));

        chain.execute(DUMMY_REQUEST, DUMMY_CONTEXT);

        assertEquals(List.of(10, 20, 30), executionOrder);
    }

    @Test
    void emptyAdvisorList_callsLlmDirectly() {
        var chain = new AdvisorChain(List.of(), successLlmClient("direct"));
        var result = chain.execute(DUMMY_REQUEST, DUMMY_CONTEXT);
        assertTrue(result.isSuccess());
        assertEquals("direct", result.value().get().content());
    }

    // ── Short-circuit ──────────────────────────────────────────────────────

    @Test
    void advisor_canShortCircuit_withoutCallingLlm() {
        boolean[] llmCalled = {false};
        var chain = new AdvisorChain(
                List.of(shortCircuitAdvisor("blocked")),
                new LlmClient() {
                    @Override
                    public AgentResult<LlmResponse> call(LlmRequest request) {
                        llmCalled[0] = true;
                        return AgentResult.success(LlmResponse.endTurn("should not reach", 0, 0, "m"));
                    }

                    @Override
                    public String providerName() {
                        return "";
                    }
                });

        var result = chain.execute(DUMMY_REQUEST, DUMMY_CONTEXT);
        assertTrue(result.isSuccess());
        assertEquals("blocked", result.value().get().content());
        assertFalse(llmCalled[0]);
    }

    // ── Request modification ───────────────────────────────────────────────

    @Test
    void advisor_canModifyRequestBeforePassingDown() {
        String[] capturedContent = {null};
        var chain = new AdvisorChain(
                List.of(prefixAdvisor("SYSTEM: ")),
                new LlmClient() {
                    @Override
                    public AgentResult<LlmResponse> call(LlmRequest request) {
                        capturedContent[0] = request.messages().get(0).content();
                        return AgentResult.success(LlmResponse.endTurn("ok", 0, 0, "m"));
                    }

                    @Override
                    public String providerName() {
                        return "";
                    }
                });

        chain.execute(DUMMY_REQUEST, DUMMY_CONTEXT);
        assertEquals("SYSTEM: hello", capturedContent[0]);
    }

    // ── Failure propagation ────────────────────────────────────────────────

    @Test
    void llmFailure_propagatesThroughChain() {
        var chain = new AdvisorChain(
                List.of(passthroughAdvisor(10)),
                new LlmClient() {
                    @Override
                    public AgentResult<LlmResponse> call(LlmRequest request) {
                        return AgentResult.failure("network error");
                    }

                    @Override
                    public String providerName() {
                        return "";
                    }
                });

        var result = chain.execute(DUMMY_REQUEST, DUMMY_CONTEXT);
        assertTrue(result.isFailure());
        assertEquals("network error", result.failureReason().get());
    }

    // ── Stub builders ──────────────────────────────────────────────────────

    private AgentAdvisor recordingAdvisor(int order, List<Integer> log) {
        return new AgentAdvisor() {
            @Override public String name() { return "recorder-" + order; }
            @Override public int order() { return order; }
            @Override public AgentResult<LlmResponse> advise(LlmRequest req,
                                                             AdvisorContext ctx,
                                                             NextAdvisor chain) {
                log.add(order);
                return chain.next(req, ctx);
            }
        };
    }

    private AgentAdvisor shortCircuitAdvisor(String content) {
        return new AgentAdvisor() {
            @Override public String name() { return "short-circuit"; }
            @Override public int order() { return 1; }
            @Override public AgentResult<LlmResponse> advise(LlmRequest req,
                                                             AdvisorContext ctx,
                                                             NextAdvisor chain) {
                return AgentResult.success(LlmResponse.endTurn(content, 0, 0, "stub"));
            }
        };
    }

    private AgentAdvisor prefixAdvisor(String prefix) {
        return new AgentAdvisor() {
            @Override public String name() { return "prefixer"; }
            @Override public int order() { return 1; }
            @Override public AgentResult<LlmResponse> advise(LlmRequest req,
                                                             AdvisorContext ctx,
                                                             NextAdvisor chain) {
                LlmMessage first = req.messages().get(0);
                LlmMessage modified = LlmMessage.user(prefix + first.content());
                LlmRequest modified_req = LlmRequest.of(req.promptName(),
                        List.of(modified), req.config());
                return chain.next(modified_req, ctx);
            }
        };
    }

    private AgentAdvisor passthroughAdvisor(int order) {
        return new AgentAdvisor() {
            @Override public String name() { return "passthrough-" + order; }
            @Override public int order() { return order; }
            @Override public AgentResult<LlmResponse> advise(LlmRequest req,
                                                             AdvisorContext ctx,
                                                             NextAdvisor chain) {
                return chain.next(req, ctx);
            }
        };
    }

    @FunctionalInterface
    interface StubLlmClient {
        AgentResult<LlmResponse> call(LlmRequest request);
    }

    private io.agentframework.llm.LlmClient successLlmClient(String content) {
        return new io.agentframework.llm.LlmClient() {
            @Override public AgentResult<LlmResponse> call(LlmRequest req) {
                return AgentResult.success(LlmResponse.endTurn(content, 10, 20, "stub-model"));
            }
            @Override public String providerName() { return "stub"; }
        };
    }
}