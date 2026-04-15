package io.agentframework.config.impl;

import io.agentframework.config.AgentConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * {@link AgentConfig} backed by a {@code .properties} file.
 *
 * <p>Read-only — does not support live updates. Suitable for
 * applications that configure via {@code application.properties}
 * or a custom properties file.
 *
 * <p>When using Spring Boot, prefer wiring this via:
 * <pre>
 * {@literal @}Bean
 * public AgentConfig agentConfig(Environment env) {
 *     return new PropertiesAgentConfig(env::getProperty);
 * }
 * </pre>
 *
 * <p>Or load from a file directly:
 * <pre>
 * AgentConfig config = PropertiesAgentConfig.fromClasspath("agent.properties");
 * </pre>
 */
public class PropertiesAgentConfig implements AgentConfig {

    private final PropertySource source;

    @FunctionalInterface
    public interface PropertySource {
        String getProperty(String key);
    }

    /**
     * Creates a config backed by any {@link PropertySource}.
     * Works with Spring's {@code Environment}, a {@code Properties} object,
     * a {@code Map}, or any function that resolves keys to values.
     */
    public PropertiesAgentConfig(PropertySource source) {
        this.source = source;
    }

    /**
     * Loads from a {@code .properties} file on the classpath.
     *
     * @param classpathResource path relative to classpath root (e.g. "agent.properties")
     * @throws IllegalArgumentException if the file cannot be found or read
     */
    public static PropertiesAgentConfig fromClasspath(String classpathResource) {
        Properties props = new Properties();
        try (InputStream is = PropertiesAgentConfig.class
                .getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "Classpath resource not found: " + classpathResource);
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to load: " + classpathResource, e);
        }
        return new PropertiesAgentConfig(props::getProperty);
    }

    /**
     * Creates from an existing {@link Properties} object.
     */
    public static PropertiesAgentConfig of(Properties properties) {
        return new PropertiesAgentConfig(properties::getProperty);
    }

    @Override
    public String getString(String key, String defaultValue) {
        String value = source.getProperty(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(source.getProperty(key));
    }
}