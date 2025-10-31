package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.assignTaskToUser;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.completeTaskMatchingTaskId;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskDetails;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.startProcessInstanceWithVariables;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.updateLocalVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.updateTaskDueDate;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.waitForEvent;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.it.Queries.pollForTaskHistory;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;
import static uk.gov.moj.cpp.businessprocesses.util.WireMockStubUtils.setupLoggedInUsersPermissionQueryStub;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TaskHistoryIT {

    private static final String PUBLIC_EVENT_HEARING_TRIAL_VACATED = "public.hearing.trial-vacated";
    private static final String HEARING_TRIAL_VACATED_PROCESS_ID = "hearing-vacated";
    private static final String CANCEL_INTERPRETER_TASK_NAME = "Cancel Interpreter Case";
    private static final String CANCEL_INTERPRETER_TASK_DEFINITION_KEY = "Cancel_interpreter_case";

    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String CHANGE_AUTHOR_CREATED = "John Smith Created";
    private static final String CHANGE_AUTHOR_UPDATED = "John Smith Updated";
    private static final String CHANGE_AUTHOR_ID_UPDATED = randomUUID().toString();
    private static final String HEARING_TRAIL_VACATED_CASE_DETAILS_JSON = "hearingTrialVacated_caseDetails.json";
    private static final String DUE_DATE_VALUE = "2024-08-30T10:00:00.000+0200";
    private static final String DUE_DATE_VALUE2 = "2024-09-15T10:00:00.000+0200";
    private static final String HEARING_TRIAL_CASE_TASK_NAME = "Cancel_interpreter_case";
    private static final String CASE_JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";

    private static final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    private static final UUID USER_ID_VALUE = randomUUID();
    private static final UUID USER_ID_VALUE2 = randomUUID();

    private static final String TASK_VARIABLE_JSON_INPUT_NAME = "taskVariablesJsonString";
    private static final String USER_ID_NAME = "userId";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String CUSTOM_TASK_TYPE = "customTaskType";
    private static final String WORK_QUEUE = "workQueue";
    private static final String PROCESS_DEFINITION_KEY = "single_custom_task_process";
    private static final String BUSINESS_KEY = "customTask";

    @BeforeEach
    public void setUp() throws IOException {
        reset();
        setupLoggedInUsersPermissionQueryStub();
        stubPublicHolidays();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartHearingVacatedProcessAndVerifyTaskHistoryIsRecordedAsTaskIsProcessed() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-trial-vacated", true);
        stubFeaturesFor("businessprocesses", features);

        final String caseId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final boolean hasInterpreter = true;

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_TRIAL_VACATED)
                .withId(randomUUID())
                .withUserId(USER_ID_VALUE.toString())
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_TRAIL_VACATED_CASE_DETAILS_JSON, hasInterpreter, caseId, "CASE_ID", hearingId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_TRIAL_VACATED, eventPayload, metadata);
        waitForEvent();
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


        final String taskId = taskDetails.getString("id");
        mimicLastUpdatedIdAndName(processInstanceId, CANCEL_INTERPRETER_TASK_DEFINITION_KEY);

        verify(taskId, processInstanceId, CANCEL_INTERPRETER_TASK_DEFINITION_KEY);

    }

    @Test
    public void shouldCustomTaskTaskHistoryIsRecordedAsTaskIsProcessed() throws IOException {
        final String taskVariableJsonData = getJsonStringFromResource("task-json-data/create-custom-task.json");

        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, taskVariableJsonData);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE.toString());
        variablesMap.put(LAST_UPDATED_BY_ID, "4b1b2be0-f92a-4291-9b99-17af7e645123");
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR_CREATED);
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        variablesMap.put(CUSTOM_TASK_TYPE, "custom_activity");
        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Test Activity"));


        assertThat(processInstanceId, is(notNullValue()));

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskId, is(notNullValue()));
        assertThat(taskDetails.getString("due"), notNullValue());

        mimicLastUpdatedIdAndName(processInstanceId, "custom_activity");

        verify(taskId, processInstanceId, "custom_activity");

    }

    private void verify(final String taskId, final String processInstanceId, final String activity) throws IOException {
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[0].eventType", equalTo("Task Created"))));

        // assign
        assignTaskToUser(taskId, USER_ID_VALUE.toString());
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[1].eventType", equalTo("Task Assigned")),
                withJsonPath("$.history[1].details", equalTo("Assigned to: " + USER_ID_VALUE))
        ));

        // update date
        updateTaskDueDate(taskId, DUE_DATE_VALUE, USER_ID_VALUE.toString());
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[2].eventType", equalTo("Task Due Date Updated"))
        ));

        // update date
        updateTaskDueDate(taskId, DUE_DATE_VALUE2, USER_ID_VALUE.toString());
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[3].eventType", equalTo("Task Due Date Updated"))
        ));

        assignTaskToUser(taskId, USER_ID_VALUE2.toString());
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[4].eventType", equalTo("Task Reassigned"))
        ));

        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "Rota");
        updateWorkQueue(processInstanceId, activity, UUID.randomUUID().toString());
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[5].eventType", equalTo("Task Work Queue Updated"))
        ));

        completeTaskMatchingTaskId(taskId);
        pollForTaskHistory(taskId, allOf(
                withJsonPath("$.taskId", equalTo(taskId)),
                withJsonPath("$.history[6].eventType", equalTo("Task Completed"))
        ));
    }

    private void updateWorkQueue(final String processInstanceId, final String taskName, final String workQueueId) {
        updateLocalVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey(processInstanceId, taskName, WORK_QUEUE, workQueueId);
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

    private void mimicLastUpdatedIdAndName(final String processInstanceId, final String taskName) {

        /**
         *These calls are added for miming work-management-proxy's behaviour adding logged in user id and name to local variables
         */
        updateLocalVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey(processInstanceId, taskName, LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID_UPDATED);
        updateLocalVariablesOnTaskMatchingProcessInstanceAndTaskDefinitionKey(processInstanceId, taskName, LAST_UPDATED_BY_NAME, CHANGE_AUTHOR_UPDATED);

    }

}
