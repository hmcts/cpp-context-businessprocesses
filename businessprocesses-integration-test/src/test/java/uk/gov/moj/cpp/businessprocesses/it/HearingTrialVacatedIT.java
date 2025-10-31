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

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

public class HearingTrialVacatedIT {

    private static final String PUBLIC_EVENT_HEARING_TRIAL_VACATED = "public.hearing.trial-vacated";
    private static final String HEARING_TRIAL_VACATED_PROCESS_ID = "hearing-vacated";
    private static final String CANCEL_INTERPRETER_TASK_NAME = "Cancel Interpreters Application";
    private static final String CANCEL_INTERPRETER_TASK_DEFINITION_KEY = "Cancel_interpreter_application";

    private static final String HEARING_TRAIL_VACATED_CASE_DETAILS_JSON = "hearingTrialVacated_caseDetails.json";
    private static final String HEARING_TRAIL_VACATED_APPLICATION_DETAILS_JSON = "hearingTrialVacated_applicationDetails.json";

    private static final String HEARING_TRIAL_CASE_TASK_DEFINITION_KEY = "Cancel_interpreter_case";
    private static final String HEARING_TRIAL_CASE_TASK_NAME = "Cancel Interpreter Case";

    private static final String HEARING_TRIAL_APPLICATION_TASK_NAME = "Cancel_interpreter_application";

    private static final String CASE_JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";

    private static final String USER_ID = randomUUID().toString();

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";

    @BeforeEach
    public void setUp() throws IOException {
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartProcessWithCancelInterpreterCaseTaskWhenTrialWithInterpretersIsVacatedWithMagistratesWorkQueue() {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-trial-vacated", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final boolean hasInterpreter = true;

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_TRIAL_VACATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_VACATED_CASE_DETAILS_JSON, hasInterpreter, caseId,"CASE_ID", hearingId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_TRIAL_VACATED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_TRIAL_VACATED_PROCESS_ID, hearingId);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, hearingId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(hearingId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(HEARING_TRIAL_CASE_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(HEARING_TRIAL_CASE_TASK_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2022-08-24T01:46:36.068Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantRemandStatus").getString("value"), is("status"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is("32ff0722-c287-11e9-9cb5-2a2ae2dbcce4"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("Abbie ARMSTRONG"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/case-at-a-glance/".concat(caseId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(caseId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL34652354"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
    }


    @Test
    public void shouldStartProcessWithCancelInterpreterApplicationTaskWhenTrialWithInterpretersIsVacatedWithMagistratesWorkQueue() {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-trial-vacated", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String applicationId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final boolean hasInterpreter = true;

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_TRIAL_VACATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_VACATED_APPLICATION_DETAILS_JSON, hasInterpreter, applicationId, "APPLICATION_ID", hearingId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_TRIAL_VACATED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_TRIAL_VACATED_PROCESS_ID, hearingId);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, hearingId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(hearingId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(CANCEL_INTERPRETER_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(CANCEL_INTERPRETER_TASK_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2022-08-24T01:46:36.068Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantRemandStatus").getString("value"), is("status"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is("32ff0722-c287-11e9-9cb5-2a2ae2dbcce4"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("Abbie ARMSTRONG"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("Cancel_interpreter_application_deepLink").getString("value"), is("/prosecution-casefile/application-at-a-glance/".concat(applicationId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("Cancel_interpreter_application_dueDate").getString("value"), notNullValue());
        assertThat(taskVariablesPayloadResponse.getJsonObject("applicationId").getString("value"), is(applicationId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL34652354"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(taskVariablesPayloadResponse.getJsonObject("Cancel_interpreter_application_taskTypeId").getString("value"), is(notNullValue()));
    }

    @Test
    public void shouldStartAndStopProcessWithNoTasksWhenTrialWithoutInterpretersIsVacatedMagistrates() {
        final String caseId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final boolean hasInterpreter = false;

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_TRIAL_VACATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_VACATED_CASE_DETAILS_JSON, hasInterpreter, caseId, "CASE_ID", hearingId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_TRIAL_VACATED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_TRIAL_VACATED_PROCESS_ID, hearingId);
        assertThat(processInstanceList.size(), is(0));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final boolean hasInterpreter, final String id, final String idType, final String hearingId, final String jurisdictionType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HAS_INTERPRETER", hasInterpreter)
                        .put(idType, id)
                        .put("HEARING_ID", hearingId)
                        .put("JURISDICTION_TYPE", jurisdictionType)
                        .build());
    }
}
