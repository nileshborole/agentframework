package io.agentframework.example.runner;

import io.agentframework.core.AgentDecision;
import io.agentframework.core.StepType;
import io.agentframework.core.result.AgentResult;
import io.agentframework.example.domain.WeatherPhase;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw LLM text responses into typed {@link AgentDecision} objects.
 *
 * <p>This is the bridge between unstructured LLM output and the framework's
 * typed decision system. In production, you would typically instruct the LLM
 * to respond in JSON and parse it. This example uses a simple text-based
 * protocol for demonstration without requiring an actual LLM.
 *
 * <h3>Expected LLM Response Format</h3>
 * <pre>
 * ACTION: RESPOND | CALL_TOOL | END
 * CONFIDENCE: 0.95
 * TOOL: tool_name (only if ACTION=CALL_TOOL)
 * TOOL_INPUT: key=value,key2=value2 (only if ACTION=CALL_TOOL)
 * REPLY: The text to send to the user
 * </pre>
 */
public class WeatherDecisionParser {

    private static final Pattern ACTION_PATTERN = Pattern.compile("ACTION:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("CONFIDENCE:\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_PATTERN = Pattern.compile("TOOL:\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_INPUT_PATTERN = Pattern.compile("TOOL_INPUT:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_PATTERN = Pattern.compile("REPLY:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Parse a raw LLM response string into an {@link AgentDecision}.
     *
     * @param rawResponse the raw text from the LLM
     * @return success with parsed decision, or failure with parse error
     */
    public AgentResult<AgentDecision> parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return AgentResult.failure("Empty LLM response");
        }

        try {
            String action = extractRequired(rawResponse, ACTION_PATTERN, "ACTION");
            double confidence = extractDouble(rawResponse, CONFIDENCE_PATTERN, 0.9);
            String reply = extractOptional(rawResponse, REPLY_PATTERN, "");

            StepType stepType = switch (action.toUpperCase()) {
                case "RESPOND" -> new StepType.Respond();
                case "CALL_TOOL" -> {
                    String toolName = extractRequired(rawResponse, TOOL_PATTERN, "TOOL");
                    Map<String, Object> toolInput = parseToolInput(
                            extractOptional(rawResponse, TOOL_INPUT_PATTERN, ""));
                    yield new StepType.CallTool(toolName, toolInput, reply.isBlank() ? null : reply);
                }
                case "END" -> new StepType.TransitionPhase(WeatherPhase.DONE, reply);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };

            // Build the decision record
            final StepType finalStepType = stepType;
            final double finalConfidence = confidence;
            final String finalReply = reply;

            return AgentResult.success(new AgentDecision() {
                @Override public StepType stepType() { return finalStepType; }
                @Override public String reply() { return finalReply; }
                @Override public double confidence() { return finalConfidence; }
            });

        } catch (Exception e) {
            return AgentResult.failure("Failed to parse decision: " + e.getMessage(), e);
        }
    }

    private String extractRequired(String text, Pattern pattern, String fieldName) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return m.group(1).trim();
    }

    private String extractOptional(String text, Pattern pattern, String defaultValue) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : defaultValue;
    }

    private double extractDouble(String text, Pattern pattern, double defaultValue) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Map<String, Object> parseToolInput(String input) {
        Map<String, Object> map = new HashMap<>();
        if (input == null || input.isBlank()) return map;
        for (String pair : input.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }
}