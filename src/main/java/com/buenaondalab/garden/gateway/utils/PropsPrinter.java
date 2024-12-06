package com.buenaondalab.garden.gateway.utils;

import java.util.Collection;
import java.util.logging.Logger;

import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class PropsPrinter {

    private static final Logger LOGGER = Logger.getLogger(PropsPrinter.class.getName());

    @EventListener
    public void handleContextRefreshed(ContextRefreshedEvent event) {
        ConfigurableEnvironment env = (ConfigurableEnvironment) event.getApplicationContext().getEnvironment();
        env.getPropertySources()
            .stream()
            .filter(ps -> ps instanceof MapPropertySource)
            .filter(ps -> ps.getName().contains("application"))
            .map(ps -> ((MapPropertySource) ps).getSource().keySet())
            .flatMap(Collection::stream)
            .distinct()
            .sorted()
            .forEach(key -> LOGGER.info(() -> key+"="+env.getProperty(key)));
    }
}
