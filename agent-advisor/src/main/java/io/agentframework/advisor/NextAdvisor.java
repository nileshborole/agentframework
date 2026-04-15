package io.agentframework.advisor;

import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import io.agentframework.core.result.AgentResult;

/**
 * Functional interface for the next step in an advisor chain.
 * Passed to each advisor so it can invoke the remainder of the chain.
 */
@FunctionalInterface
public interface NextAdvisor {
    AgentResult<LlmResponse> next(LlmRequest request, AdvisorContext context);
}