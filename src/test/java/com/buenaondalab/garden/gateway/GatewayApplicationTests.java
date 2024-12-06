package com.buenaondalab.garden.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Logger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "test")
@AutoConfigureWireMock(port = 0)
class GatewayApplicationTests {

	@TestConfiguration
    static class TestConfig {

        @MockBean
		DiscoveryClient discoveryClient;

        @PostConstruct
        public void initMock(){
			ServiceInstance si = mock(ServiceInstance.class);
			when(discoveryClient.getServices()).thenReturn(List.of("garden", "config", "eureka-server", "auth-server"));
			when(discoveryClient.getInstances(anyString())).thenReturn(List.of(si,si)); //! DO NOT CHANGE NUMofINSTANCES !!
        }
    }

	@Autowired
	private WebClient.Builder webClientBuilder;
	@Autowired
	private WebTestClient wtc;
	
	@Value("${wiremock.server.port}")
	private String wiremockPort;
	@LocalServerPort
	private String port;

	Logger log = Logger.getLogger("TEST");

	@Test
	@DisplayName("rewritePath filter + circuitbreaker filter (garden configuration timeout)")
	void timeoutTest() {

		final String URI = "/garden/garden/timeout";
		final String scenario = "STARTED-DELAY-TIMEOUT";
		
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(ok())
				.willSetStateTo("DELAY"));
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs("DELAY")
				.willReturn(ok().withFixedDelay(1500))
				.willSetStateTo("TIMEOUT"));
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs("TIMEOUT")
				.willReturn(ok().withFixedDelay(2500)));

		WebClient wc = webClientBuilder
			.baseUrl("http://localhost:"+port)
			.build();

		// First request -> OK
		assertTrue(reqNoCache(wc, URI).getStatusCode().is2xxSuccessful());
		// Second request -> OK
		assertTrue(reqNoCache(wc, URI).getStatusCode().is2xxSuccessful());
		// Third request -> Timeout
		assertTrue(reqNoCache(wc, URI).getStatusCode().is5xxServerError());
	}

	@Test
	@DisplayName("Local Response Cache is active")
	void cacheTest() {
		final String URI = "/garden/garden/cache";
		final String scenario = "STARTED-RETRY-TIMEOUT";

		WebClient wc = webClientBuilder
			.baseUrl("http://localhost:"+port)
			.build();
		
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(ok())
				.willSetStateTo("RETRY"));
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs("RETRY")
				.willReturn(ok().withFixedDelay(2500))
				.willSetStateTo("TIMEOUT"));
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs("TIMEOUT")
				.willReturn(ok().withFixedDelay(2500))
				.willSetStateTo(Scenario.STARTED));
		
		assertTrue(reqNoCache(wc, URI).getStatusCode().is2xxSuccessful()); // no cache
		assertTrue(req(wc, URI).getStatusCode().is5xxServerError()); // timeout
		assertTrue(req(wc, URI).getStatusCode().is2xxSuccessful()); // cache
		assertTrue(req(wc, URI).getStatusCode().is2xxSuccessful()); // cached response
	}

	@Test
	@DisplayName("Retry filter")
	void retryTest() {

		final String URI = "/garden/garden/retry";
		final String scenario = "STARTED-RETRY";

		WebClient wc = webClientBuilder
			.baseUrl("http://localhost:"+port)
			.build();
		
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs(Scenario.STARTED)
				.willReturn(ok().withFixedDelay(2500))
				.willSetStateTo("RETRY"));
		stubFor(
			get(urlPathMatching("/garden/.*"))
				.inScenario(scenario)
				.whenScenarioStateIs("RETRY")
				.willReturn(ok()));

		assertTrue(reqNoCache(wc, URI).getStatusCode().is2xxSuccessful());
	}

	private ResponseEntity<Void> req(WebClient wc, String uri) {
		return wc.get().uri(uri)
			.retrieve()
			.onStatus(code -> code.is5xxServerError(), r -> Mono.empty())
			.toBodilessEntity().block();
	}

	private ResponseEntity<Void> reqNoCache(WebClient wc, String uri) {
		return wc.get().uri(uri)
			.header("Cache-Control", "no-store")
			.retrieve()
			.onStatus(code -> code.is5xxServerError(), r -> Mono.empty())
			.toBodilessEntity().block();
	}

	@Test
	@DisplayName("Eureka routes defined")
	void eureka(){
		stubFor(
			get(urlPathMatching("/eureka/.*")).atPriority(5)
				.willReturn(ok("resources")));
		stubFor(
			get(urlPathEqualTo("/eureka/apps")).atPriority(1)
				.willReturn(ok("api")));
		stubFor(
			get(urlPathEqualTo("/"))
				.willReturn(ok("web")));

		wtc.get().uri("/eureka/api/apps").exchange().expectStatus().is2xxSuccessful().expectBody(String.class).isEqualTo("api");
		wtc.get().uri("/eureka/web").exchange().expectStatus().is2xxSuccessful().expectBody(String.class).isEqualTo("web");
		wtc.get().uri("/eureka/other").exchange().expectStatus().is2xxSuccessful().expectBody(String.class).isEqualTo("resources");
	}

	@Test
	@DisplayName("Auth server routes defined")
	void authServer(){
		stubFor(
			get(urlPathMatching("/oauth/.*"))
				.willReturn(ok("auth server")));

		wtc.get().uri("/oauth/secureme").exchange().expectStatus().is2xxSuccessful().expectBody(String.class).isEqualTo("auth server");
	}
}
