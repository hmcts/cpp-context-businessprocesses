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
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubDocumentTypes;
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

public class DocumentEventIT {

    private static final String TRIAGE_INCOMING_DOCUMENT_EVENT = "public.progression.document-added";
    private static final String TRIAGE_INCOMING_DOCUMENT_JSON = "public.progression.document-added.json";
    public static final String TRIAGE_INCOMING_DOCUMENT_IDENTIFIED = "triage_incoming_document_identified";
    private static final String CASE_ID = randomUUID().toString();
    private static final String USER_ID = randomUUID().toString();
    private static final String TRIAGE_INCOMING_DOCUMENT_BPMN_DISPLAY_NAME = "Triage_incoming_document";
    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();

    @BeforeEach
    public void setUp() throws IOException {
        stubDocumentTypes();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
        stubProsecutionCase(CASE_ID);
        publicMessageClient.startProducer("public.event");
    }

    @Test
    public void shouldStartProcessTriageIncomingDocumentWhenDocumentUploaded() {
        final ImmutableMap<String, Boolean> features = of("camunda-progression-document-added", true);
        stubFeaturesFor("businessprocesses", features);

        final Metadata metadata = metadataBuilder()
                .withName(TRIAGE_INCOMING_DOCUMENT_EVENT)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .build();

        final JsonObject eventPayload = getEventPayload(TRIAGE_INCOMING_DOCUMENT_JSON, CASE_ID, "CASE_ID");

        publicMessageClient.sendMessage(TRIAGE_INCOMING_DOCUMENT_EVENT, eventPayload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(TRIAGE_INCOMING_DOCUMENT_IDENTIFIED, CASE_ID);

        final JsonObject processInstances = getProcessInstances(processInstanceList, CASE_ID);
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(CASE_ID));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(taskId -> getTaskDetails(taskId))
                .orElse(null);

        assertThat(taskDetails.getString("name"), is(TRIAGE_INCOMING_DOCUMENT_BPMN_DISPLAY_NAME));
        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseId").getString("value"), is(CASE_ID));

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
}
