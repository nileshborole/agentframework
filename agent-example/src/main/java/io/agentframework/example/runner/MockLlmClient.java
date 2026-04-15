package io.agentframework.example.runner;

import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmClient;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A mock {@link LlmClient} that returns pre-programmed responses.
 *
 * <p>This allows running the example without a real LLM provider.
 * Each call to {@link #call(LlmRequest)} dequeues the next scripted response.
 *
 * <p>In production, you would use:
 * <ul>
 *   <li>{@code AnthropicLlmClient} from {@code agent-anthropic} module</li>
 *   <li>{@code SpringAiLlmClient} from {@code agent-spring-ai} module</li>
 *   <li>Or implement your own {@code LlmClient} for any provider</li>
 * </ul>
 */
public class MockLlmClient implements LlmClient {

    private final Queue<String> scriptedResponses = new LinkedList<>();

    /**
     * Add a scripted response that will be returned on the next {@link #call}.
     */
    public MockLlmClient addResponse(String response) {
        scriptedResponses.add(response);
        return this;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public AgentResult<LlmResponse> call(LlmRequest request) {
        String response = scriptedResponses.poll();
        if (response == null) {
            // Default: respond with a simple message
            response = """
                    ACTION: RESPOND
                    CONFIDENCE: 0.9
                    REPLY: I don't have any more scripted responses. This is a mock LLM client.
                    """;
        }
        return AgentResult.success(
                LlmResponse.endTurn(response, 100, 50, "mock-model")
        );
    }
}