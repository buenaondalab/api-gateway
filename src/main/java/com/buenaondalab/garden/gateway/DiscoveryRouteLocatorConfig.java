package com.buenaondalab.garden.gateway;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
	@Value("${garden.eureka.host}")
	String eurekaHost;
	@Value("${garden.eureka.port}")
	String eurekaPort;

    Logger log = Logger.getLogger(this.getClass().getName());

    @Bean
    RouteLocator myRoutes(RouteLocatorBuilder builder, DiscoveryClient discoveryClient) {

		String eurekaBaseUri = "http://"+eurekaHost+":"+eurekaPort;
		return () -> {
			Builder routeBuilder = builder.routes();
			return addDiscoveryRoutes(routeBuilder, discoveryClient)
			// TODO: check if eureka is self registered, if not use garden.eureka.host etc...
				.route("eureka-api", p -> p.path("/eureka/api/{remaining}").filters(f -> f.setPath("/eureka/{remaining}")).uri(eurekaBaseUri))
				.route("eureka-web", p -> p.path("/eureka/web").filters(f -> f.setPath("/")).uri(eurekaBaseUri))
				.route("eureka-web-resources", p -> p.path("/eureka/**").uri(eurekaBaseUri))
				
				.route("auth-server", p -> p.path("/oauth/**").uri(baseUri+"auth-server"))
				.build().getRoutes();
		};		
	}

	private Builder addDiscoveryRoutes(Builder routeBuilder, DiscoveryClient discoveryClient) {
		List<String> routableServices =
			discoveryClient.getServices().stream().filter(s -> !s.equalsIgnoreCase("eureka-server"))
												  .filter(s -> !s.equalsIgnoreCase("auth-server"))
												  .collect(Collectors.toList());
		log.info(() -> "Building routes for the following services: " + routableServices.toString());
		routableServices.forEach(service -> {
			int n = discoveryClient.getInstances(service).size();
			addServiceRoute(routeBuilder, service, Math.max(1, n-1));
		});
		return routeBuilder;
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
					.tokenRelay()
					.retry(config -> config.setMethods(HttpMethod.GET).setSeries(SERVER_ERROR).setRetries(retries))
					.circuitBreaker(config -> config.setName(service).setFallbackUri("forward:/fallback/"+service))
				 )
				 .uri(baseUri+service)
			);
	}
    
}
