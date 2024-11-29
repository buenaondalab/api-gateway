package com.buenaondalab.garden.gateway;

import java.time.Duration;

import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

@Configuration
public class CircuitBreakersConfig {

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                .build());
    }

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> gardenCustomizer() {
        return factory ->
            factory.configure(builder -> builder
                .timeLimiterConfig(timeLimiterConfig())
                .circuitBreakerConfig(circuitBreakerConfig()), "garden");
    }
    
    private CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(30))
            .slowCallRateThreshold(50)
            .slidingWindow(50, 50, SlidingWindowType.COUNT_BASED)
            .permittedNumberOfCallsInHalfOpenState(10)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
    }

    private TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(2))
            .build();
    }
}
