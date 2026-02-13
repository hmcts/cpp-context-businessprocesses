package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static com.google.common.io.Resources.getResource;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskDetails;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getVariablesLocal;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.CASE_URN;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.COURT_CODES;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.CUSTODY_TIME_LIMIT;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.DUE_DATE;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.HEARING_DATE;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.HEARING_ID;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.HEARING_TYPE;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.LAST_UPDATED_BY_ID;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.LAST_UPDATED_BY_NAME;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.PREVIOUS_DUE;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.PREVIOUS_WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.TASK_TYPE_ID;
import static uk.gov.moj.cpp.businessprocesses.helper.Constants.WORK_QUEUE;
import static uk.gov.moj.cpp.businessprocesses.helper.FileUtil.getFileContentAsJson;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubIsCaseSensitive;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubResultDefinition;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubResultDefinitionSensitive;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubResultDefinitionSeriousBail;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubCourtRoomName;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import javax.jms.MessageConsumer;
import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HearingResultedProcessorIT {
    public static final String PUBLIC_HEARING_RESULTED_EVENT = "public.events.hearing.hearing-resulted";
    private static final String USER_ID = randomUUID().toString();
    public static final String REFERENCE = "reference";
    public static final String TYPE = "type";
    public static final String LIST_BAIL_APPEAL_HEARING = "list_bail_appeal_hearing";
    public static final String LIST_BAIL_APPEAL_HEARING_PROCESS = "list_bail_appeal_hearing_process";
    public static final String LIST_MURDER_CASE_FOR_BAIL_HEARING = "List Murder Case For Bail Hearing";
    public static final String LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS = "list_murder_case_for_bail_hearing_process";
    public static final String TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING = "list_murder_case_for_bail_hearing";
    public static final String UPDATE_ACCESS_TO_SENSITIVE_CASE = "update_access_to_sensitive_case";

    private static final String JMS_TOPIC_PREFIX = "jms.topic.";

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();
    private static MessageConsumer processTaskCreatedConsumer;

    public static final String PUBLIC_HEARING_HEARING_RESULTED = "hearing-hearing-resulted.json";

    public static final String HEARING_RESULTED_WELSH_CASE = "hearing-resulted-welsh-case.json";

    public static final String PUBLIC_HEARING_HEARING_RESULTED_NEXT_HEARING = "hearing-resulted-sensitive.json";

    private final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private Metadata metadata;

    private static final String BUSINESS_PROCESSES_CONTEXT = "businessprocesses";

    @BeforeEach
    public void setUp() throws IOException {
        metadata = metadataBuilder()
                .withName(PUBLIC_HEARING_RESULTED_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();
        publicMessageClient.startProducer("public.event");
        reset();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        stubCourtRoomName();
        processTaskCreatedConsumer = QueueUtil.businessProcessesEvents.createConsumer("businessprocesses.event.task-created");
        QueueUtil.removeMessagesFromQueue(processTaskCreatedConsumer);
    }

    @Test
    void shouldStartListBailAppealHearing() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-hearing-resulted", true, "list_bail_appeal_hearing", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseUrn1 = "CASE_URN_" + new Random().nextInt(1000);
        final String caseUrn2 = "CASE_URN_" + new Random().nextInt(1000);
        final String caseUrn3 = "CASE_URN_" + new Random().nextInt(1000);
        final String hearingId = randomUUID().toString();

        final JsonObject payload = getEventPayload(PUBLIC_HEARING_HEARING_RESULTED, hearingId, caseUrn1, randomUUID().toString(), "INACTIVE", caseUrn2, randomUUID().toString(), "INACTIVE", caseUrn3, randomUUID().toString(), "ACTIVE");
        stubResultDefinition("7a0932f5-8264-412e-a83c-2b6d1dce1506");
        publicMessageClient.sendMessage(PUBLIC_HEARING_RESULTED_EVENT, payload, metadata);

        final JsonPath jsonResponse = QueueUtil.retrieveMessage(processTaskCreatedConsumer);

        assertThat(jsonResponse, is(notNullValue()));
        assertThat(jsonResponse.getString(TYPE), is(LIST_BAIL_APPEAL_HEARING));

        final JsonArray processInstanceList = getProcessInstanceList(LIST_BAIL_APPEAL_HEARING_PROCESS, caseUrn1);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, caseUrn1);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(caseUrn1));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(LIST_BAIL_APPEAL_HEARING));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(LIST_BAIL_APPEAL_HEARING));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_DATE).getString("value"), is("2018-09-28T12:13:00.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_TYPE).getString("value"), is("Bail Application"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(CASE_URN).getString("value"), is(caseUrn1));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_ID).getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject(WORK_QUEUE).getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(DUE_DATE).getString("value"), is(calculateDueDate(0)));
        assertThat(taskVariablesPayloadResponse.getJsonObject(PREVIOUS_DUE).getString("value"), is(calculateDueDate(0)));
        assertThat(taskVariablesPayloadResponse.getJsonObject(PREVIOUS_WORK_QUEUE).getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(TASK_TYPE_ID).getString("value"), is("392f4966-9833-4c7e-b084-75e882a5d190"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(COURT_CODES).getString("value"), is("B62IZ00"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(CUSTODY_TIME_LIMIT).getString("value"), is("2022-03-31"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(LAST_UPDATED_BY_ID).getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject(LAST_UPDATED_BY_NAME).getString("value"), is("SYSTEM"));
    }

//    @Test
//    void shouldCreateInterpreterForWelshActivityAssignToWelshGroup() throws IOException {
//        final ImmutableMap<String, Boolean> features = of("camunda-hearing-hearing-resulted", true, "Book_interpreter_welsh_case", true);
//        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);
//
//        final Metadata metadata = metadataBuilder()
//                .withName(PUBLIC_HEARING_RESULTED_EVENT)
//                .withId(randomUUID())
//                .withUserId(USER_ID)
//                .withClientCorrelationId(randomUUID().toString())
//                .build();
//
//        final JsonObject eventPayload = getEventPayload(HEARING_RESULTED_WELSH_CASE, randomUUID().toString(), randomUUID().toString(), "ACTIVE");
//        stubResultDefinition("7a0932f5-8264-412e-a83c-2b6d1dce1506");
//        publicMessageClient.sendMessage(PUBLIC_HEARING_RESULTED_EVENT, eventPayload, metadata);
//
//        final String taskCreatedEvent = processTaskCreatedConsumer.retrieveMessage(90000).orElse(null);
//
//        assertThat(taskCreatedEvent, is(notNullValue()));
//
//        final JsonObject taskCreatedEventJsonObject = stringToJsonObjectConverter.convert(taskCreatedEvent);
//        assertThat(taskCreatedEventJsonObject.getString(REFERENCE), is("TFL98765"));
//        assertThat(taskCreatedEventJsonObject.getString(TYPE), is("Book interpreter welsh case"));
//        assertThat(taskCreatedEventJsonObject.getString("workQueue"), is("d48961f7-09e1-3c38-b300-baa9d723594c"));
//    }

    @Test
    void shouldStartListSeriousBailAppealHearing() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-hearing-resulted", true, "list_murder_case_for_bail_hearing", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseUrn1 = "CASE_URN_" + new Random().nextInt(1000);
        final String caseUrn2 = "CASE_URN_" + new Random().nextInt(1000);
        final String caseUrn3 = "CASE_URN_" + new Random().nextInt(1000);
        final String hearingId = randomUUID().toString();

        final JsonObject payload = getEventPayload(PUBLIC_HEARING_HEARING_RESULTED, hearingId, caseUrn1, randomUUID().toString(), "INACTIVE", caseUrn2, randomUUID().toString(), "INACTIVE", caseUrn3, randomUUID().toString(), "ACTIVE");
        stubResultDefinitionSeriousBail("7a0932f5-8264-412e-a83c-2b6d1dce1506");
        publicMessageClient.sendMessage(PUBLIC_HEARING_RESULTED_EVENT, payload, metadata);

        final JsonPath jsonResponse = QueueUtil.retrieveMessage(processTaskCreatedConsumer);

        assertThat(jsonResponse, is(notNullValue()));
        assertThat(jsonResponse.getString(TYPE), is(LIST_MURDER_CASE_FOR_BAIL_HEARING));

        final JsonArray processInstanceList = getProcessInstanceList(LIST_MURDER_CASE_FOR_BAIL_HEARING_PROCESS, caseUrn1);
        assertThat(processInstanceList.size(), is(1));

        final JsonObject processInstances = getProcessInstances(processInstanceList, caseUrn1);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(caseUrn1));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is(LIST_MURDER_CASE_FOR_BAIL_HEARING));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(TASK_NAME_LIST_MURDER_CASE_FOR_BAIL_HEARING));
        assertThat(taskDetails.getString("due"), notNullValue());

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_DATE).getString("value"), is("2018-09-28T12:13:00.000Z"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_TYPE).getString("value"), is("Bail Application"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(CASE_URN).getString("value"), is(caseUrn1));
        assertThat(taskVariablesPayloadResponse.getJsonObject(HEARING_ID).getString("value"), is(hearingId));
        assertThat(taskVariablesPayloadResponse.getJsonObject(WORK_QUEUE).getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(DUE_DATE).getString("value"), is(calculateDueDate(0)));
        assertThat(taskVariablesPayloadResponse.getJsonObject(PREVIOUS_DUE).getString("value"), is(calculateDueDate(0)));
        assertThat(taskVariablesPayloadResponse.getJsonObject(PREVIOUS_WORK_QUEUE).getString("value"), is("66567e81-931e-337e-a5b3-47e0b221bcc7"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(TASK_TYPE_ID).getString("value"), is("d5d09d44-f50b-4c77-b6d1-424062e91758"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(COURT_CODES).getString("value"), is("B62IZ00"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(CUSTODY_TIME_LIMIT).getString("value"), is("2022-03-31"));
        assertThat(taskVariablesPayloadResponse.getJsonObject(LAST_UPDATED_BY_ID).getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject(LAST_UPDATED_BY_NAME).getString("value"), is("SYSTEM"));
    }

    @Test
    void shouldStartSensitiveCaseHearing() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-hearing-hearing-resulted", true, "update_access_to_sensitive_case", true);
        stubFeaturesFor(BUSINESS_PROCESSES_CONTEXT, features);

        final String caseUrn = "TFL98765";

        final JsonObject payload = getEventPayload(PUBLIC_HEARING_HEARING_RESULTED_NEXT_HEARING, randomUUID().toString(), caseUrn, randomUUID().toString(), "INACTIVE", caseUrn, randomUUID().toString(), "INACTIVE", caseUrn, randomUUID().toString(), "ACTIVE");
        stubResultDefinitionSensitive("7a0932f5-8264-412e-a83c-2b6d1dce1506");
        stubIsCaseSensitive("55df6649-8c4d-4614-a1db-a54b70cdc306", getJsonStringFromResource("permissions.json"));
        publicMessageClient.sendMessage(PUBLIC_HEARING_RESULTED_EVENT, payload, metadata);
        final JsonPath jsonResponse = QueueUtil.retrieveMessage(processTaskCreatedConsumer);

        assertThat(jsonResponse, is(notNullValue()));
        assertThat(jsonResponse.getString(REFERENCE), is("76GD6351219"));
        assertThat(jsonResponse.getString(TYPE), is(UPDATE_ACCESS_TO_SENSITIVE_CASE));
    }

    public static String getJsonStringFromResource(final String path) throws IOException {
        return Resources.toString(getResource(path), defaultCharset()).replaceAll("HEARING_ID", randomUUID().toString());
    }

    private JsonObject getEventPayload(final String fileName, final String hearingId, final String caseUrn1, final String caseId1, final String caseId1Status, final String caseUrn2, final String caseId2, final String caseId2Status, final String caseUrn3, final String caseId3, final String caseId3Status) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("CASE_URN_1", caseUrn1)
                        .put("CASE_URN_2", caseUrn2)
                        .put("CASE_URN_3", caseUrn3)
                        .put("HEARING_ID", hearingId)
                        .put("CASE_ID1", caseId1)
                        .put("CASE_ID2", caseId2)
                        .put("CASE_ID3", caseId3)
                        .put("CASE_ID1_STATUS", caseId1Status)
                        .put("CASE_ID2_STATUS", caseId2Status)
                        .put("CASE_ID3_STATUS", caseId3Status)
                        .build());
    }

    private JsonObject getEventPayload(final String fileName, final String hearingId, final String caseId1, final String caseId1Status) {
        return getFileContentAsJson(fileName,
                ImmutableMap.<String, Object>builder()
                        .put("HEARING_ID", hearingId)
                        .put("CASE_ID1", caseId1)
                        .put("CASE_ID1_STATUS", caseId1Status)
                        .build());
    }

    private void verifyActivityType(final String type) {
        await().pollInterval(ONE_SECOND).atMost(ONE_MINUTE).until(() ->
                QueueUtil.retrieveMessage(processTaskCreatedConsumer).getString(TYPE), is(type));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

    public static String calculateDueDate(int daysToAdd) {
        return LocalDate.now().plusDays(daysToAdd).format(DateTimeFormatter.ISO_LOCAL_DATE) + "T23:59:59.000Z";
    }
}
