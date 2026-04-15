package io.agentframework.advisor.builtin;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Outermost advisor — emits structured log events on every LLM call.
 *
 * <p>Generic replacement for your existing {@code ObservabilityAdvisor}.
 * No domain types, no Spring AI coupling. Works with any {@link io.agentframework.llm.LlmClient}.
 *
 * <p>Emits two events per call:
 * <ul>
 *   <li>{@code AGENT_LLM_START} — before the LLM is called (includes prompt name, step, estimated tokens)</li>
 *   <li>{@code AGENT_LLM_DONE} — after the response (includes duration, actual token counts, finish reason)</li>
 * </ul>
 *
 * <p>Callers set observability context via {@link AdvisorContext}:
 * <pre>
 * AdvisorContext ctx = AdvisorContext.builder()
 *     .param(ObservabilityAdvisor.PROMPT_NAME_KEY, "explore_step_0")
 *     .traceId(traceId)
 *     .stepNumber(step)
 *     .state(agentState)
 *     .build();
 * </pre>
 *
 * <p>Order: 10 — outermost. All other advisors run inside this one.
 */
public class ObservabilityAdvisor implements AgentAdvisor {

    public static final String PROMPT_NAME_KEY = "promptName";

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAdvisor.class);
    private static final int ORDER = 10;

    @Override
    public String name() { return "ObservabilityAdvisor"; }

    @Override
    public int order() { return ORDER; }

    @Override
    public AgentResult<LlmResponse> advise(LlmRequest request,
                                           AdvisorContext context,
                                           NextAdvisor chain) {
        String promptName = context.paramString(PROMPT_NAME_KEY, "unknown");
        String sessionId  = context.sessionId();
        String phase      = context.phaseId();
        String traceId    = context.traceId();
        int step          = context.stepNumber();

        int estimatedInputTokens = request.messages().stream()
                .mapToInt(m -> m.content() != null ? m.content().length() / 4 : 0)
                .sum();

        log.info("event=AGENT_LLM_START promptName={} sessionId={} phase={} "
                        + "traceId={} step={} estimatedInputTokens={}",
                promptName, sessionId, phase, traceId, step, estimatedInputTokens);

        Instant start = Instant.now();

        AgentResult<LlmResponse> result = chain.next(request, context);

        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

        if (result.isSuccess()) {
            LlmResponse response = result.value().get();
            log.info("event=AGENT_LLM_DONE promptName={} sessionId={} phase={} "
                            + "traceId={} step={} durationMs={} inputTokens={} outputTokens={} "
                            + "totalTokens={} finishReason={} model={}",
                    promptName, sessionId, phase, traceId, step,
                    durationMs, response.inputTokens(), response.outputTokens(),
                    response.totalTokens(), response.finishReason(), response.model());
        } else {
            log.warn("event=AGENT_LLM_FAILED promptName={} sessionId={} traceId={} "
                            + "step={} durationMs={} reason={}",
                    promptName, sessionId, traceId, step, durationMs,
                    result.failureReason().orElse("unknown"));
        }

        return result;
    }

}