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
import static uk.gov.moj.cpp.businessprocesses.stub.ProgressionServiceStub.stubProsecutionCase;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubCourtRooms;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubCourtRoomsByCourtRoomCode;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ErrorHandlingTaskIT {

    private static final String CASE_VALIDATION_ERROR_IDENTIFIED_EVENT = "public.prosecutioncasefile.events.case-validation-failed";
    private static final String DEFENDANT_VALIDATION_ERROR_IDENTIFIED_EVENT = "public.prosecutioncasefile.events.defendant-validation-failed";
    private static final String SJP_VALIDATION_ERROR_IDENTIFIED_EVENT = "public.prosecutioncasefile.events.sjp-validation-failed";
    private static final String CORRESPONDENCE_FAILURE_EVENT = "public.correspondence.event.outbound-correspondence-notification-failed";
    private static final String CASE_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.case-validation-failed.json";
    private static final String CORRESPONDENCE_FAILURE_JSON = "public.correspondence.event.outbound-correspondence-notification-failed.json";
    private static final String DEFENDANT_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.defendant-validation-failed.json";
    private static final String SJP_VALIDATION_ERROR_IDENTIFIED_JSON = "public.prosecutioncasefile.events.sjp-validation-failed.json";
    private static final String SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME = "spi_error_identified";
    private static final String CORRESPONDENCE_FAILURE_BPMN_PROCESS_NAME = "correspondence_failure_identified";
    private static final String CASE_ID = randomUUID().toString();
    private static final String DOCUMENT_ID = randomUUID().toString();
    private static final String USER_ID = randomUUID().toString();
    private static final String CORRESPONDENCE_ID = randomUUID().toString();
    private static final String SPI_ERROR_IDENTIFIED_BPMN_DISPLAY_NAME = "SPI_error";
    private static final String CORRESPONDENCE_FAILURE_BPMN_DISPLAY_NAME = "Correspondence_failure";
    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    private static final String DRAFT_CORRESPONDENCE_ID = "DRAFT_CORRESPONDENCE_ID";
    private static final String CORRESPONDENCE_FAILURE_ID_TAG = "correspondenceId";

    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";

    private static final String COURT_CODE = "B62IZ00";
    private static final String HEARING_DATE = "1978-02-04T09:05:01.001Z";
    private static final String REGION_VAL = "Wales";

    @BeforeEach
    public void setUp() throws IOException {
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubCourtRooms(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"));
        stubPublicHolidays();
        stubProsecutionCase("bfd45440-c32d-11eb-91bd-08002783bfed");
        stubCourtRoomsByCourtRoomCode(UUID.fromString("f8254db1-1683-483e-afb3-b87fde5a0a26"), "B62IZ00");
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartProcessSPIErrorWhenCaseValidationError() {
        final ImmutableMap<String, Boolean> features = of("camunda-prosecutioncasefile-events-case-validation-failed", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final Metadata metadata = metadataBuilder()
                .withName(CASE_VALIDATION_ERROR_IDENTIFIED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();

        final JsonObject eventPayload = getEventPayload(CASE_VALIDATION_ERROR_IDENTIFIED_JSON, CASE_ID, "CASE_ID");

        publicMessageClient.sendMessage(CASE_VALIDATION_ERROR_IDENTIFIED_EVENT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME, CASE_ID);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, CASE_ID);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(CASE_ID));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getString("name"), is(SPI_ERROR_IDENTIFIED_BPMN_DISPLAY_NAME));
        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(CASE_ID));
        assertThat(taskVariablesPayloadResponse.getJsonObject("courtCodes").getString("value"), is(COURT_CODE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is(HEARING_DATE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("region").getString("value"), is(REGION_VAL));

        List<String> candidateGroups = BpmRestApiHelper.getCandidateGroups(taskDetails.getString("id"));
        assertThat(candidateGroups.size(), is(2));
        assertThat(candidateGroups.containsAll(List.of("CTSC Admin", "Operational Delivery Admin")), is(true));
    }

    @Test
    public void shouldStartProcessSPIErrorWhenSJPValidationError() {
        final ImmutableMap<String, Boolean> features = of("camunda-prosecutioncasefile-events-sjp-validation-failed", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final Metadata metadata = metadataBuilder()
                .withName(SJP_VALIDATION_ERROR_IDENTIFIED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();

        final JsonObject eventPayload = getEventPayload(SJP_VALIDATION_ERROR_IDENTIFIED_JSON, CASE_ID, "CASE_ID");

        publicMessageClient.sendMessage(SJP_VALIDATION_ERROR_IDENTIFIED_EVENT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME, CASE_ID);

        final JsonObject processInstances = getProcessInstances(processInstanceList, CASE_ID);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(CASE_ID));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getString("name"), is(SPI_ERROR_IDENTIFIED_BPMN_DISPLAY_NAME));
        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(CASE_ID));
        assertThat(taskVariablesPayloadResponse.getJsonObject("courtCodes").getString("value"), is(COURT_CODE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is(HEARING_DATE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("region").getString("value"), is(REGION_VAL));

        List<String> candidateGroups = BpmRestApiHelper.getCandidateGroups(taskDetails.getString("id"));
        assertThat(candidateGroups.size(), is(2));
        assertThat(candidateGroups.containsAll(List.of("CTSC Admin", "Operational Delivery Admin")), is(true));
    }

    @Test
    public void shouldStartProcessSPIErrorWhenDefendantValidationError() {
        final ImmutableMap<String, Boolean> features = of("camunda-prosecutioncasefile-events-defendant-validation-failed", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final Metadata metadata = metadataBuilder()
                .withName(DEFENDANT_VALIDATION_ERROR_IDENTIFIED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();

        final JsonObject eventPayload = getEventPayload(DEFENDANT_VALIDATION_ERROR_IDENTIFIED_JSON, CASE_ID, "CASE_ID");

        publicMessageClient.sendMessage(DEFENDANT_VALIDATION_ERROR_IDENTIFIED_EVENT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(SPI_ERROR_IDENTIFIED_BPMN_PROCESS_NAME, CASE_ID);

        final JsonObject processInstances = getProcessInstances(processInstanceList, CASE_ID);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(CASE_ID));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getString("name"), is(SPI_ERROR_IDENTIFIED_BPMN_DISPLAY_NAME));
        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(CASE_ID));
        assertThat(taskVariablesPayloadResponse.getJsonObject("courtCodes").getString("value"), is(COURT_CODE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is(HEARING_DATE));
        assertThat(taskVariablesPayloadResponse.getJsonObject("region").getString("value"), is(REGION_VAL));

        List<String> candidateGroups = BpmRestApiHelper.getCandidateGroups(taskDetails.getString("id"));
        assertThat(candidateGroups.size(), is(2));
        assertThat(candidateGroups.containsAll(List.of("CTSC Admin", "Operational Delivery Admin")), is(true));
    }

    @Test
    public void shouldStartProcessCorrespondenceProcess() {
        final ImmutableMap<String, Boolean> features = of("camunda-prosecutioncasefile-events-sjp-validation-failed", true, "camunda-correspondence-event-outbound-correspondence-notification-failed", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final Metadata metadata = metadataBuilder()
                .withName(CORRESPONDENCE_FAILURE_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();

        final JsonObject eventPayload = getEventPayload(CORRESPONDENCE_FAILURE_JSON, DOCUMENT_ID, "DOCUMENT_ID", CORRESPONDENCE_ID);

        publicMessageClient.sendMessage(CORRESPONDENCE_FAILURE_EVENT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(CORRESPONDENCE_FAILURE_BPMN_PROCESS_NAME, CORRESPONDENCE_ID);

        final JsonObject processInstances = getProcessInstances(processInstanceList, CORRESPONDENCE_ID);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(CORRESPONDENCE_ID));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getString("name"), is(CORRESPONDENCE_FAILURE_BPMN_DISPLAY_NAME));
        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(CORRESPONDENCE_FAILURE_ID_TAG).getString("value"), is(CORRESPONDENCE_ID));

        List<String> candidateGroups = BpmRestApiHelper.getCandidateGroups(taskDetails.getString("id"));
        assertThat(candidateGroups.size(), is(2));
        assertThat(candidateGroups.containsAll(List.of("CTSC Admin", "Operational Delivery Admin")), is(true));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String idType) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put(idType, id)
                        .build());
    }

    private JsonObject getEventPayload(final String fileName, final String id, final String idType, final String correspondenceId) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put(idType, id)
                        .put(DRAFT_CORRESPONDENCE_ID, correspondenceId)
                        .build());
    }
}
