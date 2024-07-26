package com.colak.springtutorial.webclientbuilder;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ExtendWith(WireMockExtension.class)
@WireMockTest
@Slf4j
class EnhancedWebClientBuilderTest {

    // See https://medium.com/@dixitsatish34/how-to-improve-webclient-response-time-in-spring-boot-3c0c898f06b4
    @Test
    void testParallel(WireMockRuntimeInfo wmRuntimeInfo) {
        // Create server endpoints
        WireMock
                .stubFor(WireMock.get("/endpoint1")
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "text/plain; charset=UTF-8")
                                .withBody("Response from endpoint 1"))
                );
        WireMock.stubFor(WireMock.get("/endpoint2")
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "text/plain; charset=UTF-8")
                        .withBody("Response from endpoint 2"))
        );

        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        WebClient webClient = new EnhancedWebClientBuilder(Duration.ofSeconds(10), baseUrl)
                .build();


        Mono<String> response1 = webClient.get()
                .uri("/endpoint1")
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> response2 = webClient.get()
                .uri("/endpoint2")
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> combinedResponse = Mono.zip(response1, response2, (r1, r2) -> r1 + " " + r2);
        String string = combinedResponse.block();
        log.info("testParallel result : {}", string);
    }

    @Test
    void testWithRetry(WireMockRuntimeInfo wmRuntimeInfo) {
        // Create server endpoint
        WireMock.stubFor(WireMock.get("/")
                .willReturn(WireMock.serverError())
        );

        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        WebClient webClient = new EnhancedWebClientBuilder(Duration.ofMillis(100), baseUrl)
                .withCircuitBreaker(50, Duration.ofMillis(200), 1, 2)
                .withRetry((byte) 2, Duration.ofMillis(50), 0.5)
                .build();
        invokeAndSwallowException(webClient, 1);

        // Verify WireMock is called 3 times
        WireMock.verify(WireMock.exactly(3), WireMock.getRequestedFor(WireMock.urlEqualTo("/")));
    }

    void invokeAndSwallowException(WebClient webClient, int times) {
        for (int i = 0; i < times; i++) {
            try {
                webClient.get()
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } catch (Exception exception) {
                // We get a Exceptions$RetryExhaustedException because Retries exhausted: 2/2
                log.error("Exception caught ", exception);
            }
        }
    }
}
