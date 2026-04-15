# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **agent-core**: Core contracts — `AgentPhase`, `AgentState`, `AgentDecision`, `StepType` (sealed interface)
- **agent-core**: Tool system — `AgentTool`, `ToolRegistry`, `ToolContext`, `ToolInputSpec`, `ToolResult`, `ToolSignal`
- **agent-core**: Memory system — `AgentMemory`, `InMemoryAgentMemory`
- **agent-core**: Observer system — `AgentObserver`, `StepTrace`, `CompositeAgentObserver`, `Slf4jAgentObserver`
- **agent-core**: Result types — `AgentResult` (sealed), `AgentRunResult`, `AgentRunOutcome`
- **agent-core**: Context builders — `ContextBuilder`, `ContextBuilderSelector`
- **agent-core**: Runner framework — `AbstractAgentRunner` (ReAct loop), `AgentRunner`, `AgentRunConfig`
- **agent-core**: Orchestration — `AbstractAgentOrchestrator`, `AgentOrchestrator`, `AgentHandoff`
- **agent-core**: State management — `DefaultAgentState` (thread-safe, factory methods)
- **agent-llm**: LLM abstraction — `LlmClient`, `LlmMessage`, `LlmRequest`, `LlmResponse`, `LlmClientConfig`
- **agent-advisor**: Middleware chain — `AgentAdvisor`, `NextAdvisor`, `AdvisorContext`, `AdvisorChain`
- **agent-advisor**: Built-in advisors — `ObservabilityAdvisor`, `RateLimitAdvisor`, `RetryAdvisor`, `TokenBudgetAdvisor`, `ConfidenceGuardAdvisor`
- **agent-config**: Configuration — `AgentConfig`, `InMemoryAgentConfig`, `PropertiesAgentConfig`
- **agent-config**: Persistent config SPI — `AgentConfigStore`, `JdbcAgentConfig` with validation and rollback
- **agent-anthropic**: Anthropic SDK integration — `AnthropicLlmClient`, `AbstractAnthropicAgentRunner`
- **agent-spring-ai**: Spring AI integration — `SpringAiLlmClient`, `AbstractSpringAiAgentRunner`, `SpringAiContextBuilder`, `MicrometerAgentObserver`, `SpringAiAdvisorBridge`, `SpringAiMemoryAdapter`
- **agent-spring**: Spring Boot auto-configuration — `@EnableAgentFramework`, `AgentFrameworkAutoConfiguration`, `AgentFrameworkProperties`, `AgentToolAutoRegistrar`
- GitHub Actions CI/CD workflows (build + release)