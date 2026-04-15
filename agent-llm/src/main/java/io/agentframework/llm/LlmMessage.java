package io.agentframework.llm;

/**
 * A single message in an LLM conversation, independent of any provider's
 * specific message type (Spring AI, Anthropic SDK, OpenAI SDK, etc.).
 *
 * <p>LLM client implementations convert these to and from their
 * provider-specific types internally. The framework never sees
 * provider types — only {@code LlmMessage}.
 */
public record LlmMessage(
        Role role,
        String content
) {

    public enum Role {
        /** Message from the user or tool result fed back to the model. */
        USER,
        /** Message from the assistant (model output). */
        ASSISTANT,
        /** System-level instruction to the model. */
        SYSTEM,
        /** Tool invocation result. */
        TOOL_RESULT
    }

    // ── Factory methods ───────────────────────────────────────────────────

    public static LlmMessage system(String content) {
        return new LlmMessage(Role.SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content);
    }

    public static LlmMessage toolResult(String content) {
        return new LlmMessage(Role.TOOL_RESULT, content);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public boolean isSystem()     { return role == Role.SYSTEM; }
    public boolean isUser()       { return role == Role.USER; }
    public boolean isAssistant()  { return role == Role.ASSISTANT; }
    public boolean isToolResult() { return role == Role.TOOL_RESULT; }
}