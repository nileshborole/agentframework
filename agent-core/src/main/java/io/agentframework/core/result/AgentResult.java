package io.agentframework.core.result;

import java.util.Optional;
import java.util.function.Function;

/**
 * Typed outcome of an agent operation — either a success carrying a value,
 * or a failure carrying a reason and optional cause.
 *
 * <p>Replaces inconsistent error handling patterns (null returns, raw exceptions,
 * boolean flags) with a single, composable result type used throughout the framework.
 *
 * @param <T> the type of the success value
 */
public sealed interface AgentResult<T>
        permits AgentResult.Success, AgentResult.Failure {

    /** Returns true if this result represents a successful operation. */
    boolean isSuccess();

    /** Returns true if this result represents a failed operation. */
    default boolean isFailure() { return !isSuccess(); }

    /**
     * Returns the success value wrapped in Optional, or empty on failure.
     */
    Optional<T> value();

    /**
     * Returns the failure reason, or empty on success.
     */
    Optional<String> failureReason();

    /**
     * Returns the underlying cause of the failure, if one exists.
     */
    Optional<Throwable> cause();

    /**
     * Maps the success value to a new type. Failures pass through unchanged.
     */
    <U> AgentResult<U> map(Function<T, U> mapper);

    /**
     * Returns the success value or a fallback on failure.
     */
    T orElse(T fallback);

    // ── Factory methods ───────────────────────────────────────────────────

    static <T> AgentResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> AgentResult<T> failure(String reason) {
        return new Failure<>(reason, null);
    }

    static <T> AgentResult<T> failure(String reason, Throwable cause) {
        return new Failure<>(reason, cause);
    }

    static <T> AgentResult<T> failure(Throwable cause) {
        return new Failure<>(cause.getMessage(), cause);
    }

    // ── Implementations ───────────────────────────────────────────────────

    record Success<T>(T data) implements AgentResult<T> {

        @Override public boolean isSuccess() { return true; }

        @Override public Optional<T> value() { return Optional.ofNullable(data); }

        @Override public Optional<String> failureReason() { return Optional.empty(); }

        @Override public Optional<Throwable> cause() { return Optional.empty(); }

        @Override
        public <U> AgentResult<U> map(Function<T, U> mapper) {
            return AgentResult.success(mapper.apply(data));
        }

        @Override public T orElse(T fallback) { return data; }
    }

    record Failure<T>(String reason, Throwable error) implements AgentResult<T> {

        @Override public boolean isSuccess() { return false; }

        @Override public Optional<T> value() { return Optional.empty(); }

        @Override public Optional<String> failureReason() { return Optional.ofNullable(reason); }

        @Override public Optional<Throwable> cause() { return Optional.ofNullable(error); }

        @Override
        @SuppressWarnings("unchecked")
        public <U> AgentResult<U> map(Function<T, U> mapper) {
            return (AgentResult<U>) this;
        }

        @Override public T orElse(T fallback) { return fallback; }
    }
}