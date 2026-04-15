package io.agentframework.advisor.builtin;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces a per-run token budget by tracking cumulative token usage
 * across all LLM calls within a single agent run.
 *
 * <p>When the budget is exceeded, subsequent LLM calls are short-circuited
 * with a {@code BUDGET_EXCEEDED} failure result. The runner detects this
 * and terminates the loop with {@code AgentRunOutcome.STEP_LIMIT_REACHED}.
 *
 * <p>Token counter is carried via {@link AdvisorContext} attributes so it
 * accumulates across multiple calls within the same run:
 * <pre>
 * // Create one counter per agent run and pass it in the context
 * AtomicInteger tokenCounter = new AtomicInteger(0);
 * AdvisorContext ctx = AdvisorContext.builder()
 *     .param(TokenBudgetAdvisor.BUDGET_KEY, 100_000)
 *     .param(TokenBudgetAdvisor.COUNTER_KEY, tokenCounter)
 *     .build();
 * </pre>
 *
 * <p>Order: 40 — after retry (30), before confidence guard (50).
 */
public class TokenBudgetAdvisor implements AgentAdvisor {

    public static final String BUDGET_KEY  = "token.budget";
    public static final String COUNTER_KEY = "token.counter";
    public static final String BUDGET_EXCEEDED_ATTR = "token.budgetExceeded";

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetAdvisor.class);
    private static final int    ORDER = 40;
    private static final int    DEFAULT_BUDGET = 100_000;
    private static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";

    @Override
    public String name()  { return "TokenBudgetAdvisor"; }

    @Override
    public int order()    { return ORDER; }

    @Override
    public AgentResult<LlmResponse> advise(LlmRequest request,
                                           AdvisorContext context,
                                           NextAdvisor chain) {
        int budget = (int) context.paramDouble(BUDGET_KEY, DEFAULT_BUDGET);
        AtomicInteger counter = context.param(COUNTER_KEY, AtomicInteger.class)
                .orElse(new AtomicInteger(0));

        // Pre-call: check if we're already over budget
        if (counter.get() >= budget) {
            log.warn("[TokenBudgetAdvisor] sessionId={} token budget exhausted: {}/{}",
                    context.sessionId(), counter.get(), budget);
            context.setAttribute(BUDGET_EXCEEDED_ATTR, true);
            return AgentResult.failure(BUDGET_EXCEEDED);
        }

        // Proceed with the call
        AgentResult<LlmResponse> result = chain.next(request, context);

        // Post-call: accumulate actual tokens used
        if (result.isSuccess()) {
            LlmResponse response = result.value().get();
            int used = counter.addAndGet(response.totalTokens());
            log.debug("[TokenBudgetAdvisor] sessionId={} tokens used: {}/{} (+{})",
                    context.sessionId(), used, budget, response.totalTokens());

            // Flag budget exceeded after this call (for next iteration check)
            if (used >= budget) {
                log.warn("[TokenBudgetAdvisor] sessionId={} token budget reached: {}/{}",
                        context.sessionId(), used, budget);
                context.setAttribute(BUDGET_EXCEEDED_ATTR, true);
            }
        }

        return result;
    }

}