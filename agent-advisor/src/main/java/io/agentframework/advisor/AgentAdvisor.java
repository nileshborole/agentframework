
package io.agentframework.advisor;

import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;

/**
 * Middleware interface wrapping every LLM call in the agent loop.
 *
 * <p>Advisors intercept the request before it reaches the LLM and/or
 * the response before it returns to the runner. They are ordered by
 * {@link #order()} and composed into an AdvisorChain.
 *
 * <p>This pattern is directly derived from your existing codebase's
 * Spring AI {@code CallAdvisor} chain. The framework generalises it
 * to work with any LLM client — not just Spring AI.
 *
 * <p>Advisors are stateless and thread-safe. Per-call state is
 * carried in {@link AdvisorContext}.
 *
 * <p>Built-in advisors shipped with the framework:
 * <ul>
 *   <li>{@code ObservabilityAdvisor} — structured logging on every call</li>
 *   <li>{@code RetryAdvisor} — exponential backoff on transient failures</li>
 *   <li>{@code ConfidenceGuardAdvisor} — flags low-confidence responses</li>
 *   <li>{@code TokenBudgetAdvisor} — enforces per-run token limits</li>
 *   <li>{@code RateLimitAdvisor} — semaphore-based concurrent call cap</li>
 * </ul>
 *
 * <p>Domains add their own advisors for cross-cutting concerns:
 * persona updates, context injection, A/B testing, caching.
 */
public interface AgentAdvisor {

    /**
     * Intercepts the LLM call. Implementations may:
     * <ul>
     *   <li>Modify the request before passing it to the chain</li>
     *   <li>Call {@code chain.next()} to invoke the LLM (or next advisor)</li>
     *   <li>Inspect or modify the response before returning it</li>
     *   <li>Short-circuit the chain by returning without calling {@code chain.next()}</li>
     * </ul>
     *
     * @param request the LLM request (may be modified before passing to chain)
     * @param context per-call context carrying advisor parameters and state
     * @param chain   the remaining advisor chain; call {@code chain.next()} to continue
     * @return the final LLM result after all downstream advisors have run
     */
    AgentResult<LlmResponse> advise(LlmRequest request,
                                    AdvisorContext context,
                                    NextAdvisor chain);

    /**
     * The position of this advisor in the chain. Lower values run first
     * (outermost). Higher values run last (innermost, closest to LLM).
     *
     * <p>Recommended ordering from the framework:
     * <pre>
     *  10 — ObservabilityAdvisor  (outermost — logs everything)
     *  20 — RateLimitAdvisor      (throttle before any LLM work)
     *  30 — RetryAdvisor          (retry wraps the actual call)
     *  40 — TokenBudgetAdvisor    (check budget before each call)
     *  50 — ConfidenceGuardAdvisor (read response, flag low confidence)
     *  60 — domain advisors       (e.g. ContextInjectionAdvisor)
     *  70 — PersonaUpdateAdvisor  (innermost — mutates state from response)
     * </pre>
     */
    int order();

    /**
     * Human-readable name for this advisor. Used in logs and traces.
     */
    String name();
}