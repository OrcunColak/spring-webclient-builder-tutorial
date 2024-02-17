package com.colak.springwebclientbuildertutorial.webclientexamples.webclientbuildermethods;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import wiremock.org.eclipse.jetty.http.HttpStatus;

/**
 * Create a filter using WebClient.builder().filter()
 * See <a href="https://medium.com/@vigneshwaran4817/exceptional-handling-in-spring-boot-best-practices-for-seamless-error-management-b9024787cbc9">...</a>
 */
@ExtendWith(WireMockExtension.class)
@WireMockTest(httpPort = 8080)
@Slf4j
class OfResponseProcessorTest {

    @Test
    void test(WireMockRuntimeInfo wmRuntimeInfo) {
        // Wiremock setup
        ResponseDefinitionBuilder responseDefinitionBuilder = WireMock.aResponse()
                .withStatus(HttpStatus.FORBIDDEN_403).withBody("Forbidden, WireMock!");
        WireMock.stubFor(WireMock.get("/").willReturn(responseDefinitionBuilder));

        String expectedMessage = "Expected Message";
        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                // This is the example
                // Create a filter to do something according to HttpStatus codes
                .filter(ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
                    if (clientResponse.statusCode().is4xxClientError()) {
                        return Mono.error(new RuntimeException(expectedMessage));
                    }
                    return Mono.just(clientResponse);
                }))
                .build();


        try {
            webClient.get()
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException exception) {
            Assertions.assertEquals(expectedMessage, exception.getMessage());
        }
    }
}
