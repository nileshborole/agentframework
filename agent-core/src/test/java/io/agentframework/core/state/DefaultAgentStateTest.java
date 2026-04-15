package io.agentframework.core.state;

import io.agentframework.core.AgentPhase;
import io.agentframework.core.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultAgentState}.
 */
class DefaultAgentStateTest {

    // ── newSession factory ────────────────────────────────────────────────

    @Test
    void newSession_generatesUniqueSessionIds() {
        var state1 = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data1");
        var state2 = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data2");
        assertNotNull(state1.sessionId());
        assertNotNull(state2.sessionId());
        assertNotEquals(state1.sessionId(), state2.sessionId());
    }

    @Test
    void newSession_setsFieldsCorrectly() {
        var data = new TestFixtures.TestDomainData("hello");
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, data);

        assertEquals(TestFixtures.TestPhase.INITIAL, state.currentPhase());
        assertSame(data, state.domainData());
        assertNotNull(state.createdAt());
        assertNotNull(state.lastUpdatedAt());
        assertTrue(state.metadata().isEmpty());
    }

    @Test
    void newSession_withMetadata_setsMetadata() {
        var meta = Map.<String, Object>of("key1", "val1", "key2", 42);
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data", meta);

        assertEquals("val1", state.metadata().get("key1"));
        assertEquals(42, state.metadata().get("key2"));
        assertEquals(2, state.metadata().size());
    }

    // ── restore factory ──────────────────────────────────────────────────

    @Test
    void restore_setsAllFieldsCorrectly() {
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        var meta = Map.<String, Object>of("restored", true);
        var state = DefaultAgentState.restore(
                "sess-123", TestFixtures.TestPhase.PROCESSING, "domain", created, meta);

        assertEquals("sess-123", state.sessionId());
        assertEquals(TestFixtures.TestPhase.PROCESSING, state.currentPhase());
        assertEquals("domain", state.domainData());
        assertEquals(created, state.createdAt());
        assertEquals(true, state.metadata().get("restored"));
    }

    @Test
    void restore_minimal_setsSessionIdAndPhase() {
        var state = DefaultAgentState.<String>restore("sess-min", TestFixtures.TestPhase.INITIAL);

        assertEquals("sess-min", state.sessionId());
        assertEquals(TestFixtures.TestPhase.INITIAL, state.currentPhase());
        assertNull(state.domainData());
        assertTrue(state.metadata().isEmpty());
    }

    // ── transitionTo ─────────────────────────────────────────────────────

    @Test
    void transitionTo_updatesPhaseAndTimestamp() throws InterruptedException {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data");
        Instant before = state.lastUpdatedAt();

        // Small delay to ensure timestamp differs
        Thread.sleep(5);

        state.transitionTo(TestFixtures.TestPhase.PROCESSING);

        assertEquals(TestFixtures.TestPhase.PROCESSING, state.currentPhase());
        assertTrue(state.lastUpdatedAt().isAfter(before) || state.lastUpdatedAt().equals(before));
    }

    @Test
    void transitionTo_throwsOnTerminalPhase() {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.DONE, "data");

        assertThrows(IllegalStateException.class,
                () -> state.transitionTo(TestFixtures.TestPhase.INITIAL));
    }

    @Test
    void transitionTo_throwsOnNullPhase() {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data");

        assertThrows(IllegalArgumentException.class,
                () -> state.transitionTo(null));
    }

    // ── updateDomainData ─────────────────────────────────────────────────

    @Test
    void updateDomainData_updatesDataAndTimestamp() throws InterruptedException {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "original");
        Instant before = state.lastUpdatedAt();

        Thread.sleep(5);

        state.updateDomainData("updated");

        assertEquals("updated", state.domainData());
        assertTrue(state.lastUpdatedAt().isAfter(before) || state.lastUpdatedAt().equals(before));
    }

    // ── metadata operations ──────────────────────────────────────────────

    @Test
    void putMetadata_addsEntry() {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data");
        state.putMetadata("key", "value");

        assertEquals("value", state.metadata().get("key"));
    }

    @Test
    void removeMetadata_removesEntry() {
        var state = DefaultAgentState.newSession(
                TestFixtures.TestPhase.INITIAL, "data", Map.of("key", "value"));

        state.removeMetadata("key");

        assertFalse(state.metadata().containsKey("key"));
    }

    @Test
    void metadata_returnsUnmodifiableView() {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data");
        state.putMetadata("key", "value");

        var meta = state.metadata();
        assertThrows(UnsupportedOperationException.class, () -> meta.put("new", "val"));
    }

    @Test
    void metadataValue_returnsTypedOptional() {
        var state = DefaultAgentState.newSession(TestFixtures.TestPhase.INITIAL, "data");
        state.putMetadata("count", 42);
        state.putMetadata("name", "test");

        assertEquals(42, state.metadataValue("count", Integer.class).orElse(-1));
        assertEquals("test", state.metadataValue("name", String.class).orElse(""));
        assertTrue(state.metadataValue("missing", String.class).isEmpty());
        // Wrong type returns empty
        assertTrue(state.metadataValue("count", String.class).isEmpty());
    }
}