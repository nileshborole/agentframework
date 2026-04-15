package io.agentframework.llm;

import java.util.List;

/**
 * Encapsulates all inputs for a single LLM call.
 *
 * <p>Provider-agnostic — {@link LlmClient} implementations convert this
 * to their provider-specific request format (Spring AI {@code Prompt},
 * Anthropic {@code MessageCreateParams}, etc.).
 */
public record LlmRequest(
        List<LlmMessage> messages,
        LlmClientConfig config,
        String promptName
) {

    /**
     * Creates a request with the given messages and default config.
     *
     * @param promptName logical name for observability (e.g. "explore_step_0")
     * @param messages   the ordered conversation messages
     */
    public static LlmRequest of(String promptName, List<LlmMessage> messages) {
        return new LlmRequest(List.copyOf(messages), LlmClientConfig.defaults(), promptName);
    }

    /**
     * Creates a request with custom config.
     */
    public static LlmRequest of(String promptName,
                                List<LlmMessage> messages,
                                LlmClientConfig config) {
        return new LlmRequest(List.copyOf(messages), config, promptName);
    }

    /**
     * Returns a copy of this request with an additional message appended.
     */
    public LlmRequest withMessage(LlmMessage message) {
        var updated = new java.util.ArrayList<>(messages);
        updated.add(message);
        return new LlmRequest(List.copyOf(updated), config, promptName);
    }
}