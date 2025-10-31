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

public class ApplicationReceivedIT {

    private static final String PUBLIC_COURT_APPLICATION_CREATED = "public.progression.court-application-created";
    private static final String COURT_APPLICATION_CREATED_BPMN_PROCESS_NAME = "application-received";
    private static final String APP_TYPE = "Application for an order of reimbursement in relation to a closure order";
    private static final String COURT_APPLICATION_CREATED_TASK_NAME = "Process ".concat(APP_TYPE).concat(" Application");
    private static final String COURT_APPLICATION_CREATED_JSON_EXTERNAL_PROSECUTOR = "court-application-created-external-prosecutor.json";
    private static final String COURT_APPLICATION_CREATED_JSON_INTERNAL = "court-application-created-internal.json";

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
    public void shouldStartProcessAndTaskWillCreatedApplicationTypeISExternalProsecutor() {
        final ImmutableMap<String, Boolean> features = of("camunda-progression-court-application-created", true);
        stubFeaturesFor("businessprocesses", features);

        final String applicationId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_COURT_APPLICATION_CREATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(COURT_APPLICATION_CREATED_JSON_EXTERNAL_PROSECUTOR, APP_TYPE, applicationId);

        publicMessageClient.sendMessage(PUBLIC_COURT_APPLICATION_CREATED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(COURT_APPLICATION_CREATED_BPMN_PROCESS_NAME, applicationId);

        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, applicationId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(applicationId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(COURT_APPLICATION_CREATED_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(notNullValue()));

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL4359536"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/application-at-a-glance/".concat(applicationId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("appType").getString("value"), is(APP_TYPE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("dueDate").getString("value"), notNullValue());
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskName").getString("value"), is("Process_Application"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is("cd3b251d-20e8-44ad-b95e-2f81afde56a4"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("M G"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("applicationId").getString("value"), is(applicationId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is("MAGISTRATES"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("isApplicationProsecutorOrDefence").getBoolean("value"), is(true));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
    }

    @Test
    public void shouldStartProcessAndTaskNotCreatedWhenInternalCourtApplicationIsCreated() {
        final String applicationId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_COURT_APPLICATION_CREATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(COURT_APPLICATION_CREATED_JSON_INTERNAL, APP_TYPE, applicationId);

        publicMessageClient.sendMessage(PUBLIC_COURT_APPLICATION_CREATED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(COURT_APPLICATION_CREATED_BPMN_PROCESS_NAME, applicationId);
        assertThat(processInstanceList.size(), is(0));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final String appType, final String applicationId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("APP_TYPE", appType)
                        .put("APPLICATION_ID", applicationId)
                        .build());
    }
}
