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

public class ProgressionServiceStub {
    private static final String PROGRESSION_SERVICE_NAME = "progression-service";
    private static final String PROSECUTION_CASE_CAAG_JSON = "prosecutionCaseCaag.json";
    private static final String PROSECUTION_CASE_JSON = "prosecutionCaseCaag.json";
    private static final String PROSECUTION_CASE_CAAG_WITH_APPLICATION_JSON = "prosecutionCaseCaagWithApplication.json";
    private static final String PROGRESSION_QUERY_PROSECUTIONCASE_CAAG_JSON = "application/vnd.progression.query.prosecutioncase.caag+json";
    private static final String PROGRESSION_QUERY_PROSECUTIONCASE_JSON = "application/vnd.progression.query.prosecutioncase+json";
    private static final String PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL = "/progression-service/query/api/rest/progression/prosecutioncases/";

    public static final String PROGRESSION_PROSECUTION_JSON = "progression-prosecution-case.json";
    public static void stubProsecutionCaseCaag(String caseId) throws IOException {
        stubPingFor(PROGRESSION_SERVICE_NAME);
        final String payload = getJsonStringFromResource(PROSECUTION_CASE_CAAG_JSON);

        stubFor(get(urlPathMatching(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTIONCASE_CAAG_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId, PROGRESSION_QUERY_PROSECUTIONCASE_CAAG_JSON);
    }

    public static void stubProsecutionCaseCaagWithApplication(String caseId) throws IOException {
        stubPingFor(PROGRESSION_SERVICE_NAME);
        final String payload = getJsonStringFromResource(PROSECUTION_CASE_CAAG_WITH_APPLICATION_JSON);

        stubFor(get(urlPathMatching(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTIONCASE_CAAG_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId, PROGRESSION_QUERY_PROSECUTIONCASE_CAAG_JSON);
    }

    public static void stubProsecutionCase(String caseId) throws IOException {
        stubPingFor(PROGRESSION_SERVICE_NAME);
        final String payload = getJsonStringFromResource(PROSECUTION_CASE_JSON);

        stubFor(get(urlPathMatching(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTIONCASE_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId, PROSECUTION_CASE_JSON);
    }

    public static void stubProsecutionCase(String caseId, String responseFileName) throws IOException {
        stubPingFor(PROGRESSION_SERVICE_NAME);
        final String payload = getJsonStringFromResource(responseFileName);

        stubFor(get(urlPathMatching(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", PROGRESSION_QUERY_PROSECUTIONCASE_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(PROGRESSION_SERVICE_PROSECUTION_CASE_CAAG_QUERY_URL + caseId, PROSECUTION_CASE_JSON);
    }
}
