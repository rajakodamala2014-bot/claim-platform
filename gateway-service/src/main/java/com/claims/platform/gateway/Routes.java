package com.claims.platform.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.net.http.HttpClient;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * The gateway routing table. Order is precedence: the first matching route wins, so the specific Notification
 * and Insights paths come before the Claims catch-alls. Each service must only expose the paths routed to it.
 */
@Configuration
class Routes {
    static final String CLAIMS = "claims";
    static final String INSIGHTS = "insights";
    static final String NOTIFICATION = "notification";

    private final GatewayProperties properties;
    private final RateLimiter rateLimiter;
    private final UpstreamBreakers breakers;

    Routes(GatewayProperties properties) {
        this.properties = properties;
        this.rateLimiter = new RateLimiter(properties.rateLimit());
        this.breakers = new UpstreamBreakers(properties.circuitBreaker());
    }

    @Bean
    UpstreamBreakers upstreamBreakers() {
        return breakers;
    }

    @Bean
    RouterFunction<ServerResponse> gatewayRoutes() {
        GatewayProperties.Upstreams upstreams = properties.upstreams();
        RouterFunctions.Builder table = RouterFunctions.route();

        // ---- Notification Service ----
        table.add(api(NOTIFICATION, upstreams.notification(),
                path("/claimant/v1/notifications/**")
                        .or(path("/claimant/v1/notification-preferences/**"))
                        .or(path("/staff/v1/claims/{claimId}/notifications"))
                        .or(path("/admin/v1/templates/**"))
                        .or(path("/internal/v1/providers/**"))));

        // ---- Insights Service (read-only) ----
        table.add(api(INSIGHTS, upstreams.insights(),
                path("/staff/v1/work-queue/**")
                        .or(path("/staff/v1/search/**"))
                        .or(path("/staff/v1/approvals/**"))
                        .or(path("/staff/v1/insights/**"))));

        // ---- Claims Service ----
        RequestPredicate claims = path("/claimant/v1/**").or(path("/staff/v1/claims/**"));
        if (properties.devTokenRoute()) {
            // local token issuer (claims-service 'dev' profile) standing in for the identity provider
            claims = claims.or(POST("/dev/token"));
        }
        table.add(api(CLAIMS, upstreams.claims(), claims));

        // Everything else under an API prefix is not exposed: service-to-service endpoints, actuators and
        // unrouted paths answer 404 instead of falling through to the portal.
        table.add(route("not-exposed")
                .route(path("/internal/**").or(path("/actuator/**")).or(path("/dev/**"))
                                .or(path("/claimant/**")).or(path("/staff/**")).or(path("/admin/**")),
                        request -> GatewayProblems.of(HttpStatus.NOT_FOUND, GatewayProblems.ROUTE_NOT_FOUND,
                                "No route for " + request.path()))
                .build());

        // ---- Claimant portal (static build + SPA fallback) ----
        table.add(route("claims-ui")
                .route(path("/**"), http())
                .before(uri(upstreams.ui()))
                .filter(EdgeHeaders.forwardRequestHeaders())
                .build());

        return table.build();
    }

    /** An API route, outermost first: body limit, rate limit, circuit breaker, response headers, request headers, proxy. */
    private RouterFunction<ServerResponse> api(String upstream, URI target, RequestPredicate predicate) {
        HandlerFilterFunction<ServerResponse, ServerResponse> edge = EdgeHeaders.maxBodySize(properties.maxBodySize())
                .andThen(rateLimiter)
                .andThen(breakers.filter(upstream))
                .andThen(EdgeHeaders.apiResponseHeaders())
                .andThen(EdgeHeaders.forwardRequestHeaders());
        return route(upstream)
                .route(predicate, http())
                .before(uri(target))
                .filter(edge)
                .build();
    }

    /**
     * HTTP/1.1 only: the JDK client's default h2c upgrade attempt breaks POSTs against the services (same
     * reason as the Policy adapter). Redirects are passed to the caller, never followed.
     */
    @Bean
    ClientHttpRequestFactory gatewayClientHttpRequestFactory() {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.httpClient().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.httpClient().readTimeout());
        return factory;
    }
}
