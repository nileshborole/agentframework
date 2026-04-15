# Agent Framework — Example Module

A complete working example demonstrating how to build an AI agent using the agent-framework.

## Quick Start

```bash
# From the repository root
mvn compile -pl agent-example -am

# Run the example
mvn exec:java -pl agent-example -Dexec.mainClass="io.agentframework.example.WeatherAgentExample"
```

## What This Example Demonstrates

This module implements a **Weather Assistant Agent** that can:
- Look up current weather for any city
- Retrieve multi-day forecasts
- Have multi-turn conversations with memory

It uses a **mock LLM client** so you can run it without any API keys.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────┐
│                  Your Domain Code                 │
│  ┌──────────┐  ┌───────────┐  ┌───────────────┐ │
│  │  Phases   │  │   Tools   │  │ Decision      │ │
│  │  (enum)   │  │ (AgentTool│  │ Parser        │ │
│  └──────────┘  └───────────┘  └───────────────┘ │
│  ┌──────────┐  ┌───────────┐  ┌───────────────┐ │
│  │  Domain   │  │  Context  │  │ Agent Runner  │ │
│  │  Data     │  │  Builder  │  │ (extends      │ │
│  │  (POJO)   │  │           │  │  Abstract)    │ │
│  └──────────┘  └───────────┘  └───────────────┘ │
└──────────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────┐
│              Agent Framework (Library)            │
│  ┌─────────────────────────────────────────────┐ │
│  │  AbstractAgentRunner — The ReAct Loop       │ │
│  │  buildContext → callLlm → parseDecision     │ │
│  │  → dispatch (respond / tool / phase)        │ │
│  └─────────────────────────────────────────────┘ │
│  ┌────────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ToolRegistry│ │  Memory  │ │   Observer    │  │
│  │            │ │          │ │   (logging)   │  │
│  └────────────┘ └──────────┘ └───────────────┘  │
│  ┌────────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ AgentState │ │  Config  │ │  Advisor      │  │
│  │            │ │          │ │  Chain        │  │
│  └────────────┘ └──────────┘ └───────────────┘  │
└──────────────────────────────────────────────────┘
```

---

## Step-by-Step Guide

### Step 1: Define Your Domain Model

Every agent needs two domain types:

#### Phases (`AgentPhase`)
Phases control the agent's behavior flow. Define them as an enum implementing `AgentPhase`:

```java
public enum WeatherPhase implements AgentPhase {
    CONVERSATION,  // active chatting phase
    DONE;          // terminal — stops the loop

    @Override public String id() { return name(); }
    @Override public boolean isTerminal() { return this == DONE; }
}
```

> **Key rule**: At least one phase must be terminal (`isTerminal() == true`). The framework prevents transitions once a terminal phase is reached.

#### Domain Data (any POJO)
This is the `S` type parameter that holds your domain state:

```java
public class WeatherData {
    private List<String> queriedCities = new ArrayList<>();
    private String lastWeatherResult;
    // getters, setters...
}
```

> This data persists across loop iterations within a session. Access it via `state.domainData()`.

**📁 See:** [`domain/WeatherPhase.java`](src/main/java/io/agentframework/example/domain/WeatherPhase.java), [`domain/WeatherData.java`](src/main/java/io/agentframework/example/domain/WeatherData.java)

---

### Step 2: Implement Tools

Tools give your agent capabilities. Each tool implements the `AgentTool` interface:

```java
public class GetWeatherTool implements AgentTool {

    @Override public String name() { return "get_weather"; }

    @Override public String description() {
        return "Get current weather for a city";
    }

    @Override public List<ToolInputSpec> inputs() {
        return List.of(
            ToolInputSpec.required("city", "string", "The city name")
        );
    }

    @Override public ToolResult execute(ToolContext<?> context) {
        String city = context.requireString("city");
        String weather = lookupWeather(city);  // your logic
        return ToolResult.continueWith(weather);
    }
}
```

#### Tool Input Types
```java
// Required parameter
ToolInputSpec.required("city", "string", "The city name")

// Optional parameter with default
ToolInputSpec.optional("days", "Number of forecast days", 3)

// Enum parameter with allowed values
ToolInputSpec.enumParam("unit", "Temperature unit", List.of("celsius", "fahrenheit"))
```

#### Tool Result Signals
```java
// Agent loop continues after this tool
ToolResult.continueWith("Weather: Sunny, 22°C")

// Agent pauses and returns control to the caller
ToolResult.pauseForUser("Need confirmation", confirmationPayload)

// Agent terminates immediately
ToolResult.terminate("Booking complete", bookingResult)

// Error result (agent loop continues, but LLM sees the error)
ToolResult.error("City not found")
```

**📁 See:** [`tool/GetWeatherTool.java`](src/main/java/io/agentframework/example/tool/GetWeatherTool.java), [`tool/GetForecastTool.java`](src/main/java/io/agentframework/example/tool/GetForecastTool.java)

---

### Step 3: Create a Decision Parser

The decision parser converts raw LLM text into typed `AgentDecision` objects. The framework supports four decision types:

```java
// 1. Respond — send a message to the user and stop
new StepType.Respond()

// 2. Call Tool — invoke a registered tool
new StepType.CallTool("get_weather", Map.of("city", "London"), "Looking it up...")

// 3. Transition Phase — move to a different agent phase
new StepType.TransitionPhase(WeatherPhase.DONE, "Goodbye!")

// 4. Custom — for domain-specific logic
new StepType.Custom("my_custom_action")
```

Your parser returns `AgentResult<AgentDecision>`:
```java
public AgentResult<AgentDecision> parse(String rawResponse) {
    // Parse LLM output (JSON, structured text, etc.)
    // Return AgentResult.success(decision) or AgentResult.failure("reason")
}
```

**📁 See:** [`runner/WeatherDecisionParser.java`](src/main/java/io/agentframework/example/runner/WeatherDecisionParser.java)

---

### Step 4: Extend AbstractAgentRunner

This is the core of your agent. Extend `AbstractAgentRunner<S, M>` and implement four methods:

```java
public class WeatherAgentRunner extends AbstractAgentRunner<WeatherData, String> {

    // 1. Build the LLM context (system prompt, tools, history, user input)
    @Override
    protected List<String> buildContext(AgentState<WeatherData> state,
                                        String userInput,
                                        List<String> missingFields) {
        // Assemble messages for the LLM
    }

    // 2. Send context to the LLM and get raw response
    @Override
    protected AgentResult<String> callLlm(AgentState<WeatherData> state,
                                           List<String> context,
                                           String traceId, int step) {
        // Call your LLM provider
    }

    // 3. Parse LLM response into a typed decision
    @Override
    protected AgentResult<AgentDecision> parseDecision(String rawResponse) {
        return decisionParser.parse(rawResponse);
    }

    // 4. Fallback when confidence is too low
    @Override
    protected String buildFallbackOutput(AgentState<WeatherData> state, String reason) {
        return "I'm not sure about that. Could you rephrase?";
    }
}
```

#### Type Parameters
- `S` = your domain data type (e.g., `WeatherData`)
- `M` = your message type for context (e.g., `String`, `LlmMessage`, Spring AI `Message`)

#### Optional Hooks
```java
// Token tracking for budget management
@Override protected int[] tokenUsage(String rawResponse) { ... }

// Output delivery to your UI/API
@Override protected void sendOutput(AgentState<S> state, String output, OutputType type) { ... }

// Store tool results for the next LLM iteration
@Override protected void addToolObservation(AgentState<S> state, String toolName, String result) { ... }

// Handle custom step types
@Override protected AgentRunResult handleCustomStep(...) { ... }
```

**📁 See:** [`runner/WeatherAgentRunner.java`](src/main/java/io/agentframework/example/runner/WeatherAgentRunner.java)

---

### Step 5: Wire and Run

```java
// 1. Register tools
ToolRegistry toolRegistry = new ToolRegistry(List.of(
    new GetWeatherTool(),
    new GetForecastTool()
));

// 2. Create memory and observer
AgentMemory memory = new InMemoryAgentMemory();
AgentObserver observer = new Slf4jAgentObserver();

// 3. Create LLM client (use AnthropicLlmClient or SpringAiLlmClient in production)
LlmClient llmClient = new MockLlmClient();

// 4. Create runner
WeatherAgentRunner runner = new WeatherAgentRunner(llmClient, toolRegistry, memory, observer);

// 5. Create session state
DefaultAgentState<WeatherData> state = DefaultAgentState.newSession(
    WeatherPhase.CONVERSATION,
    new WeatherData()
);

// 6. Run!
AgentRunResult result = runner.run(state, "What's the weather?", List.of());

// 7. Check result
if (result.isSuccess()) {
    System.out.println("Output: " + result.outputIfPresent().orElse("done"));
} else {
    System.out.println("Failed: " + result.failureReason());
}
```

**📁 See:** [`WeatherAgentExample.java`](src/main/java/io/agentframework/example/WeatherAgentExample.java)

---

## Module Reference

| Module | Purpose | When to Use |
|---|---|---|
| `agent-core` | Core contracts, runner loop, tools, memory, observer, state | Always — this is the foundation |
| `agent-llm` | LLM client abstraction (`LlmClient`, `LlmMessage`, `LlmResponse`) | Always — defines the LLM interface |
| `agent-advisor` | Middleware chain for LLM calls (retry, rate limit, observability) | When you need cross-cutting LLM concerns |
| `agent-config` | Typed configuration (`InMemoryAgentConfig`, `PropertiesAgentConfig`) | For externalized agent configuration |
| `agent-anthropic` | Anthropic Claude SDK adapter | When using Claude directly (no Spring) |
| `agent-spring-ai` | Spring AI adapter with Micrometer metrics | When using Spring AI |
| `agent-spring` | Spring Boot auto-configuration | For Spring Boot applications |

### Dependency Graph
```
agent-core  ←── agent-llm  ←── agent-advisor
    ↑                ↑               ↑
    │                │               │
agent-config    agent-anthropic  agent-spring-ai
    ↑                                ↑
    └──────── agent-spring ──────────┘
```

---

## Advanced Topics

### Using the Advisor Chain

The advisor chain wraps LLM calls with middleware (retry, rate limiting, token budgeting):

```java
import io.agentframework.advisor.chain.AdvisorChain;
import io.agentframework.advisor.builtin.*;

AdvisorChain chain = new AdvisorChain(
    List.of(
        new ObservabilityAdvisor(),    // order=10: logs LLM calls
        new RateLimitAdvisor(5),       // order=20: max 5 concurrent calls
        new RetryAdvisor(),            // order=30: exponential backoff
        new TokenBudgetAdvisor(),      // order=40: enforce token budget
        new ConfidenceGuardAdvisor()   // order=50: check response confidence
    ),
    llmClient
);

// Use chain.execute() instead of direct llmClient.call()
AgentResult<LlmResponse> result = chain.execute(request, advisorContext);
```

### Using AgentRunConfig

Control agent behavior with configuration:

```java
AgentRunConfig.defaults()        // 15 steps, 100k tokens, 5min timeout, 0.5 confidence
AgentRunConfig.conservative()    // 8 steps, 50k tokens, 2min timeout, 0.7 confidence
AgentRunConfig.extended()        // 30 steps, 200k tokens, 10min timeout, 0.4 confidence

// Custom
AgentRunConfig config = AgentRunConfig.defaults()
    .withMaxSteps(10)
    .withConfidenceThreshold(0.8)
    .withTokenBudget(50_000);
```

### Using Configuration

```java
import io.agentframework.config.impl.InMemoryAgentConfig;

AgentConfig config = InMemoryAgentConfig.defaults();
int maxSteps = config.maxStepsPerTurn();           // 15
boolean toolOn = config.isToolEnabled("get_weather"); // true

// Or from properties file
AgentConfig config = PropertiesAgentConfig.fromClasspath("agent.properties");
```

### Multi-Agent Orchestration

For complex scenarios with multiple specialized agents:

```java
public class TripOrchestrator extends AbstractAgentOrchestrator<TripData> {

    @Override
    public AgentRunResult orchestrate(AgentState<TripData> state, String goal, AgentRunConfig config) {
        // Run agents in parallel
        Map<String, AgentRunResult> results = runParallel(
            List.of("find flights", "find hotels", "find activities"),
            task -> assignToAgent(task),
            config,
            Duration.ofMinutes(2)
        );
        // ... combine results
    }
}
```

---

## File Structure

```
agent-example/
├── pom.xml
├── README.md                          ← You are here
└── src/main/java/io/agentframework/example/
    ├── WeatherAgentExample.java       ← Main entry point
    ├── domain/
    │   ├── WeatherPhase.java          ← Agent phases (enum)
    │   └── WeatherData.java           ← Domain state (POJO)
    ├── tool/
    │   ├── GetWeatherTool.java        ← Current weather tool
    │   └── GetForecastTool.java       ← Forecast tool
    └── runner/
        ├── WeatherAgentRunner.java    ← Agent runner (extends AbstractAgentRunner)
        ├── WeatherDecisionParser.java ← LLM response parser
        └── MockLlmClient.java         ← Mock LLM for demo
```

## Production Checklist

When moving from this example to a real agent:

- [ ] Replace `MockLlmClient` with `AnthropicLlmClient` or `SpringAiLlmClient`
- [ ] Implement proper JSON-based decision parsing
- [ ] Add error handling and retries to tool implementations
- [ ] Configure `AgentRunConfig` with appropriate limits
- [ ] Add the `AdvisorChain` for rate limiting and retry
- [ ] Use `MicrometerAgentObserver` for production metrics
- [ ] Store memory in a persistent store (Redis, DB) instead of in-memory
- [ ] Add integration tests for your tools