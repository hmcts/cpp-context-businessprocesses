package uk.gov.moj.cpp.businessprocesses.it;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskDetails;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getVariablesLocal;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;
import uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber;

import java.io.IOException;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SendDocumentsToPrisonIT {


    private static final String PUBLIC_EVENT_HEARING_RESULTED = "public.progression.hearing-resulted";
    private static final String CASE_RESULTED_BPMN_PROCESS_NAME = "case-results";

    private static final String HEARING_RESULTED_JSON = "hearing-resulted_with_convicted_results.json";


    private static final String SEND_DOCUMENTS_TO_PRISON = "Send_documents_to_prison";
    private static final String SEND_DOCUMENTS_TO_PRISON_NAME = "Send_documents_to_prison";
    private static final String SEND_DOCUMENTS_TO_PRISON_DEFINITION_KEY = "Send_documents_to_prison";

    private static final String USER_ID = randomUUID().toString();
    private static final String CASE_ID = randomUUID().toString();
    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    @BeforeEach
    public void setUp() throws IOException {
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartHearingResultedProcessWithSendDocumentToPrison() {
        final ImmutableMap<String, Boolean> features = of( "camunda-progression-hearing-resulted", true);
        FeatureStubber.clearCache("businessprocesses");
        stubFeaturesFor("businessprocesses", features);

        final String hearingId = randomUUID().toString();
        final Integer imprisonmentCode = 1002;
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_RESULTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_RESULTED_JSON, hearingId, CASE_ID, imprisonmentCode);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_RESULTED, eventPayload, metadata);

        final String caseHearingId = UUID.nameUUIDFromBytes((hearingId + CASE_ID).getBytes()).toString();

        final JsonArray processInstanceList = getProcessInstanceList(CASE_RESULTED_BPMN_PROCESS_NAME, caseHearingId);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, caseHearingId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(caseHearingId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(SEND_DOCUMENTS_TO_PRISON_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(SEND_DOCUMENTS_TO_PRISON_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("note").getString("value"), is("[Edward Harrison]"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2018-09-28T12:13:00.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is("MAGISTRATES"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("dueDate").getString("value"), is("2018-10-18T23:59:59.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("dueDate").getString("value"), is("2018-10-18T23:59:59.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("executeInstantly").getBoolean("value"), is(false));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(CASE_ID));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskName").getString("value"), is(SEND_DOCUMENTS_TO_PRISON));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/case-at-a-glance/".concat(CASE_ID)));

    }

    @Test
    public void shouldStartAndStopProcessWithNoTasksWhenNoCustodialResults() {
        final String hearingId = randomUUID().toString();
        final Integer nonValidCode = 1003;

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_RESULTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_RESULTED_JSON, hearingId, CASE_ID, nonValidCode);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_RESULTED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(CASE_RESULTED_BPMN_PROCESS_NAME, hearingId);
        assertThat(processInstanceList.size(), is(0));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }


    private JsonObject getEventPayload(final String fileName, final String hearingId, final String caseId1, final Integer code) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HEARING_ID", hearingId)
                        .put("CASE_ID", caseId1)
                        .put("CJC_CODE", code)
                        .build());
    }
}
