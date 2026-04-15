package io.agentframework.spring.autoconfigure;

import io.agentframework.config.AgentConfig;
import io.agentframework.config.impl.PropertiesAgentConfig;
import io.agentframework.core.memory.AgentMemory;
import io.agentframework.core.memory.InMemoryAgentMemory;
import io.agentframework.core.observer.AgentObserver;
import io.agentframework.core.observer.Slf4jAgentObserver;
import io.agentframework.core.tool.AgentTool;
import io.agentframework.core.tool.ToolRegistry;
import io.agentframework.spring.registry.AgentToolAutoRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Spring Boot auto-configuration for the Agent Framework.
 *
 * <p>Provides sensible defaults for all framework beans.
 * Every bean uses {@code @ConditionalOnMissingBean} — applications
 * override any default by declaring their own bean of the same type.
 *
 * <p>Activated via {@link io.agentframework.spring.annotation.EnableAgentFramework}
 * or through Spring Boot's auto-configuration mechanism
 * (see {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}).
 *
 * <p>What gets auto-configured:
 * <ul>
 *   <li>{@link ToolRegistry} — populated with all {@code AgentTool} beans in context</li>
 *   <li>{@link AgentMemory} — {@code InMemoryAgentMemory} by default</li>
 *   <li>{@link AgentObserver} — {@code Slf4jAgentObserver} by default</li>
 *   <li>{@link AgentConfig} — reads from Spring {@code Environment} by default</li>
 * </ul>
 */
@Configuration
public class AgentFrameworkAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(AgentFrameworkAutoConfiguration.class);

    /**
     * Auto-registers all {@code AgentTool} beans found in the Spring context.
     *
     * <p>This is the generic replacement for the Yodeki pattern of:
     * <pre>
     * {@literal @}Autowired
     * public ToolRegistry(List&lt;AgentTool&gt; agentToolList) { ... }
     * </pre>
     *
     * <p>The difference: the framework's {@link ToolRegistry} has no Spring
     * dependency. The registrar bridges Spring's bean discovery to the
     * framework's DI-agnostic registry.
     */
    @Bean
    @ConditionalOnMissingBean(ToolRegistry.class)
    public ToolRegistry toolRegistry(
            @Autowired(required = false) List<AgentTool> tools) {
        ToolRegistry registry = new ToolRegistry();
        if (tools != null) {
            tools.forEach(registry::register);
            log.info("[AgentFramework] Registered {} AgentTool beans: {}",
                    tools.size(),
                    tools.stream().map(AgentTool::name).toList());
        } else {
            log.info("[AgentFramework] No AgentTool beans found in context.");
        }
        return registry;
    }

    /**
     * Default in-memory agent memory.
     *
     * <p>Override with a JDBC or Redis implementation for distributed
     * or persistent memory:
     * <pre>
     * {@literal @}Bean
     * public AgentMemory agentMemory(DataSource ds) {
     *     return new JdbcAgentMemory(ds);
     * }
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean(AgentMemory.class)
    public AgentMemory agentMemory() {
        log.info("[AgentFramework] Using InMemoryAgentMemory " +
                "(not suitable for distributed deployments)");
        return new InMemoryAgentMemory();
    }

    /**
     * Default SLF4J structured logging observer.
     *
     * <p>Override with a Micrometer + SLF4J composite for production:
     * <pre>
     * {@literal @}Bean
     * public AgentObserver agentObserver(MeterRegistry registry) {
     *     return new CompositeAgentObserver(List.of(
     *         new Slf4jAgentObserver(),
     *         new MicrometerAgentObserver(registry, "my-runner")
     *     ));
     * }
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean(AgentObserver.class)
    public AgentObserver agentObserver() {
        log.info("[AgentFramework] Using Slf4jAgentObserver " +
                "(override with MicrometerAgentObserver for metrics)");
        return new Slf4jAgentObserver();
    }

    /**
     * Default config backed by Spring's {@link Environment}.
     *
     * <p>Reads all agent config from {@code application.properties} /
     * {@code application.yml} using the standard Spring property resolution.
     *
     * <p>Override with a JDBC-backed implementation for live reloading:
     * <pre>
     * {@literal @}Bean
     * public AgentConfig agentConfig(AgentConfigDao dao) {
     *     return new JdbcAgentConfig(dao);
     * }
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean(AgentConfig.class)
    public AgentConfig agentConfig(Environment environment) {
        log.info("[AgentFramework] Using Spring Environment-backed AgentConfig");
        return new PropertiesAgentConfig(key -> environment.getProperty(key));
    }

    /**
     * The auto-registrar that discovers and registers {@code AgentTool} beans.
     * Separated from {@link #toolRegistry} so it can be reused independently.
     */
    @Bean
    @ConditionalOnMissingBean(AgentToolAutoRegistrar.class)
    public AgentToolAutoRegistrar agentToolAutoRegistrar(ToolRegistry registry) {
        return new AgentToolAutoRegistrar(registry);
    }
}