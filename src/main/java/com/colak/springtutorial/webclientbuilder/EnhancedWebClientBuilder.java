package com.colak.springtutorial.webclientbuilder;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Web client with resiliency patterns
 */
public class EnhancedWebClientBuilder {
    private final WebClient.Builder builder;

    EnhancedWebClientBuilder(Duration timeout, String baseUrl) {
        this.builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(getConnector(timeout));
    }

    public WebClient build() {
        return this.builder
                .filter(ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
                    if (clientResponse.statusCode().is5xxServerError()) {
                        return clientResponse.createError();
                    }
                    return Mono.just(clientResponse);
                })).build();
    }

    public EnhancedWebClientBuilder withHeader(Map<String, String> headerMap) {
        // Enable gzip compression to reduce the payload size and improve response times for large responses.
        // .defaultHeader("Accept-Encoding", "gzip")
        // .defaultHeader("Content-Type", "application/json")
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            builder.defaultHeader(entry.getKey(), entry.getValue());
        }
        return this;
    }

    // retry policy is a simple count based algorithm with backoff behaviour
    public EnhancedWebClientBuilder withRetry(byte attempts, Duration minBackoff, double jitter) {
        final var retry = Retry
                // exponential backoff. The first backoff starts with minBackoff
                .backoff(attempts, minBackoff)
                // Jitter adds randomness to each backoff
                .jitter(jitter);
        this.builder.filter((request, next) -> next.exchange(request)
                .retryWhen(retry));
        return this;
    }

    // circuit breaker layer takes place before retry policy
    public EnhancedWebClientBuilder withCircuitBreaker(float failureRateThreshold,
                                                       Duration waitDurationInOpenState,
                                                       int permittedNumberOfCallsInHalfOpenState,
                                                       int slidingWindowSize) {

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .slidingWindowSize(slidingWindowSize)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("web client circuit breaker", circuitBreakerConfig);
        this.builder.filter(((request, next) ->
                next.exchange(request).transform(CircuitBreakerOperator.of(circuitBreaker))
        ));
        return this;
    }

    private static ClientHttpConnector getConnector(Duration timeout) {
        // Create pool
        ConnectionProvider connectionProvider = ConnectionProvider.builder("custom")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));
        return new ReactorClientHttpConnector(httpClient);
    }

}
