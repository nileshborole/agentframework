package io.agentframework.llm;

import io.agentframework.core.result.AgentResult;

/**
 * Provider-agnostic interface for making LLM calls.
 *
 * <p>All LLM interactions in the framework go through this interface.
 * Provider-specific implementations live in integration modules:
 * <ul>
 *   <li>{@code SpringAiLlmClient} — wraps Spring AI's {@code ChatClient} (agent-spring-ai)</li>
 *   <li>{@code AnthropicLlmClient} — wraps the Anthropic Java SDK (agent-anthropic)</li>
 * </ul>
 *
 * <p>This design means:
 * <ul>
 *   <li>Domain runners are tested without any LLM SDK on the classpath</li>
 *   <li>Switching from Spring AI to the raw Anthropic SDK is one line change</li>
 *   <li>Multiple providers can be used in the same application</li>
 * </ul>
 *
 * <p>Example usage in a domain runner:
 * <pre>
 * AgentResult&lt;LlmResponse&gt; result = llmClient.call(request);
 * if (result.isFailure()) {
 *     return AgentRunResult.failure(sessionId, AgentRunOutcome.ERROR,
 *             result.failureReason().orElse("LLM call failed"), ...);
 * }
 * LlmResponse response = result.value().get();
 * </pre>
 */
public interface LlmClient {

    /**
     * Makes a single LLM call and returns the response.
     *
     * <p>Never throws — all errors (network, rate limit, timeout) are
     * returned as {@link AgentResult#failure(String, Throwable)}.
     *
     * @param request the LLM request containing messages and config
     * @return a result wrapping the response or a failure reason
     */
    AgentResult<LlmResponse> call(LlmRequest request);

    /**
     * Makes an LLM call and attempts to deserialise the response content
     * as the given type. Useful for structured JSON outputs.
     *
     * <p>Default implementation calls {@link #call} and delegates
     * deserialisation to the provider. Override if the provider has
     * native structured-output support (e.g. Anthropic's tool-use JSON).
     *
     * @param request      the LLM request
     * @param responseType the class to deserialise the response content into
     * @return a result wrapping the deserialised object or a failure reason
     */
    default <T> AgentResult<T> callForObject(LlmRequest request, Class<T> responseType) {
        return call(request).map(response -> {
            throw new UnsupportedOperationException(
                    "callForObject not implemented by " + getClass().getSimpleName()
                            + ". Use the provider-specific implementation.");
        });
    }

    /**
     * Returns the provider identifier for this client.
     * Used in logs and metrics.
     * Example: {@code "spring-ai"}, {@code "anthropic-sdk"}
     */
    String providerName();
}