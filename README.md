# Agent Framework

[![CI](https://github.com/YOUR_GITHUB/agent-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_GITHUB/agent-framework/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A generic Java AI agent orchestration framework. Eliminates the boilerplate ReAct loop, tool dispatch, advisor middleware, and observability wiring so domain code only implements prompts, tools, and decision parsing.

## Features

- **Zero-boilerplate ReAct loop** — `AbstractAgentRunner` handles the step loop, tool dispatch, phase transitions, and termination conditions
- **Sealed step types** — `Respond`, `CallTool`, `TransitionPhase`, `Custom` via Java 21 sealed interfaces
- **Advisor middleware chain** — rate limiting, retry with exponential backoff, token budgeting, observability, confidence guard
- **Provider-agnostic LLM abstraction** — swap between Anthropic SDK, Spring AI, or any custom provider
- **Spring Boot auto-configuration** — `@EnableAgentFramework` wires tools, memory, observers, and config automatically
- **Micrometer observability** — run duration, step counts, token usage, tool call metrics out of the box
- **Type-safe configuration** — `AgentConfig` with in-memory, properties, and JDBC-backed implementations
- **Thread-safe state management** — `DefaultAgentState` with synchronized transitions and metadata

## Modules

| Module | Description | External Dependencies |
|---|---|---|
| `agent-core` | Contracts, runner, tools, memory, observer, state | SLF4J only |
| `agent-llm` | LLM client abstraction (`LlmClient`, `LlmRequest`, `LlmResponse`) | SLF4J only |
| `agent-advisor` | Middleware chain with built-in advisors | SLF4J only |
| `agent-config` | Typed configuration with SPI for persistence | SLF4J only |
| `agent-anthropic` | Anthropic SDK adapter | Anthropic Java SDK |
| `agent-spring-ai` | Spring AI adapter, Micrometer observer, advisor bridge | Spring AI, Micrometer |
| `agent-spring` | Spring Boot auto-configuration | Spring Boot |

## Quick Start

### Maven

```xml
<!-- Core + LLM + Advisor -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-llm</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- Pick ONE LLM adapter -->
<!-- Option A: Anthropic SDK -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-anthropic</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- Option B: Spring AI -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-spring-ai</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot auto-config (optional) -->
<dependency>
    <groupId>io.agentframework</groupId>
    <artifactId>agent-spring</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Implement Your Agent

1. **Define your tools** by implementing `AgentTool`
2. **Extend `AbstractAgentRunner`** (or `AbstractAnthropicAgentRunner` / `AbstractSpringAiAgentRunner`)
3. **Implement 3 methods**: `buildContext()`, `callLlm()`, `parseDecision()`

```java
public class MyAgent extends AbstractAnthropicAgentRunner<MyDomainData> {

    @Override
    protected List<LlmMessage> buildContext(AgentState<MyDomainData> state,
                                             String userInput,
                                             List<String> missingFields) {
        // Build your prompt messages
    }

    @Override
    protected AgentResult<AgentDecision> parseDecision(String rawResponse) {
        // Parse LLM response into a decision
    }

    @Override
    protected String buildFallbackOutput(AgentState<MyDomainData> state, String reason) {
        return "I'm sorry, I couldn't complete that request: " + reason;
    }
}
```

### Spring Boot Configuration

```yaml
agent:
  runner:
    max-steps-per-turn: 20
    min-confidence-threshold: 0.6
  llm:
    model: claude-sonnet-4-20250514
    temperature: 0.3
  tools:
    enabled:
      search_flights: true
      book_hotel: false
```

## Building

```bash
# Compile and test
mvn clean verify

# Build with release profile
mvn clean verify -P release
```

Requires Java 21+.

## License

[Apache License 2.0](LICENSE)
