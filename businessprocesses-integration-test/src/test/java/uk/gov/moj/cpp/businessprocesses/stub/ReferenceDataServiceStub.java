package uk.gov.moj.cpp.businessprocesses.stub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.justice.services.messaging.JsonObjects.createReader;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getPayload;
import static uk.gov.moj.cpp.businessprocesses.helper.WiremockHelper.waitForStubToBeReady;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;

import uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

import javax.json.JsonObject;

public class ReferenceDataServiceStub {
    private static final String REFERENCE_DATA_SERVICE_NAME = "referencedata-service";
    private static final String REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON = "application/vnd.referencedata.query.workflow-task-types+json";
    private static final String REFERENCE_DATA_QUERY_WORK_QUEUE_JSON = "application/vnd.referencedata.query.work-queues+json";
    private static final String REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL = "/referencedata-service/query/api/rest/referencedata/workflow-task-types";
    private static final String REFERENCE_DATA_WORK_QUEUE_QUERY_URL = "/referencedata-service/query/api/rest/referencedata/work-queues";
    private static final String REFERENCE_DATA_PUBLIC_HOLIDAYS_MEDIA_TYPE = "application/vnd.referencedata.query.public-holidays+json";
    private static final String REFERENCE_DATA_PUBLIC_HOLIDAYS_URL = "/referencedata-service/query/api/rest/referencedata/public-holidays";

    private static final String REFERENCE_DATA_DOCUMENT_TYPES_QUERY_URL = "/referencedata-service/query/api/rest/referencedata/document-type-access/.*";
    private static final String REFERENCE_DATA_QUERY_DOCUMENT_TYPES_MEDIA_TYPE = "application/vnd.referencedata.query.document-type-access+json";

    private static final String REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON = "application/vnd.referencedata.get-result-definition+json";
    private static final String REFERENCE_DATA_QUERY_RESULT_DEFINITION_QUERY_URL = "/referencedata-service/query/api/rest/referencedata/result-definitions/";

    private static final String CONTENT_TYPE_RESTRICTED_CASE_PERMISSIONS = "application/vnd.usersgroups.restricted-case-permissions+json";
    private static final String COURT_ROOM_QUERY_URL = "/referencedata-service/query/api/rest/referencedata/courtrooms";
    private static final String COURT_ROOM_MEDIA_TYPE = "application/vnd.referencedata.ou-courtroom+json";
    private static final String OU_COURT_ROOM_CODE_MEDIA_TYPE = "application/vnd.referencedata.ou.courtrooms.ou-courtroom-code+json";


    public static void stubWorkflowTaskTypes() throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getJsonStringFromResource("workflow-task-types.json");

        stubFor(get(urlPathMatching(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL, REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON);
    }

    public static void stubWorkflowTaskTypesWithQueueIdAndTaskName(final String taskName, final UUID workQueueId) throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getJsonStringFromResource("workflow-task-types-with-queue-id-task-name.json")
                .replaceAll("%TASK_NAME%", taskName)
                .replaceAll("%WORK_QUEUE_ID%", workQueueId.toString());

        stubFor(get(urlPathMatching(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL, REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON);
    }

    public static void stubWorkflowTaskTypesWithQueueIdAndTaskNameV2(final UUID workQueueId) throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getJsonStringFromResource("workflow-task-types-with-queue-id-task-nameV2.json")
                .replaceAll("%WORK_QUEUE_ID%", workQueueId.toString());

        stubFor(get(urlPathMatching(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(REFERENCE_DATA_WORKFLOW_TASK_TYPES_QUERY_URL, REFERENCE_DATA_QUERY_WORKFLOW_TASK_TYPES_JSON);
    }

    public static void stubDocumentTypes() throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getJsonStringFromResource("document-type-access.json");

        stubFor(get(urlPathMatching(REFERENCE_DATA_DOCUMENT_TYPES_QUERY_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_DOCUMENT_TYPES_MEDIA_TYPE)
                        .withBody(payload)));

        waitForStubToBeReady(REFERENCE_DATA_DOCUMENT_TYPES_QUERY_URL, REFERENCE_DATA_QUERY_DOCUMENT_TYPES_MEDIA_TYPE);
    }

    public static void stubPublicHolidays() {
        InternalEndpointMockUtils.stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getPayload("referencedata.query.public-holidays.json");
        stubFor(get(urlPathMatching(REFERENCE_DATA_PUBLIC_HOLIDAYS_URL))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_PUBLIC_HOLIDAYS_MEDIA_TYPE)
                        .withBody(payload)));
        waitForStubToBeReady(REFERENCE_DATA_PUBLIC_HOLIDAYS_URL, REFERENCE_DATA_PUBLIC_HOLIDAYS_MEDIA_TYPE);
    }

    public static  void stubResultDefinition(String resultDefinition)  throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);

