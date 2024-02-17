package com.colak.springwebclientbuildertutorial.webclientbuilder;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@ExtendWith(WireMockExtension.class)
@WireMockTest
@Slf4j
class EnhancedWebClientBuilderTest {

    @Test
    void testWithRetry(WireMockRuntimeInfo wmRuntimeInfo) {
        WireMock.stubFor(WireMock.get("/").willReturn(WireMock.serverError()));

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
