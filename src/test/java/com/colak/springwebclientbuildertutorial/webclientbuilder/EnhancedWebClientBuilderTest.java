package com.colak.springwebclientbuildertutorial.webclientbuilder;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@ExtendWith(WireMockExtension.class)
@WireMockTest(httpPort = 8080)
class EnhancedWebClientBuilderTest {

    @Test
    void testWithRetry() {
        WireMock.stubFor(WireMock.get("/").willReturn(WireMock.serverError()));

        var client = new EnhancedWebClientBuilder(Duration.ofMillis(100), "http://localhost:8080")
                .withCircuitBreaker(50, Duration.ofMillis(200), 1, 2)
                .withRetry((byte) 2, Duration.ofMillis(50), 0.5)
                .build();
        invokeAndSwallowException(client, 1);
        WireMock.verify(WireMock.exactly(3), WireMock.getRequestedFor(WireMock.urlEqualTo("/")));
    }

    void invokeAndSwallowException(WebClient client, int times) {
        for (int i = 0; i < times; i++) {
            try {
                client.get().retrieve().bodyToMono(String.class).block();
            } catch (Exception ignored) {
            }
        }
    }

}
