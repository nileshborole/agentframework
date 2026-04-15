package io.agentframework.llm;

/**
 * Configuration parameters for LLM calls.
 *
 * <p>Provider implementations map these to their own parameter types.
 * Settings that a provider doesn't support are silently ignored.
 */
public record LlmClientConfig(
        String model,
        int maxTokens,
        double temperature,
        boolean jsonMode,
        String responseFormat
) {

    public static LlmClientConfig defaults() {
        return new LlmClientConfig("claude-opus-4-5", 2048, 0.7, false, null);
    }

    public static LlmClientConfig forStructuredOutput() {
        return new LlmClientConfig("claude-opus-4-5", 2048, 0.3, true, null);
    }

    public static LlmClientConfig forJudge() {
        return new LlmClientConfig("claude-haiku-4-5", 512, 0.0, true, null);
    }

    public LlmClientConfig withModel(String model) {
        return new LlmClientConfig(model, maxTokens, temperature, jsonMode, responseFormat);
    }

    public LlmClientConfig withMaxTokens(int maxTokens) {
        return new LlmClientConfig(model, maxTokens, temperature, jsonMode, responseFormat);
    }

    public LlmClientConfig withTemperature(double temperature) {
        return new LlmClientConfig(model, maxTokens, temperature, jsonMode, responseFormat);
    }

    public LlmClientConfig withJsonMode(boolean jsonMode) {
        return new LlmClientConfig(model, maxTokens, temperature, jsonMode, responseFormat);
    }
}