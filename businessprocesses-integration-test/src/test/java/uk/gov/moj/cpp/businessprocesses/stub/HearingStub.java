package uk.gov.moj.cpp.businessprocesses.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.util.UUID.randomUUID;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.moj.cpp.businessprocesses.helper.WiremockHelper.waitForStubToBeReady;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;

import java.io.IOException;

public class HearingStub {

    public static final String HEARING_SERVICE_NAME = "hearing-service";
    private static final String HEARING_GET_HEARING_URL = "/hearing-service/query/api/rest/hearing/hearings/";
    private static final String HEARING_GET_HEARING_MEDIA_TYPE = "application/vnd.hearing.get.hearing+json";

    public static void stubGetHearing(final String hearingId, final String payload) throws IOException {
        stubPingFor(HEARING_SERVICE_NAME);

        stubFor(get(urlPathMatching(HEARING_GET_HEARING_URL + hearingId))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", HEARING_GET_HEARING_MEDIA_TYPE)
                        .withBody(payload)));

        waitForStubToBeReady(HEARING_GET_HEARING_URL + hearingId, HEARING_GET_HEARING_MEDIA_TYPE);
    }
}
