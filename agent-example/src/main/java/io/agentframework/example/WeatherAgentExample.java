package io.agentframework.example;

import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.memory.InMemoryAgentMemory;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.observer.Slf4jAgentObserver;
import io.agentframework.core.result.AgentRunResult;
import io.agentframework.core.runner.AgentRunConfig;
import io.agentframework.core.state.DefaultAgentState;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.example.domain.WeatherData;
import io.agentframework.example.domain.WeatherPhase;
import io.agentframework.example.runner.MockLlmClient;
import io.agentframework.example.runner.WeatherAgentRunner;
import io.agentframework.example.tool.GetForecastTool;
import io.agentframework.example.tool.GetWeatherTool;

import java.util.List;

/**
 * Main entry point for the weather agent example.
 *
 * <p>This demonstrates the complete wiring of an agent using the framework:
 *
 * <ol>
 *   <li><b>Define domain</b> — {@link WeatherPhase}, {@link WeatherData}</li>
 *   <li><b>Implement tools</b> — {@link GetWeatherTool}, {@link GetForecastTool}</li>
 *   <li><b>Create runner</b> — {@link WeatherAgentRunner} extends AbstractAgentRunner</li>
 *   <li><b>Wire and run</b> — assemble components and call {@code runner.run()}</li>
 * </ol>
 *
 * <p>Run this class to see the agent process a scripted conversation.
 */
public class WeatherAgentExample {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     Agent Framework — Weather Agent Example     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // ── Step 1: Create the tools ──────────────────────────────────────
        // Tools are registered in a ToolRegistry. The framework uses this
        // registry to dispatch tool calls from the LLM.
        ToolRegistry toolRegistry = new ToolRegistry(List.of(
                new GetWeatherTool(),
                new GetForecastTool()
        ));
        System.out.println("✓ Registered " + toolRegistry.toolNames().size() + " tools: " + toolRegistry.toolNames());

        // ── Step 2: Create the memory ─────────────────────────────────────
        // AgentMemory stores conversation history per session.
        // InMemoryAgentMemory is the built-in implementation.
        AgentMemory memory = new InMemoryAgentMemory();

        // ── Step 3: Create the observer ───────────────────────────────────
        // Observers receive lifecycle events (run start, steps, completion).
        // Slf4jAgentObserver logs them as structured key=value pairs.
        AgentObserver observer = new Slf4jAgentObserver();

        // ── Step 4: Create the LLM client ─────────────────────────────────
        // MockLlmClient returns scripted responses for this demo.
        // In production, use AnthropicLlmClient or SpringAiLlmClient.
        MockLlmClient llmClient = new MockLlmClient();

        // Script a conversation: user asks for weather → LLM calls tool → LLM responds
        llmClient
                // Turn 1: LLM decides to call the get_weather tool
                .addResponse("""
                        ACTION: CALL_TOOL
                        CONFIDENCE: 0.95
                        TOOL: get_weather
                        TOOL_INPUT: city=London
                        REPLY: Let me check the weather in London for you.
                        """)
                // Turn 2: After receiving tool result, LLM responds to user
                .addResponse("""
                        ACTION: RESPOND
                        CONFIDENCE: 0.95
                        REPLY: The weather in London is currently cloudy at 14°C with 78% humidity and northwest winds at 12 km/h. It's a typical London day!
                        """);

        // ── Step 5: Create the agent runner ───────────────────────────────
        // The runner extends AbstractAgentRunner and implements the 4 required methods.
        WeatherAgentRunner runner = new WeatherAgentRunner(
                llmClient, toolRegistry, memory, observer
        );
        System.out.println("✓ Created runner: " + runner.runnerName());

        // ── Step 6: Create the agent state ────────────────────────────────
        // DefaultAgentState manages session ID, current phase, and domain data.
        // newSession() generates a unique session ID automatically.
        DefaultAgentState<WeatherData> state = DefaultAgentState.newSession(
                WeatherPhase.CONVERSATION,
                new WeatherData()
        );
        System.out.println("✓ Created session: " + state.sessionId());
        System.out.println("✓ Initial phase: " + state.currentPhase().id());
        System.out.println();

        // ── Step 7: Run the agent ─────────────────────────────────────────
        // The framework's ReAct loop handles everything:
        //   buildContext → callLlm → parseDecision → dispatch (respond/tool/phase)
        System.out.println("─── Running agent for: \"What's the weather in London?\" ───");
        System.out.println();

        AgentRunResult result = runner.run(
                state,
                "What's the weather in London?",
                List.of(),  // no missing fields
                AgentRunConfig.defaults()
        );

        // ── Step 8: Inspect the result ────────────────────────────────────
        System.out.println();
        System.out.println("─── Agent Run Result ───");
        System.out.println("  Outcome:       " + result.outcome());
        System.out.println("  Success:       " + result.isSuccess());
        System.out.println("  Steps:         " + result.stepsExecuted());
        System.out.println("  Output:        " + result.outputIfPresent().orElse("(none)"));
        System.out.println("  Input tokens:  " + result.totalInputTokens());
        System.out.println("  Output tokens: " + result.totalOutputTokens());
        System.out.println("  Duration:      " + result.duration());

        // ── Step 9: Check domain state ────────────────────────────────────
        System.out.println();
        System.out.println("─── Domain State ───");
        System.out.println("  Queried cities:    " + state.domainData().getQueriedCities());
        System.out.println("  Last weather:      " + state.domainData().getLastWeatherResult());
        System.out.println("  Memory entries:    " + memory.size(state.sessionId()));
        System.out.println("  Current phase:     " + state.currentPhase().id());

        System.out.println();
        System.out.println("─── Example 2: Multi-turn with forecast ───");
        System.out.println();

        // Add more scripted responses for a second turn
        llmClient
                .addResponse("""
                        ACTION: CALL_TOOL
                        CONFIDENCE: 0.92
                        TOOL: get_forecast
                        TOOL_INPUT: city=Tokyo,days=5
                        REPLY: I'll get the 5-day forecast for Tokyo.
                        """)
                .addResponse("""
                        ACTION: RESPOND
                        CONFIDENCE: 0.93
                        REPLY: Here's the 5-day forecast for Tokyo! Looks like a mix of weather coming up.
                        """);

        AgentRunResult result2 = runner.run(
                state,
                "What's the 5-day forecast for Tokyo?",
                List.of(),
                AgentRunConfig.defaults()
        );

        System.out.println();
        System.out.println("  Outcome:        " + result2.outcome());
        System.out.println("  Steps:          " + result2.stepsExecuted());
        System.out.println("  Queried cities: " + state.domainData().getQueriedCities());
        System.out.println("  Memory entries: " + memory.size(state.sessionId()));

        System.out.println();
        System.out.println("════════════════════════════════════════════════════");
        System.out.println("  Example complete! See agent-example/README.md");
        System.out.println("  for detailed documentation on each module.");
        System.out.println("════════════════════════════════════════════════════");
    }
}