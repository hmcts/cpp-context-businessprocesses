package uk.gov.moj.cpp.businessprocesses.it;

import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.google.common.collect.ImmutableMap.of;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getProcessInstanceList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getTaskList;
import static uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper.getVariablesLocal;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubPublicHolidays;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkQueueForQueueName;
import static uk.gov.moj.cpp.businessprocesses.stub.ReferenceDataServiceStub.stubWorkflowTaskTypes;
import static uk.gov.moj.cpp.businessprocesses.util.JsonUtil.getJsonStringFromResource;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.moj.cpp.businessprocesses.helper.BpmRestApiHelper;
import uk.gov.moj.cpp.businessprocesses.helper.CustomMessageProducerClient;

import java.io.IOException;
import java.util.UUID;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormDocumentFinaliseIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormDocumentFinaliseIT.class);

    public static final String PUBLIC_PROGRESSION_FORM_FINALISED = "public.progression.form-finalised";
    private static final String USER_ID = randomUUID().toString();
    private static final String BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST_PROCESS_NAME = "bcm_welsh_language_translation_request";
    private static final String REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT_TASK_NAME = "Request_WLU_to_translate_welsh_document";

    private final CustomMessageProducerClient publicMessageClient = new CustomMessageProducerClient();
    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    @BeforeEach
    public void setUp() throws IOException {
        LOGGER.info("Setting up test");
        publicMessageClient.startProducer("public.event");
        reset();
        stubWorkflowTaskTypes();
        stubWorkQueueForQueueName(fromString("66567e81-931e-337e-a5b3-47e0b221bcc7"), "work_queue_name");
        stubPublicHolidays();
    }

    @Test
    public void shouldStartProcessAndVerifyCreatedEvent() throws IOException {
        final ImmutableMap<String, Boolean> features = of("camunda-progression-form-finalised", true);
        stubFeaturesFor("businessprocesses", features);

        final UUID courtFormId = randomUUID();
        final UUID caseId = randomUUID();

        final Metadata metadata = metadataBuilder()
                .withName(PUBLIC_PROGRESSION_FORM_FINALISED)
                .withId(randomUUID())
                .withUserId(USER_ID)
                .withClientCorrelationId(randomUUID().toString())
                .build();
        final String inputEvent = getJsonStringFromResource("finalised-form-event-data.json")
                .replaceAll("%caseId%", caseId.toString())
                .replaceAll("%courtFormId%", courtFormId.toString())
                .replaceAll("%userId%", USER_ID);
        final JsonObject payload = stringToJsonObjectConverter.convert(inputEvent);

        publicMessageClient.sendMessage(PUBLIC_PROGRESSION_FORM_FINALISED, payload, metadata);

        final JsonArray processInstanceList = getProcessInstanceList(BCM_WELSH_LANGUAGE_TRANSLATION_REQUEST_PROCESS_NAME, courtFormId.toString());

        final JsonObject processInstances = getProcessInstances(processInstanceList, courtFormId.toString());
        final String processInstanceId = processInstances.getString("id");

        assertThat(processInstances.getString("businessKey"), is(courtFormId.toString()));
        assertThat(processInstanceId, is(notNullValue()));

        final JsonObject taskDetails = getTaskList(processInstanceId).stream()
                .findFirst()
                .map(BpmRestApiHelper::getTaskDetails)
                .orElse(null);

        assertThat(taskDetails.getBoolean("suspended"), is(false));
        assertThat(taskDetails.getString("processDefinitionId"), is(notNullValue()));
        assertThat(taskDetails.getString("name"), is("Request WLU to translate welsh document"));
        assertThat(taskDetails.getString("id"), is(notNullValue()));
        assertThat(taskDetails.getString("taskDefinitionKey"), is(REQUEST_WLU_TO_TRANSLATE_WELSH_DOCUMENT_TASK_NAME));

        final JsonObject taskVariablesPayloadResponse = getVariablesLocal(taskDetails.getString("id"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("caseURN").getString("value"), is("CASEURN123456789"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskName").getString("value"), is("Request WLU to translate welsh document"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByID").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("lastUpdatedByName").getString("value"), is("SYSTEM"));
        assertThat(taskVariablesPayloadResponse.getJsonObject("taskTypeId").getString("value"), is(notNullValue()));
        assertThat(taskVariablesPayloadResponse.getJsonObject("hearingDate").getString("value"), is("2025-12-22T10:45:00Z"));
    }

    private JsonObject getProcessInstances(final JsonArray processInstances, final String businessKey) {
        return !processInstances.isEmpty() && processInstances.getJsonObject(0).getString("businessKey").equals(businessKey) ? processInstances.getJsonObject(0) : null;
    }

}
