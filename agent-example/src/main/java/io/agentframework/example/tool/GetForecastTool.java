package io.agentframework.example.tool;

import io.agentframework.core.tool.AgentTool;
import io.agentframework.core.tool.ToolContext;
import io.agentframework.core.tool.ToolInputSpec;
import io.agentframework.core.tool.ToolResult;

import java.util.List;
import java.util.Random;

/**
 * Example tool that retrieves a multi-day weather forecast.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Multiple input parameters (required + optional)</li>
 *   <li>Using {@link ToolInputSpec#optional} for default values</li>
 *   <li>Returning formatted multi-line output</li>
 * </ul>
 */
public class GetForecastTool implements AgentTool {

    private static final Random RANDOM = new Random();
    private static final String[] CONDITIONS = {"Sunny", "Cloudy", "Partly cloudy", "Rainy", "Clear", "Overcast"};

    @Override
    public String name() {
        return "get_forecast";
    }

    @Override
    public String description() {
        return "Get a multi-day weather forecast for a city. Returns daily high/low temperatures and conditions.";
    }

    @Override
    public List<ToolInputSpec> inputs() {
        return List.of(
                ToolInputSpec.required("city", "string", "The city name to get forecast for"),
                ToolInputSpec.optional("days", "Number of days to forecast (1-7)", 3)
        );
    }

    @Override
    public ToolResult execute(ToolContext<?> context) {
        String city = context.requireString("city");
        int days = context.optionalInt("days").orElse(3);
        days = Math.max(1, Math.min(7, days));

        StringBuilder forecast = new StringBuilder();
        forecast.append("Forecast for ").append(city).append(" (").append(days).append(" days):\n");

        int baseTemp = 15 + RANDOM.nextInt(15);
        for (int i = 1; i <= days; i++) {
            int high = baseTemp + RANDOM.nextInt(5);
            int low = baseTemp - 3 - RANDOM.nextInt(5);
            String condition = CONDITIONS[RANDOM.nextInt(CONDITIONS.length)];
            forecast.append("  Day ").append(i).append(": ")
                    .append(condition).append(", High ").append(high)
                    .append("°C / Low ").append(low).append("°C\n");
        }

        return ToolResult.continueWith(forecast.toString().trim());
    }
}