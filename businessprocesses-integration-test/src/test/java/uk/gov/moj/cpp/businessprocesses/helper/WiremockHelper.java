package uk.gov.moj.cpp.businessprocesses.helper;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;

import uk.gov.justice.services.test.utils.core.http.RequestParams;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;

public class WiremockHelper {
    private static final String HOST = System.getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final String BASE_URI = "http://" + HOST + ":8080";

    public static void verifyCommandIssued(final String url, final String contentType, final String content, final long delayTimePeriod) {
        final var requestPatternBuilder = postRequestedFor(urlMatching(url));

        await().atMost(delayTimePeriod + 60, TimeUnit.SECONDS)
                .until(() -> !findAll(requestPatternBuilder).isEmpty());

        verify(1, requestPatternBuilder);
    }

    public static void verifyCommandIssued(final String url) {
        final var requestPatternBuilder = postRequestedFor(urlMatching(url));

        await().atMost( 60, TimeUnit.SECONDS)
                .until(() -> !findAll(requestPatternBuilder).isEmpty());
        verify(1, requestPatternBuilder);
    }

    public static void waitForStubToBeReady(String resource, String mediaType) {
        waitForStubToBeReady(resource, mediaType, Response.Status.OK);
    }

    public static void waitForStubToBeReady(String resource, String mediaType, Response.Status expectedStatus) {
        final RequestParams requestParams = requestParams(BASE_URI + resource, mediaType).build();

        poll(requestParams)
                .until(
                        status().is(expectedStatus)
                );
    }

    public static void waitForStubToBeReady(String resource, String mediaType, String expectedInBody) {
        final RequestParams requestParams = requestParams(BASE_URI + resource, mediaType).build();

        poll(requestParams)
                .until(
                        status().is(Response.Status.OK),
                        payload().that(containsString(expectedInBody))
                );
    }

    public static void waitForStubToBeReady(String resource, String mediaType, Response.Status expectedStatus, String headerName, String headerValue) {
        final RequestParams requestParams = requestParams(BASE_URI + resource, mediaType)
                .withHeader(headerName, headerValue)
                .build();
        poll(requestParams)
                .until(
                        status().is(expectedStatus)
                );
    }
}
