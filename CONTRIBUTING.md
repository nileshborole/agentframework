# Contributing to Agent Framework

Thank you for your interest in contributing! This guide will help you get started.

## Prerequisites

- **Java 21** (with preview features enabled)
- **Maven 3.9+**
- Git

## Building

```bash
mvn clean compile
```

## Running Tests

```bash
mvn test
```

Tests use plain JUnit 5 — no Mockito, no Spring test context in core modules.

## Module Dependency Rules

These constraints are **enforced in CI** (dependency-boundary check):

| Module | Forbidden Imports |
|---|---|
| `agent-core` | No Spring, Micrometer, or Anthropic imports |
| `agent-llm` | No Spring, Micrometer, or Anthropic imports |
| `agent-advisor` | No Spring, Micrometer, or Anthropic imports |
| `agent-config` | No Spring, Micrometer, or Anthropic imports |

Only `agent-spring-ai` and `agent-spring` may import Spring. Only `agent-anthropic` may import Anthropic SDK classes.

## Code Style

- **No Lombok** — zero annotation processors in framework modules
- Every public API must have **Javadoc with a usage example**
- Use Java 21 features: sealed interfaces, records, pattern matching
- `--enable-preview` is required (configured in Maven)

## Pull Request Process

1. Fork the repository and create a feature branch from `develop`
2. Ensure all tests pass: `mvn verify`
3. Ensure the dependency-boundary check passes
4. Update `CHANGELOG.md` with your changes
5. Submit a PR targeting `develop`

## Reporting Issues

Use GitHub Issues. Please include:
- Java version and OS
- Minimal reproduction steps
- Expected vs actual behavior

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).