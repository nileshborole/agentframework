package io.agentframework.example.domain;

import io.agentframework.core.AgentPhase;

/**
 * Phases for the weather assistant agent.
 *
 * <p>Every domain agent defines its own phases by implementing {@link AgentPhase}.
 * The framework uses phases to route context building, prompt selection,
 * and decide when the agent is done.
 *
 * <p>This example has two phases:
 * <ul>
 *   <li>{@link #CONVERSATION} — the agent is chatting and can look up weather</li>
 *   <li>{@link #DONE} — terminal phase, the conversation is over</li>
 * </ul>
 */
public enum WeatherPhase implements AgentPhase {

    /** Active conversation phase — agent can respond or call tools. */
    CONVERSATION,

    /** Terminal phase — no more loop iterations. */
    DONE;

    @Override
    public String id() {
        return name();
    }

    @Override
    public boolean isTerminal() {
        return this == DONE;
    }
}