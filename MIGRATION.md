# Migration Guide — Yodeki to Agent Framework

This guide helps migrate a production agent (like Yodeki's trip-planning agent) to the generic `agent-framework`.

## Overview

The agent framework extracts the boilerplate ReAct loop, tool dispatch, advisor middleware, and observability wiring so domain code only implements prompts, tools, and decision parsing.

## Step-by-Step Migration

### 1. Add Dependencies

Replace your custom agent loop code with framework modules:

```xml
<!-- Core framework (zero Spring dependency) -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-core</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- LLM abstraction -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-llm</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Choose ONE provider adapter: -->

<!-- Option A: Spring AI -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-spring-ai</artifactId>
    <version>0.1.0</version>
</dependency>

<!-- Option B: Anthropic SDK direct -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-anthropic</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Define Your Phases

Replace your custom phase management with `AgentPhase`:

```java
public enum TripPhase implements AgentPhase {
    DISCOVERY, PLANNING, BOOKING, DONE;

    @Override public String id() { return name(); }
    @Override public boolean isTerminal() { return this == DONE; }
}
```

### 3. Define Your Domain State

Create a domain data record and use `DefaultAgentState`:

```java
public record TripData(String destination, List<Activity> activities, Budget budget) {}

var state = DefaultAgentState.newSession(TripPhase.DISCOVERY, new TripData(...));
```

### 4. Implement Tools

Replace inline tool implementations with `AgentTool`:

```java
public class SearchFlightsTool implements AgentTool {
    @Override public String name() { return "search_flights"; }
    @Override public String description() { return "Search for flights"; }
    @Override public List<ToolInputSpec> inputs() {
        return List.of(
            ToolInputSpec.required("origin", "Departure airport code"),
            ToolInputSpec.required("destination", "Arrival airport code"),
            ToolInputSpec.required("date", "string", "Travel date (YYYY-MM-DD)")
        );
    }
    @Override public ToolResult execute(ToolContext<?> context) {
        String origin = context.requireString("origin");
        // ... call flight API
        return ToolResult.continueWith(jsonResult);
    }
}
```

### 5. Create a Runner

**With Spring AI:**
```java
public class TripPlannerRunner extends AbstractSpringAiAgentRunner<TripData> {
    // Implement: parseDecision(), buildFallbackOutput(), runnerName()
}
```

**With Anthropic SDK:**
```java
public class TripPlannerRunner extends AbstractAnthropicAgentRunner<TripData> {
    // Implement: parseDecision(), buildFallbackOutput(), runnerName()
}
```

### 6. Implement Context Builders

Replace your prompt assembly with `ContextBuilder` implementations — one per phase:

```java
public class DiscoveryContextBuilder extends SpringAiContextBuilder<TripData> {
    @Override protected List<String> buildSystemPrompts(AgentState<TripData> state) {
        return List.of("You are a travel planning assistant...");
    }
    @Override public boolean appliesTo(AgentState<TripData> state) {
        return state.currentPhase() == TripPhase.DISCOVERY;
    }
}
```

### 7. Register Tools and Wire Up

**Spring Boot:**
```java
@EnableAgentFramework
@SpringBootApplication
public class TripApp { ... }
```

Tools annotated with `@Component` are auto-discovered. Configure via `application.yml`:
```yaml
agent:
  runner:
    max-steps-per-turn: 15
    min-confidence-threshold: 0.5
  tools:
    enabled:
      search_flights: true
      book_hotel: false  # disabled for testing
```

### 8. Remove Boilerplate

Delete your custom:
- ReAct loop implementation → replaced by `AbstractAgentRunner`
- Tool dispatch logic → replaced by `ToolRegistry`
- Retry/rate-limit wrappers → replaced by `RetryAdvisor`, `RateLimitAdvisor`
- Observability code → replaced by `AgentObserver` + `MicrometerAgentObserver`
- Configuration management → replaced by `AgentConfig` implementations

## Key Differences

| Yodeki Pattern | Framework Equivalent |
|---|---|
| Custom while loop | `AbstractAgentRunner.run()` |
| `switch` on action type | Sealed `StepType` with pattern matching |
| Manual tool dispatch | `ToolRegistry.find(name).execute(context)` |
| Custom retry logic | `RetryAdvisor` in `AdvisorChain` |
| In-code config values | `AgentConfig` with typed accessors |
| Manual logging | `AgentObserver` + `StepTrace` |