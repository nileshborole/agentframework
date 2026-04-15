package io.agentframework.advisor.builtin;

import io.agentframework.advisor.AdvisorContext;
import io.agentframework.advisor.AgentAdvisor;
import io.agentframework.advisor.NextAdvisor;
import io.agentframework.core.result.AgentResult;
import io.agentframework.llm.LlmRequest;
import io.agentframework.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects low-confidence LLM responses and signals the runner via
 * an {@link AdvisorContext} attribute.
 *
 * <p>Generic replacement for your existing {@code ConfidenceGuardAdvisor}.
 * No domain coupling — reads the {@code confidence} field from any
 * JSON-structured LLM response.
 *
 * <p>Callers configure the threshold and mode via {@link AdvisorContext} params:
 * <pre>
 * AdvisorContext ctx = AdvisorContext.builder()
 *     .param(ConfidenceGuardAdvisor.THRESHOLD_KEY, 0.5)
 *     .build();
 * </pre>
 *
 * <p>After the chain runs, the runner reads the result:
 * <pre>
 * boolean lowConfidence = context.attribute(
 *     ConfidenceGuardAdvisor.LOW_CONFIDENCE_ATTR, Boolean.class)
 *     .orElse(false);
 * </pre>
 *
 * <p>Order: 50 — runs after retry (30) and token budget (40),
 * before domain-specific advisors (60+).
 */
public class ConfidenceGuardAdvisor implements AgentAdvisor {

    public static final String THRESHOLD_KEY       = "confidence.threshold";
    public static final String LOW_CONFIDENCE_ATTR = "confidence.isLow";
    public static final String CONFIDENCE_VALUE_ATTR = "confidence.value";

    private static final Logger log = LoggerFactory.getLogger(ConfidenceGuardAdvisor.class);
    private static final int ORDER = 50;
    private static final double DEFAULT_THRESHOLD = 0.5;

    @Override
    public String name()  { return "ConfidenceGuardAdvisor"; }

    @Override
    public int order()    { return ORDER; }

    @Override
    public AgentResult<LlmResponse> advise(LlmRequest request,
                                           AdvisorContext context,
                                           NextAdvisor chain) {
        AgentResult<LlmResponse> result = chain.next(request, context);

        if (result.isFailure()) return result;

        double threshold = context.paramDouble(THRESHOLD_KEY, DEFAULT_THRESHOLD);
        double confidence = extractConfidence(result.value().get().content());

        boolean isLow = confidence > 0.0 && confidence < threshold;

        // Write to context attributes so runner and downstream advisors can read
        context.setAttribute(LOW_CONFIDENCE_ATTR, isLow);
        context.setAttribute(CONFIDENCE_VALUE_ATTR, confidence);

        if (isLow) {
            log.info("[ConfidenceGuardAdvisor] sessionId={} step={} "
                            + "low confidence detected: {:.2f} < threshold {:.2f}",
                    context.sessionId(), context.stepNumber(), confidence, threshold);
        }

        return result;
    }

    /**
     * Extracts the {@code confidence} field from a JSON string.
     * Returns 0.0 if the field is absent or the content is not valid JSON.
     */
    private double extractConfidence(String content) {
        if (content == null || content.isBlank()) return 0.0;
        try {
            // Lightweight extraction without a full JSON parser dependency.
            // Looks for: "confidence": 0.85 or "confidence":0.85
            int idx = content.indexOf("\"confidence\"");
            if (idx < 0) return 0.0;
            int colon = content.indexOf(':', idx);
            if (colon < 0) return 0.0;
            int start = colon + 1;
            // Skip whitespace
            while (start < content.length()
                    && Character.isWhitespace(content.charAt(start))) start++;
            int end = start;
            while (end < content.length()
                    && (Character.isDigit(content.charAt(end))
                    || content.charAt(end) == '.'
                    || content.charAt(end) == '-')) end++;
            if (end == start) return 0.0;
            return Double.parseDouble(content.substring(start, end));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

}