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

public class ListingServiceStub {
    private static final String LISTING_SERVICE_NAME = "listing-service";
    private static final String HEARINGS_JSON = "hearings.json";
    private static final String LISTING_QUERY_SEARCH_HEARINGS_QUERY_JSON = "application/vnd.listing.search.hearings+json";
    private static final String LISTING_QUERY_SEARCH_HEARINGS_QUERY_URL = "/listing-service/query/api/rest/listing/hearings/allocated-and-unallocated";

    public static void stubListingHearingList() throws IOException {
        stubPingFor(LISTING_SERVICE_NAME);
        String payload = getJsonStringFromResource(HEARINGS_JSON);

        stubFor(get(urlPathMatching(LISTING_QUERY_SEARCH_HEARINGS_QUERY_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", LISTING_QUERY_SEARCH_HEARINGS_QUERY_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(LISTING_QUERY_SEARCH_HEARINGS_QUERY_URL, LISTING_QUERY_SEARCH_HEARINGS_QUERY_JSON);
    }
}
