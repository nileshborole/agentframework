package io.agentframework.llm;

import java.util.Optional;

/**
 * Encapsulates the output of a single LLM call, independent of provider.
 *
 * <p>Provider implementations map their response types to this record.
 * The framework reads tokens and content from here for observability
 * and routing.
 */
public record LlmResponse(
        String content,
        FinishReason finishReason,
        int inputTokens,
        int outputTokens,
        String model,
        String rawResponse
) {

    /**
     * Why the LLM stopped generating.
     */
    public enum FinishReason {
        /** Model completed its response naturally. */
        END_TURN,
        /** Model wants to call a tool (function call / tool_use). */
        TOOL_USE,
        /** Response was cut off by max_tokens. */
        MAX_TOKENS,
        /** Provider-specific or unknown reason. */
        OTHER
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public int totalTokens() { return inputTokens + outputTokens; }

    public boolean isEndTurn()  { return finishReason == FinishReason.END_TURN; }
    public boolean isToolUse()  { return finishReason == FinishReason.TOOL_USE; }
    public boolean isMaxTokens(){ return finishReason == FinishReason.MAX_TOKENS; }

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

    public Optional<String> contentIfPresent() {
        return Optional.ofNullable(content).filter(s -> !s.isBlank());
    }

    // ── Factory ───────────────────────────────────────────────────────────

    public static LlmResponse of(String content, FinishReason reason,
                                 int inputTokens, int outputTokens, String model) {
        return new LlmResponse(content, reason, inputTokens, outputTokens, model, null);
    }

    public static LlmResponse endTurn(String content,
                                      int inputTokens, int outputTokens, String model) {
        return of(content, FinishReason.END_TURN, inputTokens, outputTokens, model);
    }

    public static LlmResponse toolUse(String content,
                                      int inputTokens, int outputTokens, String model) {
        return of(content, FinishReason.TOOL_USE, inputTokens, outputTokens, model);
    }
}