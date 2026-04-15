package io.agentframework.core.context;

import io.agentframework.core.AgentState;

import java.util.List;

/**
 * Selects the appropriate {@link ContextBuilder} for the current agent state.
 *
 * <p>Domains register multiple context builders — one per phase or scenario
 * (e.g. a {@code DiscoveryContextBuilder} and a {@code PlanContextBuilder}).
 * The selector evaluates each builder's {@link ContextBuilder#appliesTo}
 * predicate and returns the first match.
 *
 * <p>Example registration (Spring):
 * <pre>
 * {@literal @}Bean
 * public ContextBuilderSelector&lt;TripData, Message&gt; selector(
 *         DiscoveryContextBuilder discovery,
 *         PlanContextBuilder plan) {
 *     return new ContextBuilderSelector&lt;&gt;(List.of(discovery, plan));
 * }
 * </pre>
 *
 * @param <S> the domain state type
 * @param <M> the LLM message type
 */
public class ContextBuilderSelector<S, M> {

    private final List<ContextBuilder<S, M>> builders;
    private final ContextBuilder<S, M> fallback;

    /**
     * Creates a selector with an ordered list of builders.
     * Builders are evaluated in list order — first match wins.
     *
     * @param builders ordered list of candidate builders
     * @throws IllegalArgumentException if the list is empty
     */
    public ContextBuilderSelector(List<ContextBuilder<S, M>> builders) {
        if (builders == null || builders.isEmpty()) {
            throw new IllegalArgumentException("At least one ContextBuilder is required");
        }
        this.builders = List.copyOf(builders);
        this.fallback = builders.get(builders.size() - 1);
    }

    /**
     * Returns the first builder whose {@link ContextBuilder#appliesTo}
     * returns true for the given state.
     *
     * <p>Falls back to the last registered builder if none match,
     * ensuring the selector never returns null.
     *
     * @param state the current agent state
     * @return the selected context builder; never null
     */
    public ContextBuilder<S, M> select(AgentState<S> state) {
        return builders.stream()
                .filter(b -> b.appliesTo(state))
                .findFirst()
                .orElse(fallback);
    }
}