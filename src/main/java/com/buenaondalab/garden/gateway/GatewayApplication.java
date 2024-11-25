package com.buenaondalab.garden.gateway;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.ws.rs.PathParam;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;

@SpringBootApplication
@EnableDiscoveryClient
@RestController
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

    @Bean
    RouteLocator myRoutes(RouteLocatorBuilder builder, DiscoveryClient discoveryClient) {

		return () -> {
			Builder routeBuilder = builder.routes();
			List<String> services = discoveryClient.getServices();
			return gatewayLocator(services, routeBuilder).getRoutes();
		};		
	}

	private RouteLocator gatewayLocator(List<String> services, Builder routeBuilder) {
		services.forEach(service -> 
			routeBuilder.route(service, p ->
				p.path("/" + service + "/**")
				 .filters(f -> f
					// .localResponseCache(Duration.ofMinutes(10), null)
					.rewritePath("/"+service+"/?(?<remaining>.*)", "/${remaining}")
					.circuitBreaker(config -> config.setName(service+"CB").setFallbackUri("forward:/fallback/"+service))
					.retry(config -> config.allMethods().setRetries(1)))
				 .uri("lb://"+service)
		));
		return routeBuilder.build();
	}

	@RequestMapping("/fallback/{service}")
	public Mono<String> fallback(@PathVariable("service") String service) {
		return Mono.just(service + " service is not available.");
	}

}
