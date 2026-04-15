package io.agentframework.springai.bridge;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmMessage;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bridges an {@link AgentAdvisor} (framework advisor) into the Spring AI
 * {@link CallAdvisor} interface so it can participate in a Spring AI advisor chain.
 *
 * <p>This allows framework advisors (rate limiting, retry, observability, etc.)
 * to be used transparently alongside native Spring AI advisors.
 *
 * <p>Example usage:
 * <pre>
 * AgentAdvisor rateLimiter = new RateLimitAdvisor(5);
 * CallAdvisor springAdvisor = SpringAiAdvisorBridge.wrap(rateLimiter);
 * // Register springAdvisor with Spring AI ChatClient
 * </pre>
 *
 * <p>On failure, the bridge passes through to the chain (logs and continues)
 * rather than breaking the Spring AI pipeline.
 *
 * @see AgentAdvisor
 * @see CallAdvisor
 */
public class SpringAiAdvisorBridge implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAdvisorBridge.class);

    private final AgentAdvisor delegate;

    private SpringAiAdvisorBridge(AgentAdvisor delegate) {
        this.delegate = delegate;
    }

    /**
     * Wraps a framework {@link AgentAdvisor} as a Spring AI {@link CallAdvisor}.
     *
     * @param advisor the framework advisor to wrap
     * @return a Spring AI CallAdvisor that delegates to the framework advisor
     */
    public static CallAdvisor wrap(AgentAdvisor advisor) {
        return new SpringAiAdvisorBridge(advisor);
    }

    @Override
    public String getName() {
        return delegate.name();
    }

    @Override
    public int getOrder() {
        return delegate.order();
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        try {
            // Build AdvisorContext from Spring AI request context
            Map<String, Object> contextMap = chatClientRequest.context();
            AdvisorContext.Builder ctxBuilder = AdvisorContext.builder();

            // Forward all context params
            if (contextMap != null) {
                contextMap.forEach(ctxBuilder::param);
                // Extract well-known keys
                Object traceId = contextMap.get("traceId");
                if (traceId instanceof String s) {
                    ctxBuilder.traceId(s);
                }
                Object stepNumber = contextMap.get("stepNumber");
                if (stepNumber instanceof Number n) {
                    ctxBuilder.stepNumber(n.intValue());
                }
            }

            AdvisorContext advisorContext = ctxBuilder.build();

            // Build LlmRequest from Spring AI prompt
            List<LlmMessage> llmMessages = toLlmMessages(chatClientRequest.prompt());
            LlmRequest llmRequest = LlmRequest.of("spring-ai-bridge", llmMessages);

            // Wire NextAdvisor that delegates back to the Spring AI chain
            NextAdvisor next = (req, ctx) -> {
                // Continue the Spring AI chain
                ChatClientResponse springResponse = callAdvisorChain.nextCall(chatClientRequest);
                // Translate response back to framework LlmResponse
                return translateResponse(springResponse);
            };

            // Call the framework advisor
            AgentResult<LlmResponse> result = delegate.advise(llmRequest, advisorContext, next);

            if (result.isFailure()) {
                // On failure, pass through to chain — don't break Spring AI pipeline
                log.warn("Framework advisor '{}' failed: {}. Passing through to chain.",
                        delegate.name(), result.failureReason().orElse("unknown"));
                return callAdvisorChain.nextCall(chatClientRequest);
            }

            // If the advisor succeeded via the chain, the response was already returned
            // from the nextCall above. If it short-circuited, we need to reconstruct.
            // Since the chain was called inside next(), the ChatClientResponse was already produced.
            // We pass through to the chain for the actual response.
            return callAdvisorChain.nextCall(chatClientRequest);

        } catch (Exception e) {
            // Framework advisor failure should never break Spring AI chain
            log.error("Unexpected error in SpringAiAdvisorBridge for '{}': {}",
                    delegate.name(), e.getMessage(), e);
            return callAdvisorChain.nextCall(chatClientRequest);
        }
    }

    /**
     * Converts a Spring AI {@link Prompt} to a list of framework {@link LlmMessage}s.
     */
    private List<LlmMessage> toLlmMessages(Prompt prompt) {
        List<LlmMessage> messages = new ArrayList<>();
        if (prompt == null || prompt.getInstructions() == null) {
            return messages;
        }
        for (Message msg : prompt.getInstructions()) {
            if (msg instanceof SystemMessage) {
                messages.add(LlmMessage.system(msg.getText()));
            } else if (msg instanceof AssistantMessage) {
                messages.add(LlmMessage.assistant(msg.getText()));
            } else if (msg instanceof UserMessage) {
                messages.add(LlmMessage.user(msg.getText()));
            } else {
                messages.add(LlmMessage.user(msg.getText()));
            }
        }
        return messages;
    }

    /**
     * Translates a Spring AI {@link ChatClientResponse} to a framework {@link AgentResult}.
     */
    private AgentResult<LlmResponse> translateResponse(ChatClientResponse springResponse) {
        try {
            ChatResponse chatResponse = springResponse.chatResponse();
            if (chatResponse == null || chatResponse.getResults().isEmpty()) {
                return AgentResult.success(LlmResponse.endTurn("", 0, 0, "unknown"));
            }

            Generation generation = chatResponse.getResults().get(0);
            String content = generation.getOutput() != null ? generation.getOutput().getText() : "";

            return AgentResult.success(LlmResponse.endTurn(
                    content != null ? content : "", 0, 0, "spring-ai"));
        } catch (Exception e) {
            return AgentResult.failure("Failed to translate Spring AI response: " + e.getMessage());
        }
    }
}