package io.agentframework.config;

import io.agentframework.config.impl.InMemoryAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAgentConfigTest {

    @Test
    void getString_returnsDefaultWhenAbsent() {
        var config = InMemoryAgentConfig.defaults();
        assertEquals("fallback", config.getString("missing.key", "fallback"));
    }

    @Test
    void getString_returnsSetValue() {
        var config = InMemoryAgentConfig.of(Map.of("key", "value"));
        assertEquals("value", config.getString("key", "default"));
    }

    @Test
    void getInt_parsesIntegerValue() {
        var config = InMemoryAgentConfig.of("agent.max_steps_per_turn", "7");
        assertEquals(7, config.getInt("agent.max_steps_per_turn", 15));
    }

    @Test
    void getInt_returnsDefaultOnMissingKey() {
        var config = InMemoryAgentConfig.defaults();
        assertEquals(15, config.maxStepsPerTurn());
    }

    @Test
    void getDouble_parsesDoubleValue() {
        var config = InMemoryAgentConfig.of("agent.min_confidence_threshold", "0.7");
        assertEquals(0.7, config.minConfidenceThreshold(), 0.001);
    }

    @Test
    void getBoolean_parsesBooleanValue() {
        var config = InMemoryAgentConfig.of("agent.tool.enabled.search_web", "false");
        assertFalse(config.isToolEnabled("search_web"));
    }

    @Test
    void isToolEnabled_defaultsToTrue() {
        var config = InMemoryAgentConfig.defaults();
        assertTrue(config.isToolEnabled("any_tool"));
    }

    @Test
    void getList_parsesCommaSeparatedValues() {
        var config = InMemoryAgentConfig.of("keywords", "again,revisit,last time");
        List<String> list = config.getList("keywords", List.of());
        assertEquals(3, list.size());
        assertTrue(list.contains("revisit"));
    }

    @Test
    void getList_trimsWhitespace() {
        var config = InMemoryAgentConfig.of("keys", " a , b , c ");
        List<String> list = config.getList("keys", List.of());
        assertEquals(List.of("a", "b", "c"), list);
    }

    @Test
    void update_changesValueLive() {
        var config = InMemoryAgentConfig.defaults();
        assertEquals(15, config.maxStepsPerTurn());
        config.update("agent.max_steps_per_turn", "5", "admin");
        assertEquals(5, config.maxStepsPerTurn());
    }

    @Test
    void set_fluentChaining() {
        var config = new InMemoryAgentConfig()
                .set("agent.max_steps_per_turn", "8")
                .set("agent.min_confidence_threshold", "0.6");
        assertEquals(8, config.maxStepsPerTurn());
        assertEquals(0.6, config.minConfidenceThreshold(), 0.001);
    }

    @Test
    void of_keyValuePairs_worksCorrectly() {
        var config = InMemoryAgentConfig.of(
                "agent.max_steps_per_turn", "10",
                "agent.max_token_budget", "50000");
        assertEquals(10, config.maxStepsPerTurn());
        assertEquals(50000, config.maxTokenBudget());
    }

    @Test
    void frameworkDefaults_areCorrect() {
        var config = InMemoryAgentConfig.defaults();
        assertEquals(15,     config.maxStepsPerTurn());
        assertEquals(100000, config.maxTokenBudget());
        assertEquals(0.5,    config.minConfidenceThreshold(), 0.001);
        assertEquals(10,     config.maxConcurrentLlmCalls());
    }
}