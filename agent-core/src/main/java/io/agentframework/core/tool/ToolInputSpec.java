package io.agentframework.core.tool;

import java.util.List;
import java.util.Optional;

/**
 * Describes a single input parameter accepted by an {@link AgentTool}.
 *
 * <p>Used to build the tool definition text injected into LLM prompts,
 * and to generate JSON schema for LLM providers that support native
 * function calling (OpenAI, Anthropic, Gemini).
 */
public record ToolInputSpec(
        String name,
        String type,
        String description,
        boolean required,
        List<String> allowedValues,
        Object defaultValue
) {

    // ── Factory methods ───────────────────────────────────────────────────

    public static ToolInputSpec required(String name, String description) {
        return new ToolInputSpec(name, "string", description, true, List.of(), null);
    }

    public static ToolInputSpec required(String name, String type, String description) {
        return new ToolInputSpec(name, type, description, true, List.of(), null);
    }

    public static ToolInputSpec optional(String name, String description, Object defaultValue) {
        return new ToolInputSpec(name, "string", description, false, List.of(), defaultValue);
    }

    public static ToolInputSpec optional(String name, String type,
                                         String description, Object defaultValue) {
        return new ToolInputSpec(name, type, description, false, List.of(), defaultValue);
    }

    public static ToolInputSpec enumParam(String name, String description,
                                          List<String> allowedValues) {
        return new ToolInputSpec(name, "string", description, true, allowedValues, null);
    }

    public static ToolInputSpec optionalEnum(String name, String description,
                                             List<String> allowedValues, String defaultValue) {
        return new ToolInputSpec(name, "string", description, false, allowedValues, defaultValue);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Optional<Object> defaultValueIfPresent() {
        return Optional.ofNullable(defaultValue);
    }

    public boolean hasAllowedValues() {
        return allowedValues != null && !allowedValues.isEmpty();
    }

    /**
     * Renders this spec as a single line for text-based tool definitions.
     * Example: {@code ticker (string, required): Stock symbol e.g. INFY. Allowed: NSE, BSE}
     */
    public String toDefinitionString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(type).append(", ")
                .append(required ? "required" : "optional").append("): ")
                .append(description);
        if (hasAllowedValues()) {
            sb.append(". Allowed values: ").append(String.join(", ", allowedValues));
        }
        if (!required && defaultValue != null) {
            sb.append(". Default: ").append(defaultValue);
        }
        return sb.toString();
    }
}