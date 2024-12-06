package com.buenaondalab.garden.gateway;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

@Configuration
@Profile("security")
public class SecurityConfig {

    private static final Logger log = Logger.getLogger(SecurityConfig.class.getName());

    @Bean
    ReactiveClientRegistrationRepository getRegistrationRepository(DiscoveryClient dc,
            @Value("${garden.auth-server.app}") String authServerServiceId,
            @Value("${spring.security.oauth2.client.registration.gateway.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.gateway.client-secret}") String clientSecret) {
        log.info("Configuring OAuth2 client");
        List<ServiceInstance> instances = dc.getInstances(authServerServiceId);
        if(instances.size() > 0) {
            ServiceInstance authServer = instances.getFirst();
            log.info("Contacting Auth Server at " + authServer.getUri().toString());
            ClientRegistration registration = ClientRegistrations.fromIssuerLocation(authServer.getUri().toString())
                .registrationId(clientId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(OidcScopes.OPENID, OidcScopes.PROFILE)
                .build();
                return new InMemoryReactiveClientRegistrationRepository(registration);
        } else {
            log.warning("Auth Server is temporarily unavailable");
            return new InMemoryReactiveClientRegistrationRepository(ClientRegistration.withRegistrationId(clientId).build());
        }
    }

	// @Bean
	// public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
	// 	http
	// 		.oauth2Login(Customizer.withDefaults())
    //         .oauth2Client(Customizer.withDefaults());
	// 	return http.build();
	// }

}