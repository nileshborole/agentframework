package io.agentframework.core.runner;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Typed context object passed from one agent to another in a multi-agent pipeline.
 *
 * <p>Replaces raw string passing between agents with a structured, auditable
 * handoff record. The receiving agent uses this to understand what work was
 * already done and what it must do next — without re-reading the full session
 * history of the delegating agent.
 *
 * <p>Usage in a supervisor → sub-agent pipeline:
 * <pre>
 * AgentHandoff handoff = AgentHandoff.builder()
 *     .sessionId(state.sessionId())
 *     .fromAgent("supervisor")
 *     .toAgent("research-agent")
 *     .task("Research INFY stock: price, P/E ratio, recent news")
 *     .context("User wants a buy/sell recommendation")
 *     .finding("user-goal", "investment decision for INFY")
 *     .build();
 *
 * AgentRunResult subResult = researchRunner.run(subState, handoff.toPrompt(), ...);
 * </pre>
 */
public record AgentHandoff(
        String sessionId,
        String fromAgent,
        String toAgent,
        String task,
        String context,
        Map<String, String> findings,
        Instant createdAt
) {

    /**
     * Renders the handoff as a prompt section for the receiving agent.
     * This is the text the sub-agent receives as its user input.
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Handoff from ").append(fromAgent).append(" ===\n");
        sb.append("Your task: ").append(task).append("\n");

        if (context != null && !context.isBlank()) {
            sb.append("Context: ").append(context).append("\n");
        }

        if (!findings.isEmpty()) {
            sb.append("\nResults from prior agents:\n");
            findings.forEach((agent, result) ->
                    sb.append("  [").append(agent).append("]: ").append(result).append("\n"));
        }

        sb.append("=================================\n");
        sb.append("Begin your work now.");
        return sb.toString();
    }

    public Optional<String> finding(String agentName) {
        return Optional.ofNullable(findings.get(agentName));
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sessionId;
        private String fromAgent;
        private String toAgent;
        private String task;
        private String context;
        private final java.util.HashMap<String, String> findings = new java.util.HashMap<>();

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId; return this;
        }
        public Builder fromAgent(String fromAgent) {
            this.fromAgent = fromAgent; return this;
        }
        public Builder toAgent(String toAgent) {
            this.toAgent = toAgent; return this;
        }
        public Builder task(String task) {
            this.task = task; return this;
        }
        public Builder context(String context) {
            this.context = context; return this;
        }
        public Builder finding(String agentName, String result) {
            this.findings.put(agentName, result); return this;
        }
        public Builder findings(Map<String, String> findings) {
            this.findings.putAll(findings); return this;
        }

        public AgentHandoff build() {
            return new AgentHandoff(sessionId, fromAgent, toAgent, task,
                    context, Map.copyOf(findings), Instant.now());
        }
    }
}