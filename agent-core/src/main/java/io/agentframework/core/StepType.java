package io.agentframework.core;

/**
 * Sealed type hierarchy representing the three fundamental decisions
 * an agent can make at each step of its loop.
 *
 * <p>The framework's generic agent loop routes on these — not on
 * domain-specific string literals — eliminating silent routing failures
 * from typos or missing switch cases.
 *
 * <p>Domain runners may introduce additional step types by extending
 * {@link Custom}, but the three base types cover 95% of use cases.
 */
public sealed interface StepType
        permits StepType.Respond, StepType.CallTool, StepType.TransitionPhase, StepType.Custom {

    /**
     * The agent has enough information to reply to the user.
     * The loop exits after this step.
     */
    record Respond() implements StepType {}

    /**
     * The agent wants to invoke a named tool before continuing.
     *
     * @param toolName  the name of the tool to invoke — must exist in the registry
     * @param toolInput the parameters to pass to the tool
     * @param preReply  optional text to send to the user before tool execution
     */
    record CallTool(
            String toolName,
            java.util.Map<String, Object> toolInput,
            String preReply
    ) implements StepType {

        public CallTool(String toolName, java.util.Map<String, Object> toolInput) {
            this(toolName, toolInput, null);
        }

        public boolean hasPreReply() {
            return preReply != null && !preReply.isBlank();
        }
    }

    /**
     * The agent wants to move the session to a different phase.
     * The loop continues after the transition (up to maxSteps).
     *
     * @param targetPhase the phase to transition to
     * @param reply       optional message to send to the user about the transition
     */
    record TransitionPhase(AgentPhase targetPhase, String reply) implements StepType {

        public TransitionPhase(AgentPhase targetPhase) {
            this(targetPhase, null);
        }

        public boolean hasReply() {
            return reply != null && !reply.isBlank();
        }
    }

    /**
     * Extension point for domain-specific step types.
     * Domains that need additional routing logic extend this.
     *
     * @param typeId a stable string identifier for this custom step type
     */
    record Custom(String typeId) implements StepType {}
}