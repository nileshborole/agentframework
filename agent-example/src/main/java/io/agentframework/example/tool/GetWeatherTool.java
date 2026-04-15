package io.agentframework.example.tool;

import io.agentframework.core.tool.AgentTool;
import io.agentframework.core.tool.ToolContext;
import io.agentframework.core.tool.ToolInputSpec;
import io.agentframework.core.tool.ToolResult;
import io.agentframework.example.domain.WeatherData;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Example tool that retrieves current weather for a city.
 *
 * <p>Every tool implements {@link AgentTool} with four methods:
 * <ul>
 *   <li>{@link #name()} — unique tool identifier the LLM uses to call it</li>
 *   <li>{@link #description()} — tells the LLM what this tool does</li>
 *   <li>{@link #inputs()} — declares the expected input parameters</li>
 *   <li>{@link #execute(ToolContext)} — the actual tool logic</li>
 * </ul>
 *
 * <p>This is a mock implementation. In production, you would call a real weather API.
 */
public class GetWeatherTool implements AgentTool {

    private static final Random RANDOM = new Random();

    private static final Map<String, String> MOCK_WEATHER = Map.of(
            "london", "Cloudy, 14°C, humidity 78%, wind 12 km/h NW",
            "paris", "Sunny, 22°C, humidity 45%, wind 8 km/h E",
            "tokyo", "Partly cloudy, 26°C, humidity 65%, wind 15 km/h S",
            "new york", "Clear, 19°C, humidity 55%, wind 10 km/h W",
            "sydney", "Rainy, 18°C, humidity 88%, wind 20 km/h SE"
    );

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "Get the current weather conditions for a specified city. "
                + "Returns temperature, conditions, humidity, and wind speed.";
    }

    @Override
    public List<ToolInputSpec> inputs() {
        return List.of(
                ToolInputSpec.required("city", "string",
                        "The city name to get weather for (e.g., 'London', 'Tokyo')")
        );
    }

    @Override
    public ToolResult execute(ToolContext<?> context) {
        // Extract the required "city" parameter
        String city = context.requireString("city");

        // Check for missing fields (framework populates this when LLM omits required params)
        if (context.isMissing("city")) {
            return ToolResult.error("City name is required. Please specify a city.");
        }

        // Look up weather (mock data)
        String weather = MOCK_WEATHER.getOrDefault(
                city.toLowerCase().trim(),
                "Clear, " + (15 + RANDOM.nextInt(20)) + "°C, humidity "
                        + (40 + RANDOM.nextInt(50)) + "%, wind "
                        + (5 + RANDOM.nextInt(25)) + " km/h"
        );

        // Update domain data if the context is typed to our domain
        // This shows how tools can interact with domain state
        try {
            @SuppressWarnings("unchecked")
            ToolContext<WeatherData> weatherCtx = (ToolContext<WeatherData>) context;
            weatherCtx.state().domainData().addQueriedCity(city);
            weatherCtx.state().domainData().setLastWeatherResult(weather);
        } catch (ClassCastException ignored) {
            // Not our domain — that's fine, tool still works
        }

        String result = "Weather for " + city + ": " + weather;

        // Return with CONTINUE signal — the agent loop will keep running
        return ToolResult.continueWith(result);
    }
}