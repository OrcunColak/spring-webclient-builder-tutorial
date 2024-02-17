package com.colak.springwebclientbuildertutorial.webclientexamples.mono;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import wiremock.org.eclipse.jetty.http.HttpStatus;

/**
 * See <a href="https://mohosinmiah1610.medium.com/error-handling-with-webclient-in-spring-boot-e604733071e0">...</a>
 */
@ExtendWith(WireMockExtension.class)
@WireMockTest(httpPort = 8080)
@Slf4j
class OnErrorMapTest {

    @Test
    void test(WireMockRuntimeInfo wmRuntimeInfo) {
        // Wiremock setup
        ResponseDefinitionBuilder responseDefinitionBuilder = WireMock.aResponse()
                .withStatus(HttpStatus.FORBIDDEN_403).withBody("Forbidden, WireMock!");
        WireMock.stubFor(WireMock.get("/").willReturn(responseDefinitionBuilder));

        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        WebClient webClient = WebClient.create(baseUrl);

        String expectedMessage = "Client error";
        try {
            webClient.get()
                    .retrieve()
                    // Convert FORBIDDEN_403 from WireMock to exception
                    .onStatus(HttpStatusCode::isError,
                            clientResponse ->
                                    switch (clientResponse.statusCode().value()) {
                                        case 400 -> Mono.error(new RuntimeException("400"));
                                        default -> Mono.error(new RuntimeException("Mono error"));
                                    })
                    .bodyToMono(String.class)
                    // This is the example
                    // Convert exception from onStatus to another exception with Mono
                    .onErrorMap(Exception.class, exception -> new RuntimeException(expectedMessage, exception))
                    .block();
        } catch (RuntimeException exception) {
            Assertions.assertEquals(expectedMessage, exception.getMessage());
        }
    }

}
