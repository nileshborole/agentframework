package io.agentframework.spring.annotation;

import io.agentframework.spring.autoconfigure.AgentFrameworkAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables the Agent Framework in a Spring application.
 *
 * <p>Place on a {@code @Configuration} class to activate:
 * <ul>
 *   <li>Auto-registration of all {@code AgentTool} beans into {@link io.agentframework.core.tool.ToolRegistry}</li>
 *   <li>Default {@link io.agentframework.core.memory.AgentMemory} bean ({@code InMemoryAgentMemory})</li>
 *   <li>Default {@link io.agentframework.core.observer.AgentObserver} bean ({@code Slf4jAgentObserver})</li>
 *   <li>Default {@link io.agentframework.config.AgentConfig} bean (reads from Spring {@code Environment})</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * {@literal @}Configuration
 * {@literal @}EnableAgentFramework
 * public class AppConfig {
 *     // Override any default bean here
 *     {@literal @}Bean
 *     public AgentObserver agentObserver(MeterRegistry registry) {
 *         return new CompositeAgentObserver(List.of(
 *             new Slf4jAgentObserver(),
 *             new MicrometerAgentObserver(registry, "trip-runner")
 *         ));
 *     }
 * }
 * </pre>
 *
 * <p>All auto-configured beans use {@code @ConditionalOnMissingBean} so
 * application-defined beans always take precedence.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AgentFrameworkAutoConfiguration.class)
public @interface EnableAgentFramework {
}