        final String payload = getJsonStringFromResource("result-definition.json");
        final String urlPath = format(REFERENCE_DATA_QUERY_RESULT_DEFINITION_QUERY_URL+"%s", resultDefinition);
        stubFor(get(urlPathMatching(urlPath))
                //.withQueryParam("resultDefinitionId", matching(resultDefinition))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(urlPath, REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON);

    }

    public static  void stubResultDefinitionNotMatchingBailOrSeriousBailOrSensitiveCases(String resultDefinition)  throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);

        final String payload = getJsonStringFromResource("result-definition-non-match.json");
        final String urlPath = format(REFERENCE_DATA_QUERY_RESULT_DEFINITION_QUERY_URL+"%s", resultDefinition);
        stubFor(get(urlPathMatching(urlPath))
                //.withQueryParam("resultDefinitionId", matching(resultDefinition))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(urlPath, REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON);

    }

    public static  void stubResultDefinitionSeriousBail(String resultDefinition)  throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);

        final String payload = getJsonStringFromResource("result-definition_serious_bail.json");
        final String urlPath = format(REFERENCE_DATA_QUERY_RESULT_DEFINITION_QUERY_URL+"%s", resultDefinition);
        stubFor(get(urlPathMatching(urlPath))
                //.withQueryParam("resultDefinitionId", matching(resultDefinition))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(urlPath, REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON);

    }

    public static void stubResultDefinitionSensitive(String resultDefinition) throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);

        final String payload = getJsonStringFromResource("result-definition_sensitive.json");
        final String urlPath = format(REFERENCE_DATA_QUERY_RESULT_DEFINITION_QUERY_URL+"%s", resultDefinition);
        stubFor(get(urlPathMatching(urlPath))
                //.withQueryParam("resultDefinitionId", matching(resultDefinition))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(urlPath, REFERENCE_DATA_QUERY_RESULT_DEFINITION_JSON);
    }

    public static void stubIsCaseSensitive(final String targetId, final String responsePayLoad) {
        stubPingFor("usersgroups-service");

        stubFor(get(urlPathEqualTo(format("/usersgroups-service/query/api/rest/usersgroups/permissions/"+targetId)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(ID, randomUUID().toString())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(responsePayLoad)));

        waitForStubToBeReady(format("/usersgroups-service/query/api/rest/usersgroups/permissions/"+targetId), CONTENT_TYPE_RESTRICTED_CASE_PERMISSIONS);
    }

    public static void stubWorkQueueForQueueName(final UUID workQueueId, final String queueName) throws IOException {
        stubPingFor(REFERENCE_DATA_SERVICE_NAME);
        String payload = getJsonStringFromResource("work-queue-name.json")
                .replaceAll("QUEUE_NAME", queueName)
                .replaceAll("WORK_QUEUE_ID", workQueueId.toString());

        final String workQueueQueryUrlPath = format(REFERENCE_DATA_WORK_QUEUE_QUERY_URL);

        stubFor(get(urlPathMatching(workQueueQueryUrlPath))
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", REFERENCE_DATA_QUERY_WORK_QUEUE_JSON)
                        .withBody(payload)));

        waitForStubToBeReady(workQueueQueryUrlPath, REFERENCE_DATA_QUERY_WORK_QUEUE_JSON);
    }

    public static void stubCourtRooms(UUID courtRoomID) {
        String body = getPayload("referencedata.court.rooms.welsh.json").replaceAll("%COURT_ROOM_ID%", courtRoomID.toString())
                .replaceAll("%OUCODE%", "B62IZ00");
        JsonObject courtRoom = createReader(new StringReader(body)).readObject();

        stubFor(get(urlPathEqualTo(COURT_ROOM_QUERY_URL + "/" + courtRoomID))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", COURT_ROOM_MEDIA_TYPE)
                        .withBody(courtRoom.toString())));

        waitForStubToBeReady(COURT_ROOM_QUERY_URL + "/" + courtRoomID, COURT_ROOM_MEDIA_TYPE);

    }

    public static void stubCourtRoomsByCourtRoomCode(UUID courtRoomID, String oucode) {
        String body = getPayload("referencedata.court.rooms.welsh.json").replaceAll("%COURT_ROOM_ID%", courtRoomID.toString()).replaceAll("%OUCODE%", oucode);
        JsonObject courtRoom = createReader(new StringReader(body)).readObject();

        stubFor(get(urlPathEqualTo(COURT_ROOM_QUERY_URL))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", OU_COURT_ROOM_CODE_MEDIA_TYPE)
                        .withBody(courtRoom.toString())));

        waitForStubToBeReady(COURT_ROOM_QUERY_URL, OU_COURT_ROOM_CODE_MEDIA_TYPE);

    }
}
