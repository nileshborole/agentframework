package io.agentframework.advisor.builtin;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries transient LLM failures with exponential backoff.
 *
 * <p>Handles the most common production failure: rate limits and
 * momentary provider unavailability. Non-retryable failures
 * (e.g. invalid requests, auth errors) pass through immediately.
 *
 * <p>Configuration via {@link AdvisorContext} params:
 * <pre>
 * AdvisorContext ctx = AdvisorContext.builder()
 *     .param(RetryAdvisor.MAX_RETRIES_KEY, 3)
 *     .param(RetryAdvisor.BASE_DELAY_MS_KEY, 1000L)
 *     .build();
 * </pre>
 *
 * <p>Default: 3 retries, 1s base delay (doubles each retry: 1s, 2s, 4s),
 * capped at 30s.
 *
 * <p>Order: 30 — runs after observability (10) and rate limiting (20),
 * wrapping the actual LLM call.
 */
public class RetryAdvisor implements AgentAdvisor {

    public static final String MAX_RETRIES_KEY   = "retry.maxRetries";
    public static final String BASE_DELAY_MS_KEY = "retry.baseDelayMs";

    private static final Logger log = LoggerFactory.getLogger(RetryAdvisor.class);
    private static final int ORDER = 30;

    private static final int    DEFAULT_MAX_RETRIES  = 3;
    private static final long   DEFAULT_BASE_DELAY   = 1_000L;
    private static final long   MAX_DELAY_MS         = 30_000L;

    @Override
    public String name()  { return "RetryAdvisor"; }

    @Override
    public int order()    { return ORDER; }

    @Override
    public AgentResult<LlmResponse> advise(LlmRequest request,
                                           AdvisorContext context,
                                           NextAdvisor chain) {
        int maxRetries   = (int) context.paramDouble(MAX_RETRIES_KEY, DEFAULT_MAX_RETRIES);
        // note: we use a local helper since paramDouble returns Double
        long baseDelayMs = resolveBaseDelay(context);

        AgentResult<LlmResponse> result = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            result = chain.next(request, context);

            if (result.isSuccess()) {
                return result;
            }

            String reason = result.failureReason().orElse("");

            // Non-retryable — fail fast
            if (!isRetryable(reason)) {
                log.warn("[RetryAdvisor] sessionId={} non-retryable failure: {}",
                        context.sessionId(), reason);
                return result;
            }

            // Exhausted retries
            if (attempt > maxRetries) break;

            long delayMs = Math.min(baseDelayMs * (long) Math.pow(2, attempt - 1),
                    MAX_DELAY_MS);

            log.warn("[RetryAdvisor] sessionId={} attempt {}/{} failed ({}). "
                            + "Retrying in {}ms...",
                    context.sessionId(), attempt, maxRetries, reason, delayMs);

            sleep(delayMs);
        }

        return result;
    }

    private boolean isRetryable(String reason) {
        if (reason == null) return true;
        String lower = reason.toLowerCase();
        // Fast-fail on auth and malformed request errors
        return !lower.contains("401")
                && !lower.contains("403")
                && !lower.contains("400")
                && !lower.contains("invalid_api_key")
                && !lower.contains("invalid request");
    }

    private long resolveBaseDelay(AdvisorContext context) {
        // Support both Long and Double params
        return context.param(BASE_DELAY_MS_KEY, Long.class)
                .orElseGet(() -> context.param(BASE_DELAY_MS_KEY, Number.class)
                        .map(Number::longValue)
                        .orElse(DEFAULT_BASE_DELAY));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}