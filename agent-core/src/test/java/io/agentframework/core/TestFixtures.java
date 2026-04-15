package io.agentframework.core;

import io.agentframework.core.result.AgentRunResult;
import io.agentframework.core.result.AgentRunOutcome;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared test fixtures and stub implementations.
 * No mocking framework required — all stubs are hand-rolled.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── Stub AgentPhase ───────────────────────────────────────────────────

    public enum TestPhase implements AgentPhase {
        INITIAL, PROCESSING, DONE;

        @Override public String id() { return name(); }
        @Override public boolean isTerminal() { return this == DONE; }
    }

    // ── Stub domain state ─────────────────────────────────────────────────

    public static class TestDomainData {
        public String value;
        public TestDomainData(String value) { this.value = value; }
    }

    public static class TestAgentState implements AgentState<TestDomainData> {
        private final String sessionId;
        private AgentPhase phase;
        private TestDomainData data;
        private final Instant createdAt = Instant.now();
        private Instant lastUpdatedAt = Instant.now();

        public TestAgentState(String sessionId, AgentPhase phase) {
            this.sessionId = sessionId;
            this.phase = phase;
        }

        @Override public String sessionId() { return sessionId; }
        @Override public AgentPhase currentPhase() { return phase; }
        @Override public void transitionTo(AgentPhase p) { this.phase = p; }
        @Override public TestDomainData domainData() { return data; }
        @Override public void updateDomainData(TestDomainData d) {
            this.data = d;
            this.lastUpdatedAt = Instant.now();
        }
        @Override public Instant createdAt() { return createdAt; }
        @Override public Instant lastUpdatedAt() { return lastUpdatedAt; }
        @Override public Map<String, Object> metadata() { return Map.of(); }
    }

    // ── Factory helpers ───────────────────────────────────────────────────

    public static TestAgentState state(String sessionId) {
        return new TestAgentState(sessionId, TestPhase.INITIAL);
    }

    public static TestAgentState state(String sessionId, AgentPhase phase) {
        return new TestAgentState(sessionId, phase);
    }

    public static AgentRunResult successResult(String sessionId) {
        return AgentRunResult.success(sessionId, "Test output",
                2, 500, 100, Instant.now(), Instant.now());
    }

    public static AgentRunResult failureResult(String sessionId, String reason) {
        return AgentRunResult.failure(sessionId, AgentRunOutcome.ERROR,
                reason, 1, 0, 0, Instant.now(), Instant.now());
    }
}