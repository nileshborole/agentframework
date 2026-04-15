package io.agentframework.anthropic.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link LlmClient} implementation wrapping the Anthropic Java SDK.
 *
 * <p>This client translates between the framework's provider-agnostic
 * {@link LlmMessage}/{@link LlmRequest}/{@link LlmResponse} types and
 * the Anthropic SDK's native types.
 *
 * <p>Example usage:
 * <pre>
 * AnthropicClient sdk = AnthropicOkHttpClient.builder()
 *     .apiKey(System.getenv("ANTHROPIC_API_KEY"))
 *     .build();
 * LlmClient client = new AnthropicLlmClient(sdk);
 * </pre>
 *
 * <p>Dependencies ({@code provided} scope — user supplies at runtime):
 * <ul>
 *   <li>{@code com.anthropic:anthropic-java:1.4.0}</li>
 *   <li>{@code com.fasterxml.jackson.core:jackson-databind:2.17.2}</li>
 * </ul>
 */
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String PROVIDER_NAME = "anthropic";

    private final AnthropicClient client;
    private final ObjectMapper mapper;

    /**
     * Creates a client with the default Jackson ObjectMapper.
     *
     * @param client the Anthropic SDK client
     */
    public AnthropicLlmClient(AnthropicClient client) {
        this(client, new ObjectMapper());
    }

    /**
     * Creates a client with a custom Jackson ObjectMapper.
     *
     * @param client the Anthropic SDK client
     * @param mapper the ObjectMapper for structured output parsing
     */
    public AnthropicLlmClient(AnthropicClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public AgentResult<LlmResponse> call(LlmRequest request) {
        try {
            LlmClientConfig config = request.config() != null
                    ? request.config()
                    : LlmClientConfig.defaults();

            // Separate system messages from conversation messages
            String systemPrompt = request.messages().stream()
                    .filter(m -> m.role() == LlmMessage.Role.SYSTEM)
                    .map(LlmMessage::content)
                    .collect(Collectors.joining("\n\n"));

            // Build conversation messages (non-system)
            List<MessageParam> messageParams = new ArrayList<>();
            for (LlmMessage msg : request.messages()) {
                if (msg.role() == LlmMessage.Role.SYSTEM) {
                    continue; // already handled above
                }
                MessageParam.Role role = msg.role() == LlmMessage.Role.ASSISTANT
                        ? MessageParam.Role.ASSISTANT
                        : MessageParam.Role.USER; // USER and TOOL_RESULT both map to USER

                messageParams.add(MessageParam.builder()
                        .role(role)
                        .content(msg.content())
                        .build());
            }

            // Build request params
            long maxTokens = config.maxTokens() > 0 ? config.maxTokens() : 2048L;
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(Model.of(config.model()))
                    .maxTokens(maxTokens)
                    .messages(messageParams);

            if (!systemPrompt.isBlank()) {
                paramsBuilder.system(systemPrompt);
            }

            if (config.temperature() >= 0) {
                paramsBuilder.temperature(config.temperature());
            }

            // Execute the API call
            Message response = client.messages().create(paramsBuilder.build());

            // Extract text from first TextBlock
            String content = response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(block -> block.asText().text())
                    .findFirst()
                    .orElse("");

            // Map stop reason
            LlmResponse.FinishReason finishReason = response.stopReason()
                    .map(this::mapStopReason)
                    .orElse(LlmResponse.FinishReason.OTHER);

            // Extract token usage
            int inputTokens = (int) response.usage().inputTokens();
            int outputTokens = (int) response.usage().outputTokens();

            return AgentResult.success(LlmResponse.of(
                    content, finishReason, inputTokens, outputTokens, response.model().toString()));

        } catch (Exception e) {
            log.error("Anthropic API call failed for prompt '{}': {}",
                    request.promptName(), e.getMessage(), e);
            return AgentResult.failure("Anthropic API error: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> AgentResult<T> callForObject(LlmRequest request, Class<T> responseType) {
        // Append JSON instruction as user message
        LlmRequest jsonRequest = request.withMessage(
                LlmMessage.user("Respond ONLY with valid JSON matching the requested schema. "
                        + "No preamble, no markdown fences, no explanation."));

        AgentResult<LlmResponse> result = call(jsonRequest);
        if (result.isFailure()) {
            return AgentResult.failure(result.failureReason().orElse("LLM call failed"));
        }

        try {
            String raw = result.value().orElseThrow().content();
            // Strip markdown JSON fences if present
            String cleaned = raw.strip();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.strip();

            T parsed = mapper.readValue(cleaned, responseType);
            return AgentResult.success(parsed);
        } catch (Exception e) {
            log.error("Failed to parse Anthropic response as {}: {}",
                    responseType.getSimpleName(), e.getMessage());
            return AgentResult.failure("JSON parse error: " + e.getMessage(), e);
        }
    }

    private LlmResponse.FinishReason mapStopReason(StopReason stopReason) {
        if (stopReason == null) {
            return LlmResponse.FinishReason.OTHER;
        }
        if (stopReason.equals(StopReason.END_TURN)) {
            return LlmResponse.FinishReason.END_TURN;
        } else if (stopReason.equals(StopReason.TOOL_USE)) {
            return LlmResponse.FinishReason.TOOL_USE;
        } else if (stopReason.equals(StopReason.MAX_TOKENS)) {
            return LlmResponse.FinishReason.MAX_TOKENS;
        }
        return LlmResponse.FinishReason.OTHER;
    }
}