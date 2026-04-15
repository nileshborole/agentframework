package io.agentframework.core.context;

import io.agentframework.core.AgentState;

import java.util.List;

/**
 * Builds the ordered list of messages (prompt context) sent to the LLM
 * on each step of the agent loop.
 *
 * <p>{@code M} is the message type used by the underlying LLM client.
 * For Spring AI this is {@code org.springframework.ai.chat.messages.Message}.
 * For the raw Anthropic SDK it is
 * {@code com.anthropic.models.messages.MessageParam}.
 *
 * <p>Domains implement this interface to control exactly what context
 * the LLM sees at each step. The framework calls {@link #build} once per
 * step before invoking the LLM client.
 *
 * <p>A domain may provide multiple {@code ContextBuilder} implementations
 * — one per phase or scenario — and select the appropriate one via a
 * {@link ContextBuilderSelector}.
 *
 * @param <S> the domain state type
 * @param <M> the LLM message type
 */
public interface ContextBuilder<S, M> {

    /**
     * Builds the prompt context for the current agent step.
     *
     * <p>Implementations typically assemble:
     * <ol>
     *   <li>System prompt(s) — role, capabilities, output format</li>
     *   <li>Domain state — current attributes, phase, missing fields</li>
     *   <li>Tool definitions — what tools are available</li>
     *   <li>Conversation memory — recent chat history</li>
     * </ol>
     *
     * @param state        the current agent session state
     * @param userInput    the user's message for this turn
     * @param missingFields fields the domain considers incomplete at this phase
     * @return ordered list of messages for the LLM prompt; never null, never empty
     */
    List<M> build(AgentState<S> state, String userInput, List<String> missingFields);

    /**
     * Returns true if this builder is applicable for the given state.
     * Used by {@link ContextBuilderSelector} to route to the right builder.
     *
     * <p>Default: always applicable. Override to scope to a phase or scenario.
     *
     * @param state the current agent state
     */
    default boolean appliesTo(AgentState<S> state) {
        return true;
    }
}