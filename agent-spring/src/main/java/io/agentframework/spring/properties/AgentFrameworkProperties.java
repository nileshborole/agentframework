package io.agentframework.spring.properties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot configuration properties for the Agent Framework.
 *
 * <p>Bind with {@code @ConfigurationProperties(prefix = "agent")} in
 * your auto-configuration class. All properties are optional and have
 * sensible defaults.
 *
 * <p>Example {@code application.yml}:
 * <pre>
 * agent:
 *   runner:
 *     max-steps-per-turn: 20
 *     min-confidence-threshold: 0.6
 *   llm:
 *     model: claude-sonnet-4-20250514
 *     temperature: 0.3
 *   tools:
 *     enabled:
 *       search_flights: true
 *       book_hotel: false
 * </pre>
 *
 * @see io.agentframework.spring.autoconfigure.AgentFrameworkAutoConfiguration
 */
public class AgentFrameworkProperties {

    private Runner runner = new Runner();
    private Llm llm = new Llm();
    private Memory memory = new Memory();
    private Tools tools = new Tools();
    private Config config = new Config();

    public Runner getRunner() {
        return runner;
    }

    public void setRunner(Runner runner) {
        this.runner = runner;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Tools getTools() {
        return tools;
    }

    public void setTools(Tools tools) {
        this.tools = tools;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    /**
     * Runner configuration — controls the ReAct loop behaviour.
     */
    public static class Runner {
        private int maxStepsPerTurn = 15;
        private int maxTokenBudget = 100_000;
        private Duration wallClockTimeout = Duration.ofMinutes(5);
        private double minConfidenceThreshold = 0.5;

        public int getMaxStepsPerTurn() {
            return maxStepsPerTurn;
        }

        public void setMaxStepsPerTurn(int maxStepsPerTurn) {
            this.maxStepsPerTurn = maxStepsPerTurn;
        }

        public int getMaxTokenBudget() {
            return maxTokenBudget;
        }

        public void setMaxTokenBudget(int maxTokenBudget) {
            this.maxTokenBudget = maxTokenBudget;
        }

        public Duration getWallClockTimeout() {
            return wallClockTimeout;
        }

        public void setWallClockTimeout(Duration wallClockTimeout) {
            this.wallClockTimeout = wallClockTimeout;
        }

        public double getMinConfidenceThreshold() {
            return minConfidenceThreshold;
        }

        public void setMinConfidenceThreshold(double minConfidenceThreshold) {
            this.minConfidenceThreshold = minConfidenceThreshold;
        }
    }

    /**
     * LLM provider configuration.
     */
    public static class Llm {
        private String model = "claude-opus-4-5";
        private int maxTokens = 2048;
        private int maxConcurrentCalls = 10;
        private double temperature = 0.3;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }

    /**
     * Memory (conversation history) configuration.
     */
    public static class Memory {
        private int maxHistoryEntries = 20;

        public int getMaxHistoryEntries() {
            return maxHistoryEntries;
        }

        public void setMaxHistoryEntries(int maxHistoryEntries) {
            this.maxHistoryEntries = maxHistoryEntries;
        }
    }

    /**
     * Tool enablement configuration.
     *
     * <p>Tools can be selectively enabled/disabled via the {@code enabled} map.
     * Any tool not explicitly listed defaults to enabled.
     */
    public static class Tools {
        private Map<String, Boolean> enabled = new HashMap<>();

        public Map<String, Boolean> getEnabled() {
            return enabled;
        }

        public void setEnabled(Map<String, Boolean> enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns whether the specified tool is enabled.
         * Defaults to {@code true} if not explicitly configured.
         *
         * @param toolName the tool name to check
         * @return true if the tool is enabled
         */
        public boolean isToolEnabled(String toolName) {
            return enabled.getOrDefault(toolName, true);
        }
    }

    /**
     * Dynamic configuration reload settings.
     */
    public static class Config {
        private Duration autoReloadInterval = Duration.ofSeconds(60);

        public Duration getAutoReloadInterval() {
            return autoReloadInterval;
        }

        public void setAutoReloadInterval(Duration autoReloadInterval) {
            this.autoReloadInterval = autoReloadInterval;
        }
    }
}