package io.agentframework.core.tool;

import java.util.List;

/**
 * Contract for all tools available to an agent.
 *
 * <p>Tools are domain-defined capabilities the LLM can invoke —
 * web search, database queries, external APIs, sub-agents, etc.
 * The framework discovers and dispatches to them; it never knows
 * what they do internally.
 *
 * <p>Implementing classes are registered in {@link ToolRegistry}.
 * With Spring, annotate with {@code @Component} for auto-registration.
 * Without Spring, register manually via {@link ToolRegistry#register}.
 *
 * <p>Example minimal implementation:
 * <pre>
 * {@literal @}Component
 * public class StockPriceTool implements AgentTool {
 *     {@literal @}Override public String name() { return "get_stock_price"; }
 *     {@literal @}Override public String description() {
 *         return "Fetches current price for a stock ticker. Use for real-time price data.";
 *     }
 *     {@literal @}Override public List&lt;ToolInputSpec&gt; inputs() {
 *         return List.of(ToolInputSpec.required("ticker", "Stock symbol e.g. INFY"));
 *     }
 *     {@literal @}Override public ToolResult execute(ToolContext&lt;?&gt; ctx) {
 *         String ticker = ctx.requireString("ticker");
 *         String price = stockApi.fetch(ticker);
 *         return ToolResult.continueWith("Price for " + ticker + ": " + price);
 *     }
 * }
 * </pre>
 */
public interface AgentTool {

    /**
     * Unique, stable name for this tool. Used by the LLM to invoke it
     * and by the registry to look it up. Use snake_case.
     * Example: {@code "search_web"}, {@code "get_stock_price"}
     */
    String name();

    /**
     * Human-readable description for the LLM. Must explain:
     * <ul>
     *   <li>What the tool does</li>
     *   <li>When to use it</li>
     *   <li>What it does NOT do (boundaries)</li>
     * </ul>
     */
    String description();

    /**
     * Input parameter specifications for this tool.
     * Used to build the tool definition injected into the LLM prompt.
     */
    List<ToolInputSpec> inputs();

    /**
     * Executes the tool with the given context.
     *
     * <p>Implementations must never throw unchecked exceptions to the caller.
     * All errors must be returned as {@link ToolResult#error(String)}.
     *
     * @param context the execution context carrying state and input parameters
     * @return the tool result — never null
     */
    ToolResult execute(ToolContext<?> context);

    /**
     * Generates the tool definition string injected into the LLM system prompt.
     * Default implementation builds a structured text block from name, description,
     * and inputs. Override to customise formatting for your LLM provider.
     */
    default String toolDefinition() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(name()).append("\n");
        sb.append("Description: ").append(description()).append("\n");
        if (!inputs().isEmpty()) {
            sb.append("Inputs:\n");
            inputs().forEach(input -> sb.append("  - ").append(input.toDefinitionString()).append("\n"));
        }
        return sb.toString();
    }
}