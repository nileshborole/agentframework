package io.agentframework.spring.registry;

import io.agentframework.core.tool.AgentTool;
import io.agentframework.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.Map;

/**
 * Discovers all {@link AgentTool} beans in the Spring context after refresh
 * and registers them into the framework's {@link ToolRegistry}.
 *
 * <p>This approach handles tools registered programmatically after the initial
 * auto-configuration runs — for example, tools added by domain modules that
 * load conditionally.
 *
 * <p>Registered automatically by {@code AgentFrameworkAutoConfiguration}.
 * Safe to call multiple times — {@link ToolRegistry#register} replaces
 * existing tools with a warning, so duplicate registration is handled.
 */
public class AgentToolAutoRegistrar
        implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(AgentToolAutoRegistrar.class);

    private final ToolRegistry registry;

    public AgentToolAutoRegistrar(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, AgentTool> toolBeans =
                event.getApplicationContext().getBeansOfType(AgentTool.class);

        if (toolBeans.isEmpty()) {
            log.debug("[AgentToolAutoRegistrar] No AgentTool beans found after context refresh.");
            return;
        }

        toolBeans.values().forEach(tool -> {
            if (!registry.contains(tool.name())) {
                registry.register(tool);
                log.debug("[AgentToolAutoRegistrar] Late-registered tool: {}", tool.name());
            }
        });

        log.info("[AgentToolAutoRegistrar] ToolRegistry contains {} tools: {}",
                registry.toolNames().size(), registry.toolNames());
    }
}