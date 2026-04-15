package io.agentframework.advisor.chain;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmClient;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;

import java.util.Comparator;
import java.util.List;

/**
 * Executes the ordered advisor chain, terminating with an actual LLM call.
 *
 * <p>Advisors are sorted by {@link AgentAdvisor#order()} ascending (lowest
 * order = outermost = runs first and last). The chain is immutable and
 * reusable — a new execution is created per LLM call via a recursive
 * position pointer.
 *
 * <p>Usage (typically called by {@code AbstractAgentRunner}):
 * <pre>
 * AdvisorChain chain = new AdvisorChain(advisors, llmClient);
 * AgentResult&lt;LlmResponse&gt; result = chain.execute(request, context);
 * </pre>
 */
public final class AdvisorChain {

    private final List<AgentAdvisor> sorted;
    private final LlmClient llmClient;

    /**
     * Creates a chain from the given advisors and terminal LLM client.
     *
     * @param advisors  the advisors to compose; sorted by {@code order()} ascending
     * @param llmClient the terminal LLM client called after all advisors
     */
    public AdvisorChain(List<AgentAdvisor> advisors, LlmClient llmClient) {
        this.sorted    = advisors.stream()
                .sorted(Comparator.comparingInt(AgentAdvisor::order))
                .toList();
        this.llmClient = llmClient;
    }

    /**
     * Executes the full chain for the given request and context.
     *
     * @param request the LLM request
     * @param context the per-call advisor context
     * @return the LLM result after all advisors have processed it
     */
    public AgentResult<LlmResponse> execute(LlmRequest request, AdvisorContext context) {
        return buildNext(0).next(request, context);
    }

    /** Returns the number of advisors in this chain. */
    public int size() { return sorted.size(); }

    /**
     * Builds the {@link NextAdvisor} for the given position.
     * If position is past the end of the advisor list, returns the terminal
     * LLM call. Otherwise wraps the advisor at {@code position} with the
     * remainder of the chain as its {@code NextAdvisor}.
     */
    private NextAdvisor buildNext(int position) {
        if (position >= sorted.size()) {
            return (req, ctx) -> llmClient.call(req);
        }
        AgentAdvisor current = sorted.get(position);
        NextAdvisor  rest    = buildNext(position + 1);
        return (req, ctx) -> current.advise(req, ctx, rest);
    }
}