package io.agentframework.springai.llm;

import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmClient;
import io.agentframework.llm.LlmMessage;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Objects;

/**
 * {@link LlmClient} implementation backed by Spring AI's {@link ChatClient}.
 *
 * <p>Translates framework-agnostic {@link LlmRequest} and {@link LlmResponse}
 * types to and from Spring AI's message types. All Spring AI-specific details
 * (ChatOptions, ChatResponse structure, usage metadata) are contained here —
 * nothing else in the framework imports Spring AI types.
 *
 * <p>Usage with Spring Boot auto-configuration (see {@code AgentFrameworkAutoConfiguration}):
 * <pre>
 * {@literal @}Bean
 * public LlmClient agentLlmClient(
 *         {@literal @}Qualifier("TripAgentChatClient") ChatClient chatClient) {
 *     return new SpringAiLlmClient(chatClient);
 * }
 * </pre>
 *
 * <p>The {@link ChatClient} is built by the application — model selection,
 * default advisors, and default options remain the application's concern.
 * The framework only drives it through this adapter.
 */
public class SpringAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "ChatClient must not be null");
    }

    @Override
    public String providerName() {
        return "spring-ai";
    }

    // ── LlmClient ─────────────────────────────────────────────────────────

    @Override
    public AgentResult<LlmResponse> call(LlmRequest request) {
        try {
            List<Message> springMessages = toSpringMessages(request.messages());
            Prompt prompt = new Prompt(springMessages, buildOptions(request));

            ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();

            if (chatResponse == null) {
                return AgentResult.failure("Spring AI returned null ChatResponse");
            }

            return AgentResult.success(toLlmResponse(chatResponse, request));

        } catch (Exception e) {
            log.warn("[SpringAiLlmClient] LLM call failed for prompt '{}': {}",
                    request.promptName(), e.getMessage());
            return AgentResult.failure(
                    "Spring AI call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> AgentResult<T> callForObject(LlmRequest request, Class<T> responseType) {
        try {
            List<Message> springMessages = toSpringMessages(request.messages());
            Prompt prompt = new Prompt(springMessages, buildOptions(request));

            T entity = chatClient.prompt(prompt).call().entity(responseType);

            if (entity == null) {
                return AgentResult.failure(
                        "Spring AI returned null entity for type: " + responseType.getSimpleName());
            }

            return AgentResult.success(entity);

        } catch (Exception e) {
            log.warn("[SpringAiLlmClient] Structured call failed for '{}': {}",
                    request.promptName(), e.getMessage());
            return AgentResult.failure(
                    "Spring AI structured call failed: " + e.getMessage(), e);
        }
    }

    // ── Type translation ──────────────────────────────────────────────────

    /**
     * Converts framework {@link LlmMessage} list to Spring AI {@link Message} list.
     *
     * <p>Mapping:
     * <ul>
     *   <li>SYSTEM    → {@link SystemMessage}</li>
     *   <li>USER      → {@link UserMessage}</li>
     *   <li>ASSISTANT → {@link AssistantMessage}</li>
     *   <li>TOOL_RESULT → {@link UserMessage} (Spring AI tool results are user-role)</li>
     * </ul>
     */
    private List<Message> toSpringMessages(List<LlmMessage> messages) {
        return messages.stream()
                .map(this::toSpringMessage)
                .toList();
    }

    private Message toSpringMessage(LlmMessage msg) {
        return switch (msg.role()) {
            case SYSTEM      -> new SystemMessage(msg.content());
            case USER        -> new UserMessage(msg.content());
            case ASSISTANT   -> new AssistantMessage(msg.content());
            case TOOL_RESULT -> new UserMessage(msg.content());
        };
    }

    /**
     * Converts a Spring AI {@link ChatResponse} to a framework {@link LlmResponse}.
     *
     * <p>Maps Spring AI's finish reasons to {@link LlmResponse.FinishReason}:
     * <ul>
     *   <li>{@code "STOP"} / {@code "end_turn"} → {@code END_TURN}</li>
     *   <li>{@code "TOOL_USE"} / {@code "tool_calls"} → {@code TOOL_USE}</li>
     *   <li>{@code "MAX_TOKENS"} / {@code "length"} → {@code MAX_TOKENS}</li>
     *   <li>anything else → {@code OTHER}</li>
     * </ul>
     */
    private LlmResponse toLlmResponse(ChatResponse chatResponse, LlmRequest request) {
        String content = "";
        String finishReasonRaw = "unknown";
        String model = request.config().model();

        if (chatResponse.getResult() != null) {
            var output = chatResponse.getResult().getOutput();
            if (output != null && output.getText() != null) {
                content = output.getText();
            }
            if (chatResponse.getResult().getMetadata() != null
                    && chatResponse.getResult().getMetadata().getFinishReason() != null) {
                finishReasonRaw = chatResponse.getResult().getMetadata().getFinishReason();
            }
        }

        int inputTokens = 0;
        int outputTokens = 0;
        if (chatResponse.getMetadata() != null
                && chatResponse.getMetadata().getUsage() != null) {
            var usage = chatResponse.getMetadata().getUsage();
            if (usage.getPromptTokens() != null) {
                inputTokens = usage.getPromptTokens().intValue();
            }
            if (usage.getCompletionTokens() != null) {
                outputTokens = usage.getCompletionTokens().intValue();
            }
            if (chatResponse.getMetadata().getModel() != null) {
                model = chatResponse.getMetadata().getModel();
            }
        }

        LlmResponse.FinishReason finishReason = parseFinishReason(finishReasonRaw);

        return new LlmResponse(content, finishReason,
                inputTokens, outputTokens, model, null);
    }

    private LlmResponse.FinishReason parseFinishReason(String raw) {
        if (raw == null) return LlmResponse.FinishReason.OTHER;
        return switch (raw.toUpperCase()) {
            case "STOP", "END_TURN", "COMPLETE" -> LlmResponse.FinishReason.END_TURN;
            case "TOOL_USE", "TOOL_CALLS", "FUNCTION_CALL" -> LlmResponse.FinishReason.TOOL_USE;
            case "MAX_TOKENS", "LENGTH" -> LlmResponse.FinishReason.MAX_TOKENS;
            default -> LlmResponse.FinishReason.OTHER;
        };
    }

    /**
     * Builds Spring AI {@link ChatOptions} from the framework {@link io.agentframework.llm.LlmClientConfig}.
     * Returns null to use the ChatClient's defaults when no overrides are needed.
     */
    private ChatOptions buildOptions(LlmRequest request) {
        var cfg = request.config();
        // Only override if explicitly configured — let ChatClient defaults apply otherwise
        if (cfg.maxTokens() <= 0 && cfg.temperature() < 0) {
            return null;
        }
        return ChatOptions.builder()
                .model(cfg.model())
                .maxTokens(cfg.maxTokens() > 0 ? cfg.maxTokens() : null)
                .temperature(cfg.temperature() >= 0 ? cfg.temperature() : null)
                .build();
    }
}