package io.agentframework.advisor.builtin;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Advisor that limits concurrent LLM calls using a fair {@link Semaphore}.
 *
 * <p>This advisor prevents overwhelming the LLM provider with too many
 * simultaneous requests. It acquires a permit before the call and releases
 * it after, regardless of success or failure.
 *
 * <p>Configuration via {@link AdvisorContext} params:
 * <ul>
 *   <li>{@value MAX_CONCURRENT_KEY} — max concurrent calls (default: constructor value)</li>
 *   <li>{@value ACQUIRE_TIMEOUT_MS_KEY} — timeout in ms to acquire permit (default: 30000)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * RateLimitAdvisor advisor = new RateLimitAdvisor(5);
 * // or with default of 10 concurrent calls:
 * RateLimitAdvisor advisor = new RateLimitAdvisor();
 * </pre>
 *
 * @see AgentAdvisor
 */
public class RateLimitAdvisor implements AgentAdvisor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAdvisor.class);

    /** AdvisorContext param key for max concurrent calls. */
    public static final String MAX_CONCURRENT_KEY = "rateLimitAdvisor.maxConcurrent";

    /** AdvisorContext param key for acquire timeout in milliseconds. */
    public static final String ACQUIRE_TIMEOUT_MS_KEY = "rateLimitAdvisor.acquireTimeoutMs";

    private static final int DEFAULT_MAX_CONCURRENT = 10;
    private static final long DEFAULT_ACQUIRE_TIMEOUT_MS = 30_000L;
    private static final int ORDER = 20;

    private final Semaphore semaphore;
    private final int maxConcurrent;

    /**
     * Creates a rate limit advisor with the default concurrency of 10.
     */
    public RateLimitAdvisor() {
        this(DEFAULT_MAX_CONCURRENT);
    }

    /**
     * Creates a rate limit advisor with the specified max concurrent calls.
     *
     * @param maxConcurrent maximum number of concurrent LLM calls
     */
    public RateLimitAdvisor(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
        this.semaphore = new Semaphore(maxConcurrent, true);
    }

    @Override
    public AgentResult<LlmResponse> advise(LlmRequest request, AdvisorContext context, NextAdvisor chain) {
        long timeoutMs = (long) context.paramDouble(ACQUIRE_TIMEOUT_MS_KEY, DEFAULT_ACQUIRE_TIMEOUT_MS);

        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.failure("Rate limit interrupted while waiting for permit");
        }

        if (!acquired) {
            log.warn("Rate limit exceeded — could not acquire permit within {}ms for session={}",
                    timeoutMs, context.sessionId());
            return AgentResult.failure("Rate limit exceeded: could not acquire permit within " + timeoutMs + "ms");
        }

        try {
            return chain.next(request, context);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public String name() {
        return "RateLimitAdvisor";
    }

    /**
     * Returns the number of permits currently available.
     *
     * @return available permits
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Returns the estimated number of active (in-flight) LLM calls.
     *
     * <p>This is calculated as total permits minus available permits.
     *
     * @return estimated active call count
     */
    public int activeCallCount() {
        return maxConcurrent - semaphore.availablePermits();
    }
}