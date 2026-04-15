package io.agentframework.example.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain data for the weather assistant agent.
 *
 * <p>This is the "S" type parameter in {@code AgentState<S>}. It holds
 * any domain-specific state that persists across the agent loop iterations
 * within a single session.
 *
 * <p>For this weather example, we track:
 * <ul>
 *   <li>The cities the user has asked about</li>
 *   <li>The last weather result retrieved</li>
 * </ul>
 */
public class WeatherData {

    private final List<String> queriedCities = new ArrayList<>();
    private String lastWeatherResult;

    public List<String> getQueriedCities() {
        return queriedCities;
    }

    public void addQueriedCity(String city) {
        queriedCities.add(city);
    }

    public String getLastWeatherResult() {
        return lastWeatherResult;
    }

    public void setLastWeatherResult(String lastWeatherResult) {
        this.lastWeatherResult = lastWeatherResult;
    }

    @Override
    public String toString() {
        return "WeatherData{queriedCities=" + queriedCities
                + ", lastResult=" + (lastWeatherResult != null ? lastWeatherResult.substring(0, Math.min(50, lastWeatherResult.length())) + "..." : "null")
                + "}";
    }
}