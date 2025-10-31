package uk.gov.moj.cpp.businessprocesses.it;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskDetails;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getVariablesLocal;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.waitForEvent;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContent;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.PROGRESSION_PROSECUTION_JSON;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubCourtRooms;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;
import uk.gov.moj.cpp.businessprocesses.stub.HearingStub;
import uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HearingInitiatedIT {

    private static final String PUBLIC_EVENT_HEARING_LISTED = "public.hearing.initiated";
    private static final String HEARING_INITIATED_PROCESS_ID = "hearing-listed";
    private static final String BOOK_INTERPRETER_WELSH_CASE = "book_interpreter_welsh_case";
    private static final String BOOK_INTERPRETER_WELSH_APPLICATION = "book_interpreter_welsh_application";


    private static final String HEARING_INITIATED_CASE_DETAILS_JSON = "hearingInitiated_caseDetails.json";
    private static final String HEARING_INITIATED_APPLICATION_DETAILS_JSON = "hearingInitiated_applicationDetails.json";
    private static final String HEARING_INITIATED_CASE_DETAILS_WITHOUT_INTERPRETER_JSON = "hearingInitiated_caseDetails_withoutInterpreterLanguageField.json";

    private static final String HEARING_INITIATED_CASE_TASK_NAME = "Book interpreter";
    private static final String HEARING_INITIATED_CASE_TASK_DEFINITION_KEY = "Book_interpreter_case";
    private static final String BOOK_INTERPRETER_WELSH_CASE_TASK_DEFINITION_KEY = "Book_interpreter_welsh_case";
    private static final String BOOK_INTERPRETER_WELSH_APPLICATION_TASK_DEFINITION_KEY = "Book_interpreter_welsh_application";
    private static final String HEARING_INITIATED_APPLICATION_TASK_NAME = "Book_interpreter_application";

    private static final String CASE_JURISDICTION_TYPE_MAGISTRATES = "MAGISTRATES";

    private static final String USER_ID = randomUUID().toString();

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";

    @BeforeEach
    public void setUp() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-initiated", true, "camunda-interpreter", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));

        publicMessageClient.startProducer("public.event");
    }

    @Test
    void shouldStartHearingInitiatedProcessWithBookInterpreterCaseTask() throws IOException {
        final String caseId = randomUUID().toString();
        final String applicationId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final String defendantId = randomUUID().toString();

        final String payload = getHearingPayload("hearing.get-hearing-application.json", hearingId, caseId, applicationId);
        HearingStub.stubGetHearing(hearingId, payload);

        stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_INITIATED_CASE_DETAILS_JSON, caseId, "CASE_ID", hearingId, defendantId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_INITIATED_PROCESS_ID, hearingId);
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
        assertThat(taskDetails.getString("name"), is(HEARING_INITIATED_CASE_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(HEARING_INITIATED_CASE_TASK_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2015-01-12T05:27:17.210Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is(defendantId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("Abbie ARMSTRONG"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/case-at-a-glance/".concat(caseId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(caseId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL34652354"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("note").getString("value"), is("[ TFL34652354 = Abbie ARMSTRONG : Welsh,John Doe : French ][ TFL12346523 = John Doe : French ]"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
    }

    @Test
    void shouldCreateWelshInterpreterActivityForApplication() throws IOException {
        final String applicationId = randomUUID().toString();
        final String caseId = randomUUID().toString();
        final String hearingId = randomUUID().toString();

        final String payload = getHearingPayload("hearing.get-hearing-application.json", hearingId, caseId, applicationId);
        HearingStub.stubGetHearing(hearingId, payload);

        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);

        final String defendantId = UUID.fromString("e1d32d9d-29ec-4934-a932-22a50f223966").toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_INITIATED_APPLICATION_DETAILS_JSON, applicationId, "APPLICATION_ID", hearingId, defendantId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        waitForEvent();

        final JsonArray processInstanceList = getProcessInstanceList(BOOK_INTERPRETER_WELSH_APPLICATION, hearingId);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, hearingId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(hearingId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(BpmRestApiHelper::getTaskDetails)
                .orElse(null);

        assert taskDetails != null;
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is("Book interpreter for a welsh Application"));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(BOOK_INTERPRETER_WELSH_APPLICATION_TASK_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2024-06-28T00:00:00.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/application-at-a-glance/".concat(applicationId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(caseId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("CPJKJUAK8G"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("d48961f7-09e1-3c38-b300-baa9d723594c"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));

        List<String> candidateGroups = BpmRestApiHelper.getCandidateGroups(taskDetails.getString("id"));
        assertThat(candidateGroups.size(), is(6));
        assertThat(candidateGroups.containsAll(List.of("CTSC Admin", "Listing Officer", "Legal Adviser","Court clerk", "Court Associate", "Operational Delivery Admin")), is(true));
    }

    @Test
    void shouldCreateWelshInterpreterProcessAndAssignedWelshLangUnit() throws IOException {
        final String caseId = randomUUID().toString();
        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);
        final String hearingId = randomUUID().toString();
        final String defendantId = UUID.fromString("e1d32d9d-29ec-4934-a932-22a50f223966").toString();

        final String payload = getHearingPayload("hearing.get.hearing.json", hearingId, caseId, null);
        HearingStub.stubGetHearing(hearingId, payload);

        stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_INITIATED_CASE_DETAILS_JSON, caseId, "CASE_ID", hearingId, defendantId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(BOOK_INTERPRETER_WELSH_CASE, hearingId);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, hearingId);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(hearingId));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(BpmRestApiHelper::getTaskDetails)
                .orElse(null);

        assert taskDetails != null;
        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is("Book interpreter welsh case"));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(BOOK_INTERPRETER_WELSH_CASE_TASK_DEFINITION_KEY));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2024-07-12T09:30:00.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/case-at-a-glance/".concat(caseId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(caseId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("CAWBTCVEHB"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("d48961f7-09e1-3c38-b300-baa9d723594c"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
    }

    @Test
    void shouldStartHearingInitiatedProcessWithBookInterpreterApplicationTask() throws IOException {
        final String applicationId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final String caseId = randomUUID().toString();

        final String payload = getHearingPayload("hearing.get-hearing-application.json", hearingId, caseId, applicationId);
        HearingStub.stubGetHearing(hearingId, payload);
        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);
        final String defendantId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_INITIATED_APPLICATION_DETAILS_JSON, applicationId, "APPLICATION_ID", hearingId, defendantId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_INITIATED_PROCESS_ID, hearingId);
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
        assertThat(taskDetails.getString("name"), is(HEARING_INITIATED_APPLICATION_TASK_NAME));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(HEARING_INITIATED_APPLICATION_TASK_NAME));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2022-08-24T01:46:36.068Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantId").getString("value"), is(defendantId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("defendantName").getString("value"), is("Abbie ARMSTRONG"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("deepLink").getString("value"), is("/prosecution-casefile/application-at-a-glance/".concat(applicationId)));
        assertThat(taskVariablesPayloadResponse.getJsonObject("applicationId").getString("value"), is(applicationId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("TFL34652354"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingId").getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject("workQueue").getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("note").getString("value"), is("[ TFL34652354 = Abbie ARMSTRONG : French ][ TFL12346523 = John Doe : French ]"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("jurisdiction").getString("value"), is(CASE_JURISDICTION_TYPE_MAGISTRATES));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
    }

    @Test
    void shouldStartAndStopProcessWithNoTasksWhenInterpreterLanguageEmpty() {
        final String caseId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final String defendantId = randomUUID().toString();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_LISTED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_INITIATED_CASE_DETAILS_WITHOUT_INTERPRETER_JSON, caseId, "CASE_ID", hearingId, defendantId, CASE_JURISDICTION_TYPE_MAGISTRATES);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_LISTED, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(HEARING_INITIATED_PROCESS_ID, hearingId);
        assertThat(processInstanceList.size(), is(0));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String idType, final String hearingId, final String defendantId, final String jurisdictionType) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        if (isNotEmpty(idType) && isNotEmpty(id)) {
            builder.put(idType, id);
        }
        if (isNotEmpty(hearingId)) {
            builder.put("HEARING_ID", hearingId);
        }
        if (isNotEmpty(defendantId)) {
            builder.put("DEFENDANT_ID", defendantId);
        }
        if (isNotEmpty(jurisdictionType)) {
            builder.put("JURISDICTION_TYPE", jurisdictionType);
        }

        return getFileContentAsJson(fileName, builder.build());
    }

    private String getHearingPayload(final String fileName, final String hearingId, final String caseId, final String applicationId) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        if (isNotEmpty(hearingId)) {
            builder.put("HEARING_ID", hearingId);
        }
        if (isNotEmpty(caseId)) {
            builder.put("PROSECUTION_CASE_ID", caseId);
        }
        if (isNotEmpty(applicationId)) {
            builder.put("APPLICATION_ID", applicationId);
        }

        return getFileContent(fileName, builder.build());
    }
}
