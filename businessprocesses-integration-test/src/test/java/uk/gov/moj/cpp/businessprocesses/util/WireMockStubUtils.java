package uk.gov.moj.cpp.businessprocesses.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.text.MessageFormat.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getPayload;

import uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils;
import uk.gov.justice.services.test.utils.core.http.RestPoller;

import java.util.UUID;

import javax.ws.rs.core.Response.Status;

import com.google.common.base.Joiner;

/**
 * Utility class for setting stubs.
 */
public class WireMockStubUtils {

    private static final String HOST = System.getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final String BASE_URI = "http://" + HOST + ":8080";
    private static final String READ_BASE_URL = "/businessprocesses-service/query/api/rest/businessprocesses";
    private static final String USER_DETAILS_URL = "/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions";
    private static final String QUERY_PATH_USERGROUPS_PERMISSION_WITH_DETAILS_URL = "/usersgroups-service/query/api/rest/usersgroups/permissions";
    private static final String COMMAND_PATH_DELETE_BULK_PERMISSION_WITH_DETAILS_URL = "/usersgroups-service/command/api/rest/usersgroups/permissions";

    private static final String USER_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.logged-in-user-details+json";
    private static final String COMMAND_MEDIA_TYPE_DELETE_BULK_PERMISSION_WITH_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.delete-bulk-permission+json";
    private static final String QUERY_MEDIA_TYPE_USERGROUPS_PERMISSION_WITH_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.permissions+json";

    static {
        configureFor(HOST, 8080);
    }

    public static void setupLoggedInUsersPermissionQueryStub() {
        InternalEndpointMockUtils.stubPingFor("usersgroups-service");
        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPayload("stub-data/user-and-group-details-with-permissions.json"))));
    }

    public static void setupUsergoupsPermissions(final String permissionId, final String caseId) {
        stubPingFor("usersgroups-service");

        final String payload = getPayload("stub-data/user-permissions.json")
                .replace("PERMISSION_ID", permissionId);

        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/permissions"))
                .withQueryParam("action", equalTo("Access"))
                .withQueryParam("object", equalTo("Case"))
                .withQueryParam("target", equalTo(caseId))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, QUERY_MEDIA_TYPE_USERGROUPS_PERMISSION_WITH_DETAILS_MEDIA_TYPE)
                        .withBody(payload)));

    }

    public static void setupAsDeleteBulkPermission() {
        stubFor(post(urlPathEqualTo(COMMAND_PATH_DELETE_BULK_PERMISSION_WITH_DETAILS_URL))
                .willReturn(aResponse().withStatus(SC_ACCEPTED)
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, COMMAND_MEDIA_TYPE_DELETE_BULK_PERMISSION_WITH_DETAILS_MEDIA_TYPE)));
    }

    public static String getReadUrl(final String resource) {
        return Joiner.on("").join(BASE_URI, READ_BASE_URL, resource);
    }

    private static void waitForStubToBeReady(final String resource, final String mediaType) {
        waitForStubToBeReady(resource, mediaType, Status.OK);
    }

    private static void waitForStubToBeReady(final String resource, final String mediaType, final Status expectedStatus) {
        RestPoller.poll(requestParams(format("{0}/{1}", getBaseUri(), resource), mediaType)).until(status().is(expectedStatus));
    }

    private static String getBaseUri() {
        String baseUriProp = System.getProperty("INTEGRATION_HOST_KEY");
        return isNotEmpty(baseUriProp) ? String.format("http://%s:8080", baseUriProp) : "http://localhost:8080";
    }
}
