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

import java.io.IOException;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SJPCaseHearingDecisionIT {

    private static final String PUBLIC_EVENTS_SJP_CASE_REFERRED_TO_COURT = "public.events.sjp.case-referred-to-court";
    private static final String SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME = "sjp-case-hearing-decision-saved";
    private static final String PUBLIC_SJP_CASE_DECISION_TASK_NAME = "Refer SJS case for hearing";
    private static final String SJP_CASE_DECISION_SAVED_JSON = "sjpCaseDecisionSaved.json";

    private static final String USER_ID = randomUUID().toString();

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    @BeforeEach
    public void setUp() throws IOException {
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartProcessAndTaskWillCreated() {
        final ImmutableMap<String, Boolean> features = of("camunda-sjp-case-referred-to-court", true);
        stubFeaturesFor("businessprocesses", features);

        final String caseId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENTS_SJP_CASE_REFERRED_TO_COURT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(SJP_CASE_DECISION_SAVED_JSON, "REFER_FOR_COURT_HEARING", caseId);

        publicMessageClient.sendMessage(PUBLIC_EVENTS_SJP_CASE_REFERRED_TO_COURT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, caseId);

        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, caseId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(caseId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(PUBLIC_SJP_CASE_DECISION_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(notNullValue()));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/case-at-a-glance/".concat(caseId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(caseId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL34652354"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskName").getString("value"), is("Refer SJS case for hearing"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is("1a71d4b1-e0e1-4685-beb7-97e33fb2e799"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("Abbie ARMSTRONG"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("referCourtHearing").getBoolean("value"), is(true));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2024-08-22T13:00:00.000Z"));
    }

    @Test
    public void shouldStartProcessAndTaskNotCreated() {
        final String caseId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENTS_SJP_CASE_REFERRED_TO_COURT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(SJP_CASE_DECISION_SAVED_JSON, "WITHDRAW", caseId);

        publicMessageClient.sendMessage(PUBLIC_EVENTS_SJP_CASE_REFERRED_TO_COURT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(SJP_CASE_HEARING_DECISION_BPMN_PROCESS_NAME, caseId);
        assertThat(processInstanceList.size(), is(0));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final String type, final String caseId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("TYPE", type)
                        .put("CASE_ID", caseId)
                        .build());
    }
}
