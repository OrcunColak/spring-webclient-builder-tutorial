package com.colak.springwebclientbuildertutorial.webclientexamples;

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
class OnStatusTest {

    @Test
    void testOnStatusTest(WireMockRuntimeInfo wmRuntimeInfo) {
        String expectedMessage = "Client error";
        ResponseDefinitionBuilder responseDefinitionBuilder = WireMock.aResponse()
                .withStatus(HttpStatus.FORBIDDEN_403).withBody("Forbidden, WireMock!");
        WireMock.stubFor(WireMock.get("/").willReturn(responseDefinitionBuilder));

        String baseUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort();
        WebClient webClient = WebClient.create(baseUrl);

        try {
            webClient.get()
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> Mono.error(new RuntimeException(expectedMessage)))
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException exception) {
            Assertions.assertEquals(expectedMessage, exception.getMessage());
        }
    }

}
