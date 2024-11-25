package com.buenaondalab.garden.gateway;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@SpringBootApplication
@RestController
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	// @Bean
	// public RouteLocator myRoutes(RouteLocatorBuilder builder) {
	// 	return builder.routes()
	// 		.route(p -> p
	// 			.path("/serviceId/veggies")
	// 			.filters(f -> f
	// 				.localResponseCache(Duration.ofMinutes(10), null)
	// 				.circuitBreaker(config -> config
	// 					.setName("veggiesCB")
	// 					.setFallbackUri("forward:/fallback"))
	// 			)
	// 			.uri("lb://garden/veggies"))
	// 		.build();
	// }

	@RequestMapping("/fallback")
	public Mono<String> fallback() {
		return Mono.just("Service not available");
	}

}
