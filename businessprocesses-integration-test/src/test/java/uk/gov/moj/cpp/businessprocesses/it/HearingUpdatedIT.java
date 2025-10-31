package uk.gov.moj.cpp.businessprocesses.it;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getVariablesLocal;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContent;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.PROGRESSION_PROSECUTION_JSON;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;
import uk.gov.moj.cpp.businessprocesses.stub.HearingStub;
import uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub;
import uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub;

import java.io.IOException;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HearingUpdatedIT {

    private static final String PUBLIC_EVENT_HEARING_DETAILS_CHANGED = "public.events.hearing.hearing-detail-changed";
    private static final String PUBLIC_EVENT_HEARING_DEFENDANT_DETAILS_UPDATED = "public.events.hearing.defendant-details-updated";
    private static final String BOOK_INTERPRETER_WELSH_CASE = "book_interpreter_welsh_case";

    private static final String HEARING_DETAILS_CHANGED_EVENT_JSON = "hearing-details-changed-event.json";
    private static final String HEARING_DETAILS_DEFENDANT_CHANGED_EVENT_JSON = "hearing-details-defendant-changed-event.json";

    private static final String BOOK_INTERPRETER_WELSH_CASE_TASK_DEFINITION_KEY = "Book_interpreter_welsh_case";

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
    void handleHearingDetailsChangedEvent() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-initiated", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseId = randomUUID().toString();
        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);
        final String hearingId = randomUUID().toString();
        final String payload = getHearingPayload("hearing.get.hearing.json", hearingId, caseId);
        HearingStub.stubGetHearing(hearingId, payload);

        ReferenceDataServiceStub.stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_DETAILS_CHANGED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_DETAILS_CHANGED_EVENT_JSON, hearingId);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_DETAILS_CHANGED, eventPayload, metadata);

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
    void handleHearingDefendantDetailsChangedEvent() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-initiated", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseId = randomUUID().toString();
        ProgressionServiceStub.stubProsecutionCase(caseId, PROGRESSION_PROSECUTION_JSON);
        final String hearingId = randomUUID().toString();
        final String payload = getHearingPayload("hearing.get.hearing.json", hearingId, caseId);
        HearingStub.stubGetHearing(hearingId, payload);

        ReferenceDataServiceStub.stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_EVENT_HEARING_DEFENDANT_DETAILS_UPDATED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();

        final JsonObject eventPayload = getEventPayload(HEARING_DETAILS_DEFENDANT_CHANGED_EVENT_JSON, hearingId);

        publicMessageClient.sendMessage(PUBLIC_EVENT_HEARING_DEFENDANT_DETAILS_UPDATED, eventPayload, metadata);

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

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final String hearingId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HEARING_ID", hearingId)
                        .build());
    }

    private String getHearingPayload(final String fileName, final String hearingId, final String prosecutionCaseId) {
        return getFileContent(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HEARING_ID", hearingId)
                        .put("PROSECUTION_CASE_ID", prosecutionCaseId)
                        .build());
    }
}
