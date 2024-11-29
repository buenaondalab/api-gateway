package com.buenaondalab.garden.gateway;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import static org.springframework.http.HttpStatus.Series.*;

@Configuration
@EnableDiscoveryClient
public class DiscoveryRouteLocatorConfig {

    @Value("${garden.services.base-uri}")
    String baseUri;

    Logger log = Logger.getLogger(this.getClass().getName());

    @Bean
    RouteLocator myRoutes(RouteLocatorBuilder builder, DiscoveryClient discoveryClient) {

		return () -> {
			Builder routeBuilder = builder.routes();
			List<String> services = discoveryClient.getServices();
            log.info(() -> "Building routes for the following services: " + services.toString());
			return discoveryRouteLocator(services, routeBuilder, discoveryClient).getRoutes();
		};		
	}

	private RouteLocator discoveryRouteLocator(List<String> services, Builder routeBuilder, DiscoveryClient discoveryClient) {
		services.forEach(service -> {
			int n = discoveryClient.getInstances(service).size();
			addServiceRoute(routeBuilder, service, n-1);
		});
		return routeBuilder.build();
	}

	// TO TEST:
	// - proper path rewrite -- OK
	// - proper circuit breaker configuration -- OK (take garden configuration)
	// - global filter cache -- OK
	// - call to a not existing service
	// - using same name but multiple circuit breaker? RouteId?
	private Builder addServiceRoute(Builder routeBuilder, String service, int retries) {
		
		return routeBuilder.route(service, p ->
				p.path("/" + service + "/**")
				 .filters(f -> f
					// .localResponseCache(Duration.ofMinutes(10), null)
					.rewritePath("/"+service+"/?(?<remaining>.*)", "/${remaining}")
					.retry(config -> config.setMethods(HttpMethod.GET).setSeries(SERVER_ERROR).setRetries(retries))
					.circuitBreaker(config -> config.setName(service).setFallbackUri("forward:/fallback/"+service))
				 )
				 .uri(baseUri+service)
			);
	}
    
}
