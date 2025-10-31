package uk.gov.moj.cpp.businessprocesses.it;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskDetails;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.startProcessInstanceWithVariables;
import static uk.gov.moj.cpp.businessprocesses.stub.ListingServiceStub.stubListingHearingList;
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.stubProsecutionCaseCaag;
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.stubProsecutionCaseCaagWithApplication;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CustomTaskCreateIT {

    private static final String CHANGE_AUTHOR = "John Smith";
    private static final String CHANGE_AUTHOR_ID = randomUUID().toString();
    private static final String LAST_UPDATED_BY_ID = "lastUpdatedByID";
    private static final String LAST_UPDATED_BY_NAME = "lastUpdatedByName";
    private static final String PROCESS_DEFINITION_KEY = "single_custom_task_process";
    private static final String BUSINESS_KEY = "customTask";
    private static final String CASE_ID_VALUE = "8e4b2be0-f92a-4291-9b99-17af7e645472";
    private static final String CASE_ID_WITH_APPLICATION_VALUE = "6322bd2f-63ef-42e7-86c7-6cfaaf3ba19b";
    private static final String USER_ID_NAME = "userId";
    private static final String CUSTOM_TASK_TYPE = "customTaskType";
    private static final String TASK_TYPE_ID = "taskTypeId";
    private static final String TASK_TYPE_ID_VALUE = randomUUID().toString();
    private static final String USER_ID_VALUE = "6e1b2be0-f92a-4291-9b99-17af7e645500";
    private static final String TASK_VARIABLE_JSON_INPUT_NAME = "taskVariablesJsonString";
    private static final String TASK_VARIABLE_JSON_INPUT_VALUE = "{\"displayName\":\"Check phone call\",\"taskName\":\"customTask\",\"due\":\"2024-06-27T23:59:00.000Z\",\"caseURN\":\"29GD7875621\"," +
            "\"assignee\":\"customTaskAssignee\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904,6b1b2be0-f92a-4291-9b99-17af7e645123\",\"isDeletable\":true,\"isDeferrable\":false," +
            "\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"8e4b2be0-f92a-4291-9b99-17af7e645472\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false}";

    private static final String TASK_VARIABLE_JSON_WITH_APPLICATION_CASE_INPUT_VALUE = "{\"displayName\":\"Check phone call\",\"taskName\":\"customTask\",\"due\":\"2024-06-27T23:59:00.000Z\",\"caseURN\":\"29GD7875621\"," +
            "\"assignee\":\"customTaskAssignee\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904,6b1b2be0-f92a-4291-9b99-17af7e645123\",\"isDeletable\":true,\"isDeferrable\":false," +
            "\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"6322bd2f-63ef-42e7-86c7-6cfaaf3ba19b\",\"businessUnitCodes\":\"businessUnitOuCode\",\"courtCodes\":\"courtOuCode\",\"isUrgent\":false}";

    private static final String TASK_VARIABLE_JSON_WITH_CASE_URN_AND_DEFENDANTS_VALUE = "{\"displayName\":\"Check phone call\",\"taskName\":\"customTask\",\"due\":\"2024-06-27T23:59:00.000Z\",\"caseURN\":\"29GD7875621\"," +
            "\"assignee\":\"customTaskAssignee\",\"candidateGroups\":\"3d1b2be0-f92a-4291-9b99-17af7e645904,6b1b2be0-f92a-4291-9b99-17af7e645123\",\"isDeletable\":true,\"isDeferrable\":false," +
            "\"organisationId\":\"7f2b2be0-f92a-4291-9b99-17af7e645321\",\"caseId\":\"6322bd2f-63ef-42e7-86c7-6cfaaf3ba19b\",\"businessUnitCodes\":\"businessUnitOuCode\",\"isUrgent\":false,\"defendants\":[{\"id\":\"1\",\"firstName\":\"Smith\",\"lastName\":\"Philip\"},{\"id\":\"2\",\"firstName\":\"Mona\",\"lastName\":\"Lisa\"},{\"id\":\"3\",\"firstName\":\"Victor\",\"lastName\":\"Hugo\"},{\"id\":\"4\",\"firstName\":\"Leo\",\"lastName\":\"Tolstoy\"}]}";

    @BeforeEach
    public void setUp() throws IOException {
        stubListingHearingList();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubProsecutionCaseCaag(CASE_ID_VALUE);
        stubProsecutionCaseCaagWithApplication(CASE_ID_WITH_APPLICATION_VALUE);
    }

    @Test
    public void shouldCreateCustomTaskWithProgressionAndListingData() throws ParseException {
        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, TASK_VARIABLE_JSON_INPUT_VALUE);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE);
        variablesMap.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        variablesMap.put(CUSTOM_TASK_TYPE, "custom_activity");
        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Check phone call"));

    }

    @Test
    public void shouldCreateCustomTaskWithoutProgressionAndListingData() throws ParseException {
        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, TASK_VARIABLE_JSON_WITH_APPLICATION_CASE_INPUT_VALUE);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE);
        variablesMap.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Check phone call"));
    }

    @Test
    public void shouldCreateCustomTaskWithCaseURNAndDefendants() throws ParseException, IOException {
        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, TASK_VARIABLE_JSON_WITH_CASE_URN_AND_DEFENDANTS_VALUE);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE);
        variablesMap.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        variablesMap.put(CUSTOM_TASK_TYPE, "custom_activity");
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);

        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Check phone call"));
    }

    @Test
    public void shouldCreateCustomTaskWithCourtCodesAndNoDefendants() throws ParseException, IOException {
        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, TASK_VARIABLE_JSON_WITH_APPLICATION_CASE_INPUT_VALUE);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE);
        variablesMap.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        variablesMap.put(CUSTOM_TASK_TYPE, "custom_activity");
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Check phone call"));
    }

    @Test
    public void shouldValidateTaskCreatedEvent() {
        final Map<String, Object> variablesMap = new HashMap<>();
        variablesMap.put(TASK_VARIABLE_JSON_INPUT_NAME, TASK_VARIABLE_JSON_INPUT_VALUE);
        variablesMap.put(USER_ID_NAME, USER_ID_VALUE);
        variablesMap.put(LAST_UPDATED_BY_ID, CHANGE_AUTHOR_ID);
        variablesMap.put(LAST_UPDATED_BY_NAME, CHANGE_AUTHOR);
        variablesMap.put(TASK_TYPE_ID, TASK_TYPE_ID_VALUE);
        variablesMap.put(CUSTOM_TASK_TYPE, "customTask1");
        final String processInstanceId = startProcessInstanceWithVariables(PROCESS_DEFINITION_KEY, BUSINESS_KEY, variablesMap);
        final String taskId = getTaskList(processInstanceId).stream().findFirst().get();
        final JsonObject taskDetails = getTaskDetails(taskId);

        assertThat(taskDetails.getString("taskDefinitionKey"), is("custom_activity"));
        assertThat(taskDetails.getString("name"), is("Check phone call"));

    }
}
